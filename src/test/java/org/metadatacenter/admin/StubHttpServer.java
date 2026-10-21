package org.metadatacenter.admin;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * A local server that answers for a service the admin tool calls. It records every request, and answers each from
 * the most recently added route whose method matches and whose path starts with the route's; a request no route
 * matches gets 404.
 */
public final class StubHttpServer implements AutoCloseable {

  public record Request(String method, String path, String query, String authorization, String body) {
  }

  public record Reply(int status, String body) {
  }

  private record Route(String method, String pathPrefix, Function<Request, Reply> reply) {
  }

  private final HttpServer server;
  private final List<Request> requests = new CopyOnWriteArrayList<>();
  private final List<Route> routes = new CopyOnWriteArrayList<>();

  public StubHttpServer() throws IOException {
    server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
    server.createContext("/", this::handle);
    server.start();
  }

  public String host() {
    return "127.0.0.1";
  }

  public int port() {
    return server.getAddress().getPort();
  }

  public String url() {
    return "http://" + host() + ":" + port();
  }

  public StubHttpServer answer(String method, String pathPrefix, int status, String body) {
    return answer(method, pathPrefix, request -> new Reply(status, body));
  }

  public StubHttpServer answer(String method, String pathPrefix, Function<Request, Reply> reply) {
    routes.add(new Route(method, pathPrefix, reply));
    return this;
  }

  public List<Request> requests(String method, String pathPrefix) {
    List<Request> matching = new ArrayList<>();
    for (Request request : requests) {
      if (request.method().equals(method) && request.path().startsWith(pathPrefix)) {
        matching.add(request);
      }
    }
    return matching;
  }

  @Override
  public void close() {
    server.stop(0);
  }

  private void handle(HttpExchange exchange) throws IOException {
    Request request = new Request(exchange.getRequestMethod(), exchange.getRequestURI().getRawPath(),
        exchange.getRequestURI().getRawQuery() == null ? "" : exchange.getRequestURI().getRawQuery(),
        exchange.getRequestHeaders().getFirst("Authorization"),
        new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
    requests.add(request);
    Reply reply = new Reply(404, "{\"error\": \"no route\"}");
    for (int i = routes.size() - 1; i >= 0; i--) {
      Route route = routes.get(i);
      if (route.method().equals(request.method()) && request.path().startsWith(route.pathPrefix())) {
        reply = route.reply().apply(request);
        break;
      }
    }
    byte[] body = reply.body() == null ? new byte[0] : reply.body().getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().set("Content-Type", "application/json");
    exchange.sendResponseHeaders(reply.status(), reply.status() == 204 || body.length == 0 ? -1 : body.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(body);
    }
  }
}
