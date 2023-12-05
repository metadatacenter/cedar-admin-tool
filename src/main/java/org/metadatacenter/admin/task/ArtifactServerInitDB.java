package org.metadatacenter.admin.task;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.Document;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.constant.LinkedData;

public class ArtifactServerInitDB extends AbstractCedarAdminTask {

  public ArtifactServerInitDB() {
    description.add("Initializes Artifact Server MongoDB collections.");
    description.add("Adds constraints to the different collections.");
  }

  @Override
  public void init() {
    initMongoCollectionNames();
  }

  private void createUniqueIndex(MongoClient mongoClient, String collectionName, String fieldName) {
    out.info("Creating unique index on: " + collectionName + "." + fieldName);
    MongoCollection<Document> collection = mongoClient.getDatabase(mongoDatabaseName).getCollection(collectionName);

    BsonDocument fields = new BsonDocument();
    fields.append(fieldName, new BsonInt32(1));
    IndexOptions opt = new IndexOptions();
    opt.unique(true);
    collection.createIndex(fields, opt);
  }

  @Override
  public int execute() {
    MongoClient mongoClientForDocuments = CedarDataServices.getMongoClientFactoryForDocuments().getClient();
    createUniqueIndex(mongoClientForDocuments, templateFieldsCollectionName, LinkedData.ID);
    createUniqueIndex(mongoClientForDocuments, templateElementsCollectionName, LinkedData.ID);
    createUniqueIndex(mongoClientForDocuments, templatesCollectionName, LinkedData.ID);
    createUniqueIndex(mongoClientForDocuments, templateInstancesCollectionName, LinkedData.ID);

    return 0;
  }

}
