package com.openmanus.tool;

import com.openmanus.tool.base.ToolResult;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.codec.BodyCodec;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;

import java.util.List;

import static com.mesh.web.core.VertxSpringApplication.vertx;

@Component
@Slf4j
public class BrowserUseTool implements Tool {

  private final WebClient client;

  public BrowserUseTool() {
    WebClientOptions options = new WebClientOptions().setSsl(true).setTrustAll(true);
    this.client = WebClient.create(vertx, options);
  }

  @Override
  public String getName() {
    return "browser_use";
  }

  @Override
  public String getDescription() {
    return """
      Interact with a web browser to perform various actions such as navigation, element interaction,
      content extraction, and tab management. Supported actions include:
      - 'navigate': Go to a specific URL
      - 'get_html': Get page HTML content
      """;
  }

  @Override
  public JsonObject getParameters() {
    JsonObject parameters = new JsonObject();
    parameters.put("type", "object");
    JsonObject properties = new JsonObject();
    properties.put("action", new JsonObject()
      .put("type", "string")
      .put("enum", List.of("navigate", "get_html"))
      .put("description", "The browser action to perform"));
    properties.put("url", new JsonObject()
      .put("type", "string")
      .put("description", "URL for 'navigate' action"));
    parameters.put("properties", properties);
    JsonObject dependencies = new JsonObject();
    dependencies.put("navigate", List.of("url"));
    parameters.put("dependencies", dependencies);
    List<String> required = List.of("action");
    parameters.put("required", required);
    return parameters;
  }

  @Override
  public Future<Object> execute(JsonObject toolInput) {
    Promise<Object> promise = Promise.promise();
    String action = toolInput.getString("action");
    String url = toolInput.getString("url");

    switch (action) {
      case "navigate":
        if (url == null) {
          promise.complete(new ToolResult("URL is required for 'navigate' action").toJson());
          return promise.future();
        }
        navigate(url)
          .onSuccess(result -> promise.complete(new ToolResult(result).toJson()))
          .onFailure(promise::fail);
        break;
      case "get_html":
        getHtml(url)
          .onSuccess(result -> promise.complete(new ToolResult(result).toJson()))
          .onFailure(promise::fail);
        break;
      default:
        promise.complete(new ToolResult("Unknown action: " + action).toJson());
    }

    return promise.future();
  }

  private Future<String> navigate(String url) {
    Promise<String> promise = Promise.promise();
    client.getAbs(url)
      .as(BodyCodec.string())
      .send()
      .onSuccess(response -> {
        if (response.statusCode() == 200) {
          promise.complete("Navigated to " + url);
        } else {
          promise.fail("Failed to navigate to " + url + ", status code: " + response.statusCode());
        }
      })
      .onFailure(promise::fail);
    return promise.future();
  }

  private Future<String> getHtml(String url) {
    Promise<String> promise = Promise.promise();
    client.getAbs(url)
      .as(BodyCodec.string())
      .send()
      .onSuccess(response -> {
        if (response.statusCode() == 200) {
          String html = response.body();
          Document doc = Jsoup.parse(html);
          String text = doc.text();
          String truncated = text.substring(0, Math.min(text.length(), 2000)) + "...";
          promise.complete(truncated);
        } else {
          promise.fail("Failed to get html from " + url + ", status code: " + response.statusCode());
        }
      })
      .onFailure(promise::fail);
    return promise.future();
  }
}
