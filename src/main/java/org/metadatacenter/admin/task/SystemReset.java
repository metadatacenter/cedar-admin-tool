package org.metadatacenter.admin.task;

import com.mongodb.client.MongoCollection;
import org.bson.BsonDocument;
import org.bson.Document;
import org.keycloak.representations.idm.UserRepresentation;
import org.metadatacenter.admin.util.TaskExecutor;
import org.metadatacenter.admin.util.TaskRegistry;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.util.MongoFactory;

import java.util.ArrayList;
import java.util.List;

public class SystemReset extends AbstractKeycloakReadingTask {

  private List<String[]> commands;

  public SystemReset() {
    description.add("Wipes al data and recreates global data and user profiles.");
    description.add("Works with Mongo and neo4j as well.");
    description.add("Needs second parameter '" + CONFIRM + "' to run.");
  }

  @Override
  public void init() {
    commands = new ArrayList<>();
    commands.add(new String[]{"templateServer-wipeAll", "confirm"});
    commands.add(new String[]{"userProfile-createAll"});
    commands.add(new String[]{"folderServer-wipeAll", "confirm"});
    commands.add(new String[]{"folderServer-createGlobalObjects"});
    commands.add(new String[]{"folderServer-createUserHomeFolders"});
  }


  @Override
  public int execute() {
    if (arguments.size() != 2 || !CONFIRM.equals(arguments.get(1))) {
      out.warn("You need to confirm your intent by providing '" + CONFIRM + "' as the second argument!");
      return -1;
    }

    for (String[] command : commands) {
      String taskKey = command[0];
      Class<? extends ICedarAdminTask> taskClass = TaskRegistry.getTaskClassForKey(taskKey);
      int result = TaskExecutor.executeOneTask(taskKey, out, command);
      if (result != 0) {
        return result;
      }
    }

    return 0;
  }

}