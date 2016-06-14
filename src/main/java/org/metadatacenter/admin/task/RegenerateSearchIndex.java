package org.metadatacenter.admin.task;

import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.fluent.Request;
import org.apache.http.entity.ContentType;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.constant.HttpConnectionConstants;
import org.metadatacenter.constant.HttpConstants;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.service.UserService;
import org.metadatacenter.server.service.mongodb.UserServiceMongoDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RegenerateSearchIndex extends AbstractCedarAdminTask {

  private CedarConfig cedarConfig;
  CedarUser adminUser;
  private Logger logger = LoggerFactory.getLogger(RegenerateSearchIndex.class);

  public RegenerateSearchIndex() {
    description.add("It makes a REST call to the Resource server to regenerate the Elasticsearch search index");
    description.add("Note that the Resource server must be running before executing this command");
  }

  @Override
  public void init(CedarConfig config) {
    cedarConfig = config;
    UserService userService = new UserServiceMongoDB(
        cedarConfig.getMongoConfig().getDatabaseName(),
        cedarConfig.getMongoConfig().getCollections().get(CedarNodeType.USER.getValue()));
    String adminUserUUID = cedarConfig.getKeycloakConfig().getAdminUser().getUuid();
    try {
      adminUser = userService.findUser(adminUserUUID);
    } catch (Exception e) {
      logger.error("Error while loading admin user by UUID:" + adminUserUUID);
    }
    if (adminUser == null) {
      logger.error("Admin user not found by UUID:" + adminUserUUID);
      logger.error("The requested task was not completed!");
    }
  }

  private void regenerateSearchIndex(boolean force) {
    logger.info("Regenerating search index...");
    String apiKey = adminUser.getFirstActiveApiKey();
    String authString = HttpConstants.HTTP_AUTH_HEADER_APIKEY_PREFIX + apiKey;
    try {
      String url = cedarConfig.getServers().getResource().getRegenerateIndex();
      System.out.println(url);
      Request request = Request.Post(url)
          .bodyString("{\"force\":" + force + "}", ContentType.APPLICATION_JSON)
          .connectTimeout(HttpConnectionConstants.CONNECTION_TIMEOUT)
          .socketTimeout(HttpConnectionConstants.SOCKET_TIMEOUT)
          .addHeader(HttpHeaders.AUTHORIZATION, authString);
      HttpResponse response = request.execute().returnResponse();
      int statusCode = response.getStatusLine().getStatusCode();
      if (statusCode == HttpStatus.SC_OK) {
        play.Logger.info("The search index has been successfully regenerated");
      }
      else {
        logger.error("Error while regenerating search index. HTTP status code: " + statusCode);
        logger.error("The requested task was not completed!");
      }
    } catch (Exception e) {
      logger.error("Error while regenerating search index", e);
      logger.error("The requested task was not completed!");
    }
  }

  @Override
  public int execute() {
    regenerateSearchIndex(true);
    return 0;
  }

}
