package org.metadatacenter.admin.task;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.server.neo4j.Neo4JUserSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WipeNeo4jData extends AbstractNeo4JWritingTask {

  private CedarConfig cedarConfig;
  private Logger logger = LoggerFactory.getLogger(WipeNeo4jData.class);
  public static final String CONFIRM = "confirm";

  public WipeNeo4jData() {
    description.add("Deletes all CEDAR nodes from the neo4j server.");
    description.add("Needs second parameter '" + CONFIRM + "' to run.");
  }

  @Override
  public void init(CedarConfig config) {
    this.cedarConfig = config;
  }

  @Override
  public int execute() {
    if (arguments.size() != 2 || !CONFIRM.equals(arguments.get(1))) {
      System.out.println("You need to confirm your intent by providing '" + CONFIRM + "' as the second argument!");
      return -1;
    }

    Neo4JUserSession adminNeo4JSession = buildCedarAdminNeo4JSession(cedarConfig, false);
    adminNeo4JSession.wipeAllData();

    return 0;
  }

}
