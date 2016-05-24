package org.metadatacenter.admin.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.metadatacenter.admin.config.CedarConfig;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.model.folderserver.CedarFSFolder;
import org.metadatacenter.server.neo4j.Neo4JProxy;
import org.metadatacenter.server.neo4j.Neo4JUserSession;
import org.metadatacenter.server.neo4j.Neo4jConfig;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.service.UserService;
import org.metadatacenter.server.service.mongodb.UserServiceMongoDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class CreateFolderServerGlobalObjects implements CedarAdminTask {

  private String adminUserUUID;

  private String mongoDatabaseName;
  private String usersCollectionName;

  private String transactionURL;
  private String authString;

  private String rootFolderPath;
  private String rootFolderDescription;
  private String usersFolderPath;
  private String usersFolderDescription;
  private String lostAndFoundFolderPath;
  private String lostAndFoundFolderDescription;
  private String linkedDataIdPathBase;
  private String linkedDataIdPathSuffixFolders;
  private String linkedDataIdPathSuffixUsers;

  private Neo4JProxy neo4JProxy;
  private Neo4jConfig neoConfig;
  private Logger logger = LoggerFactory.getLogger(CreateFolderServerGlobalObjects.class);
  private static ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public void setArguments(String[] args) {
  }

  @Override
  public void init(CedarConfig config) {
    adminUserUUID = config.getKeycloakConfig().getAdminUser().getUuid();

    mongoDatabaseName = config.getMongoConfig().getDatabaseName();
    usersCollectionName = config.getMongoConfig().getCollections().get(CedarNodeType.USER.getValue());

    transactionURL = config.getNeo4jConfig().getRest().getTransactionUrl();
    authString = config.getNeo4jConfig().getRest().getAuthString();

    rootFolderPath = "/";
    rootFolderDescription = "CEDAR Root Folder";
    usersFolderPath = "/Users";
    usersFolderDescription = "CEDAR Users";
    lostAndFoundFolderPath = "/Lost+Found";
    lostAndFoundFolderDescription = "CEDAR Lost and Found resources";
    linkedDataIdPathBase = "https://repo.metadatacenter.orgx/";
    linkedDataIdPathSuffixFolders = "folders/";
    linkedDataIdPathSuffixUsers = "users/";
  }

  @Override
  public int execute() {

    UserService userService = new UserServiceMongoDB(mongoDatabaseName, usersCollectionName);

    neoConfig = new Neo4jConfig();
    neoConfig.setTransactionUrl(transactionURL);
    neoConfig.setAuthString(authString);
    neoConfig.setRootFolderPath(rootFolderPath);
    neoConfig.setRootFolderDescription(rootFolderDescription);
    neoConfig.setUsersFolderPath(usersFolderPath);
    neoConfig.setUsersFolderDescription(usersFolderDescription);
    neoConfig.setLostAndFoundFolderPath(lostAndFoundFolderPath);
    neoConfig.setLostAndFoundFolderDescription(lostAndFoundFolderDescription);

    String folderIdPrefix = linkedDataIdPathBase + linkedDataIdPathSuffixFolders;
    String userIdPrefix = linkedDataIdPathBase + linkedDataIdPathSuffixUsers;
    neo4JProxy = new Neo4JProxy(neoConfig, folderIdPrefix);

    CedarUser adminUser = null;
    try {
      adminUser = userService.findUser(adminUserUUID);
    } catch (Exception ex) {
      logger.error("Error while loading admin user for id:" + adminUserUUID + ":");
    }
    if (adminUser == null) {
      logger.error("Admin user not found for id:" + adminUserUUID + ":");
      logger.error("Unable to ensure the existence of global objects, exiting!");
      return -1;
    } else {
      Neo4JUserSession neo4JSession = Neo4JUserSession.get(neo4JProxy, adminUser, userIdPrefix);
      neo4JSession.ensureGlobalObjectsExists();
      CedarFSFolder createdFolder = neo4JSession.ensureUserHomeExists();
      if (createdFolder != null) {
        ObjectNode homeModification = MAPPER.createObjectNode();
        homeModification.put("homeFolderId", createdFolder.getId());
        logger.info("homeModification: " + homeModification);
        try {
          userService.updateUser(adminUser.getUserId(), homeModification);
          logger.info("User updated");
        } catch (Exception e) {
          logger.error("Error while updating the user:", e);
        }
      }

    }

    return 0;
  }


}
