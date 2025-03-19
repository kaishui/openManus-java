package com.openmanus.agent;


import com.openmanus.llm.LLM;
import com.openmanus.schema.AgentState;
import com.openmanus.schema.Message;
import com.openmanus.schema.ToolCall;
import com.openmanus.tool.CreateChatCompletion;
import com.openmanus.tool.Terminate;
import com.openmanus.tool.ToolCollection;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class ToolCallAgent extends ReActAgent {
  protected String systemPrompt = "You are a helpful assistant.";
  protected String nextStepPrompt = "What is the next step to execute?";
  protected List<String> specialToolNames = List.of(new Terminate().getName());
  protected String toolChoices = "auto";
  protected List<ToolCall> toolCalls = new ArrayList<>();

  public ToolCallAgent(ToolCollection availableTools, LLM llm) {
    super(availableTools, llm);
    if (availableTools.getTool("create_chat_completion") == null) {
      availableTools.addTool(new CreateChatCompletion());
    }
  }

  @Override
  public Future<Boolean> think() {
    Promise<Boolean> promise = Promise.promise();
    if (nextStepPrompt != null) {
      memory.addMessage(Message.userMessage(nextStepPrompt));
    }
    List<JsonObject> messages = new ArrayList<>();
    messages.add(new JsonObject().put("role", "system").put("content", systemPrompt));
    memory.getMessages().forEach(message -> messages.add(message.toJson()));
    llm.ask_tool(messages, availableTools.toParams(), toolChoices)
      .onSuccess(response -> {
        if (response.getToolCalls().isPresent()) {
          List<ToolCall> toolCalls = new ArrayList<>();
          response.getToolCalls().get().forEach(toolCall -> {
            toolCalls.add(toolCall);
          });
          this.toolCalls = toolCalls;
        }
        if (toolChoices.equals("none")) {
          if (response.getToolCalls().isPresent()) {
            System.err.println("🤔 Hmm, " + this.getName() + " tried to use tools when they weren't available!");
          }
          if (response.getContent().isPresent()) {
            memory.addMessage(Message.assistantMessage(response.getContent().get()));
            promise.complete(true);
          } else {
            promise.complete(false);
          }
        } else {
          Message assistantMsg = Message.fromToolCalls(response.getContent().orElse(""), this.toolCalls);
          memory.addMessage(assistantMsg);
          if (toolChoices.equals("required") && !this.toolCalls.isEmpty()) {
            promise.complete(true);
          } else if (toolChoices.equals("auto") && this.toolCalls.isEmpty()) {
            promise.complete(response.getContent().isPresent());
          } else {
            promise.complete(!this.toolCalls.isEmpty());
          }
        }
      })
      .onFailure(throwable -> {
        System.err.println("Oops! The " + this.getName() + "'s thinking process hit a snag: " + throwable.getMessage());
        memory.addMessage(Message.assistantMessage("Error encountered while processing: " + throwable.getMessage()));
        promise.complete(false);
      });
    return promise.future();
  }

  @Override
  public Future<String> act() {
    Promise<String> promise = Promise.promise();
    if (toolCalls.isEmpty()) {
      if (toolChoices.equals("required")) {
        promise.fail("Tool calls required but none provided");
        return promise.future();
      }
      if (!memory.getMessages().isEmpty()) {
        promise.complete(memory.getMessages().get(memory.getMessages().size() - 1).getContent().orElse("No content or commands to execute"));
      } else {
        promise.complete("No content or commands to execute");
      }
      return promise.future();
    }
    List<Future> futures = new ArrayList<>();
    for (ToolCall toolCall : toolCalls) {
      Future<String> future = executeTool(toolCall);
      futures.add(future);
    }
    // Corrected line:
    CompositeFuture.all(futures).onSuccess(result -> {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < result.size(); i++) {
        sb.append(Optional.ofNullable(result.resultAt(i)));
        if (i < result.size() - 1) {
          sb.append("\n\n");
        }
      }
      promise.complete(sb.toString());
    }).onFailure(promise::fail);
    return promise.future();
  }

  public Future<String> executeTool(ToolCall command) {
    Promise<String> promise = Promise.promise();
    if (command == null || command.getFunction() == null || command.getFunction().getName() == null) {
      promise.complete("Error: Invalid command format");
      return promise.future();
    }
    String name = command.getFunction().getName();
    if (availableTools.getTool(name) == null) {
      promise.complete("Error: Unknown tool '" + name + "'");
      return promise.future();
    }
    try {
      JsonObject args = new JsonObject(command.getFunction().getArguments() == null ? "{}" : command.getFunction().getArguments());
      System.out.println("🔧 Activating tool: '" + name + "'...");
      availableTools.execute(name, args)
        .onSuccess(result -> {
          String observation = "Observed output of cmd `" + name + "` executed:\n" + result.toString();
          if (result == null) {
            observation = "Cmd `" + name + "` completed with no output";
          }
          _handleSpecialTool(name, result);
          Message toolMsg = Message.toolMessage(result.toString(), name, command.getId());
          memory.addMessage(toolMsg);
          promise.complete(observation);
        })
        .onFailure(throwable -> {
          String errorMsg = "⚠️ Tool '" + name + "' encountered a problem: " + throwable.getMessage();
          System.err.println(errorMsg);
          promise.complete("Error: " + errorMsg);
        });
    } catch (Exception e) {
      String errorMsg = "Error parsing arguments for " + name + ": Invalid JSON format";
      System.err.println("📝 Oops! The arguments for '" + name + "' don't make sense - invalid JSON, arguments:" + command.getFunction().getArguments());
      promise.complete("Error: " + errorMsg);
    }
    return promise.future();
  }

  private void _handleSpecialTool(String name, Object result) {
    if (!_isSpecialTool(name)) {
      return;
    }
    if (_shouldFinishExecution(name, result)) {
      System.out.println("🏁 Special tool '" + name + "' has completed the task!");
      this.setState(AgentState.FINISHED);
    }
  }

  private boolean _shouldFinishExecution(String name, Object result) {
    return true;
  }

  private boolean _isSpecialTool(String name) {
    return specialToolNames.stream().anyMatch(n -> n.equalsIgnoreCase(name));
  }

  public String getSystemPrompt() {
    return systemPrompt;
  }

  public void setSystemPrompt(String systemPrompt) {
    this.systemPrompt = systemPrompt;
  }

  public String getNextStepPrompt() {
    return nextStepPrompt;
  }

  public void setNextStepPrompt(String nextStepPrompt) {
    this.nextStepPrompt = nextStepPrompt;
  }

  public List<String> getSpecialToolNames() {
    return specialToolNames;
  }

  public void setSpecialToolNames(List<String> specialToolNames) {
    this.specialToolNames = specialToolNames;
  }

  public String getToolChoices() {
    return toolChoices;
  }

  public void setToolChoices(String toolChoices) {
    this.toolChoices = toolChoices;
  }

  public List<ToolCall> getToolCalls() {
    return toolCalls;
  }

  public void setToolCalls(List<ToolCall> toolCalls) {
    this.toolCalls = toolCalls;
  }

  public String getName() {
    return "toolcall";
  }

  public String getDescription() {
    return "an agent that can execute tool calls.";
  }
}
