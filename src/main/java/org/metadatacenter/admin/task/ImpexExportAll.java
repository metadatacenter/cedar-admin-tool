package org.metadatacenter.admin.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import com.mongodb.MongoClient;
import org.metadatacenter.admin.task.importexport.ImportExportConstants;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.MongoConfig;
import org.metadatacenter.exception.security.CedarAccessException;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.model.folderserver.*;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.GraphServiceSession;
import org.metadatacenter.server.GroupServiceSession;
import org.metadatacenter.server.UserServiceSession;
import org.metadatacenter.server.jsonld.LinkedDataUtil;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.service.TemplateElementService;
import org.metadatacenter.server.service.TemplateInstanceService;
import org.metadatacenter.server.service.TemplateService;
import org.metadatacenter.server.service.UserService;
import org.metadatacenter.server.service.mongodb.TemplateElementServiceMongoDB;
import org.metadatacenter.server.service.mongodb.TemplateInstanceServiceMongoDB;
import org.metadatacenter.server.service.mongodb.TemplateServiceMongoDB;
import org.metadatacenter.util.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ImpexExportAll extends AbstractNeo4JAccessTask {

  public static final String DEFAULT_SORT = "name";
  private static final int EXPORT_MAX_COUNT = 1000000;

  private FolderServiceSession workspaceFolderSession;
  private UserServiceSession workspaceUserSession;
  private GroupServiceSession workspaceGroupSession;
  private GraphServiceSession workspaceGraphSession;
  private ObjectMapper prettyMapper;
  private List<CedarNodeType> nodeTypeList;
  private List<String> sortList;
  private static TemplateService<String, JsonNode> templateService;
  private static TemplateElementService<String, JsonNode> templateElementService;
  private static TemplateInstanceService<String, JsonNode> templateInstanceService;
  private static UserService userService;
  private LinkedDataUtil linkedDataUtil;


  public ImpexExportAll() {
    description.add("Exports folders, resources, users into a directory structure");
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

    try {
      workspaceFolderSession = createCedarFolderSession(cedarConfig);
      workspaceUserSession = createCedarUserSession(cedarConfig);
      workspaceGroupSession = createCedarGroupSession(cedarConfig);
      workspaceGraphSession = createCedarGraphSession(cedarConfig);
    } catch (CedarAccessException e) {
      e.printStackTrace();
      return -1;
    }

    linkedDataUtil = cedarConfig.getLinkedDataUtil();


    out.info("Exporting users");
    Path userExportPath = Paths.get(exportDir).resolve("users");
    serializeUsers(userExportPath);

    out.info("Exporting groups");
    Path groupExportPath = Paths.get(exportDir).resolve("groups");
    serializeGroups(groupExportPath);

    String rootPath = workspaceFolderSession.getRootPath();
    FolderServerFolder rootFolder = workspaceFolderSession.findFolderByPath(rootPath);

    out.info("Exporting resources");
    Path resourceExportPath = Paths.get(exportDir).resolve("resources");
    serializeAndWalkFolder(resourceExportPath, rootFolder);

    return 0;
  }


  private void serializeAndWalkFolder(Path path, FolderServerNode node) {
    if (node instanceof FolderServerFolder) {
      FolderServerFolder folder = (FolderServerFolder) node;
      String id = folder.getId();
      String uuid = linkedDataUtil.getUUID(id, CedarNodeType.FOLDER);
      Path createdFolder = createFolder(path, uuid);
      serializeFolder(path, id, uuid, folder);
      List<FolderServerNode> folderContents = workspaceFolderSession.findFolderContentsFiltered(id, nodeTypeList,
          EXPORT_MAX_COUNT, 0, sortList);
      for (FolderServerNode child : folderContents) {
        serializeAndWalkFolder(createdFolder, child);
      }
    } else {
      serializeResource(path, node);
    }
  }

  private Path createFolder(Path path, String name) {
    Path newFolder = path.resolve(name);
    newFolder.toFile().mkdirs();
    return newFolder;
  }

  private void serializeFolder(Path path, String id, String uuid, FolderServerFolder folder) {
    saveJsonExport(path, uuid, ImportExportConstants.FOLDER_NODE_SUFFIX, folder);

    List<FolderServerArc> outgoingArcs = workspaceGraphSession.getOutgoingArcs(id);
    saveJsonExport(path, uuid, ImportExportConstants.OUTGOING_SUFFIX, outgoingArcs);

    List<FolderServerArc> incomingArcs = workspaceGraphSession.getIncomingArcs(id);
    saveJsonExport(path, uuid, ImportExportConstants.INCOMING_SUFFIX, incomingArcs);
  }

  private void serializeResource(Path path, FolderServerNode node) {
    String id = node.getId();
    CedarNodeType nodeType = node.getType();
    String uuid = linkedDataUtil.getUUID(id, nodeType);

    saveJsonExport(path, uuid, ImportExportConstants.NODE_SUFFIX, node);

    JsonNode jsonNode = getTemplateServerContent(id, nodeType);
    saveJsonExport(path, uuid, ImportExportConstants.CONTENT_SUFFIX, jsonNode);

    List<FolderServerArc> outgoingArcs = workspaceGraphSession.getOutgoingArcs(id);
    saveJsonExport(path, uuid, ImportExportConstants.OUTGOING_SUFFIX, outgoingArcs);

    List<FolderServerArc> incomingArcs = workspaceGraphSession.getIncomingArcs(id);
    saveJsonExport(path, uuid, ImportExportConstants.INCOMING_SUFFIX, incomingArcs);
  }

  private JsonNode getTemplateServerContent(String id, CedarNodeType nodeType) {
    JsonNode response = null;
    try {
      if (nodeType == CedarNodeType.ELEMENT) {
        response = templateElementService.findTemplateElement(id);
      } else if (nodeType == CedarNodeType.TEMPLATE) {
        response = templateService.findTemplate(id);
      } else if (nodeType == CedarNodeType.INSTANCE) {
        response = templateInstanceService.findTemplateInstance(id);
      }
    } catch (IOException | ProcessingException e) {
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
    } catch (IOException | ProcessingException e) {
      out.error(e);
    }
  }

  private void serializeUser(Path path, CedarUser u) {
    String id = u.getId();
    String uuid = linkedDataUtil.getUUID(id, CedarNodeType.USER);
    saveJsonExport(path, uuid, ImportExportConstants.CONTENT_SUFFIX, u);

    FolderServerUser workspaceUser = workspaceUserSession.getUser(id);
    saveJsonExport(path, uuid, ImportExportConstants.NODE_SUFFIX, workspaceUser);

    List<FolderServerArc> outgoingArcs = workspaceGraphSession.getOutgoingArcs(id);
    saveJsonExport(path, uuid, ImportExportConstants.OUTGOING_SUFFIX, outgoingArcs);

    List<FolderServerArc> incomingArcs = workspaceGraphSession.getIncomingArcs(id);
    saveJsonExport(path, uuid, ImportExportConstants.INCOMING_SUFFIX, incomingArcs);
  }

  private void serializeGroups(Path path) {
    path.toFile().mkdirs();
    List<FolderServerGroup> groups = workspaceGroupSession.findGroups();
    out.info("Group count:" + groups.size());
    for (FolderServerGroup g : groups) {
      serializeGroup(path, g);
    }
  }

  private void serializeGroup(Path path, FolderServerGroup g) {
    String id = g.getId();
    String uuid = linkedDataUtil.getUUID(id, CedarNodeType.GROUP);
    saveJsonExport(path, uuid, ImportExportConstants.NODE_SUFFIX, g);

    List<FolderServerArc> outgoingArcs = workspaceGraphSession.getOutgoingArcs(id);
    saveJsonExport(path, uuid, ImportExportConstants.OUTGOING_SUFFIX, outgoingArcs);

    List<FolderServerArc> incomingArcs = workspaceGraphSession.getIncomingArcs(id);
    saveJsonExport(path, uuid, ImportExportConstants.INCOMING_SUFFIX, incomingArcs);
  }

  private void saveJsonExport(Path homePath, String uuid, String suffix, Object o) {
    Path wrapperDir = homePath.resolve(uuid);
    wrapperDir.toFile().mkdirs();
    String fileName = uuid + suffix;
    Path createdContentFile = wrapperDir.resolve(fileName);
    try {
      String s = prettyMapper.writeValueAsString(JsonMapper.MAPPER.valueToTree(o));
      Files.write(createdContentFile, s.getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      out.error("There was an error writing " + fileName, e);
    }
  }


}