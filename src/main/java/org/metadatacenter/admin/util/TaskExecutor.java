package org.metadatacenter.admin.util;

import org.metadatacenter.admin.task.ICedarAdminTask;
import org.metadatacenter.config.CedarConfig;

import java.lang.reflect.InvocationTargetException;

public class TaskExecutor {
  public static int executeOneTask(CedarConfig cedarConfig, String taskKey, AdminOutput out, String[] args) {
    out.println("Command  :  " + taskKey);
    ICedarAdminTask task = null;
    try {
      task = TaskRegistry.getTaskForKey(taskKey);
    } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
      out.error(e);
    }
    if (task != null) {
      task.setOutput(out);
      task.setArguments(args);
      out.println("Arguments: " + task.getArguments());
      out.printSeparator();
      task.injectConfig(cedarConfig);
      task.init();
      return task.execute();
    }
    return -1;
  }
}
