package org.metadatacenter.admin.task;

import org.metadatacenter.server.AdminServiceSession;

public class GraphDbWipeAll extends AbstractNeo4JAccessTask {

  public GraphDbWipeAll() {
    description.add("Deletes all 'CEDAR' nodes from the Neo4j server.");
  }

  @Override
  public void init() {
  }

  @Override
  public int execute() {
    if (!getConfirmInput("Deleting all 'CEDAR' nodes from the Neo4j server...")) {
      return -1;
    }

    AdminServiceSession adminSession = createCedarAdminSession(cedarConfig);
    adminSession.wipeAllData();

    return 0;
  }

}
