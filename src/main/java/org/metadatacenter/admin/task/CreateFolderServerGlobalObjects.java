package org.metadatacenter.admin.task;

import org.metadatacenter.server.neo4j.Neo4JUserSession;

public class CreateFolderServerGlobalObjects extends AbstractNeo4JAccessTask {

  public CreateFolderServerGlobalObjects() {
    description.add("Creates global folders in the graph database: /, /Users, /Lost+Found");
    description.add("Creates home folder for cedar-admin user");
    description.add("Updates cedar-admin user profile in MongoDB, sets homeFolderId");
  }

  @Override
  public void init() {

  }

  @Override
  public int execute() {
    Neo4JUserSession adminNeo4JSession = buildCedarAdminNeo4JSession(cedarConfig, false);
    adminNeo4JSession.ensureGlobalObjectsExists();
    Neo4JUserSession adminNeo4JSessionWithHome = buildCedarAdminNeo4JSession(cedarConfig, true);
    return 0;
  }
}