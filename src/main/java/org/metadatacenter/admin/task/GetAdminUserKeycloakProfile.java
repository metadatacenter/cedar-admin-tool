package org.metadatacenter.admin.task;

import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.representations.idm.UserRepresentation;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.constant.KeycloakConstants;

import java.io.InputStream;

public class GetAdminUserKeycloakProfile extends AbstractKeycloakReadingTask {

  public GetAdminUserKeycloakProfile() {
    description.add("Reads cedar-admin user details from Keycloak.");
  }

  @Override
  public void init(CedarConfig config) {
    adminUserUUID = config.getKeycloakConfig().getAdminUser().getUuid();

    cedarAdminUserName = config.getKeycloakConfig().getAdminUser().getUserName();
    cedarAdminUserPassword = config.getKeycloakConfig().getAdminUser().getPassword();
    keycloakClientId = config.getKeycloakConfig().getClientId();

    out.println("adminUserUUID         : " + adminUserUUID);
    out.println("cedarAdminUserName    : " + cedarAdminUserName);
    out.println("cedarAdminUserPassword: " + cedarAdminUserPassword);
    out.println("keycloakClientId      : " + keycloakClientId);

    InputStream keycloakConfig = Thread.currentThread().getContextClassLoader().getResourceAsStream(KeycloakConstants
        .JSON);
    KeycloakDeployment keycloakDeployment = KeycloakDeploymentBuilder.build(keycloakConfig);

    keycloakRealmName = keycloakDeployment.getRealm();
    keycloakBaseURI = keycloakDeployment.getAuthServerBaseUrl();

    out.println("keycloakRealmName     : " + keycloakRealmName);
    out.println("keycloakBaseURI       : " + keycloakBaseURI);
  }


  @Override
  public int execute() {
    UserRepresentation userRepresentation = getAdminUserFromKeycloak();
    if (userRepresentation == null) {
      out.println(cedarAdminUserName + " user was not found on Keycloak");
    } else {
      out.println(cedarAdminUserName + " user was found on Keycloak");
      out.println("First name: " + userRepresentation.getFirstName());
      out.println("Last name : " + userRepresentation.getLastName());
      out.println("Id        : " + userRepresentation.getId());
      out.println("Email     : " + userRepresentation.getEmail());
    }
    return 0;
  }

}
