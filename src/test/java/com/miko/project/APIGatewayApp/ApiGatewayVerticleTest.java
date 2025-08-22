package com.miko.project.APIGatewayApp;

import io.vertx.core.DeploymentOptions;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxTestContext;
import io.vertx.junit5.VertxExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(VertxExtension.class)
public class ApiGatewayVerticleTest {

  private HttpServer mockServer;
  private int mockPort;


  @AfterEach
  void tearDown(Vertx vertx, VertxTestContext tc) {
    if (mockServer != null) {
      mockServer.close().onComplete(ar -> tc.completeNow());
    } else {
      tc.completeNow();
    }
  }

  private void startMockServer(Vertx vertx, Router router, VertxTestContext tc, Runnable next) {
    mockServer = vertx.createHttpServer().requestHandler(router);
    mockServer.listen(0).onComplete(ar -> {
      if (ar.failed()) {
        tc.failNow(ar.cause());
      } else {
        mockPort = ar.result().actualPort();
        next.run();
      }
    });
  }

  @Test
  void aggregate_success(Vertx vertx, VertxTestContext tc) {
    Router mock = Router.router(vertx);
    mock.get("/posts/1").handler(ctx -> ctx.response().end(new JsonObject().put("title", "Hello World").encode()));
    mock.get("/users/1").handler(ctx -> ctx.response().end(new JsonObject().put("name", "Ada Lovelace").encode()));

    startMockServer(vertx, mock, tc, () -> {
      JsonObject cfg = new JsonObject()
        .put("httpPort", 8099)
        .put("postsUrl", "http://localhost:" + mockPort + "/posts/1")
        .put("usersUrl", "http://localhost:" + mockPort + "/users/1");

      vertx.deployVerticle(new ApiGatewayVerticle(), new DeploymentOptions().setConfig(cfg))
        .onFailure(tc::failNow)
        .onSuccess(id -> {
          WebClient client = WebClient.create(vertx);
          client.get(cfg.getInteger("httpPort"), "localhost", "/aggregate").send().onComplete(ar -> {
            if (ar.failed()) tc.failNow(ar.cause());
            else {
              var resp = ar.result();
              assertEquals(200, resp.statusCode());
              var json = resp.bodyAsJsonObject();
              assertEquals("Hello World", json.getString("post_title"));
              assertEquals("Ada Lovelace", json.getString("author_name"));
              tc.completeNow();
            }
          });
        });
    });
  }

  @Test
  void aggregate_failure_when_user_api_breaks(Vertx vertx, VertxTestContext tc) {
    Router mock = Router.router(vertx);
    mock.get("/posts/1").handler(ctx -> ctx.response().end(new JsonObject().put("title", "OK").encode()));
    mock.get("/users/1").handler(ctx -> ctx.response().setStatusCode(500).end("boom"));

    startMockServer(vertx, mock, tc, () -> {
      JsonObject cfg = new JsonObject()
        .put("httpPort", 8100)
        .put("postsUrl", "http://localhost:" + mockPort + "/posts/1")
        .put("usersUrl", "http://localhost:" + mockPort + "/users/1");

      vertx.deployVerticle(new ApiGatewayVerticle(), new DeploymentOptions().setConfig(cfg))
        .onFailure(tc::failNow)
        .onSuccess(id -> {
          WebClient client = WebClient.create(vertx);
          client.get(cfg.getInteger("httpPort"), "localhost", "/aggregate").send().onComplete(ar -> {
            if (ar.failed()) tc.failNow(ar.cause());
            else {
              var resp = ar.result();
              assertEquals(502, resp.statusCode());
              var json = resp.bodyAsJsonObject();
              assertTrue(json.containsKey("error"));
              assertTrue(json.containsKey("details"));
              tc.completeNow();
            }
          });
        });
    });
  }
}
