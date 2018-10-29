package org.metadatacenter.admin.task;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.service.UserService;
import org.metadatacenter.util.json.JsonMapper;

import java.util.Map;
import java.util.HashMap;

import static org.metadatacenter.constant.HttpConnectionConstants.CONNECTION_TIMEOUT;
import static org.metadatacenter.constant.HttpConnectionConstants.SOCKET_TIMEOUT;
import static org.metadatacenter.constant.HttpConstants.HTTP_HEADER_AUTHORIZATION;

public class SearchRegenerateIndex extends AbstractCedarAdminTask {

  CedarUser adminUser;

  public SearchRegenerateIndex() {
    description.add("It makes a REST call to the Resource server to regenerate the Elasticsearch search index");
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

  private void regenerateSearchIndex(boolean force) {
    out.info("Requesting search index regeneration. Force:" + force);
    String authString = adminUser.getFirstApiKeyAuthHeader();
    try {
      String url = cedarConfig.getServers().getResource().getRegenerateSearchIndex();
      out.println(url);
      Map<String, Object> requestMap = new HashMap<>();
      requestMap.put("force", force);
      Request request = Request.Post(url)
          .bodyString(JsonMapper.MAPPER.writeValueAsString(requestMap), ContentType.APPLICATION_JSON)
          .connectTimeout(CONNECTION_TIMEOUT)
          .socketTimeout(SOCKET_TIMEOUT)
          .addHeader(HTTP_HEADER_AUTHORIZATION, authString);
      HttpResponse response = request.execute().returnResponse();
      int statusCode = response.getStatusLine().getStatusCode();
      if (statusCode == HttpStatus.SC_OK) {
        out.info("The search index regeneration was successfully started. Please inspect the resource server log for progress!");
      } else {
        out.error("Error while requesting index regeneration. HTTP status code: " + statusCode);
        out.error("The requested task was not completed!");
      }
    } catch (Exception e) {
      out.error("Error while regenerating search index", e);
      out.error("The requested task was not completed!");
    }
  }

  @Override
  public int execute() {
    regenerateSearchIndex(true);
    return 0;
  }

}
