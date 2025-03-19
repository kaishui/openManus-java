package com.openmanus.agent;


import com.openmanus.llm.LLM;
import com.openmanus.schema.Function;
import com.openmanus.schema.Message;
import com.openmanus.schema.ToolCall;
import com.openmanus.tool.Tool;
import com.openmanus.tool.ToolCollection;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public abstract class ReActAgent extends BaseAgent {
  protected String systemPrompt = "You are a helpful assistant.";
  protected List<ToolCall> toolCalls = new ArrayList<>();

  public ReActAgent(ToolCollection availableTools, LLM llm) {
    super(availableTools, llm);
  }

  @Override
  public Future<Boolean> think() {
    Promise<Boolean> promise = Promise.promise();
    List<JsonObject> messages = new ArrayList<>();
    messages.add(new JsonObject().put("role", "system").put("content", systemPrompt));
    memory.getMessages().forEach(message -> messages.add(message.toJson()));
    llm.ask_tool(messages, availableTools.toParams())
      .onSuccess(response -> {
        if (response.getToolCalls().isPresent()) {
          List<ToolCall> toolCalls = new ArrayList<>();
          response.getToolCalls().get().forEach(toolCall -> {
            toolCalls.add(toolCall);
          });
          this.toolCalls = toolCalls;
          promise.complete(true);
        } else {
          promise.complete(false);
        }
      })
      .onFailure(promise::fail);
    return promise.future();
  }

  @Override
  public Future<String> act() {
    Promise<String> promise = Promise.promise();
    if (toolCalls.isEmpty()) {
      promise.complete("No tool call");
      return promise.future();
    }
    ToolCall toolCall = toolCalls.remove(0);
    String toolName = toolCall.getFunction().getName();
    String toolArguments = toolCall.getFunction().getArguments();
    JsonObject toolInput = new JsonObject(toolArguments);
    availableTools.execute(toolName, toolInput)
      .onSuccess(result -> {
        String content = result.toString();
        Message toolMessage = Message.toolMessage(content, toolName, toolCall.getId());
        memory.addMessage(toolMessage);
        promise.complete(content);
      })
      .onFailure(promise::fail);
    return promise.future();
  }
}
