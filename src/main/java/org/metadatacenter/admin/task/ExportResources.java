package org.metadatacenter.admin.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import org.metadatacenter.admin.task.importexport.ImportExportConstants;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.model.folderserver.CedarFSFolder;
import org.metadatacenter.model.folderserver.CedarFSNode;
import org.metadatacenter.server.neo4j.Neo4JUserSession;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.service.TemplateElementService;
import org.metadatacenter.server.service.TemplateInstanceService;
import org.metadatacenter.server.service.TemplateService;
import org.metadatacenter.server.service.UserService;
import org.metadatacenter.server.service.mongodb.TemplateElementServiceMongoDB;
import org.metadatacenter.server.service.mongodb.TemplateInstanceServiceMongoDB;
import org.metadatacenter.server.service.mongodb.TemplateServiceMongoDB;
import org.metadatacenter.util.CedarUserNameUtil;
import org.metadatacenter.util.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ExportResources extends AbstractNeo4JAccessTask {

  public static final String DEFAULT_SORT = "name";
  private static final int EXPORT_MAX_COUNT = 10000;

  private Neo4JUserSession adminNeo4JSession;
  private ObjectMapper prettyMapper;
  private List<CedarNodeType> nodeTypeList;
  private List<String> sortList;
  private static TemplateService<String, JsonNode> templateService;
  private static TemplateElementService<String, JsonNode> templateElementService;
  private static TemplateInstanceService<String, JsonNode> templateInstanceService;
  private static UserService userService;


  public ExportResources() {
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

    templateElementService = new TemplateElementServiceMongoDB(
        cedarConfig.getMongoConfig().getDatabaseName(),
        cedarConfig.getMongoCollectionName(CedarNodeType.ELEMENT));

    templateService = new TemplateServiceMongoDB(
        cedarConfig.getMongoConfig().getDatabaseName(),
        cedarConfig.getMongoCollectionName(CedarNodeType.TEMPLATE),
        templateElementService);

    templateInstanceService = new TemplateInstanceServiceMongoDB(
        cedarConfig.getMongoConfig().getDatabaseName(),
        cedarConfig.getMongoCollectionName(CedarNodeType.INSTANCE));

    userService = getUserService();

    adminNeo4JSession = buildCedarAdminNeo4JSession(cedarConfig, false);

    String rootPath = adminNeo4JSession.getRootPath();
    CedarFSFolder rootFolder = adminNeo4JSession.findFolderByPath(rootPath);

    out.info("Exporting resources");
    Path resourceExportPath = Paths.get(exportDir).resolve("resources");
    serializeAndWalkFolder(resourceExportPath, rootFolder);

    out.info("Exporting users");
    Path userExportPath = Paths.get(exportDir).resolve("users");
    serializeUsers(userExportPath);

    return 0;
  }


  private void serializeAndWalkFolder(Path path, CedarFSNode node) {
    if (node instanceof CedarFSFolder) {
      CedarFSFolder folder = (CedarFSFolder) node;
      String id = folder.getId();
      Path createdFolder = createFolder(path, id);
      createFolderDescriptor(createdFolder, folder);
      List<CedarFSNode> folderContents = adminNeo4JSession.findFolderContents(id, nodeTypeList, EXPORT_MAX_COUNT, 0,
          sortList);
      for (CedarFSNode child : folderContents) {
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

  private void createFolderDescriptor(Path path, CedarFSFolder folder) {
    Path folderInfo = path.resolve(ImportExportConstants.FOLDER_INFO);
    try {
      Files.write(folderInfo, prettyMapper.writeValueAsString(folder).getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      out.error("There was an error writing the info for folder info: " + folderInfo, e);
    }
  }

  private void serializeResource(Path path, CedarFSNode node) {
    String id = node.getId();
    CedarNodeType nodeType = node.getType();
    String uuid = adminNeo4JSession.getResourceUUID(id, nodeType);
    String infoName = uuid + ImportExportConstants.INFO_SUFFIX;
    Path createdInfoFile = path.resolve(infoName);
    try {
      Files.write(createdInfoFile, prettyMapper.writeValueAsString(node).getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      out.error("There was an error writing the info for: " + nodeType + ":" + id, e);
    }
    JsonNode jsonNode = getTemplateServerContent(id, nodeType);
    if (jsonNode != null) {
      String name = uuid + ImportExportConstants.CONTENT_SUFFIX;
      Path createdFile = path.resolve(name);
      try {
        Files.write(createdFile, prettyMapper.writeValueAsString(jsonNode).getBytes(StandardCharsets.UTF_8));
      } catch (IOException e) {
        out.error("There was an error writing the content for: " + nodeType + ":" + id, e);
      }
    }
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
      List<CedarUser> all = userService.findAll();
      out.info("Returned user count:" + all.size());
      for (CedarUser u : all) {
        String uuid = u.getId();
        String contentName = uuid + ImportExportConstants.CONTENT_SUFFIX;
        Path createdContentFile = path.resolve(contentName);
        try {
          String s = prettyMapper.writeValueAsString(JsonMapper.MAPPER.valueToTree(u));
          Files.write(createdContentFile, s.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
          out.error("There was an error writing the info for user: " + uuid + ":" + CedarUserNameUtil.getDisplayName(u), e);
        }
      }
    } catch (IOException | ProcessingException e) {
      out.error(e);
    }
  }


}