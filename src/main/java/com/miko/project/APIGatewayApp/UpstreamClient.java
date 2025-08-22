package com.miko.project.APIGatewayApp;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;

import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;

public class UpstreamClient {
  private final WebClient client;

  public UpstreamClient(Vertx vertx) {
    this.client = WebClient.create(vertx, new WebClientOptions()
      .setKeepAlive(true)
      .setFollowRedirects(true)
      .setSsl(true));
  }

  public Future<String> fetchField(String url, String field) {
    Promise<String> promise = Promise.promise();

    client.getAbs(url)
      .send()
      .onSuccess((HttpResponse<Buffer> resp) -> {
        int sCode = resp.statusCode();
        if (sCode < 200 || sCode >= 300) {
          promise.fail("HTTP " + sCode + " from " + url);
          return;
        }
        try {
          JsonObject json = resp.bodyAsJsonObject();
          String value = json.getString(field);
          if (value == null) {
            promise.fail("Missing '" + field + "' in response from " + url);
          } else {
            promise.complete(value);
          }
        } catch (Throwable t) {
          promise.fail("Invalid JSON from " + url + ": " + t.getMessage());
        }
      })
      .onFailure(err -> promise.fail("Request failed for " + url + ": " + err.getMessage()));

    return promise.future();
  }
}

