package org.metadatacenter.admin.task;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.Document;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.util.MongoFactory;

public class InitMongoDB extends AbstractCedarAdminTask {

  private String mongoDatabaseName;
  private String templateElementsCollectionName;
  private String templateFieldCollectionName;
  private String templateInstancesCollectionName;
  private String templatesCollectionName;
  private String usersCollectionName;
  private MongoClient mongoClient;

  public InitMongoDB() {
    description.add("Initializes MongoDB collections.");
    description.add("Adds constraints to the different collections.");
  }

  @Override
  public void init() {
    mongoDatabaseName = cedarConfig.getMongoConfig().getDatabaseName();
    templateFieldCollectionName = cedarConfig.getMongoConfig().getCollections().get(CedarNodeType.FIELD.getValue());
    templateElementsCollectionName = cedarConfig.getMongoConfig().getCollections().get(CedarNodeType.ELEMENT.getValue
        ());
    templatesCollectionName = cedarConfig.getMongoConfig().getCollections().get(CedarNodeType.TEMPLATE.getValue());
    templateInstancesCollectionName = cedarConfig.getMongoConfig().getCollections().get(CedarNodeType.INSTANCE
        .getValue());
    usersCollectionName = cedarConfig.getMongoConfig().getCollections().get(CedarNodeType.USER.getValue());
  }

  private void createUniqueIndex(String collectionName, String fieldName) {
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
    mongoClient = MongoFactory.getClient();
    createUniqueIndex(templateElementsCollectionName, "@id");
    createUniqueIndex(templateFieldCollectionName, "@id");
    createUniqueIndex(templateInstancesCollectionName, "@id");
    createUniqueIndex(templatesCollectionName, "@id");
    createUniqueIndex(usersCollectionName, "userId");

    return 0;
  }

}
