package org.metadatacenter.admin.task;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.BsonDocument;
import org.bson.Document;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.util.MongoFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WipeMongoData extends AbstractCedarAdminTask {

  private String mongoDatabaseName;
  private String templateElementsCollectionName;
  private String templateFieldCollectionName;
  private String templateInstancesCollectionName;
  private String templatesCollectionName;
  private String usersCollectionName;
  private MongoClient mongoClient;
  private Logger logger = LoggerFactory.getLogger(WipeMongoData.class);
  public static final String CONFIRM = "confirm";

  public WipeMongoData() {
    description.add("Deletes all documents from the handled MongoDB collections.");
    description.add("Needs second parameter '" + CONFIRM + "' to run.");
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


  private void emptyCollection(String collectionName) {
    logger.info("Deleting all data from collection: " + collectionName + ".");
    MongoCollection<Document> collection = mongoClient.getDatabase(mongoDatabaseName).getCollection(collectionName);
    BsonDocument allFilter = new BsonDocument();
    collection.deleteMany(allFilter);
  }

  @Override
  public int execute() {
    if (arguments.size() != 2 || !CONFIRM.equals(arguments.get(1))) {
      System.out.println("You need to confirm your intent by providing '" + CONFIRM + "' as the second argument!");
      return -1;
    }
    mongoClient = MongoFactory.getClient();
    emptyCollection(templateElementsCollectionName);
    emptyCollection(templateFieldCollectionName);
    emptyCollection(templateInstancesCollectionName);
    emptyCollection(templatesCollectionName);
    emptyCollection(usersCollectionName);

    return 0;
  }

}
