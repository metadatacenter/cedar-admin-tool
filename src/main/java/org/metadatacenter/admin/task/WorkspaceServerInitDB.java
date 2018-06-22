package org.metadatacenter.admin.task;

import org.metadatacenter.exception.security.CedarAccessException;
import org.metadatacenter.server.AdminServiceSession;
import org.metadatacenter.server.neo4j.NodeLabel;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;

public class WorkspaceServerInitDB extends AbstractNeo4JAccessTask {

  public WorkspaceServerInitDB() {
    description.add("Initializes Workspace Server Neo4j database.");
    description.add("Creates indices and constraints.");
  }

  @Override
  public void init() {
  }

  @Override
  public int execute() {
    AdminServiceSession adminSession;
    try {
      adminSession = createCedarAdminSession(cedarConfig);
    } catch (CedarAccessException e) {
      e.printStackTrace();
      return -2;
    }
    // Global
    createUniqueConstraint(adminSession, NodeLabel.SCOPE, NodeProperty.ID);

    createIndex(adminSession, NodeLabel.SCOPE, NodeProperty.OWNED_BY);
    createIndex(adminSession, NodeLabel.SCOPE, NodeProperty.NODE_TYPE);
    createIndex(adminSession, NodeLabel.SCOPE, NodeProperty.NODE_SORT_ORDER);
    createIndex(adminSession, NodeLabel.SCOPE, NodeProperty.NAME);

    // Folders
    createUniqueConstraint(adminSession, NodeLabel.FOLDER, NodeProperty.ID);

    createIndex(adminSession, NodeLabel.FOLDER, NodeProperty.OWNED_BY);
    createIndex(adminSession, NodeLabel.FOLDER, NodeProperty.NODE_TYPE);
    createIndex(adminSession, NodeLabel.FOLDER, NodeProperty.NODE_SORT_ORDER);
    createIndex(adminSession, NodeLabel.FOLDER, NodeProperty.NAME);
    createIndex(adminSession, NodeLabel.FOLDER, NodeProperty.CREATED_ON_TS);
    createIndex(adminSession, NodeLabel.FOLDER, NodeProperty.LAST_UPDATED_ON_TS);
    createIndex(adminSession, NodeLabel.FOLDER, NodeProperty.IS_ROOT);
    createIndex(adminSession, NodeLabel.FOLDER, NodeProperty.IS_SYSTEM);
    createIndex(adminSession, NodeLabel.FOLDER, NodeProperty.IS_USER_HOME);
    createIndex(adminSession, NodeLabel.FOLDER, NodeProperty.HOME_OF);

    // Groups
    createUniqueConstraint(adminSession, NodeLabel.GROUP, NodeProperty.ID);

    createIndex(adminSession, NodeLabel.GROUP, NodeProperty.NAME);
    createIndex(adminSession, NodeLabel.GROUP, NodeProperty.SPECIAL_GROUP);

    // Users
    createUniqueConstraint(adminSession, NodeLabel.USER, NodeProperty.ID);

    // Resources
    createUniqueConstraint(adminSession, NodeLabel.RESOURCE, NodeProperty.ID);

    createIndex(adminSession, NodeLabel.RESOURCE, NodeProperty.OWNED_BY);
    createIndex(adminSession, NodeLabel.RESOURCE, NodeProperty.NODE_TYPE);
    createIndex(adminSession, NodeLabel.RESOURCE, NodeProperty.NODE_SORT_ORDER);
    createIndex(adminSession, NodeLabel.RESOURCE, NodeProperty.NAME);
    createIndex(adminSession, NodeLabel.RESOURCE, NodeProperty.CREATED_ON_TS);
    createIndex(adminSession, NodeLabel.RESOURCE, NodeProperty.LAST_UPDATED_ON_TS);
    createIndex(adminSession, NodeLabel.RESOURCE, NodeProperty.IS_BASED_ON);
    createIndex(adminSession, NodeLabel.RESOURCE, NodeProperty.DERIVED_FROM);
    createIndex(adminSession, NodeLabel.RESOURCE, NodeProperty.VERSION);
    createIndex(adminSession, NodeLabel.RESOURCE, NodeProperty.PUBLICATION_STATUS);
    createIndex(adminSession, NodeLabel.RESOURCE, NodeProperty.IS_LATEST_VERSION);


    createUniqueConstraint(adminSession, NodeLabel.FIELD, NodeProperty.ID);

    createUniqueConstraint(adminSession, NodeLabel.ELEMENT, NodeProperty.ID);

    createUniqueConstraint(adminSession, NodeLabel.TEMPLATE, NodeProperty.ID);

    createUniqueConstraint(adminSession, NodeLabel.INSTANCE, NodeProperty.ID);

    return 0;
  }

  private void createIndex(AdminServiceSession adminSession, NodeLabel nodeLabel, NodeProperty property) {
    out.info("Creating index on: " + nodeLabel.getSimpleLabel() + "." + property.getValue());
    adminSession.createIndex(nodeLabel, property);
  }

  private void createUniqueConstraint(AdminServiceSession adminSession, NodeLabel nodeLabel, NodeProperty property) {
    out.info("Creating unique constraint on: " + nodeLabel.getSimpleLabel() + "." + property.getValue());
    adminSession.createUniqueConstraint(nodeLabel, property);
  }

}
