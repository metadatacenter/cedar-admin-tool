package org.metadatacenter.admin.task;

import org.keycloak.representations.idm.UserRepresentation;

import java.util.List;

public class UserProfileListAll extends AbstractKeycloakReadingTask {

  public UserProfileListAll() {
    description.add("Reads all registered user data from Keycloak.");
  }

  @Override
  public void init() {
    initKeycloak(cedarConfig);
  }

  @Override
  public int execute() {
    List<UserRepresentation> userRepresentations = listAllUsersFromKeycloak();
    if (userRepresentations == null) {
      out.println("Users not found on Keycloak");
    } else {
      for (UserRepresentation ur : userRepresentations) {
        out.printSeparator();
        printOutUser(out, ur);
        out.printSeparator();
      }
    }
    return 0;
  }

}
