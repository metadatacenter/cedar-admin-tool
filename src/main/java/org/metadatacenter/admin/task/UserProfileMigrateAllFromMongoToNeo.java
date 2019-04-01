package org.metadatacenter.admin.task;

import org.keycloak.representations.idm.UserRepresentation;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.service.UserService;

import java.util.List;

public class UserProfileMigrateAllFromMongoToNeo extends AbstractKeycloakReadingTask {

  public UserProfileMigrateAllFromMongoToNeo() {
    description.add(
        "Reads all users in Keycloak. Then reads them one by one from Mongo. If found, updates the representation in " +
            "Neo with preferences and homeFolderOf data");
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
      UserService mongoUserService = getMongoUserService();
      UserService neoUserService = getNeoUserService();
      for (UserRepresentation ur : userRepresentations) {
        out.printSeparator();
        printOutUser(out, ur);

        CedarUser user = null;
        boolean exceptionWhileReading = false;
        try {
          String id = linkedDataUtil.getUserId(ur.getId());
          user = mongoUserService.findUser(id);
        } catch (Exception e) {
          out.error("Error while reading user: " + ur.getEmail(), e);
          exceptionWhileReading = true;
        }

        if (user == null && !exceptionWhileReading) {
          out.error("The user was not found for id:" + ur.getId());
        } else {
          try {
            neoUserService.updateUser(user);
            out.println("The user was updated in Neo");
          } catch (Exception e) {
            out.error("Error while updating user in Neo: " + ur.getEmail(), e);
          }
        }
        out.printSeparator();
      }
    }
    return 0;
  }

}
