package org.metadatacenter.admin.task;

import com.mongodb.MongoWriteException;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.metadatacenter.admin.util.AdminOutput;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.util.test.EmbeddedCedarMongo;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The native path that provisions the artifact store: a unique index on {@code @id} in each of the
 * four artifact collections. The artifact server neither creates that index nor notices its
 * absence, and without it two concurrent creates of one identifier both succeed and every lookup is
 * a full scan. Production ran for years without it, so the task that creates it is tested against a
 * real MongoDB rather than trusted.
 */
@Tag("integration")
class ArtifactServerInitDBTest {

  static {
    EmbeddedCedarMongo.startAndRedirectEnvironment();
  }

  @Test
  void theTaskCreatesAUniqueIdIndexOnEveryArtifactCollection() {
    CedarConfig cedarConfig = CedarConfig.getInstance(CedarEnvironmentVariableProvider.getFor(SystemComponent.ADMIN_TOOL));
    CedarDataServices.initializeMongoClientFactoryForDocuments(cedarConfig.getArtifactServerConfig().getMongoConnection());
    ArtifactServerInitDB task = new ArtifactServerInitDB();
    task.injectConfig(cedarConfig);
    task.setOutput(new AdminOutput());
    task.init();
    MongoDatabase database = CedarDataServices.getInstance().getMongoClientFactoryForDocuments().getClient()
        .getDatabase(task.mongoDatabaseName);
    List<String> collections = List.of(task.templateFieldsCollectionName, task.templateElementsCollectionName,
        task.templatesCollectionName, task.templateInstancesCollectionName);
    assertEquals(EmbeddedCedarMongo.ARTIFACT_COLLECTIONS, collections,
        "the task should provision the collections the artifact server uses");

    // The embedded store arrives provisioned, as a real one should be, so the indexes are removed
    // first and the task has to create them.
    collections.forEach(name -> database.getCollection(name).dropIndexes());

    assertEquals(0, task.execute());
    assertEquals(0, task.execute(), "running the task again should change nothing");

    for (String name : collections) {
      MongoCollection<Document> collection = database.getCollection(name);
      Document index = collection.listIndexes().into(new java.util.ArrayList<>()).stream()
          .filter(candidate -> new Document("@id", 1).equals(candidate.get("key", Document.class)))
          .findFirst()
          .orElseThrow(() -> new AssertionError(name + " has no index on @id"));
      assertTrue(index.getBoolean("unique", false), name + "'s @id index should be unique");

      String id = "https://repo.metadatacenter.orgx/" + name + "/duplicate";
      try {
        collection.insertOne(new Document("@id", id));
        MongoWriteException refused = assertThrows(MongoWriteException.class,
            () -> collection.insertOne(new Document("@id", id)), name + " should refuse a second document with one @id");
        assertEquals(11000, refused.getError().getCode(), "the refusal should be a duplicate key");
      } finally {
        collection.deleteMany(new Document("@id", id));
      }
    }
  }
}
