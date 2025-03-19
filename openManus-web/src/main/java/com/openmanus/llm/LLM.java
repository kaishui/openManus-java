package com.openmanus.llm;

import com.openmanus.schema.Message;
import com.openmanus.schema.ToolCall;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.codec.BodyCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.mesh.web.core.VertxSpringApplication.vertx;

@Slf4j
@Component
public class LLM {
  private final WebClient client;
  private final String model;
  private final int maxTokens;
  private final double temperature;
  private final String apiKey;
  private final String baseUrl;
  private final String token ="";


  public LLM() {
    WebClientOptions options = new WebClientOptions().setSsl(true).setTrustAll(true);
    this.client = WebClient.create(vertx, options);
    // Load configuration from environment variables or a config file
    this.model = System.getenv().getOrDefault("OPENAI_MODEL", "gpt-4o-mini");
    this.maxTokens = Integer.parseInt(System.getenv().getOrDefault("OPENAI_MAX_TOKENS", "4096"));
    this.temperature = Double.parseDouble(System.getenv().getOrDefault("OPENAI_TEMPERATURE", "0.7"));
    this.apiKey = System.getenv().getOrDefault("OPENAI_API_KEY", token);
    this.baseUrl = System.getenv().getOrDefault("OPENAI_BASE_URL", "https://api.openai.com/v1");
  }

  public Future<String> ask(List<JsonObject> messages) {
    return ask(messages, null, false);
  }

  public Future<String> ask(List<JsonObject> messages, List<JsonObject> systemMessages, boolean stream) {
    Promise<String> promise = Promise.promise();
    JsonObject requestBody = new JsonObject()
      .put("model", model)
      .put("messages", new JsonArray(messages))
      .put("max_tokens", maxTokens)
      .put("temperature", temperature)
      .put("stream", stream);

    client.postAbs(baseUrl + "/chat/completions")
      .putHeader("Content-Type", "application/json")
      .putHeader("Authorization", "Bearer " + apiKey)
      .as(BodyCodec.string())
      .sendJsonObject(requestBody)
      .onSuccess(response -> {
        if (response.statusCode() == 200) {
          JsonObject body = response.bodyAsJsonObject();
          JsonArray choices = body.getJsonArray("choices");
          if (choices != null && !choices.isEmpty()) {
            JsonObject choice = choices.getJsonObject(0);
            JsonObject message = choice.getJsonObject("message");
            String content = message.getString("content");
            promise.complete(content);
          } else {
            promise.fail("Empty or invalid response from LLM");
          }
        } else {
          promise.fail("LLM request failed with status code: " + response.statusCode());
        }
      })
      .onFailure(promise::fail);

    return promise.future();
  }

  public Future<Message> ask_tool(List<JsonObject> messages, List<JsonObject> tools) {
    return ask_tool(messages, tools, "auto");
  }

  public Future<Message> ask_tool(List<JsonObject> messages, List<JsonObject> tools, String toolChoice) {
    Promise<Message> promise = Promise.promise();
    JsonObject requestBody = new JsonObject()
      .put("model", model)
      .put("messages", new JsonArray(messages))
      .put("max_tokens", maxTokens)
      .put("temperature", temperature)
      .put("tools", new JsonArray(tools))
      .put("tool_choice", toolChoice);

    client.postAbs(baseUrl + "/chat/completions")
      .putHeader("Content-Type", "application/json")
      .putHeader("Authorization", "Bearer " + apiKey)
      .as(BodyCodec.jsonObject())
      .sendJsonObject(requestBody)
      .onSuccess(response -> {
        if (response.statusCode() == 200) {
          JsonObject body = response.body();
          JsonArray choices = body.getJsonArray("choices");
          if (choices != null && !choices.isEmpty()) {
            JsonObject choice = choices.getJsonObject(0);
            JsonObject message = choice.getJsonObject("message");
            String content = message.getString("content");
            JsonArray toolCallsJson = message.getJsonArray("tool_calls");
            List<ToolCall> toolCalls = new ArrayList<>();
            if (toolCallsJson != null) {
              for (int i = 0; i < toolCallsJson.size(); i++) {
                JsonObject toolCallJson = toolCallsJson.getJsonObject(i);
                ToolCall toolCall = ToolCall.fromJson(toolCallJson);
                toolCalls.add(toolCall);
              }
            }
            Message result = new Message();
            result.setContent(Optional.ofNullable(content));
            result.setToolCalls(Optional.ofNullable(toolCalls));
            promise.complete(result);
          } else {
            promise.fail("Empty or invalid response from LLM");
          }
        } else {
          promise.fail("LLM request failed with status code: " + response.statusCode());
        }
      })
      .onFailure(handler -> {
        log.warn("Error", handler.getCause());
        promise.fail(handler.getCause());
      });

    return promise.future();
  }
}
