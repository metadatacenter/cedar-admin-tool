package org.metadatacenter.admin.task;

import org.keycloak.representations.idm.UserRepresentation;
import org.metadatacenter.model.folderserver.CedarFSFolder;
import org.metadatacenter.server.neo4j.Neo4JProxy;
import org.metadatacenter.server.neo4j.Neo4JUserSession;
import org.metadatacenter.server.security.CedarUserRolePermissionUtil;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.service.UserService;
import org.metadatacenter.util.json.JsonMapper;

import java.util.List;

public class FolderServerCreateUserHomeFolders extends AbstractKeycloakReadingTask {

  public FolderServerCreateUserHomeFolders() {
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
          Neo4JProxy neo4JProxy = buildNeo4JProxy();
          Neo4JUserSession neoSession = buildNeo4JSession(neo4JProxy, user);

          String homeFolderPath = neoSession.getHomeFolderPath();

          out.println("Home folder: " + homeFolderPath);

          CedarFSFolder userHomeFolder = neoSession.findFolderByPath(homeFolderPath);

          if (userHomeFolder != null) {
            out.warn("User home folder is already present.");
          } else {
            neoSession = buildNeo4JSession(neo4JProxy, user, true);
            userHomeFolder = neoSession.findFolderByPath(homeFolderPath);
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
