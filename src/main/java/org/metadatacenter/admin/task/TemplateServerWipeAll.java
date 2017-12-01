package org.metadatacenter.admin.task;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.BsonDocument;
import org.bson.Document;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.model.CedarNodeType;

public class TemplateServerWipeAll extends AbstractCedarAdminTask {

  private String mongoDatabaseName;
  private String templateElementsCollectionName;
  //private String templateFieldCollectionName;
  private String templateInstancesCollectionName;
  private String templatesCollectionName;
  private String usersCollectionName;

  public TemplateServerWipeAll() {
    description.add("Deletes all documents from the handled MongoDB collections.");
  }

  @Override
  public void init() {
    mongoDatabaseName = cedarConfig.getTemplateServerConfig().getDatabaseName();
    /*templateFieldCollectionName = cedarConfig.getTemplateServerConfig().getCollections().get(CedarNodeType.FIELD
        .getValue());*/
    templateElementsCollectionName = cedarConfig.getTemplateServerConfig().getCollections().get(CedarNodeType.ELEMENT
        .getValue
            ());
    templatesCollectionName = cedarConfig.getTemplateServerConfig().getCollections().get(CedarNodeType.TEMPLATE
        .getValue());
    templateInstancesCollectionName = cedarConfig.getTemplateServerConfig().getCollections().get(CedarNodeType.INSTANCE
        .getValue());
    usersCollectionName = cedarConfig.getUserServerConfig().getCollections().get(CedarNodeType.USER.getValue());
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
    MongoClient mongoClientForUsers = CedarDataServices.getMongoClientFactoryForUsers().getClient();

    emptyCollection(mongoClientForDocuments, templateElementsCollectionName);
    //emptyCollection(mongoClientForDocuments, templateFieldCollectionName);
    emptyCollection(mongoClientForDocuments, templateInstancesCollectionName);
    emptyCollection(mongoClientForDocuments, templatesCollectionName);
    emptyCollection(mongoClientForUsers, usersCollectionName);

    return 0;
  }

}
