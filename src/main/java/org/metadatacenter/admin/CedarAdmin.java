package org.metadatacenter.admin;

import org.metadatacenter.admin.config.CedarConfig;
import org.metadatacenter.admin.task.CedarAdminTask;
import org.metadatacenter.admin.task.CreateAdminUserProfile;
import org.metadatacenter.admin.task.CreateFolderServerGlobalObjects;
import org.metadatacenter.admin.task.InitMongoDB;

import java.util.HashMap;
import java.util.Map;

public class CedarAdmin {

  static Map<String, Class<? extends CedarAdminTask>> taskMap;

  static {
    taskMap = new HashMap<>();
    taskMap.put("initMongoDB", InitMongoDB.class);
    taskMap.put("createAdminUserProfile", CreateAdminUserProfile.class);
    taskMap.put("createFolderServerGlobalObjects", CreateFolderServerGlobalObjects.class);
  }

  private static void showTitle() {
    System.out.println("CEDAR Admin Tools");
  }

  private static void showUsageAndExit() {
    showTitle();
    System.out.println("\nUsage:");
    System.out.println("\tcedar-admin-tools command parameters...");
    System.out.println("\nAvailable commands:");
    for (String key : taskMap.keySet()) {
      System.out.println("\t" + key);
    }
    System.exit(-1);
  }

  public static void main(String[] args) {

    //args = new String[]{"initMongoDB"};
    //args = new String[]{"createAdminUserProfile"};
    args = new String[]{"createFolderServerGlobalObjects"};

    if (args == null || args.length == 0) {
      showUsageAndExit();
    } else {
      String firstArg = args[0];
      if (firstArg == null || firstArg.trim().length() == 0) {
        showUsageAndExit();
      } else {
        Class<? extends CedarAdminTask> taskClass = taskMap.get(firstArg);
        if (taskClass == null) {
          showUsageAndExit();
        } else {
          CedarAdminTask task = null;
          try {
            task = taskClass.newInstance();
          } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
          }
          if (task != null) {
            CedarConfig c = CedarConfig.getInstance();
            task.setArguments(args);
            task.init(c);
            System.exit(task.execute());
          }
        }
      }
    }
  }

}
