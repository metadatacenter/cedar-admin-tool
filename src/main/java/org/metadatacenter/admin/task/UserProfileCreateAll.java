package org.metadatacenter.admin.task;

import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.representations.idm.UserRepresentation;
import org.metadatacenter.constant.KeycloakConstants;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.CedarUserRole;
import org.metadatacenter.server.security.util.CedarUserUtil;
import org.metadatacenter.server.service.UserService;
import org.metadatacenter.server.service.mongodb.UserServiceMongoDB;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class UserProfileCreateAll extends AbstractKeycloakReadingTask {

  private UserService userService;

  public UserProfileCreateAll() {
    description.add("Creates user profiles in Mongo based on all the registered user data from Keycloak.");
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

    userService = new UserServiceMongoDB(
        cedarConfig.getMongoConfig().getDatabaseName(),
        cedarConfig.getMongoConfig().getCollections().get(CedarNodeType.USER.getValue()));

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

        List<CedarUserRole> roles = null;
        if (adminUserUUID.equals(ur.getId())) {
          roles = new ArrayList<>();
          roles.add(CedarUserRole.TEMPLATE_CREATOR);
          roles.add(CedarUserRole.TEMPLATE_INSTANTIATOR);
          roles.add(CedarUserRole.BUILT_IN_SYSTEM_ADMINISTRATOR);
          roles.add(CedarUserRole.ADMINISTRATOR);
          roles.add(CedarUserRole.FILESYSTEM_ADMINISTRATOR);
        }

        CedarUser user = CedarUserUtil.createUserFromBlueprint(ur.getId(),
            ur.getFirstName() + " " + ur.getLastName(),
            roles,
            cedarConfig.getBlueprintUserProfile(),
            cedarConfig.getBlueprintUIPreferences());

        try {
          CedarUser u = userService.createUser(user);
        } catch (Exception e) {
          out.error("Error while creating user: " + ur.getEmail(), e);
        }

        out.printSeparator();
      }
    }
    return 0;
  }

}
