package org.metadatacenter.admin.task;

import org.metadatacenter.exception.security.CedarAccessException;
import org.metadatacenter.server.AdminServiceSession;

public class WorkspaceServerCreateGlobalObjects extends AbstractNeo4JAccessTask {

  public WorkspaceServerCreateGlobalObjects() {
    description.add("Creates global folders in the graph database: /, /Users");
    description.add("Creates home folder for 'cedar-admin' user");
    description.add("Creates 'Everybody' group");
    description.add("Updates 'cedar-admin' user profile in MongoDB, sets homeFolderId");
  }

  @Override
  public void init() {

  }

  @Override
  public int execute() {
    AdminServiceSession adminSession;
    try {
      adminSession = createCedarAdminSession(cedarConfig);
    } catch (CedarAccessException e) {
      e.printStackTrace();
      return -1;
    }
    adminSession.ensureGlobalObjectsExists();
    return 0;
  }
}