package org.metadatacenter.admin.task;

import org.metadatacenter.admin.util.TaskExecutor;
import org.metadatacenter.admin.util.TaskRegistry;

import java.util.ArrayList;
import java.util.List;

public class SystemReset extends AbstractKeycloakReadingTask {

  private List<String[]> commands;

  public SystemReset() {
    description.add("Wipes al data and recreates global data and user profiles.");
    description.add("Works with MongoDB and Neo4j as well.");
  }

  @Override
  public void init() {
    commands = new ArrayList<>();
    commands.add(new String[]{TaskRegistry.ARTIFACT_SERVER_WIPE_ALL});
    commands.add(new String[]{TaskRegistry.ARTIFACT_SERVER_INIT_DB});
    commands.add(new String[]{TaskRegistry.GRAPH_DB_SERVER_WIPE_ALL});
    commands.add(new String[]{TaskRegistry.GRAPH_DB_SERVER_INIT_DB});
    commands.add(new String[]{TaskRegistry.GRAPH_DB_SERVER_CREATE_GLOBAL_OBJECTS});
    commands.add(new String[]{TaskRegistry.GRAPH_DB_SERVER_CREATE_ALL_USERS});
    commands.add(new String[]{TaskRegistry.SEARCH_REGENERATE_INDEX});
  }

  @Override
  public int execute() {
    if (!getConfirmInput("Performing system reset...")) {
      return -1;
    }

    for (String[] command : commands) {
      String taskKey = command[0];
      Class<? extends ICedarAdminTask> taskClass = TaskRegistry.getTaskClassForKey(taskKey);
      int result = TaskExecutor.executeOneTask(cedarConfig, taskKey, out, command);
      if (result != 0) {
        return result;
      }
    }

    return 0;
  }

}
