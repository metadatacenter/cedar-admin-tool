package org.metadatacenter.admin.task;

import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.util.mongo.MongoFactory;

public class UserProfileWipeAll extends AbstractMongoTask {

  private String usersCollectionName;

  public UserProfileWipeAll() {
    description.add("Deletes all user profiles from MongoDB.");
    description.add("Needs second parameter '" + CONFIRM + "' to run.");
  }

  @Override
  public void init() {
    mongoDatabaseName = cedarConfig.getMongoConfig().getDatabaseName();
    usersCollectionName = cedarConfig.getMongoConfig().getCollections().get(CedarNodeType.USER.getValue());
  }

  @Override
  public int execute() {
    if (arguments.size() != 2 || !CONFIRM.equals(arguments.get(1))) {
      out.warn("You need to confirm your intent by providing '" + CONFIRM + "' as the second argument!");
      return -1;
    }
    mongoClient = MongoFactory.getClient();
    emptyCollection(usersCollectionName);

    return 0;
  }

}
