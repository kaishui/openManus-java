package com.openmanus.tool;

import com.mesh.web.core.VertxSpringApplication;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.ext.web.codec.BodyCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ConfluenceSearch implements Tool {

  private final WebClient client;

  public ConfluenceSearch() {
    WebClientOptions options = new WebClientOptions().setSsl(true).setTrustAll(true);
    this.client = WebClient.create(VertxSpringApplication.vertx, options);
  }

  @Override
  public String getName() {
    return "confluence_search";
  }

  @Override
  public String getDescription() {
    return "Perform a search within Confluence and return a list of relevant page titles and URLs.";
  }

  @Override
  public JsonObject getParameters() {
    JsonObject parameters = new JsonObject();
    parameters.put("type", "object");
    JsonObject properties = new JsonObject();
    properties.put("query", new JsonObject().put("type", "string").put("description", "(required) The search query to submit to Confluence."));
    properties.put("confluence_url", new JsonObject().put("type", "string").put("description", "(optional) The base URL of your Confluence instance.").put("default", "https://your-confluence-site.atlassian.net"));
    properties.put("username", new JsonObject().put("type", "string").put("description", "(optional) Your Confluence username.").put("default", "your_username"));
    properties.put("api_token", new JsonObject().put("type", "string").put("description", "(optional) Your Confluence API token.").put("default", "your_api_token"));
    properties.put("limit", new JsonObject().put("type", "integer").put("description", "(optional) The maximum number of search results to return.").put("default", 10));
    parameters.put("properties", properties);
    JsonArray required = new JsonArray().add("query");
    parameters.put("required", required);
    return parameters;
  }

  @Override
  public Future<Object> execute(JsonObject toolInput) {
    Promise<Object> promise = Promise.promise();
    String query = toolInput.getString("query");
    String confluenceUrl = toolInput.getString("confluence_url", "https://your-confluence-site.atlassian.net");
    String username = toolInput.getString("username", "your_username");
    String apiToken = toolInput.getString("api_token", "your_api_token");
    int limit = toolInput.getInteger("limit", 10);

    String searchUrl = confluenceUrl + "/wiki/rest/api/search";
    String cql = "cql=text~%22" + query + "%22";

    client.getAbs(searchUrl)
      .basicAuthentication(username, apiToken)
      .putHeader("Accept", "application/json")
      .addQueryParam(cql, "").addQueryParam("limit", String.valueOf(limit))
      .as(BodyCodec.jsonObject())
      .send()
      .onSuccess(response -> {
        if (response.statusCode() == 200) {
          JsonObject body = response.body();
          JsonArray results = body.getJsonArray("results");
          JsonArray searchResults = new JsonArray();
          for (int i = 0; i < results.size(); i++) {
            JsonObject result = results.getJsonObject(i);
            JsonObject content = result.getJsonObject("content");
            String title = content.getString("title");
            String url = confluenceUrl + content.getJsonObject("_links").getString("webui");
            searchResults.add(new JsonObject().put("title", title).put("url", url));
          }
          promise.complete(searchResults);
        } else {
          promise.fail("Confluence search failed with status code: " + response.statusCode());
        }
      })
      .onFailure(promise::fail);

    return promise.future();
  }
}
