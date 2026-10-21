package org.metadatacenter.admin.task;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.Document;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.metadatacenter.admin.util.AdminOutput;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.MongoConfig;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.util.test.EmbeddedCedarMongo;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * artifactServer-wipeAll deletes every document from the artifact store's four collections, after the operator types
 * the confirmation. Anything else the operator types leaves the store as it was.
 */
@Tag("integration")
class ArtifactServerWipeAllTest {

  private static final List<CedarResourceType> TYPES = List.of(CedarResourceType.FIELD, CedarResourceType.ELEMENT,
      CedarResourceType.TEMPLATE, CedarResourceType.INSTANCE);

  private static CedarConfig cedarConfig;
  private static MongoClient client;

  @BeforeAll
  static void startTheStore() {
    EmbeddedCedarMongo.startAndRedirectEnvironment();
    cedarConfig = CedarConfig.getInstance(CedarEnvironmentVariableProvider.getFor(SystemComponent.ADMIN_TOOL));
    CedarDataServices.initializeMongoClientFactoryForDocuments(cedarConfig.getArtifactServerConfig().getMongoConnection());
    client = CedarDataServices.getInstance().getMongoClientFactoryForDocuments().getClient();
  }

  @Test
  void aDeclinedWipeLeavesEveryDocument() {
    String marker = addOneDocumentToEachCollection();

    assertEquals(-1, wipe("no"));

    for (CedarResourceType type : TYPES) {
      assertEquals(1, collection(type).countDocuments(new Document("marker", marker)), type.getValue());
    }
  }

  @Test
  void aConfirmedWipeEmptiesEveryCollection() {
    addOneDocumentToEachCollection();

    assertEquals(0, wipe(AbstractCedarAdminTask.CONFIRM));

    for (CedarResourceType type : TYPES) {
      assertEquals(0, collection(type).countDocuments(), type.getValue());
    }
  }

  private static String addOneDocumentToEachCollection() {
    String marker = UUID.randomUUID().toString();
    for (CedarResourceType type : TYPES) {
      collection(type).insertOne(new Document("@id", "https://repo.metadatacenter.orgx/" + type.getValue() + "/" + marker)
          .append("marker", marker));
      assertTrue(collection(type).countDocuments() > 0);
    }
    return marker;
  }

  private static MongoCollection<Document> collection(CedarResourceType type) {
    MongoConfig store = cedarConfig.getArtifactServerConfig();
    return client.getDatabase(store.getDatabaseName()).getCollection(store.getMongoCollectionName(type));
  }

  private static int wipe(String typed) {
    ArtifactServerWipeAll task = new ArtifactServerWipeAll();
    task.setArguments(new String[]{"artifactServer-wipeAll"});
    task.injectConfig(cedarConfig);
    task.setOutput(new AdminOutput());
    task.init();
    InputStream original = System.in;
    System.setIn(new ByteArrayInputStream((typed + "\n").getBytes(StandardCharsets.UTF_8)));
    try {
      return task.execute();
    } finally {
      System.setIn(original);
    }
  }
}
