package com.openmanus.schema;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.Getter;

import java.util.List;
import java.util.Optional;

@Getter
public class Message {
  private String role;
  private Optional<String> content = Optional.empty();
  private Optional<List<ToolCall>> toolCalls = Optional.empty();
  private Optional<String> name = Optional.empty();
  private Optional<String> toolCallId = Optional.empty();

  public Message() {
  }

  public Message(String role, String content) {
    this.role = role;
    this.content = Optional.ofNullable(content);
  }

  public Message(String role, String content, List<ToolCall> toolCalls) {
    this.role = role;
    this.content = Optional.ofNullable(content);
    this.toolCalls = Optional.ofNullable(toolCalls);
  }

  public Message(String role, String content, String name, String toolCallId) {
    this.role = role;
    this.content = Optional.ofNullable(content);
    this.name = Optional.ofNullable(name);
    this.toolCallId = Optional.ofNullable(toolCallId);
  }

  public void setRole(String role) {
    this.role = role;
  }

  public void setContent(Optional<String> content) {
    this.content = content;
  }

  public void setToolCalls(Optional<List<ToolCall>> toolCalls) {
    this.toolCalls = toolCalls;
  }

  public void setName(Optional<String> name) {
    this.name = name;
  }

  public void setToolCallId(Optional<String> toolCallId) {
    this.toolCallId = toolCallId;
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    json.put("role", this.role);
    this.content.ifPresent(c -> json.put("content", c));
    this.name.ifPresent(n -> json.put("name", n));
    this.toolCallId.ifPresent(t -> json.put("tool_call_id", t));
    if (this.toolCalls.isPresent()) {
      JsonArray toolCallsJson = new JsonArray();
      for (ToolCall toolCall : this.toolCalls.get()) {
        toolCallsJson.add(toolCall.toJson());
      }
      json.put("tool_calls", toolCallsJson);
    }
    return json;
  }

  public static Message userMessage(String content) {
    return new Message("user", content);
  }

  public static Message systemMessage(String content) {
    return new Message("system", content);
  }

  public static Message assistantMessage(String content) {
    return new Message("assistant", content);
  }

  public static Message toolMessage(String content, String name, String toolCallId) {
    return new Message("tool", content, name, toolCallId);
  }

  public static Message fromToolCalls(String content, List<ToolCall> toolCalls) {
    return new Message("assistant", content, toolCalls);
  }
}
