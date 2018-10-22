package org.metadatacenter.admin.task;

import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.service.UserService;

import static org.metadatacenter.constant.HttpConnectionConstants.CONNECTION_TIMEOUT;
import static org.metadatacenter.constant.HttpConnectionConstants.SOCKET_TIMEOUT;
import static org.metadatacenter.constant.HttpConstants.HTTP_HEADER_AUTHORIZATION;

public class RulesRegenerateIndex extends AbstractCedarAdminTask {

  CedarUser adminUser;

  public RulesRegenerateIndex() {
    description.add("It makes a REST call to the Resource server to regenerate the Elasticsearch rules index");
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

  private void regenerateRulesIndex(boolean force) {
    out.info("Regenerating rules index...");
    String authString = adminUser.getFirstApiKeyAuthHeader();
    try {
      String url = cedarConfig.getServers().getResource().getRegenerateRulesIndex();
      out.println(url);
      Request request = Request.Post(url)
          .bodyString("{\"force\":" + force + "}", ContentType.APPLICATION_JSON)
          .connectTimeout(CONNECTION_TIMEOUT)
          .socketTimeout(SOCKET_TIMEOUT)
          .addHeader(HTTP_HEADER_AUTHORIZATION, authString);
      HttpResponse response = request.execute().returnResponse();
      int statusCode = response.getStatusLine().getStatusCode();
      if (statusCode == HttpStatus.SC_OK) {
        out.info("The rules index has been successfully regenerated");
      } else {
        out.error("Error while regenerating rules index. HTTP status code: " + statusCode);
        out.error("The requested task was not completed!");
      }
    } catch (Exception e) {
      out.error("Error while regenerating rules index", e);
      out.error("The requested task was not completed!");
    }
  }

  @Override
  public int execute() {
    regenerateRulesIndex(true);
    return 0;
  }

}
