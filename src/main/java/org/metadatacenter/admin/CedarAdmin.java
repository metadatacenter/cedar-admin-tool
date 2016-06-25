package org.metadatacenter.admin;

import org.metadatacenter.admin.task.*;
import org.metadatacenter.admin.task.importflatfolder.ImportFlatFolder;
import org.metadatacenter.config.CedarConfig;

import java.util.LinkedHashMap;
import java.util.Map;

public class CedarAdmin {

  static Map<String, Class<? extends CedarAdminTask>> taskMap;

  static {
    taskMap = new LinkedHashMap<>();
    taskMap.put("wipeMongoData", WipeMongoData.class);
    taskMap.put("initMongoDB", InitMongoDB.class);
    taskMap.put("getAdminUserKeycloakProfile", GetAdminUserKeycloakProfile.class);
    taskMap.put("createAdminUserProfile", CreateAdminUserProfile.class);
    taskMap.put("wipeNeo4jData", WipeNeo4jData.class);
    taskMap.put("createFolderServerGlobalObjects", CreateFolderServerGlobalObjects.class);
    taskMap.put("exportResources", ExportResources.class);
    taskMap.put("regenerateSearchIndex", RegenerateSearchIndex.class);
    taskMap.put("importFlatFolder", ImportFlatFolder.class);
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
      CedarAdminTask t = null;
      try {
        t = taskMap.get(key).newInstance();
      } catch (InstantiationException | IllegalAccessException e) {
        e.printStackTrace();
      }
      if (t != null) {
        System.out.println("\t\tDetails:");
        for (String desc : t.getDescription()) {
          System.out.println("\t\t* " + desc);
        }
        System.out.println();
      }
    }
    System.out.println("\n");
    System.exit(-1);
  }

  public static void main(String[] args) {

    //args = new String[]{"wipeMongoData"};
    //args = new String[]{"initMongoDB"};
    //args = new String[]{"getAdminUserKeycloakProfile"};
    //args = new String[]{"createAdminUserProfile"};
    //args = new String[]{"createFolderServerGlobalObjects"};
    //args = new String[]{"wipeNeo4jData"};
    //args = new String[]{"exportResources"};
    /*args = new String[]{"importFlatFolder",
        "/Users/egyedia/Development/git_repos/CEDAR/import/",
        "https://repo.metadatacenter.orgx/folders/600f75c6-389b-458c-9a4b-465fa89fd0a3",
        "8c99c2ae-8633-47d4-a049-0dce14795a45"};*/


    if (args == null || args.length == 0) {
      showUsageAndExit();
    } else {
      String firstArg = args[0];
      if (firstArg == null || firstArg.trim().length() == 0) {
        showUsageAndExit();
      } else {
        Class<? extends CedarAdminTask> taskClass = taskMap.get(firstArg);
        if (taskClass == null) {
          System.out.println("ERROR: Unknown command: " + firstArg + "\n");
          showUsageAndExit();
        } else {
          System.out.println("Command  :  " + firstArg);
          CedarAdminTask task = null;
          try {
            task = taskClass.newInstance();
          } catch (InstantiationException | IllegalAccessException e) {
            e.printStackTrace();
          }
          if (task != null) {
            CedarConfig config = CedarConfig.getInstance();
            task.setArguments(args);
            System.out.println("Arguments: " + task.getArguments());
            System.out.println("------------------------------------");
            task.init(config);
            System.exit(task.execute());
          }
        }
      }
    }
  }

}
