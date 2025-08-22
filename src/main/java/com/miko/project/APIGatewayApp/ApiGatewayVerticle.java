package com.miko.project.APIGatewayApp;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
//import java.util.Arrays;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;

import io.vertx.circuitbreaker.CircuitBreaker;
import io.vertx.circuitbreaker.CircuitBreakerOptions;
//import io.vertx.core.*;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

public class ApiGatewayVerticle extends AbstractVerticle {

  private UpstreamClient upstreamClient;
  private CircuitBreaker postsBreaker;
  private CircuitBreaker usersBreaker;

  private String postsUrl;
  private String usersUrl;
  private int httpPort;

  @Override
  public void start(Promise<Void> startPromise) {
    this.httpPort = config().getInteger("httpPort", 8080);
    this.postsUrl = config().getString("postsUrl", "https://jsonplaceholder.typicode.com/posts/1");
    this.usersUrl = config().getString("usersUrl", "https://jsonplaceholder.typicode.com/users/1");

    this.upstreamClient = new UpstreamClient(vertx);

    this.postsBreaker = CircuitBreaker.create("postsBreaker", vertx,
      new CircuitBreakerOptions()
        .setMaxFailures(3)
        .setTimeout(2500)
        .setResetTimeout(5000)
        .setFallbackOnFailure(false)
    );

    this.usersBreaker = CircuitBreaker.create("usersBreaker", vertx,
      new CircuitBreakerOptions()
        .setMaxFailures(3)
        .setTimeout(2500)
        .setResetTimeout(5000)
        .setFallbackOnFailure(false)
    );

    Router router = Router.router(vertx);
    router.get("/aggregate").handler(this::handleAggregate);

    vertx.createHttpServer()
      .requestHandler(router)
      .listen(httpPort)
      .onSuccess(s -> {
        System.out.println("HTTP server started on port " + s.actualPort());
        startPromise.complete();
      })
      .onFailure(startPromise::fail);
  }

  private void handleAggregate(RoutingContext ctx) {
    Future<String> postTitleF = postsBreaker.execute(p ->
      upstreamClient.fetchField(postsUrl, "title")
        .onSuccess(p::complete)
        .onFailure(p::fail)
    );

    Future<String> authorNameF = usersBreaker.execute(p ->
      upstreamClient.fetchField(usersUrl, "name")
        .onSuccess(p::complete)
        .onFailure(p::fail)
    );

    CompositeFuture.all(postTitleF, authorNameF)
      .onSuccess(cf -> {
        JsonObject out = new JsonObject()
          .put("post_title", postTitleF.result())
          .put("author_name", authorNameF.result());
        sendJson(ctx, 200, out);
      })
      .onFailure(err -> {
        JsonObject error = new JsonObject()
          .put("error", "Upstream fetch failed")
          .put("details", err.getMessage());
        sendJson(ctx, 502, error);
      });
  }

  private void sendJson(RoutingContext ctx, int status, JsonObject body) {
    ctx.response()
      .setStatusCode(status)
      .putHeader(HttpHeaders.CONTENT_TYPE, "application/json")
      .end(body.encode());
  }
}
