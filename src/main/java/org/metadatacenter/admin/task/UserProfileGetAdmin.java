package org.metadatacenter.admin.task;

import org.keycloak.representations.idm.UserRepresentation;
import org.metadatacenter.admin.util.Color;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.service.UserService;

public class UserProfileGetAdmin extends AbstractKeycloakReadingTask {

  private String adminUserId;

  public UserProfileGetAdmin() {
    description.add("Reads cedar-admin user details from Keycloak.");
  }

  @Override
  public void init() {
    adminUserUUID = cedarConfig.getAdminUserConfig().getUuid();
    adminUserId = cedarConfig.getAdminUserId();

    initKeycloak();

    out.println();
    out.println("Data from config:", Color.YELLOW);
    out.printIndented("adminUserUUID         : " + adminUserUUID);
    out.printIndented("cedarAdminUserName    : " + cedarAdminUserName);
    out.printIndented("cedarAdminUserPassword: " + cedarAdminUserPassword);
    out.printIndented("keycloakClientId      : " + keycloakClientId);
    out.printIndented("keycloakRealmName     : " + keycloakRealmName);
    out.printIndented("keycloakBaseURI       : " + keycloakBaseURI);

    out.println();
    out.println("Computed data:", Color.YELLOW);
    out.printIndented("adminUserId           : " + adminUserId);
  }


  @Override
  public int execute() {
    UserRepresentation userRepresentation = getAdminUserFromKeycloak();
    out.println();
    out.println("Data from Keycloak:", Color.YELLOW);
    if (userRepresentation == null) {
      out.printIndented(cedarAdminUserName + " user was not found on Keycloak", Color.RED);
    } else {
      out.printIndented(cedarAdminUserName + " user was found on Keycloak", Color.GREEN);
      out.printIndented("First name: " + userRepresentation.getFirstName());
      out.printIndented("Last name : " + userRepresentation.getLastName());
      out.printIndented("Id        : " + userRepresentation.getId());
      out.printIndented("Email     : " + userRepresentation.getEmail());
    }

    out.println();
    out.println("Data from MongoDB:", Color.YELLOW);

    UserService userService = getUserService();

    CedarUser user = null;
    boolean exceptionWhileReading = false;
    try {
      user = userService.findUser(adminUserId);
    } catch (Exception e) {
      out.error("Error while reading user for id: " + adminUserId, e);
      exceptionWhileReading = true;
    }

    if (user == null && !exceptionWhileReading) {
      out.printIndented(cedarAdminUserName + " user was not found in MongoDB", Color.RED);
    } else {
      out.printIndented(cedarAdminUserName + " user was found in MongoDB", Color.GREEN);
      out.printIndented("First active API KEY: " + user.getFirstActiveApiKey());
      out.printIndented("Home folder Id      : " + user.getHomeFolderId());
    }

    return 0;
  }

}
