package org.metadatacenter.admin.task;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.Document;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.bridge.GraphDbPermissionReader;
import org.metadatacenter.config.FolderStructureConfig;
import org.metadatacenter.constant.LinkedData;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.id.CedarArtifactId;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.currentuserpermissions.FolderServerFolderCurrentUserReport;
import org.metadatacenter.model.folderserver.extract.FolderServerResourceExtract;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.ResourcePermissionServiceSession;
import org.metadatacenter.server.service.UserService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class FolderPurgeContent extends AbstractNeo4JAccessTask {

  public FolderPurgeContent() {
    description.add("Enumerates the artifacts and subfolders in the folder with the id passed as parameter, at any depth");
    description.add("Deletes those artifacts from Neo4j and their documents from MongoDB");
    description.add("Deletes the subfolders from Neo4j, deepest first, and keeps the folder itself");
    description.add("Refuses a root, system or special folder, and a folder holding a user's home folder at any depth");
    description.add("*** A reindex should be run manually after this task");
  }

  @Override
  public void init() {
    super.init();
    initMongoCollectionNames();
  }

  @Override
  public int execute() {
    if (arguments.size() != 2) {
      out.error("A folderId parameter must be passed for this task:");
      out.info("Usage:");
      out.info("$ cedarat folder-purgeContent https://repo.metadatacenter.org/folders/88888888-4444-4444-4444-121212121212");
      return -1;
    }
    String folderId = arguments.get(1);
    CedarFolderId fid = CedarFolderId.build(folderId);

    UserService userService = getNeoUserService();
    CedarRequestContext cedarRequestContext = CedarRequestContextFactory.fromAdminUser(cedarConfig, userService);
    FolderServiceSession folderSession = CedarDataServices.getInstance().getFolderServiceSession(cedarRequestContext);
    ResourcePermissionServiceSession permissionSession = CedarDataServices.getInstance().getResourcePermissionServiceSession(cedarRequestContext);

    MongoClient mongoClient = CedarDataServices.getInstance().getMongoClientFactoryForDocuments().getClient();

    FolderServerFolder folder = folderSession.findFolderById(fid);
    if (folder == null) {
      out.error("Parent folder not found by id: '" + folderId + "'");
      return -2;
    }

    FolderServerFolderCurrentUserReport folderReport = null;
    try {
      folderReport = GraphDbPermissionReader.getFolderCurrentUserReport(cedarRequestContext, folderSession, permissionSession, fid);
    } catch (CedarException e) {
      out.error("Error reading folder report");
      return -3;
    }

    out.info("Parent folder found");
    out.info("Name: " + folder.getName());
    out.info("Path: " + folderReport.getPath());

    List<FileSystemResource> artifacts = new ArrayList<>();
    List<FolderServerFolder> subfolders = new ArrayList<>();
    collectSubtree(folderSession, fid, artifacts, subfolders);

    Set<String> specialFolderIds = specialFolderIds(folderSession);
    String targetRefusal = protectedFolderReason(folder, folderReport.getPath(), specialFolderIds);
    if (targetRefusal != null) {
      out.error("Refusing to purge folder '" + folder.getName() + "' (" + folderId + "): it is " + targetRefusal +
          ". Nothing was deleted.");
      return -5;
    }
    for (FolderServerFolder subfolder : subfolders) {
      String reason = subfolder.isUserHome() ? "a user's home folder" :
          protectedFolderReason(subfolder, null, specialFolderIds);
      if (reason != null) {
        out.error("Refusing to purge folder '" + folder.getName() + "' (" + folderId + "): it holds folder '" +
            subfolder.getName() + "' (" + subfolder.getId() + "), which is " + reason + ". Nothing was deleted.");
        return -5;
      }
    }
    int artifactCount = artifacts.size();
    int subfolderCount = subfolders.size();

    out.info("Artifact count in the folder and its subfolders: " + artifactCount);
    out.info("Subfolder count: " + subfolderCount);
    if (!confirmPurge(folder.getName(), folderId, artifactCount, subfolderCount)) {
      return -4;
    }

    Map<CedarResourceType, MongoCollection<Document>> collectionMap = new HashMap<>();
    collectionMap.put(CedarResourceType.FIELD, mongoClient.getDatabase(mongoDatabaseName).getCollection(templateFieldsCollectionName));
    collectionMap.put(CedarResourceType.ELEMENT, mongoClient.getDatabase(mongoDatabaseName).getCollection(templateElementsCollectionName));
    collectionMap.put(CedarResourceType.TEMPLATE, mongoClient.getDatabase(mongoDatabaseName).getCollection(templatesCollectionName));
    collectionMap.put(CedarResourceType.INSTANCE, mongoClient.getDatabase(mongoDatabaseName).getCollection(templateInstancesCollectionName));

    int i = 0;
    int neoCount = 0;
    int mongoCount = 0;

    for (FileSystemResource r : artifacts) {
      String id = r.getId();
      CedarArtifactId aid = CedarArtifactId.build(id, r.getType());
      boolean deletedFromNeo = folderSession.deleteResourceById(aid);
      if (deletedFromNeo) {
        neoCount++;
      }

      BsonDocument oneFilter = new BsonDocument();
      oneFilter.put(LinkedData.ID, new BsonString(r.getId()));
      Document deletedFromMongo = collectionMap.get(r.getType()).findOneAndDelete(oneFilter);
      if (deletedFromMongo != null) {
        mongoCount++;
      }
      i++;
      if (i % 100 == 0) {
        out.info("Deleted: " + i + " / " + artifactCount);
      }

    }

    // Each subfolder follows its own subfolders in the list, so each is empty when its turn comes.
    int folderCount = 0;
    for (FolderServerFolder subfolder : subfolders) {
      if (folderSession.deleteFolderById(subfolder.getResourceId())) {
        folderCount++;
      }
    }

    out.info("Deleted: " + i + " / " + artifactCount);
    out.info("\n");
    out.info("-- Final report --");
    out.info("Artifacts found in the folder and its subfolders using Neo4j: " + artifactCount);
    out.info("Artifacts deleted from Neo4j: " + neoCount);
    out.info("Artifacts deleted from MongoDB: " + mongoCount);
    out.info("Subfolders found using Neo4j: " + subfolderCount);
    out.info("Subfolders deleted from Neo4j: " + folderCount);
    out.info("\n");
    out.warn("You should regenerate the search index using 'cedarat search-regenerateIndex' !!!");

    return 0;
  }

  /**
   * Lists the artifacts and subfolders under the folder at any depth. Each subfolder is listed after every subfolder
   * it holds, so deleting the subfolders in list order deletes the deepest first.
   */
  private static void collectSubtree(FolderServiceSession folderSession, CedarFolderId folderId,
                                     List<FileSystemResource> artifacts, List<FolderServerFolder> subfolders) {
    for (FileSystemResource child : folderSession.findAllChildArtifactsOfFolder(folderId)) {
      if (child instanceof FolderServerFolder subfolder) {
        collectSubtree(folderSession, subfolder.getResourceId(), artifacts, subfolders);
        subfolders.add(subfolder);
      } else {
        artifacts.add(child);
      }
    }
  }

  /** The identifiers of the folders the graph marks as special, such as shared folders. */
  private static Set<String> specialFolderIds(FolderServiceSession folderSession) {
    int count = Math.toIntExact(folderSession.viewSpecialFoldersCount());
    if (count == 0) {
      return Set.of();
    }
    return folderSession.viewSpecialFolders(count, 0, List.of("name")).stream()
        .map(FolderServerResourceExtract::getId).collect(Collectors.toSet());
  }

  /**
   * Why the folder may not be purged, or null when it may. A path, when known, is also compared with the root and
   * users folders the folder structure configuration names.
   */
  private String protectedFolderReason(FolderServerFolder candidate, String path, Set<String> specialFolderIds) {
    FolderStructureConfig structure = cedarConfig.getFolderStructureConfig();
    if (candidate.isRoot() || (path != null && path.equals(structure.getRootFolder().getPath()))) {
      return "the root folder";
    }
    if (path != null && path.equals(structure.getUsersFolder().getPath())) {
      return "the users folder";
    }
    if (candidate.isSystem()) {
      return "a system folder";
    }
    if (specialFolderIds.contains(candidate.getId())) {
      return "a special folder";
    }
    return null;
  }

  protected boolean confirmPurge(String folderName, String folderId, int artifactCount, int subfolderCount) {
    return getConfirmInput("Permanently deleting " + artifactCount + " artifact(s) and " + subfolderCount +
        " subfolder(s) from folder '" + folderName + "' (" + folderId + ") in Neo4j and MongoDB...");
  }
}
