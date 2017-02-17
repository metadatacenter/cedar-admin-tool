package org.metadatacenter.admin.task;

import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.representations.idm.UserRepresentation;
import org.metadatacenter.admin.util.Color;
import org.metadatacenter.constant.KeycloakConstants;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.service.UserService;

import java.io.InputStream;

public class UserProfileGetAdmin extends AbstractKeycloakReadingTask {

  public UserProfileGetAdmin() {
    description.add("Reads cedar-admin user details from Keycloak.");
  }

  @Override
  public void init() {
    adminUserUUID = cedarConfig.getAdminUserConfig().getUuid();

    cedarAdminUserName = cedarConfig.getAdminUserConfig().getUserName();
    cedarAdminUserPassword = cedarConfig.getAdminUserConfig().getPassword();
    keycloakClientId = cedarConfig.getKeycloakConfig().getClientId();
    out.println();
    out.println("Data from config:", Color.YELLOW);
    out.printIndented("adminUserUUID         : " + adminUserUUID);
    out.printIndented("cedarAdminUserName    : " + cedarAdminUserName);
    out.printIndented("cedarAdminUserPassword: " + cedarAdminUserPassword);
    out.printIndented("keycloakClientId      : " + keycloakClientId);

    InputStream keycloakConfig = Thread.currentThread().getContextClassLoader().getResourceAsStream(KeycloakConstants
        .JSON);
    KeycloakDeployment keycloakDeployment = KeycloakDeploymentBuilder.build(keycloakConfig);

    keycloakRealmName = keycloakDeployment.getRealm();
    keycloakBaseURI = keycloakDeployment.getAuthServerBaseUrl();

    out.printIndented("keycloakRealmName     : " + keycloakRealmName);
    out.printIndented("keycloakBaseURI       : " + keycloakBaseURI);
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
      user = userService.findUser(adminUserUUID);
    } catch (Exception e) {
      out.error("Error while reading user for id: " + adminUserUUID, e);
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
