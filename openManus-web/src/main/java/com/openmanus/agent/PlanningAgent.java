package com.openmanus.agent;

import com.openmanus.llm.LLM;
import com.openmanus.schema.Message;
import com.openmanus.schema.ToolCall;
import com.openmanus.tool.PlanningTool;
import com.openmanus.tool.Terminate;
import com.openmanus.tool.ToolCollection;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
@Slf4j
public class PlanningAgent extends ReActAgent {
  private String name = "planning";
  private String description = "An agent that creates and manages plans to solve tasks";
  private String systemPrompt = "You are a planning assistant. Your task is to create a detailed plan with clear steps.";
  private String nextStepPrompt = "What is the next step to execute?";
  private List<String> specialToolNames = List.of(new Terminate().getName());
  private String activePlanId;
  private Map<String, Map<String, Object>> stepExecutionTracker = new HashMap<>();
  private Integer currentStepIndex;
  private Integer maxSteps = 20;

  public PlanningAgent(ToolCollection availableTools, LLM llm) {
    super(availableTools, llm);
    this.activePlanId = "plan_" + Instant.now().getEpochSecond();
    if (availableTools.getTool("planning") == null) {
      availableTools.addTool(new PlanningTool());
    }
  }

  @Override
  public Future<Boolean> think() {
    Promise<Boolean> promise = Promise.promise();
    getPlan().onSuccess(plan -> {
      String prompt = "CURRENT PLAN STATUS:\n" + plan + "\n\n" + nextStepPrompt;
      memory.addMessage(Message.userMessage(prompt));
      _getCurrentStepIndex().onSuccess(currentStepIndex -> {
        this.currentStepIndex = currentStepIndex;
        super.think().onSuccess(result -> {
          if (result && !toolCalls.isEmpty()) {
            ToolCall latestToolCall = toolCalls.get(0);
            if (!latestToolCall.getFunction().getName().equals("planning") && !specialToolNames.contains(latestToolCall.getFunction().getName()) && currentStepIndex != null) {
              Map<String, Object> tracker = new HashMap<>();
              tracker.put("step_index", currentStepIndex);
              tracker.put("tool_name", latestToolCall.getFunction().getName());
              tracker.put("status", "pending");
              stepExecutionTracker.put(latestToolCall.getId(), tracker);
            }
          }
          promise.complete(result);
        }).onFailure(promise::fail);
      }).onFailure(promise::fail);
    }).onFailure(promise::fail);
    return promise.future();
  }

  @Override
  public Future<String> act() {
    Promise<String> promise = Promise.promise();
    super.act().onSuccess(result -> {
      if (!toolCalls.isEmpty()) {
        ToolCall latestToolCall = toolCalls.get(0);
        if (stepExecutionTracker.containsKey(latestToolCall.getId())) {
          Map<String, Object> tracker = stepExecutionTracker.get(latestToolCall.getId());
          tracker.put("status", "completed");
          tracker.put("result", result);
          if (!latestToolCall.getFunction().getName().equals("planning") && !specialToolNames.contains(latestToolCall.getFunction().getName())) {
            updatePlanStatus(latestToolCall.getId());
          }
        }
      }
      promise.complete(result);
    }).onFailure(promise::fail);
    return promise.future();
  }

  public Future<String> getPlan() {
    Promise<String> promise = Promise.promise();
    if (activePlanId == null) {
      promise.complete("No active plan. Please create a plan first.");
      return promise.future();
    }
    availableTools.execute("planning", new JsonObject().put("command", "get").put("plan_id", activePlanId))
      .onSuccess(result -> {
        promise.complete(result.toString());
      })
      .onFailure(promise::fail);
    return promise.future();
  }

  @Override
  public Future<String> run(String request) {
    if (request != null) {
      return createInitialPlan(request).compose(v -> super.run(null));
    } else {
      return super.run(null);
    }
  }

