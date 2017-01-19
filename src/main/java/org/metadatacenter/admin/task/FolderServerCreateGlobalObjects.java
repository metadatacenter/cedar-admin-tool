package org.metadatacenter.admin.task;

import org.metadatacenter.exception.security.CedarAccessException;
import org.metadatacenter.server.AdminServiceSession;

public class FolderServerCreateGlobalObjects extends AbstractNeo4JAccessTask {

  public FolderServerCreateGlobalObjects() {
    description.add("Creates global folders in the graph database: /, /Users, /Lost+Found");
    description.add("Creates home folder for 'cedar-admin' user");
    description.add("Creates 'Everybody' group");
    description.add("Updates 'cedar-admin' user profile in MongoDB, sets homeFolderId");
  }

  @Override
  public void init() {

  }

  @Override
  public int execute() {
    AdminServiceSession adminSession = null;
    try {
      adminSession = createCedarAdminSession(cedarConfig, false);
    } catch (CedarAccessException e) {
      e.printStackTrace();
      return -1;
    }
    adminSession.ensureGlobalObjectsExists();
    try {
      AdminServiceSession adminSessionWithHome = createCedarAdminSession(cedarConfig, true);
    } catch (CedarAccessException e) {
      e.printStackTrace();
      return -2;
    }
    return 0;
  }
}