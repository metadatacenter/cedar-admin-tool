package org.metadatacenter.admin.task;

import org.keycloak.representations.idm.UserRepresentation;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.UserServiceSession;
import org.metadatacenter.server.security.model.user.CedarSuperRole;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.CedarUserExtract;
import org.metadatacenter.server.security.util.CedarUserUtil;
import org.metadatacenter.server.service.UserService;

import java.util.List;

public class GraphDbCreateAllUsers extends AbstractKeycloakReadingTask {

  public GraphDbCreateAllUsers() {
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
      UserService userService = getNeoUserService();
      for (UserRepresentation ur : userRepresentations) {
        out.printSeparator();
        printOutUser(out, ur);

        CedarSuperRole superRole = null;
        List<String> realmRoles = ur.getRealmRoles();
        if (realmRoles != null) {
          if (realmRoles.contains(CedarSuperRole.BUILT_IN_ADMIN.getValue())) {
            superRole = CedarSuperRole.BUILT_IN_ADMIN;
          } else if (realmRoles.contains(CedarSuperRole.NORMAL.getValue())) {
            superRole = CedarSuperRole.NORMAL;
          }
        }

        if (superRole == null) {
          out.error("The user '" + ur.getUsername() + "' has no recognized roles. The user will not be created in MongoDB");
        } else {

          String userUUID = ur.getId();
          String userId = linkedDataUtil.getUserId(userUUID);
          CedarUserExtract cue = new CedarUserExtract(userId, ur.getFirstName(), ur.getLastName(), ur.getEmail());
          CedarUser user = CedarUserUtil.createUserFromBlueprint(cedarConfig.getBlueprintUserProfile(), cue, superRole, cedarConfig,
              ur.getUsername());

          try {

            CedarUser existingUser = userService.findUser(user.getResourceId());
            if (existingUser == null) {
              existingUser = userService.createUser(user);
            }

            CedarRequestContext userRequestContext = CedarRequestContextFactory.fromUser(existingUser);
            UserServiceSession userSession = CedarDataServices.getUserServiceSession(userRequestContext);

            userSession.addUserToEverybodyGroup(existingUser.getResourceId());

            FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(userRequestContext);
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

            out.println("Id        : " + userId);
            out.println("User created.");
          } catch (Exception e) {
            out.error("Error while creating user: " + ur.getEmail(), e);
          }

          out.printSeparator();
        }
      }
    }
    return 0;
  }

}
