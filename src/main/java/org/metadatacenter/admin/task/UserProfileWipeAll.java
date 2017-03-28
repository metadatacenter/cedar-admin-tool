package org.metadatacenter.admin.task;

import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.model.CedarNodeType;

public class UserProfileWipeAll extends AbstractMongoTask {

  private String usersCollectionName;

  public UserProfileWipeAll() {
    description.add("Deletes all user profiles from MongoDB.");
  }

  @Override
  public void init() {
    mongoDatabaseName = cedarConfig.getUserServerConfig().getDatabaseName();
    usersCollectionName = cedarConfig.getUserServerConfig().getCollections().get(CedarNodeType.USER.getValue());
  }

  @Override
  public int execute() {
    if (!getConfirmInput("Deleting all user profiles from MongoDB...")) {
      return -1;
    }

    mongoClient = CedarDataServices.getMongoClientFactoryForUsers().getClient();
    emptyCollection(usersCollectionName);

    return 0;
  }

}
