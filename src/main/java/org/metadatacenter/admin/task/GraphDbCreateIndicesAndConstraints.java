package org.metadatacenter.admin.task;

import org.metadatacenter.server.AdminServiceSession;
import org.metadatacenter.server.neo4j.NodeLabel;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;

public class GraphDbCreateIndicesAndConstraints extends AbstractNeo4JAccessTask {

  public GraphDbCreateIndicesAndConstraints() {
    description.add("Creates indices and constraints.");
  }

  @Override
  public void init() {
    super.init();
  }

  @Override
  public int execute() {
    AdminServiceSession adminSession = createUnconditionalCedarAdminSession(cedarConfig);

    // Global unique ID constraint
    createUniqueConstraint(adminSession, NodeLabel.RESOURCE, NodeProperty.ID);

    // Global
    createIndex(adminSession, NodeLabel.RESOURCE, NodeProperty.OWNED_BY);
    createIndex(adminSession, NodeLabel.RESOURCE, NodeProperty.RESOURCE_TYPE);
    createIndex(adminSession, NodeLabel.RESOURCE, NodeProperty.NODE_SORT_ORDER);
    createIndex(adminSession, NodeLabel.RESOURCE, NodeProperty.NAME);

    // Groups
    createIndex(adminSession, NodeLabel.GROUP, NodeProperty.ID);
    createIndex(adminSession, NodeLabel.GROUP, NodeProperty.NAME);
    createIndex(adminSession, NodeLabel.GROUP, NodeProperty.SPECIAL_GROUP);

    // Users
    createIndex(adminSession, NodeLabel.USER, NodeProperty.ID);
    createIndex(adminSession, NodeLabel.USER, NodeProperty.API_KEYS);

    // Categories
    createIndex(adminSession, NodeLabel.CATEGORY, NodeProperty.ID);
    createIndex(adminSession, NodeLabel.CATEGORY, NodeProperty.NAME);
    createIndex(adminSession, NodeLabel.CATEGORY, NodeProperty.PARENT_CATEGORY_ID);

    // Filesystem resources

    // FSNode

    // Folders
    createIndex(adminSession, NodeLabel.FOLDER, NodeProperty.OWNED_BY);
    createIndex(adminSession, NodeLabel.FOLDER, NodeProperty.RESOURCE_TYPE);
    createIndex(adminSession, NodeLabel.FOLDER, NodeProperty.NODE_SORT_ORDER);
    createIndex(adminSession, NodeLabel.FOLDER, NodeProperty.NAME);
    createIndex(adminSession, NodeLabel.FOLDER, NodeProperty.CREATED_ON_TS);
    createIndex(adminSession, NodeLabel.FOLDER, NodeProperty.LAST_UPDATED_ON_TS);
    createIndex(adminSession, NodeLabel.FOLDER, NodeProperty.IS_ROOT);
    createIndex(adminSession, NodeLabel.FOLDER, NodeProperty.IS_SYSTEM);
    createIndex(adminSession, NodeLabel.FOLDER, NodeProperty.IS_USER_HOME);
    createIndex(adminSession, NodeLabel.FOLDER, NodeProperty.HOME_OF);
    createIndex(adminSession, NodeLabel.FOLDER, NodeProperty.EVERYBODY_PERMISSION);

    // Artifacts
    createIndex(adminSession, NodeLabel.ARTIFACT, NodeProperty.OWNED_BY);
    createIndex(adminSession, NodeLabel.ARTIFACT, NodeProperty.RESOURCE_TYPE);
    createIndex(adminSession, NodeLabel.ARTIFACT, NodeProperty.NODE_SORT_ORDER);
    createIndex(adminSession, NodeLabel.ARTIFACT, NodeProperty.NAME);
    createIndex(adminSession, NodeLabel.ARTIFACT, NodeProperty.CREATED_ON_TS);
    createIndex(adminSession, NodeLabel.ARTIFACT, NodeProperty.LAST_UPDATED_ON_TS);
    createIndex(adminSession, NodeLabel.ARTIFACT, NodeProperty.IS_BASED_ON);
    createIndex(adminSession, NodeLabel.ARTIFACT, NodeProperty.DERIVED_FROM);
    createIndex(adminSession, NodeLabel.ARTIFACT, NodeProperty.VERSION);
    createIndex(adminSession, NodeLabel.ARTIFACT, NodeProperty.PUBLICATION_STATUS);
    createIndex(adminSession, NodeLabel.ARTIFACT, NodeProperty.IS_LATEST_VERSION);
    createIndex(adminSession, NodeLabel.ARTIFACT, NodeProperty.EVERYBODY_PERMISSION);

    // Groups
    createIndex(adminSession, NodeLabel.GROUP, NodeProperty.NAME);
    createIndex(adminSession, NodeLabel.GROUP, NodeProperty.SPECIAL_GROUP);

    // Users
    createIndex(adminSession, NodeLabel.USER, NodeProperty.API_KEYS);

    // Resources
    createIndex(adminSession, NodeLabel.RESOURCE, NodeProperty.OWNED_BY);
    createIndex(adminSession, NodeLabel.RESOURCE, NodeProperty.RESOURCE_TYPE);
    createIndex(adminSession, NodeLabel.RESOURCE, NodeProperty.NODE_SORT_ORDER);
    createIndex(adminSession, NodeLabel.RESOURCE, NodeProperty.NAME);
    createIndex(adminSession, NodeLabel.RESOURCE, NodeProperty.CREATED_ON_TS);
    createIndex(adminSession, NodeLabel.RESOURCE, NodeProperty.LAST_UPDATED_ON_TS);
    createIndex(adminSession, NodeLabel.RESOURCE, NodeProperty.IS_BASED_ON);
    createIndex(adminSession, NodeLabel.RESOURCE, NodeProperty.DERIVED_FROM);
    createIndex(adminSession, NodeLabel.RESOURCE, NodeProperty.VERSION);
    createIndex(adminSession, NodeLabel.RESOURCE, NodeProperty.PUBLICATION_STATUS);
    createIndex(adminSession, NodeLabel.RESOURCE, NodeProperty.IS_LATEST_VERSION);
    createIndex(adminSession, NodeLabel.RESOURCE, NodeProperty.EVERYBODY_PERMISSION);

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
