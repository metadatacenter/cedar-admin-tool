package org.metadatacenter.admin.task;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.server.AdminServiceSession;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.neo4j.Neo4JProxy;
import org.metadatacenter.server.neo4j.Neo4JUserSession;
import org.metadatacenter.server.neo4j.Neo4jConfig;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.service.UserService;

public abstract class AbstractNeo4JAccessTask extends AbstractCedarAdminTask {

  protected CedarUser adminUser;

  protected AdminServiceSession createCedarAdminSession(CedarConfig cedarConfig, boolean createHome) {
    UserService userService = getUserService();

    String adminUserUUID = cedarConfig.getKeycloakConfig().getAdminUser().getUuid();

    Neo4jConfig neoConfig = Neo4jConfig.fromCedarConfig(cedarConfig);

    String genericIdPrefix = cedarConfig.getLinkedDataConfig().getBase();
    String usersIdPrefix = cedarConfig.getLinkedDataConfig().getUsersBase();
    Neo4JProxy neo4JProxy = new Neo4JProxy(neoConfig, genericIdPrefix, usersIdPrefix);

    try {
      adminUser = userService.findUser(adminUserUUID);
    } catch (Exception ex) {
      out.error("Error while loading admin user for id:" + adminUserUUID + ":");
    }
    if (adminUser == null) {
      out.error("Admin user not found for id:" + adminUserUUID + ".");
      out.error("The requested task was not completed!");
      return null;
    } else {
      return Neo4JUserSession.get(neo4JProxy, userService, adminUser, createHome);
    }
  }

  protected FolderServiceSession createCedarFolderSession(CedarConfig cedarConfig) {
    UserService userService = getUserService();

    String adminUserUUID = cedarConfig.getKeycloakConfig().getAdminUser().getUuid();

    Neo4jConfig neoConfig = Neo4jConfig.fromCedarConfig(cedarConfig);

    String genericIdPrefix = cedarConfig.getLinkedDataConfig().getBase();
    String usersIdPrefix = cedarConfig.getLinkedDataConfig().getUsersBase();
    Neo4JProxy neo4JProxy = new Neo4JProxy(neoConfig, genericIdPrefix, usersIdPrefix);

    try {
      adminUser = userService.findUser(adminUserUUID);
    } catch (Exception ex) {
      out.error("Error while loading admin user for id:" + adminUserUUID + ":");
    }
    if (adminUser == null) {
      out.error("Admin user not found for id:" + adminUserUUID + ".");
      out.error("The requested task was not completed!");
      return null;
    } else {
      return Neo4JUserSession.get(neo4JProxy, userService, adminUser, false);
    }
  }
}