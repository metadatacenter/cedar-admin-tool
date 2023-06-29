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

  public static final String ARTIFACT_SERVER_WIPE_ALL = "artifactServer-wipeAll";

  public static final String ARTIFACT_SERVER_INIT_DB = "artifactServer-initDB";

  public static final String GRAPH_DB_SERVER_WIPE_ALL = "graphDb-wipeAll";

  public static final String GRAPH_DB_SERVER_WIPE_CATEGORIES = "graphDb-wipeCategories";

  public static final String GRAPH_DB_SERVER_CREATE_INDICES = "graphDb-createIndices";

  public static final String GRAPH_DB_SERVER_CREATE_GLOBAL_OBJECTS = "graphDb-createGlobalObjects";

  public static final String GRAPH_DB_SERVER_CREATE_CADSR_OBJECTS = "graphDb-createCaDSRObjects";

  public static final String GRAPH_DB_SERVER_CREATE_ALL_USERS = "graphDb-createAllUsers";

  public static final String USER_PROFILE_GET_ADMIN = "userProfile-get-admin";

  public static final String USER_PROFILE_LIST_ALL = "userProfile-listAll";

  public static final String USER_PROFILE_UPDATE_ALL_UPDATE_PERMISSIONS = "userProfile-updateAll-updatePermissions";

  public static final String USER_PROFILE_RESET_UI_PREFERENCES = "userProfile-reset-UIPreferences";

  public static final String SEARCH_REGENERATE_INDEX = "search-regenerateIndex";

  public static final String SEARCH_GENERATE_EMPTY_INDEX = "search-generateEmptyIndex";

  public static final String RULES_REGENERATE_INDEX = "rules-regenerateIndex";

  public static final String RULES_GENERATE_EMPTY_INDEX = "rules-generateEmptyIndex";

  public static final String IMPEX_EXPORT_ALL = "impex-exportAll";

  public static final String IMPEX_IMPORT_ALL = "impex-importAll";

  public static final String IMPEX_IMPORT_FLAT_FOLDER = "impex-importFlatFolder";

  public static final String FOLDER_PURGE_CONTENT = "folder-purgeContent";

  public static final String ENV_LIST_FOR = "env-listFor";

  public static final String SYSTEM_RESET = "system-reset";

  static {
    taskMap = new LinkedHashMap<>();
    taskMap.put(ARTIFACT_SERVER_WIPE_ALL, ArtifactServerWipeAll.class);
    taskMap.put(ARTIFACT_SERVER_INIT_DB, ArtifactServerInitDB.class);

    taskMap.put(GRAPH_DB_SERVER_WIPE_ALL, GraphDbWipeAll.class);
    taskMap.put(GRAPH_DB_SERVER_WIPE_CATEGORIES, GraphDbWipeCategories.class);
    taskMap.put(GRAPH_DB_SERVER_CREATE_INDICES, GraphDbCreateIndicesAndConstraints.class);
    taskMap.put(GRAPH_DB_SERVER_CREATE_GLOBAL_OBJECTS, GraphDbCreateGlobalObjects.class);
    taskMap.put(GRAPH_DB_SERVER_CREATE_CADSR_OBJECTS, GraphDbCreateCaDSRObjects.class);
    taskMap.put(GRAPH_DB_SERVER_CREATE_ALL_USERS, GraphDbCreateAllUsers.class);

    taskMap.put(USER_PROFILE_GET_ADMIN, UserProfileGetAdmin.class);
    taskMap.put(USER_PROFILE_LIST_ALL, UserProfileListAll.class);
    taskMap.put(USER_PROFILE_UPDATE_ALL_UPDATE_PERMISSIONS, UserProfileUpdateAllUpdatePermissions.class);
    taskMap.put(USER_PROFILE_RESET_UI_PREFERENCES, UserProfileResetUIPreferences.class);

    taskMap.put(SEARCH_REGENERATE_INDEX, SearchRegenerateIndex.class);
    taskMap.put(SEARCH_GENERATE_EMPTY_INDEX, SearchGenerateEmptyIndex.class);
    taskMap.put(RULES_REGENERATE_INDEX, RulesRegenerateIndex.class);
    taskMap.put(RULES_GENERATE_EMPTY_INDEX, RulesGenerateEmptyIndex.class);
    taskMap.put(IMPEX_EXPORT_ALL, ImpexExportAll.class);
    taskMap.put(IMPEX_IMPORT_ALL, ImpexImportAll.class);
    taskMap.put(IMPEX_IMPORT_FLAT_FOLDER, ImpexImportFlatFolder.class);

    taskMap.put(FOLDER_PURGE_CONTENT, FolderPurgeContent.class);

    taskMap.put(ENV_LIST_FOR, EnvListFor.class);

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
