package org.metadatacenter.admin.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mongodb.client.MongoClient;
import org.metadatacenter.admin.task.importexport.ImportExportConstants;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.MongoConfig;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.id.CedarGroupId;
import org.metadatacenter.id.CedarResourceId;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.folderserver.FolderServerArc;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.basic.FolderServerGroup;
import org.metadatacenter.model.folderserver.basic.FolderServerUser;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.GraphServiceSession;
import org.metadatacenter.server.GroupServiceSession;
import org.metadatacenter.server.UserServiceSession;
import org.metadatacenter.server.jsonld.LinkedDataUtil;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.ResourcePublicationStatusFilter;
import org.metadatacenter.server.security.model.user.ResourceVersionFilter;
import org.metadatacenter.server.service.*;
import org.metadatacenter.server.service.mongodb.TemplateElementServiceMongoDB;
import org.metadatacenter.server.service.mongodb.TemplateFieldServiceMongoDB;
import org.metadatacenter.server.service.mongodb.TemplateInstanceServiceMongoDB;
import org.metadatacenter.server.service.mongodb.TemplateServiceMongoDB;
import org.metadatacenter.util.json.JsonMapper;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class ImpexExportAll extends AbstractNeo4JAccessTask {

  public static final String DEFAULT_SORT = "name";
  private static final int EXPORT_MAX_COUNT = 1000000;
  private static final int LOG_BY = 100;

  private FolderServiceSession neo4jFolderSession;
  private UserServiceSession neo4jUserSession;
  private GroupServiceSession neo4jGroupSession;
  private GraphServiceSession neo4jGraphSession;
  private ObjectMapper prettyMapper;
  private List<CedarResourceType> resourceTypeList;
  private List<String> sortList;
  private static TemplateFieldService<String, JsonNode> templateFieldService;
  private static TemplateElementService<String, JsonNode> templateElementService;
  private static TemplateService<String, JsonNode> templateService;
  private static TemplateInstanceService<String, JsonNode> templateInstanceService;
  private static UserService userService;
  private LinkedDataUtil linkedDataUtil;


  public ImpexExportAll() {
    description.add("Exports folders, resources, users and groups  into a directory structure");
    description.add("The export is executed using the cedar-admin user");
    description.add("The export target is the $CEDAR_HOME/export folder");
  }

  @Override
  public void init() {
    super.init();
  }

  @Override
  public int execute() {
    String exportDir = cedarConfig.getImportExportConfig().getExportDir();
    out.println("Export dir:=>" + exportDir + "<=");

    prettyMapper = new ObjectMapper();
    prettyMapper.enable(SerializationFeature.INDENT_OUTPUT);

    resourceTypeList = new ArrayList<>();
    resourceTypeList.add(CedarResourceType.FOLDER);
    resourceTypeList.add(CedarResourceType.FIELD);
    resourceTypeList.add(CedarResourceType.ELEMENT);
    resourceTypeList.add(CedarResourceType.TEMPLATE);
    resourceTypeList.add(CedarResourceType.INSTANCE);

    sortList = new ArrayList<>();
    sortList.add(DEFAULT_SORT);

    MongoClient mongoClientForDocuments = CedarDataServices.getMongoClientFactoryForDocuments().getClient();

    MongoConfig artifactServerConfig = cedarConfig.getArtifactServerConfig();

    templateFieldService = new TemplateFieldServiceMongoDB(
        mongoClientForDocuments,
        artifactServerConfig.getDatabaseName(),
        artifactServerConfig.getMongoCollectionName(CedarResourceType.FIELD));

    templateElementService = new TemplateElementServiceMongoDB(
        mongoClientForDocuments,
        artifactServerConfig.getDatabaseName(),
        artifactServerConfig.getMongoCollectionName(CedarResourceType.ELEMENT));

    templateService = new TemplateServiceMongoDB(
        mongoClientForDocuments,
        artifactServerConfig.getDatabaseName(),
        artifactServerConfig.getMongoCollectionName(CedarResourceType.TEMPLATE));

    templateInstanceService = new TemplateInstanceServiceMongoDB(
        mongoClientForDocuments,
        artifactServerConfig.getDatabaseName(),
        artifactServerConfig.getMongoCollectionName(CedarResourceType.INSTANCE));

    userService = getNeoUserService();

    neo4jFolderSession = createCedarFolderSession(cedarConfig);
    neo4jUserSession = createCedarUserSession(cedarConfig);
    neo4jGroupSession = createCedarGroupSession(cedarConfig);
    neo4jGraphSession = createCedarGraphSession(cedarConfig);

    linkedDataUtil = cedarConfig.getLinkedDataUtil();


    out.info("Exporting users");
    Path exportPath = Paths.get(exportDir);
    Path userExportPath = exportPath.resolve("users");
    serializeUsers(userExportPath);

    out.info("Exporting groups");
    Path groupExportPath = exportPath.resolve("groups");
    serializeGroups(groupExportPath);

    String rootPath = neo4jFolderSession.getRootPath();
    FolderServerFolder rootFolder = neo4jFolderSession.findFolderByPath(rootPath);

    out.info("Exporting resources");
    Path resourceExportPath = exportPath.resolve("resources");
    walkFolder(resourceExportPath, rootFolder, 0);

    return 0;
  }


  private int walkFolder(Path path, FileSystemResource node, int idx) {
    idx++;
    if (node instanceof FolderServerFolder folder) {
      Path candidateFolder = serializeFolder(path, folder, idx);
      List<FileSystemResource> folderContents = neo4jFolderSession.findFolderContentsFiltered(folder.getResourceId(), resourceTypeList,
          ResourceVersionFilter.ALL, ResourcePublicationStatusFilter.ALL, EXPORT_MAX_COUNT, 0, sortList);
      if (!folderContents.isEmpty()) {
        candidateFolder.toFile().mkdirs();
        for (FileSystemResource child : folderContents) {
          idx = walkFolder(candidateFolder, child, idx);
        }
      }
    } else {
      serializeResource(path, node, idx);
    }
    return idx;
  }

  private Path serializeFolder(Path path, FolderServerFolder folder, int idx) {
    String id = folder.getId();
    String uuid = linkedDataUtil.getUUID(id, CedarResourceType.FOLDER);
    CedarFolderId rid = CedarFolderId.build(id);

    String partition = uuid.substring(0, 2);
    Path folderDir = path.resolve(partition);
    Path wrapperDir = folderDir.resolve(uuid);
    folderDir.toFile().mkdirs();

    //List<FolderServerArc> outgoingArcs = neo4jGraphSession.getOutgoingArcs(rid);
    List<FolderServerArc> incomingArcs = neo4jGraphSession.getIncomingArcs(rid);

    Map<String, Object> contents = new HashMap<>();
    contents.put(ImportExportConstants.NODE_SUFFIX, folder);
    //contents.put(ImportExportConstants.OUTGOING_SUFFIX, outgoingArcs);
    contents.put(ImportExportConstants.INCOMING_SUFFIX, incomingArcs);

    createZippedContent(folderDir, uuid, contents);

    logProgress("folder", idx);

    return wrapperDir;
  }

  private void logProgress(String type, int idx) {
    if (idx % LOG_BY == 0) {
      out.info("Exporting " + type + ":" + idx);
    }
  }

  private void serializeResource(Path path, FileSystemResource node, int idx) {
    String id = node.getId();
    CedarResourceType resourceType = node.getType();
    String uuid = linkedDataUtil.getUUID(id, resourceType);
    CedarResourceId rid = CedarResourceId.build(id, node.getType());

    String partition = uuid.substring(0, 2);
    Path wrapperDir = path.resolve(partition);
    wrapperDir.toFile().mkdirs();

    JsonNode resource = getArtifactServerContent(id, resourceType);
    //List<FolderServerArc> outgoingArcs = neo4jGraphSession.getOutgoingArcs(rid);
    List<FolderServerArc> incomingArcs = neo4jGraphSession.getIncomingArcs(rid);

    Map<String, Object> contents = new HashMap<>();
    contents.put(ImportExportConstants.CONTENT_SUFFIX, resource);
    contents.put(ImportExportConstants.NODE_SUFFIX, node);
    //contents.put(ImportExportConstants.OUTGOING_SUFFIX, outgoingArcs);
    contents.put(ImportExportConstants.INCOMING_SUFFIX, incomingArcs);

    createZippedContent(wrapperDir, uuid, contents);

    logProgress("artifact", idx);
  }

  private JsonNode getArtifactServerContent(String id, CedarResourceType resourceType) {
    JsonNode response = null;
    try {
      if (resourceType == CedarResourceType.FIELD) {
        response = templateFieldService.findTemplateField(id);
      } else if (resourceType == CedarResourceType.ELEMENT) {
        response = templateElementService.findTemplateElement(id);
      } else if (resourceType == CedarResourceType.TEMPLATE) {
        response = templateService.findTemplate(id);
      } else if (resourceType == CedarResourceType.INSTANCE) {
        response = templateInstanceService.findTemplateInstance(id);
      }
    } catch (IOException e) {
      out.error("There was an error retrieving content for: " + resourceType + ":" + id, e);
    }
    return response;
  }


  private void serializeUsers(Path path) {
    path.toFile().mkdirs();
    List<CedarUser> users = userService.findAll();
    out.info("User count:" + users.size());
    for (CedarUser u : users) {
      serializeUser(path, u);
    }
  }

  private void serializeUser(Path path, CedarUser u) {
    String id = u.getId();
    String uuid = linkedDataUtil.getUUID(id, CedarResourceType.USER);
    CedarUserId uid = CedarUserId.build(id);

    String partition = uuid.substring(0, 2);
    Path wrapperDir = path.resolve(partition);
    wrapperDir.toFile().mkdirs();

    FolderServerUser neoUser = neo4jUserSession.getUser(u.getResourceId());
    //List<FolderServerArc> outgoingArcs = neo4jGraphSession.getOutgoingArcs(uid);
    List<FolderServerArc> incomingArcs = neo4jGraphSession.getIncomingArcs(uid);

    Map<String, Object> contents = new HashMap<>();
    contents.put(ImportExportConstants.CONTENT_SUFFIX, u);
    contents.put(ImportExportConstants.NODE_SUFFIX, neoUser);
    //contents.put(ImportExportConstants.OUTGOING_SUFFIX, outgoingArcs);
    contents.put(ImportExportConstants.INCOMING_SUFFIX, incomingArcs);

    createZippedContent(wrapperDir, uuid, contents);
  }

  private void serializeGroups(Path path) {
    path.toFile().mkdirs();
    List<FolderServerGroup> groups = neo4jGroupSession.findGroups();
    out.info("Group count:" + groups.size());
    for (FolderServerGroup g : groups) {
      serializeGroup(path, g);
    }
  }

  private void serializeGroup(Path path, FolderServerGroup g) {
    String id = g.getId();
    String uuid = linkedDataUtil.getUUID(id, CedarResourceType.GROUP);
    CedarGroupId gid = CedarGroupId.build(id);

    String partition = uuid.substring(0, 2);
    Path wrapperDir = path.resolve(partition);
    wrapperDir.toFile().mkdirs();

    //List<FolderServerArc> outgoingArcs = neo4jGraphSession.getOutgoingArcs(gid);
    List<FolderServerArc> incomingArcs = neo4jGraphSession.getIncomingArcs(gid);

    Map<String, Object> contents = new HashMap<>();
    contents.put(ImportExportConstants.NODE_SUFFIX, g);
    //contents.put(ImportExportConstants.OUTGOING_SUFFIX, outgoingArcs);
    contents.put(ImportExportConstants.INCOMING_SUFFIX, incomingArcs);

    createZippedContent(wrapperDir, uuid, contents);
  }

  private void createZippedContent(Path wrapperDir, String uuid, Map<String, Object> contents) {
    String fileName = uuid + ".zip";
    try {
      FileOutputStream fos = new FileOutputStream(wrapperDir.resolve(fileName).toFile());
      ZipOutputStream zipOut = new ZipOutputStream(fos);
      for (String suffix : contents.keySet()) {
        addZipJsonExport(zipOut, suffix, contents.get(suffix));
      }
      zipOut.close();
      fos.close();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void addZipJsonExport(ZipOutputStream zipOut, String suffix, Object o) throws IOException {
    ZipEntry zipEntry = new ZipEntry(suffix);
    zipOut.putNextEntry(zipEntry);
    String s = prettyMapper.writeValueAsString(JsonMapper.MAPPER.valueToTree(o));
    zipOut.write(s.getBytes(StandardCharsets.UTF_8));
  }

}
