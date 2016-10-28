package org.metadatacenter.admin.task;

import org.metadatacenter.server.AdminServiceSession;

public class FolderServerWipeAll extends AbstractNeo4JAccessTask {

  public FolderServerWipeAll() {
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

    AdminServiceSession adminSession = createCedarAdminSession(cedarConfig, false);
    adminSession.wipeAllData();

    return 0;
  }

}
