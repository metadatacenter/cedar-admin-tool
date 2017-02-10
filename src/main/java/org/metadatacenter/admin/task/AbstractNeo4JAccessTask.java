package org.metadatacenter.admin.task;

import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.exception.security.CedarAccessException;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.AdminServiceSession;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.service.UserService;

public abstract class AbstractNeo4JAccessTask extends AbstractCedarAdminTask {

  protected CedarUser adminUser;

  protected AdminServiceSession createCedarAdminSession(CedarConfig cedarConfig, boolean createHome) throws
      CedarAccessException {
    UserService userService = getUserService();

    String adminUserUUID = cedarConfig.getAdminUserConfig().getUuid();

    try {
      adminUser = userService.findUser(adminUserUUID);
    } catch (Exception ex) {
      out.error("Error while loading admin user for id:" + adminUserUUID + ":");
    }
    if (adminUser == null) {
      out.error("Admin user not found for id:" + adminUserUUID + ".");
      out.error("The requested task was not completed!");
      return null;
    } else {
      CedarRequestContext cedarRequestContext = CedarRequestContextFactory.fromUser(adminUser);
      return CedarDataServices.getAdminServiceSession(cedarRequestContext);
    }
  }

  protected FolderServiceSession createCedarFolderSession(CedarConfig cedarConfig) throws CedarAccessException {
    UserService userService = getUserService();

    String adminUserUUID = cedarConfig.getAdminUserConfig().getUuid();

    try {
      adminUser = userService.findUser(adminUserUUID);
    } catch (Exception ex) {
      out.error("Error while loading admin user for id:" + adminUserUUID + ":");
    }
    if (adminUser == null) {
      out.error("Admin user not found for id:" + adminUserUUID + ".");
      out.error("The requested task was not completed!");
      return null;
    } else {
      CedarRequestContext cedarRequestContext = CedarRequestContextFactory.fromUser(adminUser);
      return CedarDataServices.getFolderServiceSession(cedarRequestContext);
    }
  }
}