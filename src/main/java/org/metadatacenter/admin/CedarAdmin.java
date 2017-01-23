package org.metadatacenter.admin;

import org.metadatacenter.admin.task.ICedarAdminTask;
import org.metadatacenter.admin.util.AdminOutput;
import org.metadatacenter.admin.util.Color;
import org.metadatacenter.admin.util.TaskExecutor;
import org.metadatacenter.admin.util.TaskRegistry;
import org.metadatacenter.config.CedarConfig;

public class CedarAdmin {

  static AdminOutput out;

  static {
    out = new AdminOutput();
  }

  private static void showTitle() {
    out.println("CEDAR Admin Tools");
  }

  private static void showUsageAndExit() {
    showTitle();
    out.printTitle("Usage:");
    out.printIndented("cedar-admin-tools command parameters...");
    out.printTitle("Available commands:");
    for (String key : TaskRegistry.getTaskKeys()) {
      out.printIndented(key, Color.BRIGHT);
      ICedarAdminTask t = null;
      try {
        t = TaskRegistry.getTaskForKey(key);
      } catch (InstantiationException | IllegalAccessException e) {
        out.error(e);
      }
      if (t != null) {
        out.printIndented("Details:", 2);
        for (String desc : t.getDescription()) {
          out.printIndented("* " + desc, 2);
        }
        out.println();
      }
    }
    out.println("\n");
    System.exit(-1);
  }

  public static void main(String[] args) {
    if (args == null || args.length == 0) {
      showUsageAndExit();
    } else {
      String firstArg = args[0];
      if (firstArg == null || firstArg.trim().length() == 0) {
        showUsageAndExit();
      } else {
        Class<? extends ICedarAdminTask> taskClass = TaskRegistry.getTaskClassForKey(firstArg);
        if (taskClass == null) {
          out.error("Unknown command: " + firstArg + "\n");
          showUsageAndExit();
        } else {
          CedarConfig cedarConfig = CedarConfig.getInstance();

          System.exit(TaskExecutor.executeOneTask(cedarConfig, firstArg, out, args));
        }
      }
    }
  }

}
