package org.metadatacenter.admin.task;

import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.representations.idm.UserRepresentation;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.constant.KeycloakConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

public class GetAdminUserKeycloakProfile extends AbstractKeycloakReadingTask {

  private Logger logger = LoggerFactory.getLogger(GetAdminUserKeycloakProfile.class);

  public GetAdminUserKeycloakProfile() {
    description.add("Reads cedar-admin user details from Keycloak.");
  }

  @Override
  public void init(CedarConfig config) {
    adminUserUUID = config.getKeycloakConfig().getAdminUser().getUuid();

    cedarAdminUserName = config.getKeycloakConfig().getAdminUser().getUserName();
    cedarAdminUserPassword = config.getKeycloakConfig().getAdminUser().getPassword();
    keycloakClientId = config.getKeycloakConfig().getClientId();

    System.out.println("adminUserUUID         : " + adminUserUUID);
    System.out.println("cedarAdminUserName    : " + cedarAdminUserName);
    System.out.println("cedarAdminUserPassword: " + cedarAdminUserPassword);
    System.out.println("keycloakClientId      : " + keycloakClientId);

    InputStream keycloakConfig = Thread.currentThread().getContextClassLoader().getResourceAsStream(KeycloakConstants
        .JSON);
    KeycloakDeployment keycloakDeployment = KeycloakDeploymentBuilder.build(keycloakConfig);

    keycloakRealmName = keycloakDeployment.getRealm();
    keycloakBaseURI = keycloakDeployment.getAuthServerBaseUrl();

    System.out.println("keycloakRealmName     : " + keycloakRealmName);
    System.out.println("keycloakBaseURI       : " + keycloakBaseURI);
  }


  @Override
  public int execute() {
    UserRepresentation userRepresentation = getAdminUserFromKeycloak();
    if (userRepresentation == null) {
      System.out.println(cedarAdminUserName + " user was not found on Keycloak");
    } else {
      System.out.println(cedarAdminUserName + " user was found on Keycloak");
      System.out.println("First name: " + userRepresentation.getFirstName());
      System.out.println("Last name : " + userRepresentation.getLastName());
      System.out.println("Id        : " + userRepresentation.getId());
      System.out.println("Email     : " + userRepresentation.getEmail());
    }
    return 0;
  }

}
