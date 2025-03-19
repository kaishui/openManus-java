package com.openmanus.flow;

import com.openmanus.agent.BaseAgent;
import com.openmanus.agent.PlanningAgent;
import com.openmanus.agent.ToolCallAgent;
import com.openmanus.llm.LLM;
import com.openmanus.schema.AgentState;
import com.openmanus.schema.Message;
import com.openmanus.schema.ToolCall;
import com.openmanus.tool.PlanningTool;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class PlanningFlow extends BaseFlow {
  private LLM llm;
  private PlanningTool planningTool;
  private List<String> executorKeys;
  private String activePlanId;
  private Integer currentStepIndex;
  private Map<String, BaseAgent> agents;
  private Optional<BaseAgent> primaryAgent;

  @Autowired
  public PlanningFlow(Map<String, BaseAgent> agents, LLM llm, PlanningTool planningTool) {
    this.llm = llm;
    this.planningTool = planningTool;
    this.executorKeys = new ArrayList<>(agents == null ? new ArrayList<>() : agents.keySet());
    this.activePlanId = "plan_" + Instant.now().getEpochSecond();
    this.primaryAgent =  Optional.of(agents.get("planningAgent"));
  }

  private BaseAgent getExecutor(String stepType) {
    if (stepType != null && agents.containsKey(stepType)) {
      return agents.get(stepType);
    }
    for (String key : executorKeys) {
      if (agents.containsKey(key)) {
        return agents.get(key);
      }
    }
    return primaryAgent.orElseThrow(() -> new IllegalStateException("No primary agent available"));
  }

  @Override
  public Future<String> execute(String input) {
    Promise<String> promise = Promise.promise();
    if ( primaryAgent.isEmpty()) {
      agents.forEach((key, agent) -> {
        if (agent.getState() == AgentState.RUNNING) {
          primaryAgent = Optional.of(agent);
        } else {
          executorKeys.add(key);
        }
      });
    }
    createInitialPlan(input)
      .compose(v -> executeLoop())
      .onSuccess(promise::complete)
      .onFailure(promise::fail);
    return promise.future();
  }

  private Future<String> executeLoop() {
    Promise<String> promise = Promise.promise();
    executeNextStep()
      .compose(result -> {
        if ("Plan completed".equals(result)) {
          return finalizePlan();
        } else {
          return executeLoop();
        }
      })
      .onSuccess(promise::complete)
      .onFailure(promise::fail);
    return promise.future();
  }

  private Future<String> executeNextStep() {
    Promise<String> promise = Promise.promise();
    getCurrentStepInfo()
      .onSuccess(stepInfo -> {
        if (currentStepIndex == null) {
          promise.complete("Plan completed");
        } else {
          String stepType = stepInfo.getString("type");
          BaseAgent executor = getExecutor(stepType);
          executeStep(executor, stepInfo)
            .onSuccess(result -> {
              if (executor.getState() == AgentState.FINISHED) {
                promise.complete("Plan completed");
              } else {
                promise.complete("Step executed");
              }
            })
            .onFailure(promise::fail);
        }
      })
      .onFailure(promise::fail);
    return promise.future();
  }

  private Future<JsonObject> getCurrentStepInfo() {
    Promise<JsonObject> promise = Promise.promise();
    if (activePlanId == null || !planningTool.getPlans().containsKey(activePlanId)) {
      log.error("Plan with ID {} not found", activePlanId);
      promise.fail("Plan with ID " + activePlanId + " not found");
      return promise.future();
    }
    JsonObject planData = planningTool.getPlans().get(activePlanId);
    JsonArray steps = planData.getJsonArray("steps");
    JsonArray stepStatuses = planData.getJsonArray("step_statuses");
    for (int i = 0; i < steps.size(); i++) {
      String status;
      if (i >= stepStatuses.size()) {
        status = "not_started";
      } else {
        status = stepStatuses.getString(i);
      }
      if ("not_started".equals(status) || "in_progress".equals(status)) {
        currentStepIndex = i;
        JsonObject stepInfo = new JsonObject().put("text", steps.getString(i));
        String step = steps.getString(i);
        java.util.regex.Matcher typeMatcher = java.util.regex.Pattern.compile("\\[([A-Z_]+)\\]").matcher(step);
        if (typeMatcher.find()) {
          stepInfo.put("type", typeMatcher.group(1).toLowerCase());
        }
        planningTool.execute(new JsonObject()
            .put("command", "mark_step")
            .put("plan_id", activePlanId)
            .put("step_index", i)
            .put("step_status", "in_progress"))
          .onSuccess(result -> promise.complete(stepInfo))
          .onFailure(promise::fail);
        return promise.future();
      }
    }
    currentStepIndex = null;
    promise.complete(new JsonObject());
    return promise.future();
  }

  private Future<String> executeStep(BaseAgent executor, JsonObject stepInfo) {
    Promise<String> promise = Promise.promise();
    getPlanText()
      .onSuccess(planStatus -> {
        String stepText = stepInfo.getString("text", "Step " + currentStepIndex);
        String stepPrompt = String.format("""
          CURRENT PLAN STATUS:
          %s

          YOUR CURRENT TASK:
          You are now working on step %d: "%s"

          Please execute this step using the appropriate tools. When you're done, provide a summary of what you accomplished.
          """, planStatus, currentStepIndex, stepText);
        executor.run(stepPrompt)
          .onSuccess(stepResult -> markStepCompleted()
            .onSuccess(v -> promise.complete(stepResult))
            .onFailure(promise::fail))
          .onFailure(promise::fail);
      })
      .onFailure(promise::fail);
    return promise.future();
  }

  private Future<Void> markStepCompleted() {
    Promise<Void> promise = Promise.promise();
    if (currentStepIndex == null) {
      promise.complete();
      return promise.future();
    }
    planningTool.execute(new JsonObject()
        .put("command", "mark_step")
        .put("plan_id", activePlanId)
        .put("step_index", currentStepIndex)
        .put("step_status", "completed"))
      .onSuccess(result -> {
        log.info("Marked step {} as completed in plan {}", currentStepIndex, activePlanId);
        promise.complete();
      })
      .onFailure(throwable -> {
        log.warn("Failed to update plan status: {}", throwable.getMessage());
        if (activePlanId != null && planningTool.getPlans().containsKey(activePlanId)) {
          JsonObject planData = planningTool.getPlans().get(activePlanId);
          JsonArray stepStatuses = planData.getJsonArray("step_statuses");
          while (stepStatuses.size() <= currentStepIndex) {
            stepStatuses.add("not_started");
          }
          stepStatuses.set(currentStepIndex, "completed");
          planData.put("step_statuses", stepStatuses);
        }
        promise.complete();
      });
    return promise.future();
  }

  private Future<String> getPlanText() {
    Promise<String> promise = Promise.promise();
    planningTool.execute(new JsonObject()
        .put("command", "get")
        .put("plan_id", activePlanId))
      .onSuccess(result -> {
        JsonObject toolResultJson = (JsonObject) result;
        promise.complete(toolResultJson.getString("output"));
      })
      .onFailure(throwable -> {
        log.error("Error getting plan: {}", throwable.getMessage());
        promise.complete(generatePlanTextFromStorage());
      });
    return promise.future();
  }

  private String generatePlanTextFromStorage() {
    try {
      if (activePlanId == null || !planningTool.getPlans().containsKey(activePlanId)) {
        return "Error: Plan with ID " + activePlanId + " not found";
      }
      JsonObject planData = planningTool.getPlans().get(activePlanId);
      String title = planData.getString("title", "Untitled Plan");
      JsonArray steps = planData.getJsonArray("steps");
      JsonArray stepStatuses = planData.getJsonArray("step_statuses");
      JsonArray stepNotes = planData.getJsonArray("step_notes");
      while (stepStatuses.size() < steps.size()) {
        stepStatuses.add("not_started");
      }
      while (stepNotes.size() < steps.size()) {
        stepNotes.add("");
      }
      Map<String, Integer> statusCounts = new HashMap<>();
      statusCounts.put("completed", 0);
      statusCounts.put("in_progress", 0);
      statusCounts.put("blocked", 0);
      statusCounts.put("not_started", 0);
      for (int i = 0; i < stepStatuses.size(); i++) {
        String status = stepStatuses.getString(i);
        statusCounts.put(status, statusCounts.getOrDefault(status, 0) + 1);
      }
      int completed = statusCounts.get("completed");
      int total = steps.size();
      double progress = total > 0 ? (double) completed / total * 100 : 0;
      StringBuilder planText = new StringBuilder();
      planText.append("Plan: ").append(title).append(" (ID: ").append(activePlanId).append(")\n");
      for (int i = 0; i < ("Plan: " + title + " (ID: " + activePlanId + ")").length(); i++) {
        planText.append("=");
      }
      planText.append("\n\n");
      planText.append("Progress: ").append(completed).append("/").append(total).append(" steps completed (").append(String.format("%.1f", progress)).append("%)\n");
      planText.append("Status: ").append(statusCounts.get("completed")).append(" completed, ").append(statusCounts.get("in_progress")).append(" in progress, ").append(statusCounts.get("blocked")).append(" blocked, ").append(statusCounts.get("not_started")).append(" not started\n\n");
      planText.append("Steps:\n");
      for (int i = 0; i < steps.size(); i++) {
        String step = steps.getString(i);
        String status = stepStatuses.getString(i);
        String notes = stepNotes.getString(i);
        String statusMark = switch (status) {
          case "not_started" -> "[ ]";
          case "in_progress" -> "[→]";
          case "completed" -> "[✓]";
          case "blocked" -> "[!]";
          default -> "[ ]";
        };
        planText.append(i).append(". ").append(statusMark).append(" ").append(step).append("\n");
        if (!notes.isEmpty()) {
          planText.append("   Notes: ").append(notes).append("\n");
        }
      }
      return planText.toString();
    } catch (Exception e) {
      log.error("Error generating plan text from storage: {}", e.getMessage());
      return "Error: Unable to retrieve plan with ID " + activePlanId;
    }
  }

  private Future<String> finalizePlan() {
    Promise<String> promise = Promise.promise();
    getPlanText()
      .onSuccess(planText -> {
        Message systemMessage = Message.systemMessage("You are a planning assistant. Your task is to summarize the completed plan.");
        Message userMessage = Message.userMessage(String.format("The plan has been completed. Here is the final plan status:\n\n%s\n\nPlease provide a summary of what was accomplished and any final thoughts.", planText));
        llm.ask(List.of(systemMessage.toJson(), userMessage.toJson()), null, false)
          .onSuccess(response -> promise.complete(String.format("Plan completed:\n\n%s", response)))
          .onFailure(throwable -> {
            log.error("Error finalizing plan with LLM: {}", throwable.getMessage());
            if (primaryAgent.isPresent()) {
              String summaryPrompt = String.format("""
                The plan has been completed. Here is the final plan status:

                %s

                Please provide a summary of what was accomplished and any final thoughts.
                """, planText);
              primaryAgent.get().run(summaryPrompt)
                .onSuccess(summary -> promise.complete(String.format("Plan completed:\n\n%s", summary)))
                .onFailure(promise::fail);
            } else {
              promise.complete("Plan completed. Error generating summary.");
            }
          });
      })
      .onFailure(promise::fail);
    return promise.future();
  }

  private Future<Void> createInitialPlan(String request) {
    Promise<Void> promise = Promise.promise();
    log.info("Creating initial plan with ID: {}", activePlanId);
    Message systemMessage = Message.systemMessage("You are a planning assistant. Your task is to create a detailed plan with clear steps.");
    Message userMessage = Message.userMessage(String.format("Create a detailed plan to accomplish this task: %s", request));
    llm.ask_tool(List.of(systemMessage.toJson(), userMessage.toJson()), List.of(planningTool.getParameters()), "required")
      .onSuccess(response -> {
        if (response.getToolCalls().isPresent()) {
          AtomicBoolean planCreated = new AtomicBoolean(false);
          for (ToolCall toolCall : response.getToolCalls().get()) {
            if ("planning".equals(toolCall.getFunction().getName())) {
              JsonObject toolInput = new JsonObject(toolCall.getFunction().getArguments());
              toolInput.put("plan_id", activePlanId);
              planningTool.execute(toolInput)
                .onSuccess(result -> {
                  log.info("Plan creation result: {}", result);
                  planCreated.set(true);
                  promise.complete();
                })
                .onFailure(promise::fail);
              return;
            }
          }
          if (!planCreated.get()) {
            log.warn("Creating default plan");
            planningTool.execute(new JsonObject()
                .put("command", "create")
                .put("plan_id", activePlanId)
                .put("title", String.format("Plan for: %s", request.substring(0, Math.min(request.length(), 50)) + (request.length() > 50 ? "..." : "")))
                .put("steps", new JsonArray(List.of("Analyze request", "Execute task", "Verify results"))))
              .onSuccess(result -> promise.complete())
              .onFailure(promise::fail);
          }
        } else {
          promise.complete();
        }
      })
      .onFailure(handler -> {
        log.info(" error message: {}", handler.getMessage());
        promise.fail(handler.getMessage());
      });
    return promise.future();
  }

  public Map<String, BaseAgent> getAgents() {
    return agents;
  }

  public Optional<BaseAgent> getPrimaryAgent() {
    return primaryAgent;
  }


  public void addAgent(String name, BaseAgent agent) {
    this.agents.put(name, agent);
    if (!this.primaryAgent.isPresent()) {
      this.primaryAgent = Optional.of(agent);
    }
  }

  public void addAgents(Map<String, BaseAgent> agents) {
    this.agents.putAll(agents);
    if (!this.primaryAgent.isPresent() && !agents.isEmpty()) {
      this.primaryAgent = agents.values().stream().findFirst();
    }
  }
}
