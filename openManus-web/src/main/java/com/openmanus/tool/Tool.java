package com.openmanus.tool;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public interface Tool {
  String getName();
  String getDescription();
  JsonObject getParameters();
  Future<Object> execute(JsonObject toolInput);
}
