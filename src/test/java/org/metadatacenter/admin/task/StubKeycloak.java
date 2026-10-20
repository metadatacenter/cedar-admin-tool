package org.metadatacenter.admin.task;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.keycloak.representations.idm.UserRepresentation;
import org.metadatacenter.admin.StubHttpServer;
import org.metadatacenter.admin.StubHttpServer.Reply;
import org.metadatacenter.util.json.JsonMapper;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A Keycloak realm with the users a test gives it, answering the admin REST calls the admin tool makes: the
 * administrator's token, a page of users, a search by user name, one user, and a user's effective realm roles.
 */
final class StubKeycloak implements AutoCloseable {

  private final StubHttpServer server;
  private final String adminPath;
  private final Map<String, UserRepresentation> users = new LinkedHashMap<>();

  StubKeycloak(String realm) throws IOException {
    server = new StubHttpServer();
    adminPath = "/admin/realms/" + realm + "/users";
    server.answer("POST", "/realms/" + realm + "/protocol/openid-connect/token", 200,
        "{\"access_token\": \"stub-token\", \"expires_in\": 300, \"refresh_expires_in\": 1800,"
            + " \"refresh_token\": \"stub-refresh\", \"token_type\": \"Bearer\", \"not-before-policy\": 0}");
    server.answer("GET", adminPath, request -> {
      String rest = request.path().substring(adminPath.length());
      if (rest.isEmpty() || rest.equals("/")) {
        return new Reply(200, list(query(request.query())).toString());
      }
      String[] parts = rest.substring(1).split("/");
      UserRepresentation user = users.get(parts[0]);
      if (user == null) {
        return new Reply(404, "{\"error\": \"User not found\"}");
      }
      if (parts.length > 1) {
        ArrayNode roles = JsonMapper.STRICT_MAPPER.createArrayNode();
        user.getRealmRoles().forEach(role -> roles.addObject().put("name", role));
        return new Reply(200, roles.toString());
      }
      return new Reply(200, json(user).toString());
    });
  }

  StubKeycloak user(UserRepresentation user) {
    users.put(user.getId(), user);
    return this;
  }

  String url() {
    return server.url();
  }

  /** How many pages of users were read. */
  int pagesRead() {
    return (int) server.requests("GET", adminPath).stream().filter(r -> r.query().contains("first=")).count();
  }

  @Override
  public void close() {
    server.close();
  }

  private ArrayNode list(Map<String, String> query) {
    List<UserRepresentation> matching = new ArrayList<>(users.values());
    if (query.containsKey("username")) {
      matching.removeIf(user -> !user.getUsername().equals(query.get("username")));
    }
    int first = Integer.parseInt(query.getOrDefault("first", "0"));
    int max = Integer.parseInt(query.getOrDefault("max", String.valueOf(Integer.MAX_VALUE)));
    ArrayNode page = JsonMapper.STRICT_MAPPER.createArrayNode();
    matching.stream().skip(first).limit(max).forEach(user -> page.add(json(user)));
    return page;
  }

  private static ObjectNode json(UserRepresentation user) {
    // Keycloak lists users without their roles, which come from a call of their own.
    ObjectNode node = JsonMapper.STRICT_MAPPER.createObjectNode();
    node.put("id", user.getId());
    node.put("username", user.getUsername());
    node.put("firstName", user.getFirstName());
    node.put("lastName", user.getLastName());
    node.put("email", user.getEmail());
    node.put("enabled", true);
    node.put("createdTimestamp", user.getCreatedTimestamp());
    return node;
  }

  private static Map<String, String> query(String query) {
    Map<String, String> parameters = new LinkedHashMap<>();
    Arrays.stream(query.split("&")).filter(p -> p.contains("=")).forEach(p -> parameters.put(
        URLDecoder.decode(p.substring(0, p.indexOf('=')), StandardCharsets.UTF_8),
        URLDecoder.decode(p.substring(p.indexOf('=') + 1), StandardCharsets.UTF_8)));
    return parameters;
  }
}
