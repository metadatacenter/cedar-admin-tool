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

import java.util.ArrayList;
import java.util.List;


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
  private static List<String> description;

  static {
    description = new ArrayList<>();
    description.add("Creates global folders in the graph database: /, /Users, /Lost+Found");
    description.add("Creates home folder for cedar-admin user");
    description.add("Updates cedar-admin user profile in MongoDB, sets homeFolderId");
  }

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

    rootFolderPath = config.getFolderStructureConfig().getRootFolder().getPath();
    rootFolderDescription = config.getFolderStructureConfig().getRootFolder().getDescription();
    usersFolderPath = config.getFolderStructureConfig().getUsersFolder().getPath();
    usersFolderDescription = config.getFolderStructureConfig().getUsersFolder().getDescription();
    lostAndFoundFolderPath = config.getFolderStructureConfig().getLostAndFoundFolder().getPath();
    lostAndFoundFolderDescription = config.getFolderStructureConfig().getLostAndFoundFolder().getDescription();
    linkedDataIdPathBase = config.getLinkedDataConfig().getBase();
    linkedDataIdPathSuffixFolders = CedarNodeType.FOLDER.getPrefix() + "/";
    linkedDataIdPathSuffixUsers = CedarNodeType.USER.getPrefix() + "/";
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
      Neo4JUserSession neo4JSession = Neo4JUserSession.get(neo4JProxy, userService, adminUser, userIdPrefix, false);
      neo4JSession.ensureGlobalObjectsExists();
      neo4JSession = Neo4JUserSession.get(neo4JProxy, userService, adminUser, userIdPrefix, true);
    }

    return 0;
  }

  @Override
  public List<String> getDescription() {
    return description;
  }

}
