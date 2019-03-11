package org.metadatacenter.admin.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mongodb.MongoClient;
import org.metadatacenter.admin.task.importexport.ImportExportConstants;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.MongoConfig;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.model.folderserver.*;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.basic.FolderServerGroup;
import org.metadatacenter.model.folderserver.basic.FolderServerNode;
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
  private List<CedarNodeType> nodeTypeList;
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
  }

  @Override
  public int execute() {
    String exportDir = cedarConfig.getImportExportConfig().getExportDir();
    out.println("Export dir:=>" + exportDir + "<=");

    prettyMapper = new ObjectMapper();
    prettyMapper.enable(SerializationFeature.INDENT_OUTPUT);

    nodeTypeList = new ArrayList<>();
    nodeTypeList.add(CedarNodeType.FOLDER);
    nodeTypeList.add(CedarNodeType.FIELD);
    nodeTypeList.add(CedarNodeType.ELEMENT);
    nodeTypeList.add(CedarNodeType.TEMPLATE);
    nodeTypeList.add(CedarNodeType.INSTANCE);

    sortList = new ArrayList<>();
    sortList.add(DEFAULT_SORT);

    MongoClient mongoClientForDocuments = CedarDataServices.getMongoClientFactoryForDocuments().getClient();

    MongoConfig templateServerConfig = cedarConfig.getTemplateServerConfig();

    templateFieldService = new TemplateFieldServiceMongoDB(
        mongoClientForDocuments,
        templateServerConfig.getDatabaseName(),
        templateServerConfig.getMongoCollectionName(CedarNodeType.FIELD));

    templateElementService = new TemplateElementServiceMongoDB(
        mongoClientForDocuments,
        templateServerConfig.getDatabaseName(),
        templateServerConfig.getMongoCollectionName(CedarNodeType.ELEMENT));

    templateService = new TemplateServiceMongoDB(
        mongoClientForDocuments,
        templateServerConfig.getDatabaseName(),
        templateServerConfig.getMongoCollectionName(CedarNodeType.TEMPLATE));

    templateInstanceService = new TemplateInstanceServiceMongoDB(
        mongoClientForDocuments,
        templateServerConfig.getDatabaseName(),
        templateServerConfig.getMongoCollectionName(CedarNodeType.INSTANCE));

    userService = getUserService();

    neo4jFolderSession = createCedarFolderSession(cedarConfig);
    neo4jUserSession = createCedarUserSession(cedarConfig);
    neo4jGroupSession = createCedarGroupSession(cedarConfig);
    neo4jGraphSession = createCedarGraphSession(cedarConfig);

    linkedDataUtil = cedarConfig.getLinkedDataUtil();


    out.info("Exporting users");
    Path userExportPath = Paths.get(exportDir).resolve("users");
    serializeUsers(userExportPath);

    out.info("Exporting groups");
    Path groupExportPath = Paths.get(exportDir).resolve("groups");
    serializeGroups(groupExportPath);

    String rootPath = neo4jFolderSession.getRootPath();
    FolderServerFolder rootFolder = neo4jFolderSession.findFolderByPath(rootPath);

    out.info("Exporting resources");
    Path resourceExportPath = Paths.get(exportDir).resolve("resources");
    walkFolder(resourceExportPath, rootFolder, 0);

    return 0;
  }


  private int walkFolder(Path path, FolderServerNode node, int idx) {
    idx++;
    if (node instanceof FolderServerFolder) {
      FolderServerFolder folder = (FolderServerFolder) node;
      Path candidateFolder = serializeFolder(path, folder, idx);
      List<FolderServerNode> folderContents = neo4jFolderSession.findFolderContentsFiltered(folder.getId(),
          nodeTypeList, ResourceVersionFilter.ALL, ResourcePublicationStatusFilter.ALL, EXPORT_MAX_COUNT, 0, sortList);
      if (!folderContents.isEmpty()) {
        candidateFolder.toFile().mkdirs();
        for (FolderServerNode child : folderContents) {
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
    String uuid = linkedDataUtil.getUUID(id, CedarNodeType.FOLDER);

    String partition = uuid.substring(0, 2);
    Path folderDir = path.resolve(partition);
    Path wrapperDir = folderDir.resolve(uuid);
    folderDir.toFile().mkdirs();

    List<FolderServerArc> outgoingArcs = neo4jGraphSession.getOutgoingArcs(id);
    List<FolderServerArc> incomingArcs = neo4jGraphSession.getIncomingArcs(id);

    Map<String, Object> contents = new HashMap<>();
    contents.put(ImportExportConstants.NODE_SUFFIX, folder);
    contents.put(ImportExportConstants.OUTGOING_SUFFIX, outgoingArcs);
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

  private void serializeResource(Path path, FolderServerNode node, int idx) {
    String id = node.getId();
    CedarNodeType nodeType = node.getType();
    String uuid = linkedDataUtil.getUUID(id, nodeType);

    String partition = uuid.substring(0, 2);
    Path wrapperDir = path.resolve(partition);
    wrapperDir.toFile().mkdirs();

    JsonNode resource = getTemplateServerContent(id, nodeType);
    List<FolderServerArc> outgoingArcs = neo4jGraphSession.getOutgoingArcs(id);
    List<FolderServerArc> incomingArcs = neo4jGraphSession.getIncomingArcs(id);

    Map<String, Object> contents = new HashMap<>();
    contents.put(ImportExportConstants.CONTENT_SUFFIX, resource);
    contents.put(ImportExportConstants.NODE_SUFFIX, node);
    contents.put(ImportExportConstants.OUTGOING_SUFFIX, outgoingArcs);
    contents.put(ImportExportConstants.INCOMING_SUFFIX, incomingArcs);

    createZippedContent(wrapperDir, uuid, contents);

    logProgress("resource", idx);
  }

  private JsonNode getTemplateServerContent(String id, CedarNodeType nodeType) {
    JsonNode response = null;
    try {
      if (nodeType == CedarNodeType.FIELD) {
        response = templateFieldService.findTemplateField(id);
      } else if (nodeType == CedarNodeType.ELEMENT) {
        response = templateElementService.findTemplateElement(id);
      } else if (nodeType == CedarNodeType.TEMPLATE) {
        response = templateService.findTemplate(id);
      } else if (nodeType == CedarNodeType.INSTANCE) {
        response = templateInstanceService.findTemplateInstance(id);
      }
    } catch (IOException e) {
      out.error("There was an error retrieving content for: " + nodeType + ":" + id, e);
    }
    return response;
  }


  private void serializeUsers(Path path) {
    try {
      path.toFile().mkdirs();
      List<CedarUser> users = userService.findAll();
      out.info("User count:" + users.size());
      for (CedarUser u : users) {
        serializeUser(path, u);
      }
    } catch (IOException e) {
      out.error(e);
    }
  }

  private void serializeUser(Path path, CedarUser u) {
    String id = u.getId();
    String uuid = linkedDataUtil.getUUID(id, CedarNodeType.USER);

    String partition = uuid.substring(0, 2);
    Path wrapperDir = path.resolve(partition);
    wrapperDir.toFile().mkdirs();

    FolderServerUser neoUser = neo4jUserSession.getUser(id);
    List<FolderServerArc> outgoingArcs = neo4jGraphSession.getOutgoingArcs(id);
    List<FolderServerArc> incomingArcs = neo4jGraphSession.getIncomingArcs(id);

    Map<String, Object> contents = new HashMap<>();
    contents.put(ImportExportConstants.CONTENT_SUFFIX, u);
    contents.put(ImportExportConstants.NODE_SUFFIX, neoUser);
    contents.put(ImportExportConstants.OUTGOING_SUFFIX, outgoingArcs);
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
    String uuid = linkedDataUtil.getUUID(id, CedarNodeType.GROUP);

    String partition = uuid.substring(0, 2);
    Path wrapperDir = path.resolve(partition);
    wrapperDir.toFile().mkdirs();

    List<FolderServerArc> outgoingArcs = neo4jGraphSession.getOutgoingArcs(id);
    List<FolderServerArc> incomingArcs = neo4jGraphSession.getIncomingArcs(id);

    Map<String, Object> contents = new HashMap<>();
    contents.put(ImportExportConstants.NODE_SUFFIX, g);
    contents.put(ImportExportConstants.OUTGOING_SUFFIX, outgoingArcs);
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
