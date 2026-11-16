package org.metadatacenter.admin.task;

import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.server.neo4j.Neo4jConfig;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.neo4j.util.Neo4JUtil;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;

import java.util.Map;

/**
 * Counts read from the graph with Cypher. A service that finds one node or arc cannot show a second one, so a test that
 * claims a task creates something once reads the count here.
 */
final class GraphCounts {

  private GraphCounts() {
  }

  /** The user's membership arcs to the Everybody group. */
  static long everybodyMemberships(CedarConfig cedarConfig, String userId) {
    return count(cedarConfig, "MATCH (:User {" + property(NodeProperty.ID) + ": $id})-[m:MEMBEROF]->"
        + "(:Group {" + property(NodeProperty.SPECIAL_GROUP) + ": 'EVERYBODY'}) RETURN count(m)", Map.of("id", userId));
  }

  /** The home folders recorded as the user's. */
  static long homeFolders(CedarConfig cedarConfig, String userId) {
    return count(cedarConfig, "MATCH (f:Folder {" + property(NodeProperty.IS_USER_HOME) + ": true, "
        + property(NodeProperty.HOME_OF) + ": $id}) RETURN count(f)", Map.of("id", userId));
  }

  static long count(CedarConfig cedarConfig, String cypher, Map<String, Object> parameters) {
    Neo4jConfig neo4j = Neo4jConfig.fromCedarConfig(cedarConfig);
    try (Driver driver = GraphDatabase.driver(neo4j.getUri(), AuthTokens.basic(neo4j.getUserName(),
        neo4j.getUserPassword())); Session session = driver.session()) {
      return session.run(cypher, parameters).single().get(0).asLong();
    }
  }

  private static String property(NodeProperty property) {
    return Neo4JUtil.escapePropertyName(property.getValue());
  }
}
