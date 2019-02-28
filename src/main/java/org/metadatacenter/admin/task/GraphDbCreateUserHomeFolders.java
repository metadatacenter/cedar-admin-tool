package org.metadatacenter.admin.task;

import org.keycloak.representations.idm.UserRepresentation;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.exception.security.CedarAccessException;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.UserServiceSession;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.service.UserService;

import java.util.List;

public class GraphDbCreateUserHomeFolders extends AbstractKeycloakReadingTask {

  public GraphDbCreateUserHomeFolders() {
    description.add("Creates user home folders in Neo4J.");
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
        out.println("UUID      : " + ur.getId());
        out.println("Email     : " + ur.getEmail());

        String userId = null;
        CedarUser user = null;
        boolean exceptionWhileReading = false;
        try {
          userId = linkedDataUtil.getUserId(ur.getId());
          user = userService.findUser(userId);
        } catch (Exception e) {
          out.error("Error while reading user: " + ur.getEmail(), e);
          exceptionWhileReading = true;
        }

        if (user == null && !exceptionWhileReading) {
          out.error("The user was not found for id:" + userId);
        } else {
          CedarRequestContext userRequestContext = null;
          try {
            userRequestContext = CedarRequestContextFactory.fromUser(user);
          } catch (CedarAccessException e) {
            e.printStackTrace();
            return -1;
          }
          UserServiceSession userSession = CedarDataServices.getUserServiceSession(userRequestContext);
          FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(userRequestContext);

          userSession.ensureUserExists();
          FolderServerFolder userHomeFolder = folderSession.findHomeFolderOf();

          if (userHomeFolder != null) {
            out.warn("User home folder is already present.");
          } else {
            userHomeFolder = folderSession.ensureUserHomeExists();
            if (userHomeFolder != null) {
              out.println("Success: user home was created.");
            } else {
              out.error("Error: user home was not created!");
            }
          }
        }

        out.printSeparator();
      }
    }
    return 0;
  }

}
