package org.metadatacenter.admin.task;

import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import org.bson.BsonDocument;
import org.bson.BsonString;
import org.bson.Document;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.bridge.GraphDbPermissionReader;
import org.metadatacenter.constant.LinkedData;
import org.metadatacenter.exception.CedarException;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.currentuserpermissions.FolderServerFolderCurrentUserReport;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.ResourcePermissionServiceSession;
import org.metadatacenter.server.service.UserService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FolderPurgeContent extends AbstractNeo4JAccessTask {

  public FolderPurgeContent() {
    description.add("Enumerates all resources in the folder with the id passed as parameter");
    description.add("Deletes all resources in that folder from Neo4j");
    description.add("Deletes all documents from MongoDB with the enumerated ids");
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
      out.info("$ cedarat folder-purgeContent https://repo.metadatacenter" +
          ".org/folders/88888888-4444-4444-4444-121212121212");
      return -1;
    }
    String folderId = arguments.get(1);

    UserService userService = getNeoUserService();
    CedarRequestContext cedarRequestContext = CedarRequestContextFactory.fromAdminUser(cedarConfig, userService);
    FolderServiceSession folderSession = CedarDataServices.getFolderServiceSession(cedarRequestContext);
    ResourcePermissionServiceSession permissionSession =
        CedarDataServices.getResourcePermissionServiceSession(cedarRequestContext);

    MongoClient mongoClient = CedarDataServices.getMongoClientFactoryForDocuments().getClient();

    FolderServerFolder folder = folderSession.findFolderById(folderId);
    if (folder == null) {
      out.error("Parent folder not found by id: '" + folderId + "'");
      return -2;
    }

    FolderServerFolderCurrentUserReport folderReport = null;
    try {
      folderReport = GraphDbPermissionReader.getFolderCurrentUserReport(cedarRequestContext, folderSession,
          permissionSession, folderId);
    } catch (CedarException e) {
      out.error("Error reading folder report");
      return -3;
    }

    out.info("Parent folder found");
    out.info("Name: " + folder.getName());
    out.info("Path: " + folderReport.getPath());

    Map<CedarResourceType, MongoCollection<Document>> collectionMap = new HashMap<>();
    collectionMap.put(CedarResourceType.FIELD,
        mongoClient.getDatabase(mongoDatabaseName).getCollection(templateFieldsCollectionName));
    collectionMap.put(CedarResourceType.ELEMENT,
        mongoClient.getDatabase(mongoDatabaseName).getCollection(templateElementsCollectionName));
    collectionMap.put(CedarResourceType.TEMPLATE,
        mongoClient.getDatabase(mongoDatabaseName).getCollection(templatesCollectionName));
    collectionMap.put(CedarResourceType.INSTANCE,
        mongoClient.getDatabase(mongoDatabaseName).getCollection(templateInstancesCollectionName));

    List<FileSystemResource> allChildArtifacts = folderSession.findAllChildArtifactsOfFolder(folderId);


    int i = 0;
    int neoCount = 0;
    int mongoCount = 0;
    int totalCount = allChildArtifacts.size();

    out.info("Folder child artifact count: " + totalCount);

    for (FileSystemResource r : allChildArtifacts) {
      String id = r.getId();
      boolean deletedFromNeo = folderSession.deleteResourceById(id);
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
        out.info("Deleted: " + i + " / " + totalCount);
      }

    }

    out.info("Deleted: " + i + " / " + totalCount);
    out.info("\n");
    out.info("-- Final report --");
    out.info("Found in folder using Neo4j: " + totalCount);
    out.info("Deleted from Neo4j: " + neoCount);
    out.info("Deleted from MongoDB: " + mongoCount);
    out.info("\n");
    out.warn("You should regenerate the search index using 'cedarat search-regenerateIndex' !!!");

    return 0;
  }
}
