package org.metadatacenter.admin;

import org.metadatacenter.admin.task.*;
import org.metadatacenter.admin.task.importflatfolder.ImportFlatFolder;
import org.metadatacenter.admin.util.AdminOutput;
import org.metadatacenter.admin.util.Color;
import org.metadatacenter.config.CedarConfig;

import java.util.LinkedHashMap;
import java.util.Map;

public class CedarAdmin {

  static Map<String, Class<? extends ICedarAdminTask>> taskMap;
  static AdminOutput out;

  static {
    out = new AdminOutput();
    taskMap = new LinkedHashMap<>();
    taskMap.put("wipeMongoData", WipeMongoData.class);
    taskMap.put("initMongoDB", InitMongoDB.class);
    taskMap.put("getAdminUserKeycloakProfile", GetAdminUserKeycloakProfile.class);
    taskMap.put("wipeNeo4jData", WipeNeo4jData.class);
    taskMap.put("createFolderServerGlobalObjects", CreateFolderServerGlobalObjects.class);
    taskMap.put("exportResources", ExportResources.class);
    taskMap.put("regenerateSearchIndex", RegenerateSearchIndex.class);
    taskMap.put("importFlatFolder", ImportFlatFolder.class);

    taskMap.put("importFlatFolder", ImportFlatFolder.class);

    taskMap.put("userProfile-listAll", UserProfileListAll.class);
    taskMap.put("userProfile-wipeAll", UserProfileWipeAll.class);
    taskMap.put("userProfile-createAll", UserProfileCreateAll.class);

  }

  private static void showTitle() {
    out.println("CEDAR Admin Tools");
  }

  private static void showUsageAndExit() {
    showTitle();
    out.printTitle("Usage:");
    out.printIndented("cedar-admin-tools command parameters...");
    out.printTitle("Available commands:");
    for (String key : taskMap.keySet()) {
      out.printIndented(key, Color.BRIGHT);
      ICedarAdminTask t = null;
      try {
        t = taskMap.get(key).newInstance();
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

    //args = new String[]{"wipeMongoData"};
    //args = new String[]{"initMongoDB"};
    //args = new String[]{"getAdminUserKeycloakProfile"};
    //args = new String[]{"createFolderServerGlobalObjects"};
    //args = new String[]{"wipeNeo4jData"};
    //args = new String[]{"exportResources"};
    /*args = new String[]{"importFlatFolder",
        "/Users/egyedia/Development/git_repos/CEDAR/import/",
        "https://repo.metadatacenter.orgx/folders/600f75c6-389b-458c-9a4b-465fa89fd0a3",
        "8c99c2ae-8633-47d4-a049-0dce14795a45"};*/

    //args = new String[]{"userProfile-listAll"};
    //args = new String[]{"userProfile-wipeAll", AbstractCedarAdminTask.CONFIRM};
    //args = new String[]{"userProfile-createAll"};



    if (args == null || args.length == 0) {
      showUsageAndExit();
    } else {
      String firstArg = args[0];
      if (firstArg == null || firstArg.trim().length() == 0) {
        showUsageAndExit();
      } else {
        Class<? extends ICedarAdminTask> taskClass = taskMap.get(firstArg);
        if (taskClass == null) {
          out.error("Unknown command: " + firstArg + "\n");
          showUsageAndExit();
        } else {
          out.println("Command  :  " + firstArg);
          ICedarAdminTask task = null;
          try {
            task = taskClass.newInstance();
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
            System.exit(task.execute());
          }
        }
      }
    }
  }

}
