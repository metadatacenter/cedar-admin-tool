package org.metadatacenter.admin.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.github.fge.jsonschema.core.exceptions.ProcessingException;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.model.folderserver.CedarFSFolder;
import org.metadatacenter.model.folderserver.CedarFSNode;
import org.metadatacenter.server.neo4j.Neo4JUserSession;
import org.metadatacenter.server.service.TemplateElementService;
import org.metadatacenter.server.service.TemplateInstanceService;
import org.metadatacenter.server.service.TemplateService;
import org.metadatacenter.server.service.mongodb.TemplateElementServiceMongoDB;
import org.metadatacenter.server.service.mongodb.TemplateInstanceServiceMongoDB;
import org.metadatacenter.server.service.mongodb.TemplateServiceMongoDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ExportResources extends AbstractNeo4JAccessTask {

  public static final String FOLDER_INFO = "folder.info.json";
  public static final String DEFAULT_SORT = "name";
  private static final int EXPORT_MAX_COUNT = 10000;

  private CedarConfig cedarConfig;
  private Neo4JUserSession adminNeo4JSession;
  private ObjectMapper prettyMapper;
  private List<CedarNodeType> nodeTypeList;
  private List<String> sortList;
  private static TemplateService<String, JsonNode> templateService;
  private static TemplateElementService<String, JsonNode> templateElementService;
  private static TemplateInstanceService<String, JsonNode> templateInstanceService;
  private Logger logger = LoggerFactory.getLogger(ExportResources.class);

  public ExportResources() {
    description.add("Exports folders, resources, users into a directory structure");
    description.add("The export is executed using the cedar-admin user");
    description.add("The export target is the $CEDAR_HOME/export folder");
  }

  @Override
  public void init(CedarConfig cedarConfig) {
    this.cedarConfig = cedarConfig;
  }

  @Override
  public int execute() {
    String exportDir = cedarConfig.getImportExportConfig().getExportDir();
    System.out.println("Export dir:=>" + exportDir + "<=");

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
        cedarConfig.getMongoCollectionName(CedarNodeType.ELEMENT),
        cedarConfig.getLinkedDataPrefix(CedarNodeType.ELEMENT));

    templateService = new TemplateServiceMongoDB(
        cedarConfig.getMongoConfig().getDatabaseName(),
        cedarConfig.getMongoCollectionName(CedarNodeType.TEMPLATE),
        cedarConfig.getLinkedDataPrefix(CedarNodeType.TEMPLATE),
        templateElementService);

    templateInstanceService = new TemplateInstanceServiceMongoDB(
        cedarConfig.getMongoConfig().getDatabaseName(),
        cedarConfig.getMongoCollectionName(CedarNodeType.INSTANCE),
        cedarConfig.getLinkedDataPrefix(CedarNodeType.INSTANCE));


    adminNeo4JSession = buildCedarAdminNeo4JSession(cedarConfig, false);

    String rootPath = adminNeo4JSession.getRootPath();
    CedarFSFolder rootFolder = adminNeo4JSession.findFolderByPath(rootPath);

    Path exportPath = Paths.get(exportDir).resolve("resources");
    serializeAndWalkFolder(exportPath, rootFolder);
    return 0;
  }

  private void serializeAndWalkFolder(Path path, CedarFSNode node) {
    if (node instanceof CedarFSFolder) {
      CedarFSFolder folder = (CedarFSFolder) node;
      String id = folder.getId();
      String uuid = adminNeo4JSession.getFolderUUID(id);
      Path createdFolder = createFolder(path, uuid);
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
    Path folderInfo = path.resolve(FOLDER_INFO);
    try {
      Files.write(folderInfo, prettyMapper.writeValueAsString(folder).getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void serializeResource(Path path, CedarFSNode node) {
    String id = node.getId();
    CedarNodeType nodeType = node.getType();
    String uuid = adminNeo4JSession.getResourceUUID(id, nodeType);
    String infoName = uuid + ".info.json";
    Path createdInfoFile = path.resolve(infoName);
    try {
      Files.write(createdInfoFile, prettyMapper.writeValueAsString(node).getBytes(StandardCharsets.UTF_8));
    } catch (IOException e) {
      e.printStackTrace();
    }
    JsonNode jsonNode = getTemplateServerContent(id, nodeType);
    if (jsonNode != null) {
      String name = uuid + ".content.json";
      Path createdFile = path.resolve(name);
      try {
        Files.write(createdFile, prettyMapper.writeValueAsString(jsonNode).getBytes(StandardCharsets.UTF_8));
      } catch (IOException e) {
        e.printStackTrace();
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
      System.out.println("There was an error retrieving content for: " + nodeType + ":" + id);
      System.out.println(e.getMessage());
    }
    return response;
  }


}