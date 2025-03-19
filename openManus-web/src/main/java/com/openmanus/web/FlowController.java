package com.openmanus.web;

import com.mesh.web.core.controller.RouterInterface;
import com.openmanus.flow.PlanningFlow;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

@Controller
@Slf4j
public class FlowController implements RouterInterface {

  @Autowired
  private PlanningFlow planningFlow;


  public void handleFlow(RoutingContext routingContext) {
    JsonObject requestBody = routingContext.body().asJsonObject();
    String message = requestBody.getString("message");

    if (message == null || message.trim().isEmpty()) {
      routingContext.response()
        .setStatusCode(400)
        .putHeader("content-type", "application/json")
        .end(new JsonObject().put("error", "Message is required").encode());
      return;
    }

    log.info("Received message: {}", message);

    planningFlow.execute(message)
      .onSuccess(result -> {
        log.info("Flow completed successfully: {}", result);
        routingContext.response()
          .setStatusCode(200)
          .putHeader("content-type", "application/json")
          .end(new JsonObject().put("result", result).encode());
      })
      .onFailure(throwable -> {
        log.error("Flow failed: {}", throwable.getMessage(), throwable);
        routingContext.response()
          .setStatusCode(500)
          .putHeader("content-type", "application/json")
          .end(new JsonObject().put("error", "Flow execution failed: " + throwable.getMessage()).encode());
      });
  }

  @Override
  public void router(Router router) {
    router.post("/flow").handler(this::handleFlow);
  }

}
