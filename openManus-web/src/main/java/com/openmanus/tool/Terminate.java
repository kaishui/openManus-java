package com.openmanus.tool;

import com.openmanus.tool.base.ToolResult;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class Terminate implements Tool {

  private static final String TERMINATE_DESCRIPTION = """
            Terminate the interaction when the request is met OR if the assistant cannot proceed further with the task.
            """;

  @Override
  public String getName() {
    return "terminate";
  }

  @Override
  public String getDescription() {
    return TERMINATE_DESCRIPTION;
  }

  @Override
  public JsonObject getParameters() {
    JsonObject parameters = new JsonObject();
    parameters.put("type", "object");
    JsonObject properties = new JsonObject();
    properties.put("status", new JsonObject()
      .put("type", "string")
      .put("description", "The finish status of the interaction.")
      .put("enum", List.of("success", "failure")));
    parameters.put("properties", properties);
    List<String> required = List.of("status");
    parameters.put("required", required);
    return parameters;
  }

  @Override
  public Future<Object> execute(JsonObject toolInput) {
    String status = toolInput.getString("status");
    if (status == null) {
      return Future.succeededFuture(new ToolResult("Status is required").toJson());
    }
    return Future.succeededFuture(new ToolResult("The interaction has been completed with status: " + status).toJson());
  }
}
