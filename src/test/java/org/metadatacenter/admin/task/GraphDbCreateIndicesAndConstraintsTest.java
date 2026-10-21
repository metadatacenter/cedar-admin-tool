package org.metadatacenter.admin.task;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.metadatacenter.admin.util.AdminOutput;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.AdminServiceSession;
import org.metadatacenter.server.UserServiceSession;
import org.metadatacenter.server.neo4j.NodeLabel;
import org.metadatacenter.server.neo4j.Neo4jConfig;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.neo4j.util.Neo4JUtil;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.util.test.EmbeddedCedarNeo4j;
import org.metadatacenter.util.test.TestAuthUtil;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The task that gives the workspace graph its uniqueness rules and lookup indexes. Without the
 * constraints, two concurrent writes can create a duplicate identifier, a second group with the same
 * name or two siblings with one name, and the server has no other way to stop them. Without the
 * indexes every lookup scans the graph.
 * <p>
 * The task first removes the existing schema with APOC's {@code apoc.schema.assert}, which the
 * embedded Neo4j does not provide. The test therefore hands the task an admin session that removes
 * the schema with plain Cypher instead, the same way APOC does, and passes every other call
 * through. What the test does not reach is that one APOC call.
 */
@Tag("integration")
class GraphDbCreateIndicesAndConstraintsTest {

  private static CedarConfig cedarConfig;
  private static Driver driver;

  @BeforeAll
  static void startTheGraph() {
    EmbeddedCedarNeo4j.startRedirectAndSeed(SystemComponent.ADMIN_TOOL);
    cedarConfig = CedarConfig.getInstance(CedarEnvironmentVariableProvider.getFor(SystemComponent.ADMIN_TOOL));
    Neo4jConfig neo4j = Neo4jConfig.fromCedarConfig(cedarConfig);
    driver = GraphDatabase.driver(neo4j.getUri(), AuthTokens.basic(neo4j.getUserName(), neo4j.getUserPassword()));
  }

  @AfterAll
  static void closeTheDriver() {
    driver.close();
  }

  @Test
  void theTaskCreatesTheGraphsUniquenessRulesAndIndexes() throws Exception {
    String parentFolderId = property(NodeProperty.PARENT_FOLDER_ID);
    String id = property(NodeProperty.ID);
    CedarUser user = TestAuthUtil.getTestUser1(cedarConfig);
    String homeId = CedarDataServices.getInstance()
        .getFolderServiceSession(CedarRequestContextFactory.fromUser(user)).findHomeFolderOf().getId();
    // A folder written before parentFolderId existed. The sibling-name constraint keys on it, so
    // the task has to fill it in from the containing relationship before creating the constraint.
    run("MATCH (f {`" + id + "`: $id}) REMOVE f.`" + parentFolderId + "`", Map.of("id", homeId));

    assertEquals(0, task().execute());

    try (Session session = driver.session()) {
      var parent = session.run("MATCH (p)-[:CONTAINS]->(f {`" + id + "`: $id}) RETURN p.`" + id + "` AS parent, "
          + "f.`" + parentFolderId + "` AS recorded", Map.of("id", homeId)).single();
      assertEquals(parent.get("parent").asString(), parent.get("recorded").asString(),
          "the task should restore the folder's parent id from the CONTAINS relationship");
    }

    Set<String> constraints = uniquenessConstraints();
    for (String expected : List.of(
        constraint(NodeLabel.RESOURCE, NodeProperty.ID),
        constraint(NodeLabel.GROUP, NodeProperty.NAME_LOWER),
        constraint(NodeLabel.CATEGORY, NodeProperty.PARENT_CATEGORY_ID, NodeProperty.NAME_LOWER),
        constraint(NodeLabel.FOLDER, NodeProperty.PARENT_FOLDER_ID, NodeProperty.NAME_LOWER))) {
      assertTrue(constraints.contains(expected), "missing uniqueness rule " + expected + " in " + constraints);
    }
    // The servers' own constraints, which removing the old schema took away with everything else.
    for (String expected : List.of("CedarVersionLock[id]", "CedarVersionProjection[resourceId]",
        "CedarArtifactCreateCleanup[jobId]")) {
      assertTrue(constraints.contains(expected), "the task should restore " + expected + ": " + constraints);
    }

    Set<String> indexes = indexNames();
    for (NodeLabel label : List.of(NodeLabel.GROUP, NodeLabel.USER, NodeLabel.CATEGORY, NodeLabel.FOLDER,
        NodeLabel.ARTIFACT, NodeLabel.FILESYSTEM_RESOURCE, NodeLabel.FIELD, NodeLabel.ELEMENT,
        NodeLabel.TEMPLATE, NodeLabel.INSTANCE)) {
      String expected = label.getSimpleLabel() + "_" + id;
      assertTrue(indexes.contains(expected), "missing identifier index " + expected + " in " + indexes);
    }

    Set<String> schemaAfterFirstRun = schema();
    assertEquals(0, task().execute());
    assertEquals(schemaAfterFirstRun, schema(), "running the task again should leave the same schema");
  }

