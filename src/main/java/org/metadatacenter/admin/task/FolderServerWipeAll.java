package org.metadatacenter.admin.task;

import org.metadatacenter.server.AdminServiceSession;

public class FolderServerWipeAll extends AbstractNeo4JAccessTask {

  public FolderServerWipeAll() {
    description.add("Deletes all 'XCEDAR' nodes from the Neo4j server.");
    description.add("Needs second parameter '" + CONFIRM + "' to run.");
  }

  @Override
  public void init() {
  }

  @Override
  public int execute() {
    if (arguments.size() != 2 || !CONFIRM.equals(arguments.get(1))) {
      out.warn("You need to confirm your intent by providing '" + CONFIRM + "' as the second argument!");
      return -1;
    }

    AdminServiceSession adminSession = createCedarAdminSession(cedarConfig, false);
    adminSession.wipeAllData();

    return 0;
  }

}
