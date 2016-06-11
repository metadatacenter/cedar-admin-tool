package org.metadatacenter.admin.task;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.server.neo4j.Neo4JProxy;
import org.metadatacenter.server.neo4j.Neo4JUserSession;
import org.metadatacenter.server.neo4j.Neo4jConfig;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.service.UserService;
import org.metadatacenter.server.service.mongodb.UserServiceMongoDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractNeo4JAccessTask extends AbstractCedarAdminTask {

  protected CedarUser adminUser;
  private Logger logger = LoggerFactory.getLogger(AbstractNeo4JAccessTask.class);

  protected Neo4JUserSession buildCedarAdminNeo4JSession(CedarConfig cedarConfig, boolean createHome) {
    UserService userService = new UserServiceMongoDB(
        cedarConfig.getMongoConfig().getDatabaseName(),
        cedarConfig.getMongoConfig().getCollections().get(CedarNodeType.USER.getValue()));

    String adminUserUUID = cedarConfig.getKeycloakConfig().getAdminUser().getUuid();

    Neo4jConfig neoConfig = new Neo4jConfig();
    neoConfig.setTransactionUrl(cedarConfig.getNeo4jConfig().getRest().getTransactionUrl());
    neoConfig.setAuthString(cedarConfig.getNeo4jConfig().getRest().getAuthString());
    neoConfig.setRootFolderPath(cedarConfig.getFolderStructureConfig().getRootFolder().getPath());
    neoConfig.setRootFolderDescription(cedarConfig.getFolderStructureConfig().getRootFolder().getDescription());
    neoConfig.setUsersFolderPath(cedarConfig.getFolderStructureConfig().getUsersFolder().getPath());
    neoConfig.setUsersFolderDescription(cedarConfig.getFolderStructureConfig().getUsersFolder().getDescription());
    neoConfig.setLostAndFoundFolderPath(cedarConfig.getFolderStructureConfig().getLostAndFoundFolder().getPath());
    neoConfig.setLostAndFoundFolderDescription(cedarConfig.getFolderStructureConfig().getLostAndFoundFolder()
        .getDescription());

    String genericIdPrefix = cedarConfig.getLinkedDataConfig().getBase();
    Neo4JProxy neo4JProxy = new Neo4JProxy(neoConfig, genericIdPrefix);

    try {
      adminUser = userService.findUser(adminUserUUID);
    } catch (Exception ex) {
      logger.error("Error while loading admin user for id:" + adminUserUUID + ":");
    }
    if (adminUser == null) {
      logger.error("Admin user not found for id:" + adminUserUUID + ".");
      logger.error("The requested task was not completed!");
      return null;
    } else {
      return Neo4JUserSession.get(neo4JProxy, userService, adminUser, createHome);
    }
  }
}