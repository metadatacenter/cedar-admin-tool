package org.metadatacenter.admin.task;

import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.representations.idm.UserRepresentation;
import org.metadatacenter.constant.KeycloakConstants;

import java.io.InputStream;
import java.util.List;

public class UserProfileListAll extends AbstractKeycloakReadingTask {

  public UserProfileListAll() {
    description.add("Reads all registered user data from Keycloak.");
  }

  @Override
  public void init() {
    adminUserUUID = cedarConfig.getKeycloakConfig().getAdminUser().getUuid();

    cedarAdminUserName = cedarConfig.getKeycloakConfig().getAdminUser().getUserName();
    cedarAdminUserPassword = cedarConfig.getKeycloakConfig().getAdminUser().getPassword();
    keycloakClientId = cedarConfig.getKeycloakConfig().getClientId();

    InputStream keycloakConfig = Thread.currentThread().getContextClassLoader().getResourceAsStream(KeycloakConstants
        .JSON);
    KeycloakDeployment keycloakDeployment = KeycloakDeploymentBuilder.build(keycloakConfig);

    keycloakRealmName = keycloakDeployment.getRealm();
    keycloakBaseURI = keycloakDeployment.getAuthServerBaseUrl();
  }

  @Override
  public int execute() {
    List<UserRepresentation> userRepresentations = listAllUsersFromKeycloak();
    if (userRepresentations == null) {
      out.println("Users not found on Keycloak");
    } else {
      for (UserRepresentation ur : userRepresentations) {
        out.printSeparator();
        out.println("First name: " + ur.getFirstName());
        out.println("Last name : " + ur.getLastName());
        out.println("Id        : " + ur.getId());
        out.println("Email     : " + ur.getEmail());
        out.printSeparator();
      }
    }
    return 0;
  }

}