  /** The task, with the Keycloak lookup it uses to build its admin replaced by the seeded admin. */
  private static GraphDbCreateIndicesAndConstraints task() {
    GraphDbCreateIndicesAndConstraints task = new GraphDbCreateIndicesAndConstraints() {
      @Override
      public void init() {
      }

      @Override
      protected AdminServiceSession createUnconditionalCedarAdminSession(CedarConfig cedarConfig) {
        try {
          return new SchemaDroppingAdminSession(CedarDataServices.getInstance().getAdminServiceSession(
              CedarRequestContextFactory.fromUser(TestAuthUtil.getAdminUser(cedarConfig))));
        } catch (Exception e) {
          throw new IllegalStateException(e);
        }
      }
    };
    task.injectConfig(cedarConfig);
    task.setOutput(new AdminOutput());
    task.init();
    return task;
  }

  /** Removes the schema as {@code apoc.schema.assert({}, {})} does, and delegates everything else. */
  private record SchemaDroppingAdminSession(AdminServiceSession delegate) implements AdminServiceSession {

    @Override
    public boolean removeAllConstraintsAndIndices() {
      try (Session session = driver.session()) {
        for (var record : session.run("SHOW CONSTRAINTS YIELD name").list()) {
          session.run("DROP CONSTRAINT `" + record.get("name").asString() + "`").consume();
        }
        for (var record : session.run("SHOW INDEXES YIELD name, type WHERE type <> 'LOOKUP'").list()) {
          session.run("DROP INDEX `" + record.get("name").asString() + "`").consume();
        }
      }
      return true;
    }

    @Override
    public void ensureGlobalObjectsExists() {
      delegate.ensureGlobalObjectsExists();
    }

    @Override
    public void ensureCaDSRObjectsExists(CedarUser caDSRAdmin, UserServiceSession userSession) {
      delegate.ensureCaDSRObjectsExists(caDSRAdmin, userSession);
    }

    @Override
    public boolean wipeAllData() {
      return delegate.wipeAllData();
    }

    @Override
    public boolean wipeAllCategories() {
      return delegate.wipeAllCategories();
    }

    @Override
    public boolean createUniqueConstraint(NodeLabel nodeLabel, NodeProperty property) {
      return delegate.createUniqueConstraint(nodeLabel, property);
    }

    @Override
    public boolean createUniqueConstraint(NodeLabel nodeLabel, List<NodeProperty> properties) {
      return delegate.createUniqueConstraint(nodeLabel, properties);
    }

    @Override
    public boolean backfillFolderParentIds() {
      return delegate.backfillFolderParentIds();
    }

    @Override
    public boolean createIndex(NodeLabel nodeLabel, NodeProperty property) {
      return delegate.createIndex(nodeLabel, property);
    }

    @Override
    public void ensureServerSchema() {
      delegate.ensureServerSchema();
    }
  }

  private static String property(NodeProperty property) {
    return Neo4JUtil.escapePropertyName(property.getValue());
  }

  private static String constraint(NodeLabel label, NodeProperty... properties) {
    List<String> names = java.util.Arrays.stream(properties).map(GraphDbCreateIndicesAndConstraintsTest::property).toList();
    return label.getSimpleLabel() + names;
  }

  private static Set<String> uniquenessConstraints() {
    Set<String> found = new TreeSet<>();
    try (Session session = driver.session()) {
      for (var record : session.run("SHOW CONSTRAINTS YIELD labelsOrTypes, properties, type").list()) {
        if (record.get("type").asString().contains("UNIQUENESS")) {
          found.add(record.get("labelsOrTypes").asList().get(0) + record.get("properties").asList(v -> v.asString()).toString());
        }
      }
    }
    return found;
  }

  private static Set<String> indexNames() {
    Set<String> found = new TreeSet<>();
    try (Session session = driver.session()) {
      session.run("SHOW INDEXES YIELD name").list().forEach(record -> found.add(record.get("name").asString()));
    }
    return found;
  }

  private static Set<String> schema() {
    Set<String> schema = new TreeSet<>(uniquenessConstraints());
    schema.addAll(indexNames());
    return schema;
  }

  private static void run(String cypher, Map<String, Object> parameters) {
    try (Session session = driver.session()) {
      session.run(cypher, parameters).consume();
    }
  }
}
