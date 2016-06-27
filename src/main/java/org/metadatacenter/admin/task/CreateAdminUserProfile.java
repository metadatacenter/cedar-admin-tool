package org.metadatacenter.admin.task;

import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.representations.idm.UserRepresentation;
import org.metadatacenter.config.BlueprintUIPreferences;
import org.metadatacenter.config.BlueprintUserProfile;
import org.metadatacenter.config.CedarConfig;
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

public class CreateAdminUserProfile extends AbstractKeycloakReadingTask {

  private String mongoDatabaseName;
  private String usersCollectionName;
  private BlueprintUserProfile blueprintUserProfile;
  private BlueprintUIPreferences blueprintUIPreferences;
  private static UserService userService;

  public CreateAdminUserProfile() {
    description.add("Reads cedar-admin user details from Keycloak.");
    description.add("Creates cedar-admin user profile in MongoDB.");
    description.add("The value of homeFolderId for cedar-admin will be 'null' after this step.");
  }

  @Override
  public void init(CedarConfig config) {
    adminUserUUID = config.getKeycloakConfig().getAdminUser().getUuid();

    mongoDatabaseName = config.getMongoConfig().getDatabaseName();
    usersCollectionName = config.getMongoConfig().getCollections().get(CedarNodeType.USER.getValue());

    cedarAdminUserName = config.getKeycloakConfig().getAdminUser().getUserName();
    cedarAdminUserPassword = config.getKeycloakConfig().getAdminUser().getPassword();
    keycloakClientId = config.getKeycloakConfig().getClientId();

    blueprintUserProfile = config.getBlueprintUserProfile();
    blueprintUIPreferences = config.getBlueprintUIPreferences();

    userService = new UserServiceMongoDB(mongoDatabaseName, usersCollectionName);

    InputStream keycloakConfig = Thread.currentThread().getContextClassLoader().getResourceAsStream(KeycloakConstants
        .JSON);
    KeycloakDeployment keycloakDeployment = KeycloakDeploymentBuilder.build(keycloakConfig);

    keycloakRealmName = keycloakDeployment.getRealm();
    keycloakBaseURI = keycloakDeployment.getAuthServerBaseUrl();
  }

  private void createAdminUserProfileInMongoDb(UserRepresentation userRepresentation) {
    List<CedarUserRole> roles = new ArrayList<>();
    roles.add(CedarUserRole.TEMPLATE_CREATOR);
    roles.add(CedarUserRole.TEMPLATE_INSTANTIATOR);
    roles.add(CedarUserRole.BUILT_IN_SYSTEM_ADMINISTRATOR);
    CedarUser user = CedarUserUtil.createUserFromBlueprint(adminUserUUID, userRepresentation.getFirstName() + " " +
        userRepresentation.getLastName(), roles, blueprintUserProfile, blueprintUIPreferences);

    try {
      CedarUser u = userService.createUser(user);
    } catch (IOException e) {
      out.error("Error while creating " + cedarAdminUserName + " user", e);
    }
  }

  @Override
  public int execute() {
    UserRepresentation userRepresentation = getAdminUserFromKeycloak();
    if (userRepresentation == null) {
      out.error(cedarAdminUserName + " user was not found on Keycloak");
    } else {
      createAdminUserProfileInMongoDb(userRepresentation);
    }
    return 0;
  }

}
