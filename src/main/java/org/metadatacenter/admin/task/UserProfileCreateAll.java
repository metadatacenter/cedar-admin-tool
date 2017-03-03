package org.metadatacenter.admin.task;

import org.keycloak.representations.idm.UserRepresentation;
import org.metadatacenter.server.security.model.user.CedarSuperRole;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.CedarUserExtract;
import org.metadatacenter.server.security.model.user.CedarUserRole;
import org.metadatacenter.server.security.util.CedarUserUtil;
import org.metadatacenter.server.service.UserService;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class UserProfileCreateAll extends AbstractKeycloakReadingTask {

  public UserProfileCreateAll() {
    description.add("Creates user profiles in Mongo based on all the registered user data from Keycloak.");
    description.add("homeFolderId will be left null for the profiles.");
  }

  @Override
  public void init() {
    initKeycloak();
  }

  @Override
  public int execute() {
    List<UserRepresentation> userRepresentations = listAllUsersFromKeycloak();
    if (userRepresentations == null) {
      out.println("Users not found on Keycloak");
    } else {
      UserService userService = getUserService();
      for (UserRepresentation ur : userRepresentations) {
        out.printSeparator();

        out.println("First name: " + ur.getFirstName());
        out.println("Last name : " + ur.getLastName());
        out.println("Id        : " + ur.getId());
        out.println("Email     : " + ur.getEmail());

        CedarSuperRole superRole = CedarSuperRole.NORMAL;
        String apiKey = null;
        if (adminUserUUID.equals(ur.getId())) {
          superRole = CedarSuperRole.BUILT_IN_ADMIN;
          apiKey = cedarConfig.getAdminUserConfig().getApiKey();
        }

        CedarUserExtract cue = new CedarUserExtract(ur.getId(), ur.getFirstName(), ur.getLastName(), ur.getEmail());
        CedarUser user = CedarUserUtil.createUserFromBlueprint(cedarConfig, cue, apiKey, superRole);

        try {
          CedarUser u = userService.createUser(user);
          out.println("User created.");
        } catch (Exception e) {
          out.error("Error while creating user: " + ur.getEmail(), e);
        }

        out.printSeparator();
      }
    }
    return 0;
  }

}
