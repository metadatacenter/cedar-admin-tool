package org.metadatacenter.admin.task;

import org.keycloak.representations.idm.UserRepresentation;
import org.metadatacenter.server.security.CedarUserRolePermissionUtil;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.service.UserService;

import java.util.List;

public class UserProfileUpdateAllUpdatePermissions extends AbstractKeycloakReadingTask {

  public UserProfileUpdateAllUpdatePermissions() {
    description.add("Updates user profiles in Mongo. The permissions will be recalculated and updated.");
    description.add("The iteration is done on the Keycloak user list.");
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

        CedarUser user = null;
        boolean exceptionWhileReading = false;
        try {
          user = userService.findUser(ur.getId());
        } catch (Exception e) {
          out.error("Error while reading user: " + ur.getEmail(), e);
          exceptionWhileReading = true;
        }

        if (user == null && !exceptionWhileReading) {
          out.error("The user was not found for id:" + ur.getId());
        } else {
          CedarUserRolePermissionUtil.expandRolesIntoPermissions(user);
          try {
            userService.updateUser(ur.getId(), user);
            out.println("The user was updated");
          } catch (Exception e) {
            out.error("Error while updating user: " + ur.getEmail(), e);
          }
        }
        out.printSeparator();
      }
    }
    return 0;
  }

}
