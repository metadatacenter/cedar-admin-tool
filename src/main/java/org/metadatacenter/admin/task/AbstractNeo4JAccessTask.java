package org.metadatacenter.admin.task;

import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.*;
import org.metadatacenter.server.service.UserService;

public abstract class AbstractNeo4JAccessTask extends AbstractCedarAdminTask {

  protected AdminServiceSession createCedarAdminSession(CedarConfig cedarConfig) {
    UserService userService = getUserService();
    CedarRequestContext cedarRequestContext = CedarRequestContextFactory.fromAdminUser(cedarConfig, userService);
    return CedarDataServices.getAdminServiceSession(cedarRequestContext);
  }

  protected FolderServiceSession createCedarFolderSession(CedarConfig cedarConfig) {
    UserService userService = getUserService();
    CedarRequestContext cedarRequestContext = CedarRequestContextFactory.fromAdminUser(cedarConfig, userService);
    return CedarDataServices.getFolderServiceSession(cedarRequestContext);
  }

  protected UserServiceSession createCedarUserSession(CedarConfig cedarConfig) {
    UserService userService = getUserService();
    CedarRequestContext cedarRequestContext = CedarRequestContextFactory.fromAdminUser(cedarConfig, userService);
    return CedarDataServices.getUserServiceSession(cedarRequestContext);
  }

  protected GroupServiceSession createCedarGroupSession(CedarConfig cedarConfig) {
    UserService userService = getUserService();
    CedarRequestContext cedarRequestContext = CedarRequestContextFactory.fromAdminUser(cedarConfig, userService);
    return CedarDataServices.getGroupServiceSession(cedarRequestContext);
  }

  protected GraphServiceSession createCedarGraphSession(CedarConfig cedarConfig) {
    UserService userService = getUserService();
    CedarRequestContext cedarRequestContext = CedarRequestContextFactory.fromAdminUser(cedarConfig, userService);
    return CedarDataServices.getGraphServiceSession(cedarRequestContext);
  }
}