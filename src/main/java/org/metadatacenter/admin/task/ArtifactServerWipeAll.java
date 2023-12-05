package org.metadatacenter.admin.task;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.BsonDocument;
import org.bson.Document;
import org.metadatacenter.bridge.CedarDataServices;

public class ArtifactServerWipeAll extends AbstractCedarAdminTask {

  public ArtifactServerWipeAll() {
    description.add("Deletes all documents from the handled MongoDB collections.");
  }

  @Override
  public void init() {
    initMongoCollectionNames();
  }

  private void emptyCollection(MongoClient mongoClient, String collectionName) {
    out.info("Deleting all data from collection: " + collectionName + ".");
    MongoCollection<Document> collection = mongoClient.getDatabase(mongoDatabaseName).getCollection(collectionName);
    BsonDocument allFilter = new BsonDocument();
    collection.deleteMany(allFilter);
  }

  @Override
  public int execute() {
    if (!getConfirmInput("Deleting all documents from the handled MongoDB collections...")) {
      return -1;
    }

    MongoClient mongoClientForDocuments = CedarDataServices.getMongoClientFactoryForDocuments().getClient();
    emptyCollection(mongoClientForDocuments, templateFieldsCollectionName);
    emptyCollection(mongoClientForDocuments, templateElementsCollectionName);
    emptyCollection(mongoClientForDocuments, templatesCollectionName);
    emptyCollection(mongoClientForDocuments, templateInstancesCollectionName);

    return 0;
  }

}
