package org.metadatacenter.admin.task;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.IndexOptions;
import org.bson.BsonDocument;
import org.bson.BsonInt32;
import org.bson.Document;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.util.MongoFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;


public class InitMongoDB implements CedarAdminTask {

  private String mongoDatabaseName;
  private String templateElementsCollectionName;
  private String templateFieldCollectionName;
  private String templateInstancesCollectionName;
  private String templatesCollectionName;
  private String usersCollectionName;
  private MongoClient mongoClient;
  private Logger logger = LoggerFactory.getLogger(InitMongoDB.class);
  private static List<String> description;

  static {
    description = new ArrayList<>();
    description.add("Initializes MongoDB collections.");
    description.add("Adds constraints to the different collections.");
  }

  @Override
  public void setArguments(String[] args) {
  }

  @Override
  public void init(CedarConfig config) {
    mongoDatabaseName = config.getMongoConfig().getDatabaseName();
    templateFieldCollectionName = config.getMongoConfig().getCollections().get(CedarNodeType.FIELD.getValue());
    templateElementsCollectionName = config.getMongoConfig().getCollections().get(CedarNodeType.ELEMENT.getValue());
    templatesCollectionName = config.getMongoConfig().getCollections().get(CedarNodeType.TEMPLATE.getValue());
    templateInstancesCollectionName = config.getMongoConfig().getCollections().get(CedarNodeType.INSTANCE.getValue());
    usersCollectionName = config.getMongoConfig().getCollections().get(CedarNodeType.USER.getValue());
  }


  private void createUniqueIndex(String collectionName, String fieldName) {
    logger.info("Creating unique index on: " + collectionName + "." + fieldName);
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

  @Override
  public List<String> getDescription() {
    return description;
  }

}
