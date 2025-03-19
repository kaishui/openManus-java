package com.openmanus.tool.base;


import io.vertx.core.json.JsonObject;

import java.util.Optional;

public class ToolResult {
  private Optional<String> output = Optional.empty();
  private Optional<String> error = Optional.empty();
  private Optional<String> system = Optional.empty();

  public ToolResult() {
  }


  public ToolResult(String output, String system) {
    this.output = Optional.ofNullable(output);
    this.system = Optional.ofNullable(system);
  }

  public ToolResult(String output, String error, String system) {
    this.output = Optional.ofNullable(output);
    this.error = Optional.ofNullable(error);
    this.system = Optional.ofNullable(system);
  }

  public ToolResult(String error) {
    this.error = Optional.ofNullable(error);
  }

  public Optional<String> getOutput() {
    return output;
  }

  public void setOutput(Optional<String> output) {
    this.output = output;
  }

  public Optional<String> getError() {
    return error;
  }

  public void setError(Optional<String> error) {
    this.error = error;
  }

  public Optional<String> getSystem() {
    return system;
  }

  public void setSystem(Optional<String> system) {
    this.system = system;
  }

  public JsonObject toJson() {
    JsonObject json = new JsonObject();
    this.output.ifPresent(o -> json.put("output", o));
    this.error.ifPresent(e -> json.put("error", e));
    this.system.ifPresent(s -> json.put("system", s));
    return json;
  }
}
