package org.metadatacenter.admin.task;

import org.metadatacenter.server.AdminServiceSession;

public class GraphDbCreateGlobalObjects extends AbstractNeo4JAccessTask {

  public GraphDbCreateGlobalObjects() {
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
    AdminServiceSession adminSession = createCedarAdminSession(cedarConfig);
    adminSession.ensureGlobalObjectsExists();
    return 0;
  }
}
