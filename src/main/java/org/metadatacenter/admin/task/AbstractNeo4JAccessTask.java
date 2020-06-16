package org.metadatacenter.admin.task;

import org.keycloak.representations.idm.UserRepresentation;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.BlueprintUserProfile;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.security.CedarAccessException;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.*;
import org.metadatacenter.server.security.model.user.CedarSuperRole;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.CedarUserApiKey;
import org.metadatacenter.server.security.util.CedarUserUtil;
import org.metadatacenter.server.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

public abstract class AbstractNeo4JAccessTask extends AbstractKeycloakReadingTask {

  private static final Logger log = LoggerFactory.getLogger(AbstractNeo4JAccessTask.class);

  @Override
  public void init() {
    initKeycloak(cedarConfig);
  }

  protected AdminServiceSession createCedarAdminSession(CedarConfig cedarConfig) {
    UserService userService = getNeoUserService();
    CedarRequestContext cedarRequestContext = CedarRequestContextFactory.fromAdminUser(cedarConfig, userService);
    return CedarDataServices.getAdminServiceSession(cedarRequestContext);
  }

  protected AdminServiceSession createUnconditionalCedarAdminSession(CedarConfig cedarConfig) {
    UserRepresentation adminRepresentation = null;

    List<UserRepresentation> userRepresentations = listAllUsersFromKeycloak();
    for (UserRepresentation ur : userRepresentations) {
      if (cedarConfig.getAdminUserConfig().getUserName().equals(ur.getUsername())) {
        adminRepresentation = ur;
      }
    }

    CedarUser fakeAdminExtract = new CedarUser();
    fakeAdminExtract.setFirstName(adminRepresentation.getFirstName());
    fakeAdminExtract.setLastName(adminRepresentation.getLastName());
    fakeAdminExtract.setEmail(adminRepresentation.getEmail());
    fakeAdminExtract.setId(linkedDataUtil.getUserId(adminRepresentation.getId()));

    CedarUser fakeAdmin = CedarUserUtil.createUserFromBlueprint(cedarConfig.getBlueprintUserProfile(), fakeAdminExtract,
        CedarSuperRole.BUILT_IN_ADMIN, cedarConfig, adminRepresentation.getUsername());

    fakeAdmin.getApiKeys().get(0).setKey(cedarConfig.getAdminUserConfig().getApiKey());

    CedarRequestContext cedarRequestContext = null;
    try {
      cedarRequestContext = CedarRequestContextFactory.fromUser(fakeAdmin);
    } catch (CedarAccessException e) {
      log.error("Error while creating unconditional admin user", e);
    }
    return CedarDataServices.getAdminServiceSession(cedarRequestContext);
  }

  protected FolderServiceSession createCedarFolderSession(CedarConfig cedarConfig) {
    UserService userService = getNeoUserService();
    CedarRequestContext cedarRequestContext = CedarRequestContextFactory.fromAdminUser(cedarConfig, userService);
    return CedarDataServices.getFolderServiceSession(cedarRequestContext);
  }

  protected UserServiceSession createCedarUserSession(CedarConfig cedarConfig) {
    UserService userService = getNeoUserService();
    CedarRequestContext cedarRequestContext = CedarRequestContextFactory.fromAdminUser(cedarConfig, userService);
    return CedarDataServices.getUserServiceSession(cedarRequestContext);
  }

  protected GroupServiceSession createCedarGroupSession(CedarConfig cedarConfig) {
    UserService userService = getNeoUserService();
    CedarRequestContext cedarRequestContext = CedarRequestContextFactory.fromAdminUser(cedarConfig, userService);
    return CedarDataServices.getGroupServiceSession(cedarRequestContext);
  }

  protected GraphServiceSession createCedarGraphSession(CedarConfig cedarConfig) {
    UserService userService = getNeoUserService();
    CedarRequestContext cedarRequestContext = CedarRequestContextFactory.fromAdminUser(cedarConfig, userService);
    return CedarDataServices.getGraphServiceSession(cedarRequestContext);
  }
}
