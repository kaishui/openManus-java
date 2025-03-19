package com.openmanus.schema;

import io.vertx.core.json.JsonObject;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class ToolCall {
  private String id;
  private String type = "function";
  private Function function;

  public ToolCall() {
  }

  public ToolCall(String id, Function function) {
    this.id = id;
    this.function = function;
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    json.put("id", this.id);
    json.put("type", this.type);
    json.put("function", this.function.toJson());
    return json;
  }
  public static ToolCall fromJson(JsonObject json) {
    ToolCall toolCall = new ToolCall();
    toolCall.setId(json.getString("id"));
    toolCall.setType(json.getString("type"));
    toolCall.setFunction(Function.fromJson(json.getJsonObject("function")));
    return toolCall;
  }
}
