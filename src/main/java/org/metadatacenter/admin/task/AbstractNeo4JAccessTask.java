package org.metadatacenter.admin.task;

import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.security.CedarAccessException;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.AdminServiceSession;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.service.UserService;

public abstract class AbstractNeo4JAccessTask extends AbstractCedarAdminTask {

  protected AdminServiceSession createCedarAdminSession(CedarConfig cedarConfig) throws CedarAccessException {
    UserService userService = getUserService();
    CedarRequestContext cedarRequestContext = CedarRequestContextFactory.fromAdminUser(cedarConfig, userService);
    return CedarDataServices.getAdminServiceSession(cedarRequestContext);
  }

  protected FolderServiceSession createCedarFolderSession(CedarConfig cedarConfig) throws CedarAccessException {
    UserService userService = getUserService();
    CedarRequestContext cedarRequestContext = CedarRequestContextFactory.fromAdminUser(cedarConfig, userService);
    return CedarDataServices.getFolderServiceSession(cedarRequestContext);
  }
}