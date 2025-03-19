package com.openmanus.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openmanus.tool.base.ToolResult;
import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class CreateChatCompletion implements Tool {

  private static final String CREATE_CHAT_COMPLETION_DESCRIPTION = """
            Creates a structured completion with specified output formatting.
            """;

  private final Class<?> responseType;
  private final List<String> required;
  private final ObjectMapper objectMapper = new ObjectMapper();

  private static final Map<Class<?>, String> TYPE_MAPPING = new HashMap<>() {{
    put(String.class, "string");
    put(Integer.class, "integer");
    put(int.class, "integer");
    put(Double.class, "number");
    put(double.class, "number");
    put(Boolean.class, "boolean");
    put(boolean.class, "boolean");
    put(JsonObject.class, "object");
    put(JsonArray.class, "array");
    put(List.class, "array");
    put(Map.class, "object");
  }};

  // Existing constructor
  public CreateChatCompletion(Class<?> responseType) {
    this.responseType = responseType;
    this.required = List.of("response");
  }

  // New no-argument constructor with a default responseType
  public CreateChatCompletion() {
    this(String.class); // Default to String.class
  }

  @Override
  public String getName() {
    return "create_chat_completion";
  }

  @Override
  public String getDescription() {
    return CREATE_CHAT_COMPLETION_DESCRIPTION;
  }

  @Override
  public JsonObject getParameters() {
    return buildParameters();
  }

  private JsonObject buildParameters() {
    if (responseType == String.class) {
      JsonObject parameters = new JsonObject();
      parameters.put("type", "object");
      JsonObject properties = new JsonObject();
      properties.put("response", new JsonObject()
        .put("type", "string")
        .put("description", "The response text that should be delivered to the user."));
      parameters.put("properties", properties);
      parameters.put("required", new JsonArray(required));
      return parameters;
    }
    return createTypeSchema(responseType);
  }

  private JsonObject createTypeSchema(Type typeHint) {
    Class<?> rawType;
    if (typeHint instanceof Class) {
      rawType = (Class<?>) typeHint;
    } else if (typeHint instanceof ParameterizedType) {
      rawType = (Class<?>) ((ParameterizedType) typeHint).getRawType();
    } else {
      throw new IllegalArgumentException("Unsupported type hint: " + typeHint);
    }

    if (rawType == null) {
      throw new IllegalArgumentException("Unsupported type hint: " + typeHint);
    }

    if (rawType == List.class) {
      Type itemType = ((ParameterizedType) typeHint).getActualTypeArguments()[0];
      JsonObject parameters = new JsonObject();
      parameters.put("type", "object");
      JsonObject properties = new JsonObject();
      properties.put("response", new JsonObject()
        .put("type", "array")
        .put("items", getTypeInfo(itemType)));
      parameters.put("properties", properties);
      parameters.put("required", new JsonArray(required));
      return parameters;
    }

    if (rawType == Map.class) {
      Type valueType = ((ParameterizedType) typeHint).getActualTypeArguments()[1];
      JsonObject parameters = new JsonObject();
      parameters.put("type", "object");
      JsonObject properties = new JsonObject();
      properties.put("response", new JsonObject()
        .put("type", "object")
        .put("additionalProperties", getTypeInfo(valueType)));
      parameters.put("properties", properties);
      parameters.put("required", new JsonArray(required));
      return parameters;
    }

    if (TYPE_MAPPING.containsKey(rawType)) {
      JsonObject parameters = new JsonObject();
      parameters.put("type", "object");
      JsonObject properties = new JsonObject();
      properties.put("response", new JsonObject()
        .put("type", TYPE_MAPPING.get(rawType))
        .put("description", "Response of type " + rawType.getSimpleName()));
      parameters.put("properties", properties);
      parameters.put("required", new JsonArray(required));
      return parameters;
    }

    throw new IllegalArgumentException("Unsupported type hint: " + typeHint);
  }

  private JsonObject getTypeInfo(Type typeHint) {
    Class<?> rawType;
    if (typeHint instanceof Class) {
      rawType = (Class<?>) typeHint;
    } else if (typeHint instanceof ParameterizedType) {
      rawType = (Class<?>) ((ParameterizedType) typeHint).getRawType();
    } else {
      throw new IllegalArgumentException("Unsupported type hint: " + typeHint);
    }
    if (TYPE_MAPPING.containsKey(rawType)) {
      return new JsonObject()
        .put("type", TYPE_MAPPING.get(rawType))
        .put("description", "Value of type " + rawType.getSimpleName());
    }
    return createTypeSchema(typeHint);
  }

  @Override
  public Future<Object> execute(JsonObject toolInput) {
    try {
      Object result = convertType(toolInput);
      return Future.succeededFuture(new ToolResult(String.valueOf(result)).toJson());
    } catch (Exception e) {
      return Future.succeededFuture(new ToolResult("Error: " + e.getMessage()).toJson());
    }
  }

  private Object convertType(JsonObject toolInput) {
    Object result = toolInput.getValue("response");
    if (responseType == String.class) {
      return result.toString();
    }
    if (responseType == Integer.class || responseType == int.class) {
      return Integer.parseInt(result.toString());
    }
    if (responseType == Double.class || responseType == double.class) {
      return Double.parseDouble(result.toString());
    }
    if (responseType == Boolean.class || responseType == boolean.class) {
      return Boolean.parseBoolean(result.toString());
    }
    if (responseType == JsonObject.class) {
      return (JsonObject) result;
    }
    if (responseType == JsonArray.class) {
      return (JsonArray) result;
    }
    if (responseType == List.class) {
      return (List<?>) result;
    }
    if (responseType == Map.class) {
      return (Map<?, ?>) result;
    }
    try {
      return objectMapper.readValue(result.toString(), responseType);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
