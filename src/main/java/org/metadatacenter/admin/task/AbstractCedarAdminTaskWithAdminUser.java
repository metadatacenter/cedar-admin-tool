package org.metadatacenter.admin.task;

import org.apache.http.HttpResponse;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.service.UserService;
import org.metadatacenter.util.json.JsonMapper;

import java.io.IOException;
import java.util.Map;

import static org.metadatacenter.constant.HttpConnectionConstants.CONNECTION_TIMEOUT;
import static org.metadatacenter.constant.HttpConnectionConstants.SOCKET_TIMEOUT;
import static org.metadatacenter.constant.HttpConstants.HTTP_HEADER_AUTHORIZATION;

public abstract class AbstractCedarAdminTaskWithAdminUser extends AbstractCedarAdminTask {

  protected CedarUser adminUser;

  @Override
  public void init() {
    UserService userService = getNeoUserService();
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

  protected int post(String url, Map<String, Object> requestMap) throws IOException {
    String authString = adminUser.getFirstApiKeyAuthHeader();
    Request request = Request.Post(url)
        .bodyString(JsonMapper.MAPPER.writeValueAsString(requestMap), ContentType.APPLICATION_JSON)
        .connectTimeout(CONNECTION_TIMEOUT)
        .socketTimeout(SOCKET_TIMEOUT)
        .addHeader(HTTP_HEADER_AUTHORIZATION, authString);
    HttpResponse response = request.execute().returnResponse();
    return response.getStatusLine().getStatusCode();
  }

}
