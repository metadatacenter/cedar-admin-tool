package org.metadatacenter.admin.task;

import org.keycloak.representations.idm.UserRepresentation;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.CedarUserExtract;
import org.metadatacenter.server.security.model.user.CedarUserRole;
import org.metadatacenter.server.security.util.CedarUserUtil;
import org.metadatacenter.server.service.UserService;

import java.util.ArrayList;
import java.util.List;

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

        List<CedarUserRole> roles = null;
        if (adminUserUUID.equals(ur.getId())) {
          roles = new ArrayList<>();
          roles.add(CedarUserRole.TEMPLATE_CREATOR);
          roles.add(CedarUserRole.TEMPLATE_INSTANTIATOR);
          roles.add(CedarUserRole.BUILT_IN_SYSTEM_ADMINISTRATOR);
          roles.add(CedarUserRole.ADMINISTRATOR);
          roles.add(CedarUserRole.FILESYSTEM_ADMINISTRATOR);
        }

        CedarUserExtract cue = new CedarUserExtract(ur.getId(), ur.getFirstName(), ur.getLastName());
        CedarUser user = CedarUserUtil.createUserFromBlueprint(cue, roles);

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
