package org.metadatacenter.admin.util;

import org.metadatacenter.admin.task.*;
import org.metadatacenter.admin.task.importflatfolder.ImpexImportFlatFolder;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class TaskRegistry {

  static final Map<String, Class<? extends ICedarAdminTask>> taskMap;

  public static final String TEMPLATE_SERVER_WIPE_ALL = "templateServer-wipeAll";

  public static final String TEMPLATE_SERVER_INIT_DB = "templateServer-initDB";

  public static final String WORKSPACE_SERVER_WIPE_ALL = "workspaceServer-wipeAll";

  public static final String WORKSPACE_SERVER_INIT_DB = "workspaceServer-initDB";

  public static final String WORKSPACE_SERVER_CREATE_GLOBAL_OBJECTS = "workspaceServer-createGlobalObjects";

  public static final String WORKSPACE_SERVER_CREATE_USER_HOME_FOLDERS = "workspaceServer-createUserHomeFolders";

  public static final String USER_PROFILE_GET_ADMIN = "userProfile-get-admin";

  public static final String USER_PROFILE_LIST_ALL = "userProfile-listAll";

  public static final String USER_PROFILE_WIPE_ALL = "userProfile-wipeAll";

  public static final String USER_PROFILE_CREATE_ALL = "userProfile-createAll";

  public static final String USER_PROFILE_UPDATE_ALL_UPDATE_PERMISSIONS = "userProfile-updateAll-updatePermissions";

  public static final String USER_PROFILE_UPDATE_ALL_SET_HOME_FOLDER = "userProfile-updateAll-setHomeFolder";

  public static final String SEARCH_REGENERATE_INDEX = "search-regenerateIndex";

  public static final String RULES_REGENERATE_INDEX = "rules-regenerateIndex";

  public static final String IMPEX_EXPORT_ALL = "impex-exportAll";

  public static final String IMPEX_IMPORT_ALL = "impex-importAll";

  public static final String IMPEX_IMPORT_FLAT_FOLDER = "impex-importFlatFolder";

  public static final String SYSTEM_RESET = "system-reset";

  static {
    taskMap = new LinkedHashMap<>();
    taskMap.put(TEMPLATE_SERVER_WIPE_ALL, TemplateServerWipeAll.class);
    taskMap.put(TEMPLATE_SERVER_INIT_DB, TemplateServerInitDB.class);

    taskMap.put(WORKSPACE_SERVER_WIPE_ALL, WorkspaceServerWipeAll.class);
    taskMap.put(WORKSPACE_SERVER_INIT_DB, WorkspaceServerInitDB.class);
    taskMap.put(WORKSPACE_SERVER_CREATE_GLOBAL_OBJECTS, WorkspaceServerCreateGlobalObjects.class);
    taskMap.put(WORKSPACE_SERVER_CREATE_USER_HOME_FOLDERS, WorkspaceServerCreateUserHomeFolders.class);

    taskMap.put(USER_PROFILE_GET_ADMIN, UserProfileGetAdmin.class);
    taskMap.put(USER_PROFILE_LIST_ALL, UserProfileListAll.class);
    taskMap.put(USER_PROFILE_WIPE_ALL, UserProfileWipeAll.class);
    taskMap.put(USER_PROFILE_CREATE_ALL, UserProfileCreateAll.class);
    taskMap.put(USER_PROFILE_UPDATE_ALL_UPDATE_PERMISSIONS, UserProfileUpdateAllUpdatePermissions.class);
    taskMap.put(USER_PROFILE_UPDATE_ALL_SET_HOME_FOLDER, UserProfileUpdateAllSetHomeFolder.class);

    taskMap.put(SEARCH_REGENERATE_INDEX, SearchRegenerateIndex.class);
    taskMap.put(RULES_REGENERATE_INDEX, RulesRegenerateIndex.class);
    taskMap.put(IMPEX_EXPORT_ALL, ImpexExportAll.class);
    taskMap.put(IMPEX_IMPORT_ALL, ImpexImportAll.class);
    taskMap.put(IMPEX_IMPORT_FLAT_FOLDER, ImpexImportFlatFolder.class);

    taskMap.put(SYSTEM_RESET, SystemReset.class);
  }

  public static Set<String> getTaskKeys() {
    return taskMap.keySet();
  }

  public static Class<? extends ICedarAdminTask> getTaskClassForKey(String key) {
    return taskMap.get(key);
  }

  public static ICedarAdminTask getTaskForKey(String key) throws IllegalAccessException, InstantiationException,
      NoSuchMethodException, InvocationTargetException {
    Constructor<? extends ICedarAdminTask> declaredConstructor = getTaskClassForKey(key).getDeclaredConstructor();
    return declaredConstructor.newInstance();
  }
}
