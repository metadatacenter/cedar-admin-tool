package org.metadatacenter.admin.task;

import org.metadatacenter.model.RelationLabel;
import org.metadatacenter.server.AdminServiceSession;
import org.metadatacenter.server.neo4j.Neo4jConfig;
import org.metadatacenter.server.neo4j.NodeLabel;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.neo4j.util.Neo4JUtil;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;
import org.neo4j.driver.Session;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    // Neo4j refuses a uniqueness constraint over data that breaks it, and the schema is removed before any rule is
    // created, so a refused rule would leave the graph with fewer rules than it had. Every rule is therefore checked
    // against the data first, and a violation stops the task before anything changes.
    List<String> violations = uniquenessViolations();
    if (!violations.isEmpty()) {
      violations.forEach(out::error);
      out.error((violations.size() == 1 ? "1 value breaks" : violations.size() + " values break")
          + " a uniqueness rule. Nothing was changed. Resolve the nodes listed, then run the task again.");
      return -1;
    }

    AdminServiceSession adminSession = createUnconditionalCedarAdminSession(cedarConfig);

    removeAllConstraintsAndIndices(adminSession);

    // Historical folder nodes predate parentFolderId. The relationship remains authoritative;
    // materialize its parent id before creating the composite sibling-name constraint.
    backfillFolderParentIds(adminSession);

    // Global unique ID constraint
    createUniqueConstraint(adminSession, NodeLabel.RESOURCE, NodeProperty.ID);

    // Global
    createIndex(adminSession, NodeLabel.RESOURCE, NodeProperty.OWNED_BY);
    createIndex(adminSession, NodeLabel.RESOURCE, NodeProperty.RESOURCE_TYPE);
    createIndex(adminSession, NodeLabel.RESOURCE, NodeProperty.NODE_SORT_ORDER);
    createIndex(adminSession, NodeLabel.RESOURCE, NodeProperty.NAME);
    createIndex(adminSession, NodeLabel.RESOURCE, NodeProperty.NAME_LOWER);
    createIndex(adminSession, NodeLabel.RESOURCE, NodeProperty.CREATED_ON_TS);
    createIndex(adminSession, NodeLabel.RESOURCE, NodeProperty.SPECIAL_FOLDER);
    createIndex(adminSession, NodeLabel.RESOURCE, NodeProperty.IS_BASED_ON);
    createIndex(adminSession, NodeLabel.RESOURCE, NodeProperty.IS_USER_HOME);

    // Groups
    createIndex(adminSession, NodeLabel.GROUP, NodeProperty.ID);
    createIndex(adminSession, NodeLabel.GROUP, NodeProperty.NAME);
    // Group names are unique without regard to case. GroupsResource can only look the name up before
    // writing, which two concurrent requests both pass; the constraint is what actually enforces it.
    // It brings its own backing index, so NAME_LOWER needs no separate one.
    createUniqueConstraint(adminSession, NodeLabel.GROUP, NodeProperty.NAME_LOWER);
    createIndex(adminSession, NodeLabel.GROUP, NodeProperty.SPECIAL_GROUP);

    // Users
    createIndex(adminSession, NodeLabel.USER, NodeProperty.ID);
    createIndex(adminSession, NodeLabel.USER, NodeProperty.NAME);
    createIndex(adminSession, NodeLabel.USER, NodeProperty.NAME_LOWER);
    createIndex(adminSession, NodeLabel.USER, NodeProperty.API_KEYS);

    // Categories
    createIndex(adminSession, NodeLabel.CATEGORY, NodeProperty.ID);
    createIndex(adminSession, NodeLabel.CATEGORY, NodeProperty.NAME);
    createUniqueConstraint(adminSession, NodeLabel.CATEGORY,
        List.of(NodeProperty.PARENT_CATEGORY_ID, NodeProperty.NAME_LOWER));

    // Folders
    createIndex(adminSession, NodeLabel.FOLDER, NodeProperty.ID);
    createIndex(adminSession, NodeLabel.FOLDER, NodeProperty.OWNED_BY);
    createIndex(adminSession, NodeLabel.FOLDER, NodeProperty.RESOURCE_TYPE);
    createIndex(adminSession, NodeLabel.FOLDER, NodeProperty.NODE_SORT_ORDER);
    createIndex(adminSession, NodeLabel.FOLDER, NodeProperty.NAME);
    createUniqueConstraint(adminSession, NodeLabel.FOLDER,
        List.of(NodeProperty.PARENT_FOLDER_ID, NodeProperty.NAME_LOWER));
    createIndex(adminSession, NodeLabel.FOLDER, NodeProperty.CREATED_ON_TS);
    createIndex(adminSession, NodeLabel.FOLDER, NodeProperty.LAST_UPDATED_ON_TS);
    createIndex(adminSession, NodeLabel.FOLDER, NodeProperty.IS_ROOT);
    createIndex(adminSession, NodeLabel.FOLDER, NodeProperty.IS_SYSTEM);
    createIndex(adminSession, NodeLabel.FOLDER, NodeProperty.IS_USER_HOME);
    createIndex(adminSession, NodeLabel.FOLDER, NodeProperty.HOME_OF);
    createIndex(adminSession, NodeLabel.FOLDER, NodeProperty.EVERYBODY_PERMISSION);
    createIndex(adminSession, NodeLabel.FOLDER, NodeProperty.SPECIAL_FOLDER);

    // Artifacts
    createIndex(adminSession, NodeLabel.ARTIFACT, NodeProperty.ID);
    createIndex(adminSession, NodeLabel.ARTIFACT, NodeProperty.OWNED_BY);
    createIndex(adminSession, NodeLabel.ARTIFACT, NodeProperty.RESOURCE_TYPE);
    createIndex(adminSession, NodeLabel.ARTIFACT, NodeProperty.NODE_SORT_ORDER);
    createIndex(adminSession, NodeLabel.ARTIFACT, NodeProperty.NAME);
    createIndex(adminSession, NodeLabel.ARTIFACT, NodeProperty.NAME_LOWER);
    createIndex(adminSession, NodeLabel.ARTIFACT, NodeProperty.CREATED_ON_TS);
    createIndex(adminSession, NodeLabel.ARTIFACT, NodeProperty.LAST_UPDATED_ON_TS);
    createIndex(adminSession, NodeLabel.ARTIFACT, NodeProperty.IS_BASED_ON);
    createIndex(adminSession, NodeLabel.ARTIFACT, NodeProperty.DERIVED_FROM);
    createIndex(adminSession, NodeLabel.ARTIFACT, NodeProperty.VERSION);
    createIndex(adminSession, NodeLabel.ARTIFACT, NodeProperty.PREVIOUS_VERSION);
    createIndex(adminSession, NodeLabel.ARTIFACT, NodeProperty.PUBLICATION_STATUS);
    createIndex(adminSession, NodeLabel.ARTIFACT, NodeProperty.IS_LATEST_VERSION);
    createIndex(adminSession, NodeLabel.ARTIFACT, NodeProperty.IS_LATEST_PUBLISHED_VERSION);
    createIndex(adminSession, NodeLabel.ARTIFACT, NodeProperty.IS_LATEST_DRAFT_VERSION);
    createIndex(adminSession, NodeLabel.ARTIFACT, NodeProperty.EVERYBODY_PERMISSION);

    createIndex(adminSession, NodeLabel.FILESYSTEM_RESOURCE, NodeProperty.ID);
    createIndex(adminSession, NodeLabel.FILESYSTEM_RESOURCE, NodeProperty.RESOURCE_TYPE);
    createIndex(adminSession, NodeLabel.FILESYSTEM_RESOURCE, NodeProperty.NODE_SORT_ORDER);
    createIndex(adminSession, NodeLabel.FILESYSTEM_RESOURCE, NodeProperty.NAME);
    createIndex(adminSession, NodeLabel.FILESYSTEM_RESOURCE, NodeProperty.NAME_LOWER);
    createIndex(adminSession, NodeLabel.FILESYSTEM_RESOURCE, NodeProperty.EVERYBODY_PERMISSION);
    createIndex(adminSession, NodeLabel.FILESYSTEM_RESOURCE, NodeProperty.IS_USER_HOME);
    createIndex(adminSession, NodeLabel.FILESYSTEM_RESOURCE, NodeProperty.CREATED_ON_TS);
    createIndex(adminSession, NodeLabel.FILESYSTEM_RESOURCE, NodeProperty.VERSION);
    createIndex(adminSession, NodeLabel.FILESYSTEM_RESOURCE, NodeProperty.PREVIOUS_VERSION);
    createIndex(adminSession, NodeLabel.FILESYSTEM_RESOURCE, NodeProperty.PUBLICATION_STATUS);
    createIndex(adminSession, NodeLabel.FILESYSTEM_RESOURCE, NodeProperty.IS_LATEST_VERSION);
    createIndex(adminSession, NodeLabel.FILESYSTEM_RESOURCE, NodeProperty.IS_LATEST_PUBLISHED_VERSION);
    createIndex(adminSession, NodeLabel.FILESYSTEM_RESOURCE, NodeProperty.IS_LATEST_DRAFT_VERSION);

    createIndex(adminSession, NodeLabel.FIELD, NodeProperty.ID);

    createIndex(adminSession, NodeLabel.ELEMENT, NodeProperty.ID);

    createIndex(adminSession, NodeLabel.TEMPLATE, NodeProperty.ID);

    createIndex(adminSession, NodeLabel.INSTANCE, NodeProperty.ID);

    // Removing every constraint above also removed the ones the servers create at startup, and a
    // running server does not create them again until it restarts.
    adminSession.ensureServerSchema();

    return 0;
  }

  /**
   * Each value that more than one node holds under a uniqueness rule the task creates, with the identifiers of those
   * nodes. A folder's parent is read as the backfill below will record it: from its CONTAINS arc, or from the node
   * where it has none.
   */
  private List<String> uniquenessViolations() {
    String id = property(NodeProperty.ID);
    String nameLower = property(NodeProperty.NAME_LOWER);
    String parentCategoryId = property(NodeProperty.PARENT_CATEGORY_ID);
    String parentFolderId = property(NodeProperty.PARENT_FOLDER_ID);
    String folder = NodeLabel.FOLDER.getSimpleLabel();

    Map<String, String> rules = new LinkedHashMap<>();
    rules.put(NodeLabel.RESOURCE.getSimpleLabel() + "." + NodeProperty.ID.getValue(),
        "MATCH (n:" + NodeLabel.RESOURCE.getSimpleLabel() + ") WHERE n." + id + " IS NOT NULL"
            + " WITH n." + id + " AS value, collect(n." + id + ") AS nodes WHERE size(nodes) > 1"
            + " RETURN toString(value) AS value, nodes");
    rules.put(NodeLabel.GROUP.getSimpleLabel() + "." + NodeProperty.NAME_LOWER.getValue(),
        "MATCH (n:" + NodeLabel.GROUP.getSimpleLabel() + ") WHERE n." + nameLower + " IS NOT NULL"
            + " WITH n." + nameLower + " AS value, collect(n." + id + ") AS nodes WHERE size(nodes) > 1"
            + " RETURN toString(value) AS value, nodes");
    rules.put(NodeLabel.CATEGORY.getSimpleLabel() + ".[" + NodeProperty.PARENT_CATEGORY_ID.getValue() + ", "
            + NodeProperty.NAME_LOWER.getValue() + "]",
        "MATCH (n:" + NodeLabel.CATEGORY.getSimpleLabel() + ") WHERE n." + parentCategoryId + " IS NOT NULL"
            + " AND n." + nameLower + " IS NOT NULL"
            + " WITH n." + parentCategoryId + " AS parent, n." + nameLower + " AS name, collect(n." + id + ") AS nodes"
            + " WHERE size(nodes) > 1 RETURN toString(parent) + ' / ' + toString(name) AS value, nodes");
    rules.put(folder + ".[" + NodeProperty.PARENT_FOLDER_ID.getValue() + ", " + NodeProperty.NAME_LOWER.getValue() + "]",
        "MATCH (n:" + folder + ") WHERE n." + nameLower + " IS NOT NULL"
            + " OPTIONAL MATCH (p:" + folder + ")-[:" + RelationLabel.CONTAINS.getValue() + "]->(n)"
            + " WITH n, coalesce(p." + id + ", n." + parentFolderId + ") AS parent WHERE parent IS NOT NULL"
            + " WITH parent, n." + nameLower + " AS name, collect(n." + id + ") AS nodes WHERE size(nodes) > 1"
            + " RETURN toString(parent) + ' / ' + toString(name) AS value, nodes");

    List<String> violations = new ArrayList<>();
    Neo4jConfig neo4j = Neo4jConfig.fromCedarConfig(cedarConfig);
    try (Driver driver = GraphDatabase.driver(neo4j.getUri(),
        AuthTokens.basic(neo4j.getUserName(), neo4j.getUserPassword()));
         Session session = driver.session()) {
      for (Map.Entry<String, String> rule : rules.entrySet()) {
        out.info("Checking the data against the unique constraint on: " + rule.getKey());
        for (Record row : session.run(rule.getValue()).list()) {
          List<Object> nodes = row.get("nodes").asList();
          violations.add("The unique constraint on " + rule.getKey() + " cannot be created: " + nodes.size()
              + " nodes hold '" + row.get("value").asString() + "': " + nodes);
        }
      }
    }
    return violations;
  }

  private static String property(NodeProperty property) {
    return Neo4JUtil.escapePropertyName(property.getValue());
  }

  private void removeAllConstraintsAndIndices(AdminServiceSession adminSession) {
    out.info("Removing all constraints and indices");
    adminSession.removeAllConstraintsAndIndices();
  }

  private void createIndex(AdminServiceSession adminSession, NodeLabel nodeLabel, NodeProperty property) {
    out.info("Creating index on: " + nodeLabel.getSimpleLabel() + "." + property.getValue());
    adminSession.createIndex(nodeLabel, property);
  }

  private void createUniqueConstraint(AdminServiceSession adminSession, NodeLabel nodeLabel, NodeProperty property) {
    out.info("Creating unique constraint on: " + nodeLabel.getSimpleLabel() + "." + property.getValue());
    adminSession.createUniqueConstraint(nodeLabel, property);
  }

  private void createUniqueConstraint(AdminServiceSession adminSession, NodeLabel nodeLabel,
                                      List<NodeProperty> properties) {
    out.info("Creating unique constraint on: " + nodeLabel.getSimpleLabel() + "." + properties);
    adminSession.createUniqueConstraint(nodeLabel, properties);
  }

  private void backfillFolderParentIds(AdminServiceSession adminSession) {
    out.info("Backfilling folder parent identifiers");
    adminSession.backfillFolderParentIds();
  }

}
