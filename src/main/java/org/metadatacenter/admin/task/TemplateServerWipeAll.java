package org.metadatacenter.admin.task;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.BsonDocument;
import org.bson.Document;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.util.mongo.MongoFactory;

public class TemplateServerWipeAll extends AbstractCedarAdminTask {

  private String mongoDatabaseName;
  private String templateElementsCollectionName;
  private String templateFieldCollectionName;
  private String templateInstancesCollectionName;
  private String templatesCollectionName;
  private String usersCollectionName;
  private MongoClient mongoClient;

  public TemplateServerWipeAll() {
    description.add("Deletes all documents from the handled MongoDB collections.");
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


  private void emptyCollection(String collectionName) {
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

    mongoClient = MongoFactory.getClient();
    emptyCollection(templateElementsCollectionName);
    emptyCollection(templateFieldCollectionName);
    emptyCollection(templateInstancesCollectionName);
    emptyCollection(templatesCollectionName);
    emptyCollection(usersCollectionName);

    return 0;
  }

}
