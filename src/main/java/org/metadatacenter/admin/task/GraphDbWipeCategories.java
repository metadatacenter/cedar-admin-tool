package org.metadatacenter.admin.task;

import org.metadatacenter.server.AdminServiceSession;

public class GraphDbWipeCategories extends AbstractNeo4JAccessTask {

  public GraphDbWipeCategories() {
    description.add("Deletes all 'CEDAR' categories from the Neo4j server.");
  }

  @Override
  public void init() {
    super.init();
  }

  @Override
  public int execute() {
    if (!getConfirmInput("Deleting all 'CEDAR' categories from the Neo4j server...")) {
      return -1;
    }

    AdminServiceSession adminSession = createUnconditionalCedarAdminSession(cedarConfig);
    adminSession.wipeAllCategories();

    return 0;
  }

}
