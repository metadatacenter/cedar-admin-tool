package org.metadatacenter.admin.task;

import com.mongodb.client.MongoDatabase;
import org.bson.Document;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.util.test.EmbeddedCedarMongo;
import org.metadatacenter.util.test.EmbeddedCedarNeo4j;

/**
 * Every class in the suite's JVM shares one embedded Neo4j and one embedded MongoDB. A class that uses this extension
 * starts from a freshly seeded graph, with the uniqueness rules the seed creates, and from empty artifact collections,
 * whatever an earlier class created, deleted or wiped. Its result then does not depend on the order the classes run in.
 * The extension runs before the class's own {@code @BeforeAll} methods.
 */
final class FreshStores implements BeforeAllCallback {

  @Override
  public void beforeAll(ExtensionContext context) throws Exception {
    EmbeddedCedarMongo.startAndRedirectEnvironment();
    EmbeddedCedarNeo4j.startRedirectAndSeed(SystemComponent.ADMIN_TOOL);
    CedarConfig cedarConfig = CedarConfig.getInstance(CedarEnvironmentVariableProvider.getFor(SystemComponent.ADMIN_TOOL));
    EmbeddedCedarNeo4j.resetAndSeed(cedarConfig);

    // The documents go and the collections stay, with the unique identifier index each carries.
    CedarDataServices.initializeMongoClientFactoryForDocuments(cedarConfig.getArtifactServerConfig().getMongoConnection());
    MongoDatabase documents = CedarDataServices.getInstance().getMongoClientFactoryForDocuments().getClient()
        .getDatabase(cedarConfig.getArtifactServerConfig().getDatabaseName());
    for (String collection : EmbeddedCedarMongo.ARTIFACT_COLLECTIONS) {
      documents.getCollection(collection).deleteMany(new Document());
    }
  }
}
