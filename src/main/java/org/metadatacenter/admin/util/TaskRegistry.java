package org.metadatacenter.admin.util;

import org.metadatacenter.admin.task.*;
import org.metadatacenter.admin.task.importflatfolder.ImpexImportFlatFolder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class TaskRegistry {

  static final Map<String, Class<? extends ICedarAdminTask>> taskMap;

  public static final String TEMPLATE_SERVER_WIPE_ALL = "templateServer-wipeAll";

  public static final String TEMPLATE_SERVER_INIT_DB = "templateServer-initDB";

  public static final String FOLDER_SERVER_WIPE_ALL = "folderServer-wipeAll";

  public static final String FOLDER_SERVER_CREATE_GLOBAL_OBJECTS = "folderServer-createGlobalObjects";

  public static final String FOLDER_SERVER_CREATE_USER_HOME_FOLDERS = "folderServer-createUserHomeFolders";

  public static final String USER_PROFILE_GET_ADMIN = "userProfile-get-admin";

  public static final String USER_PROFILE_LIST_ALL = "userProfile-listAll";

  public static final String USER_PROFILE_WIPE_ALL = "userProfile-wipeAll";

  public static final String USER_PROFILE_CREATE_ALL = "userProfile-createAll";

  public static final String USER_PROFILE_UPDATE_ALL_UPDATE_PERMISSIONS = "userProfile-updateAll-updatePermissions";

  public static final String USER_PROFILE_UPDATE_ALL_SET_HOME_FOLDER = "userProfile-updateAll-setHomeFolder";

  public static final String SEARCH_REGENERATE_INDEX = "search-regenerateIndex";

  public static final String IMPEX_EXPORT_ALL = "impex-exportAll";

  public static final String IMPEX_IMPORT_FLAT_FOLDER = "impex-importFlatFolder";

  public static final String SYSTEM_RESET = "system-reset";

  static {
    taskMap = new LinkedHashMap<>();
    taskMap.put(TEMPLATE_SERVER_WIPE_ALL, TemplateServerWipeAll.class);
    taskMap.put(TEMPLATE_SERVER_INIT_DB, TemplateServerInitDB.class);

    taskMap.put(FOLDER_SERVER_WIPE_ALL, FolderServerWipeAll.class);
    taskMap.put(FOLDER_SERVER_CREATE_GLOBAL_OBJECTS, FolderServerCreateGlobalObjects.class);
    taskMap.put(FOLDER_SERVER_CREATE_USER_HOME_FOLDERS, FolderServerCreateUserHomeFolders.class);

    taskMap.put(USER_PROFILE_GET_ADMIN, UserProfileGetAdmin.class);
    taskMap.put(USER_PROFILE_LIST_ALL, UserProfileListAll.class);
    taskMap.put(USER_PROFILE_WIPE_ALL, UserProfileWipeAll.class);
    taskMap.put(USER_PROFILE_CREATE_ALL, UserProfileCreateAll.class);
    taskMap.put(USER_PROFILE_UPDATE_ALL_UPDATE_PERMISSIONS, UserProfileUpdateAllUpdatePermissions.class);
    taskMap.put(USER_PROFILE_UPDATE_ALL_SET_HOME_FOLDER, UserProfileUpdateAllSetHomeFolder.class);

    taskMap.put(SEARCH_REGENERATE_INDEX, SearchRegenerateIndex.class);
    taskMap.put(IMPEX_EXPORT_ALL, ImpexExportAll.class);
    taskMap.put(IMPEX_IMPORT_FLAT_FOLDER, ImpexImportFlatFolder.class);

    taskMap.put(SYSTEM_RESET, SystemReset.class);
  }

  public static Set<String> getTaskKeys() {
    return taskMap.keySet();
  }

  public static Class<? extends ICedarAdminTask> getTaskClassForKey(String key) {
    return taskMap.get(key);
  }

  public static ICedarAdminTask getTaskForKey(String key) throws IllegalAccessException, InstantiationException {
    return getTaskClassForKey(key).newInstance();
  }
}
