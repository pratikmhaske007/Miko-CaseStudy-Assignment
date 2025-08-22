package com.miko.project.APIGatewayApp;


import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class MainApp {

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();

    JsonObject cfg = new JsonObject()
      .put("httpPort", 8080)
      .put("postsUrl", "https://jsonplaceholder.typicode.com/posts/1")
      .put("usersUrl", "https://jsonplaceholder.typicode.com/users/1");

    vertx.deployVerticle(new ApiGatewayVerticle(), new io.vertx.core.DeploymentOptions().setConfig(cfg));
  }
}
