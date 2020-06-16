package org.metadatacenter.admin.task;

import org.keycloak.representations.idm.UserRepresentation;
import org.metadatacenter.admin.util.Color;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.service.UserService;

public class UserProfileGetAdmin extends AbstractKeycloakReadingTask {

  public UserProfileGetAdmin() {
    description.add("Reads cedar-admin user details from MongoDB and Keycloak.");
  }

  @Override
  public void init() {
    initKeycloak(cedarConfig);

    out.println();
    out.println("Data from config:", Color.YELLOW);
    out.printIndented("cedarAdminUserName    : " + kcInfo.getCedarAdminUserName());
    out.printIndented("cedarAdminUserPassword: " + kcInfo.getCedarAdminUserPassword());
    out.printIndented("cedarAdminUserApiKey  : " + kcInfo.getCedarAdminUserApiKey());
    out.printIndented("keycloakClientId      : " + kcInfo.getKeycloakClientId());
    out.printIndented("keycloakRealmName     : " + kcInfo.getKeycloakRealmName());
    out.printIndented("keycloakBaseURI       : " + kcInfo.getKeycloakBaseURI());
  }

  @Override
  public int execute() {
    out.println();
    out.println("Data from MongoDB:", Color.YELLOW);

    UserService userService = getNeoUserService();

    CedarUser user = null;
    boolean exceptionWhileReading = false;
    try {
      user = userService.findUserByApiKey(kcInfo.getCedarAdminUserApiKey());
    } catch (Exception e) {
      out.error("Error while reading user for apiKey: " + kcInfo.getCedarAdminUserApiKey(), e);
      exceptionWhileReading = true;
    }

    if (user == null && !exceptionWhileReading) {
      out.printIndented(kcInfo.getCedarAdminUserName() + " user was not found in neo4j", Color.RED);
    } else {
      out.printIndented(kcInfo.getCedarAdminUserName() + " user was found in neo4j", Color.GREEN);
      out.printIndented("First active API KEY: " + user.getFirstActiveApiKey());
      out.printIndented("Home folder Id      : " + user.getHomeFolderId());

      String userUUID = linkedDataUtil.getUUID(user.getId(), CedarResourceType.USER);
      UserRepresentation userRepresentation = getUserFromKeycloak(userUUID);
      out.println();
      out.println("Data from Keycloak:", Color.YELLOW);
      if (userRepresentation == null) {
        out.printIndented(kcInfo.getCedarAdminUserName() + " user was not found on Keycloak", Color.RED);
      } else {
        out.printIndented(kcInfo.getCedarAdminUserName() + " user was found on Keycloak", Color.GREEN);
        printOutUser(out, userRepresentation);
      }
    }

    return 0;
  }

}
