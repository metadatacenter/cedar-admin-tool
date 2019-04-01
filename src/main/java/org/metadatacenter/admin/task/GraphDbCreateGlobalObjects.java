package org.metadatacenter.admin.task;

import org.metadatacenter.server.AdminServiceSession;

public class GraphDbCreateGlobalObjects extends AbstractNeo4JAccessTask {

  public GraphDbCreateGlobalObjects() {
    description.add("Creates global folders in the graph database: /, /Users");
    description.add("Creates 'Everybody' group");
    description.add("Creates 'cedar-admin' user in Neo4J");
  }

  @Override
  public void init() {
    super.init();
  }

  @Override
  public int execute() {
    AdminServiceSession adminSession = createUnconditionalCedarAdminSession(cedarConfig);
    adminSession.ensureGlobalObjectsExists();
    return 0;
  }
}
