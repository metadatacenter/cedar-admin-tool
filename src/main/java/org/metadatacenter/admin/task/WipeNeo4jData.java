package org.metadatacenter.admin.task;

import org.metadatacenter.server.neo4j.Neo4JUserSession;

public class WipeNeo4jData extends AbstractNeo4JAccessTask {

  public WipeNeo4jData() {
    description.add("Deletes all CEDAR nodes from the neo4j server.");
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

    Neo4JUserSession adminNeo4JSession = buildCedarAdminNeo4JSession(cedarConfig, false);
    adminNeo4JSession.wipeAllData();

    return 0;
  }

}
