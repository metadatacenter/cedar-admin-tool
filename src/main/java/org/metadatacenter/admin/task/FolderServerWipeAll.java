package org.metadatacenter.admin.task;

import org.metadatacenter.exception.security.CedarAccessException;
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

    AdminServiceSession adminSession = null;
    try {
      adminSession = createCedarAdminSession(cedarConfig, false);
    } catch (CedarAccessException e) {
      e.printStackTrace();
      return -2;
    }
    adminSession.wipeAllData();

    return 0;
  }

}
