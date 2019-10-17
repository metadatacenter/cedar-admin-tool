package org.metadatacenter.admin.task;

import org.keycloak.representations.idm.UserRepresentation;
import org.metadatacenter.config.BlueprintUserProfile;
import org.metadatacenter.server.security.CedarUserRolePermissionUtil;
import org.metadatacenter.server.security.model.user.CedarSuperRole;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.CedarUserRole;
import org.metadatacenter.server.security.util.CedarUserUtil;
import org.metadatacenter.server.service.UserService;

import java.util.List;

public class UserProfileUpdateAllUpdatePermissions extends AbstractKeycloakReadingTask {

  public UserProfileUpdateAllUpdatePermissions() {
    description.add("Updates user profiles in Neo4j. The permissions will be recalculated and updated.");
    description.add("The iteration is done on the Keycloak user list.");
  }

  @Override
  public void init() {
    initKeycloak();
  }

  @Override
  public int execute() {
    BlueprintUserProfile blueprintUserProfile = cedarConfig.getBlueprintUserProfile();
    List<UserRepresentation> userRepresentations = listAllUsersFromKeycloak();
    if (userRepresentations == null) {
      out.println("Users not found on Keycloak");
    } else {
      UserService userService = getNeoUserService();
      for (UserRepresentation ur : userRepresentations) {
        out.printSeparator();
        printOutUser(out, ur);

        CedarUser user = null;
        boolean exceptionWhileReading = false;
        String userId = linkedDataUtil.getUserId(ur.getId());
        try {
          user = userService.findUser(userId);
        } catch (Exception e) {
          out.error("Error while reading user: " + ur.getEmail(), e);
          exceptionWhileReading = true;
        }

        if (user == null && !exceptionWhileReading) {
          out.error("The user was not found for id:" + userId);
        } else {
          user.getRoles().clear();
          for (String realRole : ur.getRealmRoles()) {
            CedarSuperRole superRole = CedarSuperRole.forValue(realRole);
            if (superRole != null) {
              List<CedarUserRole> roles = CedarUserUtil.getRolesForType(blueprintUserProfile, superRole);
              user.getRoles().addAll(roles);
            }
          }
          CedarUserRolePermissionUtil.expandRolesIntoPermissions(user);
          try {
            userService.updateUser(user);
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
