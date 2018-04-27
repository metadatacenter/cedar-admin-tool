package org.metadatacenter.admin.task;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.Document;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.model.CedarNodeType;

public class TemplateServerInitDB extends AbstractCedarAdminTask {

  private String mongoDatabaseName;
  private String templateElementsCollectionName;
  private String templateFieldCollectionName;
  private String templateInstancesCollectionName;
  private String templatesCollectionName;
  private String usersCollectionName;

  public TemplateServerInitDB() {
    description.add("Initializes Template Server MongoDB collections.");
    description.add("Adds constraints to the different collections.");
  }

  @Override
  public void init() {
    mongoDatabaseName = cedarConfig.getTemplateServerConfig().getDatabaseName();
    templateFieldCollectionName = cedarConfig.getTemplateServerConfig().getCollections().get(CedarNodeType.FIELD
        .getValue());
    templateElementsCollectionName = cedarConfig.getTemplateServerConfig().getCollections().get(CedarNodeType.ELEMENT
        .getValue());
    templatesCollectionName = cedarConfig.getTemplateServerConfig().getCollections().get(CedarNodeType.TEMPLATE
        .getValue());
    templateInstancesCollectionName = cedarConfig.getTemplateServerConfig().getCollections().get(CedarNodeType.INSTANCE
        .getValue());
    usersCollectionName = cedarConfig.getUserServerConfig().getCollections().get(CedarNodeType.USER.getValue());
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
    MongoClient mongoClientForUsers = CedarDataServices.getMongoClientFactoryForUsers().getClient();
    createUniqueIndex(mongoClientForDocuments, templateFieldCollectionName, "@id");
    createUniqueIndex(mongoClientForDocuments, templateElementsCollectionName, "@id");
    createUniqueIndex(mongoClientForDocuments, templatesCollectionName, "@id");
    createUniqueIndex(mongoClientForDocuments, templateInstancesCollectionName, "@id");
    createUniqueIndex(mongoClientForUsers, usersCollectionName, "id");

    return 0;
  }

}
