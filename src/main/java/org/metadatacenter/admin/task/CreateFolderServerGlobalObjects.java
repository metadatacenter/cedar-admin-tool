package org.metadatacenter.admin.task;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.server.neo4j.Neo4JUserSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateFolderServerGlobalObjects extends AbstractNeo4JAccessTask {

  private CedarConfig cedarConfig;
  private Logger logger = LoggerFactory.getLogger(CreateFolderServerGlobalObjects.class);

  public CreateFolderServerGlobalObjects() {
    description.add("Creates global folders in the graph database: /, /Users, /Lost+Found");
    description.add("Creates home folder for cedar-admin user");
    description.add("Updates cedar-admin user profile in MongoDB, sets homeFolderId");
  }

  @Override
  public void init(CedarConfig cedarConfig) {
    this.cedarConfig = cedarConfig;
  }

  @Override
  public int execute() {
    Neo4JUserSession adminNeo4JSession = buildCedarAdminNeo4JSession(cedarConfig, false);
    adminNeo4JSession.ensureGlobalObjectsExists();
    Neo4JUserSession adminNeo4JSessionWithHome = buildCedarAdminNeo4JSession(cedarConfig, true);
    return 0;
  }
}