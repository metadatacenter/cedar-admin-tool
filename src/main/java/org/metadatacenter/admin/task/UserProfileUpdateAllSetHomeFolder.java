package org.metadatacenter.admin.task;

import org.keycloak.representations.idm.UserRepresentation;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.exception.security.CedarAccessException;
import org.metadatacenter.model.folderserver.FolderServerFolder;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.service.UserService;
import org.metadatacenter.util.json.JsonMapper;

import java.util.List;

public class UserProfileUpdateAllSetHomeFolder extends AbstractKeycloakReadingTask {

  public UserProfileUpdateAllSetHomeFolder() {
    description.add("Updates user home folder id in Mongo.");
    description.add("The iteration is done on the Keycloak user list.");
    description.add("The home folder is read from the folder server REST API.");
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
          CedarRequestContext cedarRequestContext = null;
          try {
            cedarRequestContext = CedarRequestContextFactory.fromUser(user);
          } catch (CedarAccessException e) {
            e.printStackTrace();
            return -1;
          }
          FolderServiceSession neoSession = CedarDataServices.getFolderServiceSession(cedarRequestContext);

          String homeFolderPath = neoSession.getHomeFolderPath();
          FolderServerFolder userHomeFolder = neoSession.findFolderByPath(homeFolderPath);

          if (userHomeFolder == null) {
            out.error("Can not find home folder: " + homeFolderPath);
          } else {
            user.setHomeFolderId(userHomeFolder.getId());
            try {
              userService.updateUser(ur.getId(), JsonMapper.MAPPER.valueToTree(user));
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
