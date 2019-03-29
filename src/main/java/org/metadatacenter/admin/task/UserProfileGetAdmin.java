package org.metadatacenter.admin.task;

import org.keycloak.representations.idm.UserRepresentation;
import org.metadatacenter.admin.util.Color;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.service.UserService;

public class UserProfileGetAdmin extends AbstractKeycloakReadingTask {

  public UserProfileGetAdmin() {
    description.add("Reads cedar-admin user details from MongoDB and Keycloak.");
  }

  @Override
  public void init() {
    initKeycloak();

    out.println();
    out.println("Data from config:", Color.YELLOW);
    out.printIndented("cedarAdminUserName    : " + cedarAdminUserName);
    out.printIndented("cedarAdminUserPassword: " + cedarAdminUserPassword);
    out.printIndented("cedarAdminUserApiKey  : " + cedarAdminUserApiKey);
    out.printIndented("keycloakClientId      : " + keycloakClientId);
    out.printIndented("keycloakRealmName     : " + keycloakRealmName);
    out.printIndented("keycloakBaseURI       : " + keycloakBaseURI);
  }


  @Override
  public int execute() {
    out.println();
    out.println("Data from MongoDB:", Color.YELLOW);

    UserService userService = getNeoUserService();

    CedarUser user = null;
    boolean exceptionWhileReading = false;
    try {
      user = userService.findUserByApiKey(cedarAdminUserApiKey);
    } catch (Exception e) {
      out.error("Error while reading user for apiKey: " + cedarAdminUserApiKey, e);
      exceptionWhileReading = true;
    }

    if (user == null && !exceptionWhileReading) {
      out.printIndented(cedarAdminUserName + " user was not found in MongoDB", Color.RED);
    } else {
      out.printIndented(cedarAdminUserName + " user was found in MongoDB", Color.GREEN);
      out.printIndented("First active API KEY: " + user.getFirstActiveApiKey());
      out.printIndented("Home folder Id      : " + user.getHomeFolderId());

      String userUUID = linkedDataUtil.getUUID(user.getId(), CedarNodeType.USER);
      UserRepresentation userRepresentation = getUserFromKeycloak(userUUID);
      out.println();
      out.println("Data from Keycloak:", Color.YELLOW);
      if (userRepresentation == null) {
        out.printIndented(cedarAdminUserName + " user was not found on Keycloak", Color.RED);
      } else {
        out.printIndented(cedarAdminUserName + " user was found on Keycloak", Color.GREEN);
        printOutUser(out, userRepresentation);
      }
    }

    return 0;
  }

}
