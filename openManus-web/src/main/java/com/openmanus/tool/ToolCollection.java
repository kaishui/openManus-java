package com.openmanus.tool;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ToolCollection {
  private final Map<String, Tool> toolMap;

  @Autowired
  public ToolCollection(Tool... tools) {
    this.toolMap = new HashMap<>();
    for (Tool tool : tools) {
      this.toolMap.put(tool.getName(), tool);
    }
  }

  public List<JsonObject> toParams() {
    return toolMap.values().stream()
      .map(Tool::getParameters)
      .collect(Collectors.toList());
  }

  public Future<Object> execute(String name, JsonObject toolInput) {
    Tool tool = toolMap.get(name);
    if (tool == null) {
      return Future.failedFuture("Tool " + name + " is invalid");
    }
    return tool.execute(toolInput);
  }

  public Tool getTool(String name) {
    return toolMap.get(name);
  }

  public void addTool(Tool tool) {
    this.toolMap.put(tool.getName(), tool);
  }

  public void addTools(Tool... tools) {
    for (Tool tool : tools) {
      addTool(tool);
    }
  }
}