  public void updatePlanStatus(String toolCallId) {
    if (activePlanId == null) {
      return;
    }
    if (!stepExecutionTracker.containsKey(toolCallId)) {
      System.err.println("No step tracking found for tool call " + toolCallId);
      return;
    }
    Map<String, Object> tracker = stepExecutionTracker.get(toolCallId);
    if (!tracker.get("status").equals("completed")) {
      System.err.println("Tool call " + toolCallId + " has not completed successfully");
      return;
    }
    Integer stepIndex = (Integer) tracker.get("step_index");
    availableTools.execute("planning", new JsonObject()
        .put("command", "mark_step")
        .put("plan_id", activePlanId)
        .put("step_index", stepIndex)
        .put("step_status", "completed"))
      .onSuccess(result -> System.out.println("Marked step " + stepIndex + " as completed in plan " + activePlanId))
      .onFailure(throwable -> System.err.println("Failed to update plan status: " + throwable.getMessage()));
  }

  private Future<Integer> _getCurrentStepIndex() {
    Promise<Integer> promise = Promise.promise();
    getPlan().onSuccess(plan -> {
      String[] planLines = plan.split("\n");
      int stepsIndex = -1;
      for (int i = 0; i < planLines.length; i++) {
        if (planLines[i].trim().equals("Steps:")) {
          stepsIndex = i;
          break;
        }
      }
      if (stepsIndex == -1) {
        promise.complete(null);
        return;
      }
      for (int i = 0; i < planLines.length - stepsIndex - 1; i++) {
        String line = planLines[stepsIndex + 1 + i];
        if (line.contains("[ ]") || line.contains("[→]")) {
          int finalI = i;
          availableTools.execute("planning", new JsonObject()
              .put("command", "mark_step")
              .put("plan_id", activePlanId)
              .put("step_index", i)
              .put("step_status", "in_progress"))
            .onSuccess(result -> promise.complete(finalI))
            .onFailure(promise::fail);
          return;
        }
      }
      promise.complete(null);
    }).onFailure(promise::fail);
    return promise.future();
  }

  public Future<Void> createInitialPlan(String request) {
    Promise<Void> promise = Promise.promise();
    System.out.println("Creating initial plan with ID: " + activePlanId);
    List<JsonObject> messages = new ArrayList<>();
    messages.add(Message.userMessage("Analyze the request and create a plan with ID " + activePlanId + ": " + request).toJson());
    memory.addMessage(Message.userMessage("Analyze the request and create a plan with ID " + activePlanId + ": " + request));
    llm.ask_tool(messages, availableTools.toParams())
      .onSuccess(response -> {
        if (response.getToolCalls().isPresent()) {
          List<ToolCall> toolCalls = new ArrayList<>();
          response.getToolCalls().get().forEach(toolCall -> {
            toolCalls.add(toolCall);
          });
          Message assistantMsg = Message.fromToolCalls(response.getContent().orElse(""), toolCalls);
          memory.addMessage(assistantMsg);
          AtomicBoolean planCreated = new AtomicBoolean(false);
          for (ToolCall toolCall : toolCalls) {
            if (toolCall.getFunction().getName().equals("planning")) {
              JsonObject toolInput = new JsonObject(toolCall.getFunction().getArguments());
              availableTools.execute(toolCall.getFunction().getName(), toolInput)
                .onSuccess(result -> {
                  System.out.println("Executed tool " + toolCall.getFunction().getName() + " with result: " + result);
                  Message toolMsg = Message.toolMessage(result.toString(), toolCall.getFunction().getName(), toolCall.getId());
                  memory.addMessage(toolMsg);
                  planCreated.set(true);
                  promise.complete();
                })
                .onFailure(promise::fail);
              return;
            }
          }
          if (!planCreated.get()) {
            System.err.println("No plan created from initial request");
            Message toolMsg = Message.assistantMessage("Error: Parameter `plan_id` is required for command: create");
            memory.addMessage(toolMsg);
            promise.complete();
          }
        } else {
          promise.complete();
        }
      })
      .onFailure(promise::fail);
    return promise.future();
  }
}
