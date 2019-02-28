package org.metadatacenter.admin.task;

import org.keycloak.representations.idm.UserRepresentation;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.exception.security.CedarAccessException;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.service.UserService;

import java.util.List;

public class UserProfileUpdateAllSetHomeFolder extends AbstractKeycloakReadingTask {

  public UserProfileUpdateAllSetHomeFolder() {
    description.add("Updates user home folder id in Mongo.");
    description.add("The iteration is done on the Keycloak user list.");
    description.add("The home folder is read from the Neo4j.");
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
          CedarRequestContext userRequestContext;
          try {
            userRequestContext = CedarRequestContextFactory.fromUser(user);
          } catch (CedarAccessException e) {
            e.printStackTrace();
            return -1;
          }
          FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(userRequestContext);

          FolderServerFolder userHomeFolder = folderSession.findHomeFolderOf();

          if (userHomeFolder == null) {
            out.error("Can not find home folder for: " + userId);
          } else {
            user.setHomeFolderId(userHomeFolder.getId());
            try {
              userService.updateUser(userId, user);
              out.println("The user was updated");
            } catch (Exception e) {
              out.error("Error while updating user: " + ur.getEmail(), e);
            }
          }
        }
        out.printSeparator();
      }
    }
    return 0;
  }

}
