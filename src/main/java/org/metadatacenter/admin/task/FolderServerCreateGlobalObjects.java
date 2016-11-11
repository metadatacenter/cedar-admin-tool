package org.metadatacenter.admin.task;

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
    AdminServiceSession adminSession = createCedarAdminSession(cedarConfig, false);
    adminSession.ensureGlobalObjectsExists();
    AdminServiceSession adminSessionWithHome = createCedarAdminSession(cedarConfig, true);
    return 0;
  }
}