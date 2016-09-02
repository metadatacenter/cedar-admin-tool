package org.metadatacenter.admin.util;

import org.metadatacenter.admin.task.ICedarAdminTask;
import org.metadatacenter.config.CedarConfig;

public class TaskExecutor {
  public static int executeOneTask(String taskKey, AdminOutput out, String[] args) {
    out.println("Command  :  " + taskKey);
    ICedarAdminTask task = null;
    try {
      task = TaskRegistry.getTaskForKey(taskKey);
    } catch (InstantiationException | IllegalAccessException e) {
      out.error(e);
    }
    if (task != null) {
      CedarConfig config = CedarConfig.getInstance();
      task.setOutput(out);
      task.setArguments(args);
      out.println("Arguments: " + task.getArguments());
      out.printSeparator();
      task.injectConfig(config);
      task.init();
      return task.execute();
    }
    return -1;
  }
}
