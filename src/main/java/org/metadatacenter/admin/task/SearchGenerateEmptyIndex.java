package org.metadatacenter.admin.task;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.service.UserService;
import org.metadatacenter.util.json.JsonMapper;

import java.util.HashMap;
import java.util.Map;

import static org.metadatacenter.constant.HttpConnectionConstants.CONNECTION_TIMEOUT;
import static org.metadatacenter.constant.HttpConnectionConstants.SOCKET_TIMEOUT;
import static org.metadatacenter.constant.HttpConstants.HTTP_HEADER_AUTHORIZATION;

public class SearchGenerateEmptyIndex extends AbstractCedarAdminTask {

  CedarUser adminUser;

  public SearchGenerateEmptyIndex() {
    description.add("It makes a REST call to the Resource server to generate an empty Elasticsearch search index");
    description.add("Note that the Resource server must be running before executing this command");
  }

  @Override
  public void init() {
    UserService userService = getUserService();
    String adminUserApiKey = cedarConfig.getAdminUserConfig().getApiKey();
    try {
      adminUser = userService.findUserByApiKey(adminUserApiKey);
    } catch (Exception e) {
      out.error("Error while loading admin user by apiKey:" + adminUserApiKey);
    }
    if (adminUser == null) {
      out.error("Admin user not found by apiKey:" + adminUserApiKey);
      out.error("The requested task was not completed!");
    }
  }

  private void generateEmptySearchIndex() {
    out.info("Requesting empty search index generation.");
    String authString = adminUser.getFirstApiKeyAuthHeader();
    try {
      String url = cedarConfig.getServers().getResource().getGenerateEmptySearchIndex();
      out.println(url);
      Map<String, Object> requestMap = new HashMap<>();
      Request request = Request.Post(url)
          .bodyString(JsonMapper.MAPPER.writeValueAsString(requestMap), ContentType.APPLICATION_JSON)
          .connectTimeout(CONNECTION_TIMEOUT)
          .socketTimeout(SOCKET_TIMEOUT)
          .addHeader(HTTP_HEADER_AUTHORIZATION, authString);
      HttpResponse response = request.execute().returnResponse();
      int statusCode = response.getStatusLine().getStatusCode();
      if (statusCode == HttpStatus.SC_OK) {
        out.info("The search empty index generation was successfully started. Please inspect the resource server log for progress!");
      } else {
        out.error("Error while requesting empty index generation. HTTP status code: " + statusCode);
        out.error("The requested task was not completed!");
      }
    } catch (Exception e) {
      out.error("Error while generating empty search index", e);
      out.error("The requested task was not completed!");
    }
  }

  @Override
  public int execute() {
    generateEmptySearchIndex();
    return 0;
  }

}
