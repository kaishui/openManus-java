package com.openmanus.schema;

import io.vertx.core.json.JsonObject;

public class Function {
  private String name;
  private String arguments;

  public Function() {
  }

  public Function(String name, String arguments) {
    this.name = name;
    this.arguments = arguments;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getArguments() {
    return arguments;
  }

  public void setArguments(String arguments) {
    this.arguments = arguments;
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    json.put("name", this.name);
    json.put("arguments", this.arguments);
    return json;
  }
  public static Function fromJson(JsonObject json) {
    Function function = new Function();
    function.setName(json.getString("name"));
    function.setArguments(json.getString("arguments"));
    return function;
  }
}
