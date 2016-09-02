package org.metadatacenter.admin.util;

import org.metadatacenter.admin.task.*;
import org.metadatacenter.admin.task.importflatfolder.ImportFlatFolder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class TaskRegistry {

  static Map<String, Class<? extends ICedarAdminTask>> taskMap;

  static {
    taskMap = new LinkedHashMap<>();
    taskMap.put("exportResources", ExportResources.class);
    taskMap.put("regenerateSearchIndex", RegenerateSearchIndex.class);
    taskMap.put("importFlatFolder", ImportFlatFolder.class);

    taskMap.put("importFlatFolder", ImportFlatFolder.class);

    taskMap.put("templateServer-wipeAll", TemplateServerWipeAll.class);
    taskMap.put("templateServer-initDB", TemplateServerInitDB.class);

    taskMap.put("folderServer-wipeAll", FolderServerWipeAll.class);
    taskMap.put("folderServer-createGlobalObjects", FolderServerCreateGlobalObjects.class);
    taskMap.put("folderServer-createUserHomeFolders", FolderServerCreateUserHomeFolders.class);

    taskMap.put("userProfile-get-admin", UserProfileGetAdmin.class);
    taskMap.put("userProfile-listAll", UserProfileListAll.class);
    taskMap.put("userProfile-wipeAll", UserProfileWipeAll.class);
    taskMap.put("userProfile-createAll", UserProfileCreateAll.class);
    taskMap.put("userProfile-updateAll-updatePermissions", UserProfileUpdateAllUpdatePermissions.class);
    taskMap.put("userProfile-updateAll-setHomeFolder", UserProfileUpdateAllSetHomeFolder.class);

    taskMap.put("system-reset", SystemReset.class);
  }

  public static Set<String> getTaskKeys() {
    return taskMap.keySet();
  }

  public static Class<? extends ICedarAdminTask> getTaskClassForKey(String key){
    return taskMap.get(key);
  }

  public static ICedarAdminTask getTaskForKey(String key) throws IllegalAccessException, InstantiationException {
    return getTaskClassForKey(key).newInstance();
  }
}
