package org.metadatacenter.admin.task;

import org.keycloak.representations.idm.UserRepresentation;
import org.metadatacenter.model.folderserver.CedarFSFolder;
import org.metadatacenter.server.neo4j.IPathUtil;
import org.metadatacenter.server.neo4j.Neo4JProxy;
import org.metadatacenter.server.neo4j.Neo4JUserSession;
import org.metadatacenter.server.neo4j.Neo4jConfig;
import org.metadatacenter.server.security.CedarUserRolePermissionUtil;
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
          Neo4JProxy neo4JProxy = buildNeo4JProxy();
          Neo4JUserSession neoSession = buildNeo4JSession(neo4JProxy, user);

          String homeFolderPath = neoSession.getHomeFolderPath();
          CedarFSFolder userHomeFolder = neoSession.findFolderByPath(homeFolderPath);

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
