package org.metadatacenter.admin.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.mongodb.MongoClient;
import com.mongodb.client.MongoCollection;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.FileFileFilter;
import org.bson.BsonDocument;
import org.bson.Document;
import org.metadatacenter.admin.task.importexport.ImportExportConstants;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.MongoConfig;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.model.RelationLabel;
import org.metadatacenter.model.folderserver.basic.FolderServerNode;
import org.metadatacenter.server.AdminServiceSession;
import org.metadatacenter.server.GraphServiceSession;
import org.metadatacenter.server.jsonld.LinkedDataUtil;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.service.*;
import org.metadatacenter.server.service.mongodb.TemplateElementServiceMongoDB;
import org.metadatacenter.server.service.mongodb.TemplateFieldServiceMongoDB;
import org.metadatacenter.server.service.mongodb.TemplateInstanceServiceMongoDB;
import org.metadatacenter.server.service.mongodb.TemplateServiceMongoDB;
import org.metadatacenter.util.json.JsonMapper;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

public class ImpexImportAll extends AbstractNeo4JAccessTask {

  public static final String DEFAULT_SORT = "name";

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


  public ImpexImportAll() {
    description.add("Imports usres, groups, folders and resources from  a directory structure");
    description.add("The import is executed using the cedar-admin user");
    description.add("The import source is the $CEDAR_HOME/import folder");
  }

  @Override
  public void init() {
    super.init();
  }

  @Override
  public int execute() {
    String importDir = cedarConfig.getImportExportConfig().getImportDir();
    out.println("Import dir:=>" + importDir + "<=");

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

    MongoConfig artifactServerConfig = cedarConfig.getArtifactServerConfig();

    templateFieldService = new TemplateFieldServiceMongoDB(
        mongoClientForDocuments,
        artifactServerConfig.getDatabaseName(),
        artifactServerConfig.getMongoCollectionName(CedarNodeType.FIELD));

    templateElementService = new TemplateElementServiceMongoDB(
        mongoClientForDocuments,
        artifactServerConfig.getDatabaseName(),
        artifactServerConfig.getMongoCollectionName(CedarNodeType.ELEMENT));

    templateService = new TemplateServiceMongoDB(
        mongoClientForDocuments,
        artifactServerConfig.getDatabaseName(),
        artifactServerConfig.getMongoCollectionName(CedarNodeType.TEMPLATE));

    templateInstanceService = new TemplateInstanceServiceMongoDB(
        mongoClientForDocuments,
        artifactServerConfig.getDatabaseName(),
        artifactServerConfig.getMongoCollectionName(CedarNodeType.INSTANCE));

    userService = getNeoUserService();

    linkedDataUtil = cedarConfig.getLinkedDataUtil();

    out.info("Deleting data from MongoDB");
    deleteAllMongoData();

    out.info("Importing users into Mongo");
    Path userImportPath = Paths.get(importDir).resolve("users");
    processFolder(userImportPath, this::importUserIntoMongo);

    out.info("Deleting everything from Neo4J");
    deleteAllNeo4JData();

    neo4jGraphSession = createCedarGraphSession(cedarConfig);

    out.info("Importing users into Neo4j");
    processFolder(userImportPath, this::importUserIntoNeo);

    out.info("Importing groups");
    Path groupImportPath = Paths.get(importDir).resolve("groups");
    processFolder(groupImportPath, this::importGroup);

    out.info("Importing resources");
    Path resourceImportPath = Paths.get(importDir).resolve("resources");
    processFolder(resourceImportPath, this::importResource);

    return 0;
  }

  private void deleteAllNeo4JData() {
    AdminServiceSession adminSession = createCedarAdminSession(cedarConfig);
    adminSession.wipeAllData();
  }

  private void deleteAllMongoData() {
    String mongoDatabaseNameForDocuments = cedarConfig.getArtifactServerConfig().getDatabaseName();
    MongoClient mongoClientForDocuments = CedarDataServices.getMongoClientFactoryForDocuments().getClient();

    String mongoDatabaseNameForUsers = cedarConfig.getUserServerConfig().getDatabaseName();
    MongoClient mongoClientForUsers = CedarDataServices.getMongoClientFactoryForUsers().getClient();

    String templateFieldsCollectionName = cedarConfig.getArtifactServerConfig().getCollections().get(CedarNodeType
        .FIELD.getValue());
    String templateElementsCollectionName = cedarConfig.getArtifactServerConfig().getCollections().get(CedarNodeType
        .ELEMENT.getValue());
    String templateInstancesCollectionName = cedarConfig.getArtifactServerConfig().getCollections().get(CedarNodeType
        .INSTANCE.getValue());
    String templatesCollectionName = cedarConfig.getArtifactServerConfig().getCollections().get(CedarNodeType
        .TEMPLATE.getValue());
    String usersCollectionName = cedarConfig.getUserServerConfig().getCollections().get(CedarNodeType.USER.getValue());

    emptyCollection(mongoClientForDocuments, mongoDatabaseNameForDocuments, templateFieldsCollectionName);
    emptyCollection(mongoClientForDocuments, mongoDatabaseNameForDocuments, templateElementsCollectionName);
    emptyCollection(mongoClientForDocuments, mongoDatabaseNameForDocuments, templateInstancesCollectionName);
    emptyCollection(mongoClientForDocuments, mongoDatabaseNameForDocuments, templatesCollectionName);
    emptyCollection(mongoClientForUsers, mongoDatabaseNameForUsers, usersCollectionName);
  }

  protected void emptyCollection(MongoClient client, String databaseName, String collectionName) {
    out.info("Deleting all data from collection: " + collectionName + ".");
    MongoCollection<Document> collection = client.getDatabase(databaseName).getCollection(collectionName);
    BsonDocument allFilter = new BsonDocument();
    collection.deleteMany(allFilter);
  }


  private void processFolder(Path rootFolder, Consumer<Path> function) {
    File root = rootFolder.toFile();
    File[] files = root.listFiles((FileFilter) FileFileFilter.FILE);
    if (files != null) {
      for (File file : files) {
        function.accept(file.toPath());
      }
    }
    File[] folders = root.listFiles((FileFilter) DirectoryFileFilter.DIRECTORY);
    if (folders != null) {
      for (File dir : folders) {
        processFolder(dir.toPath(), function);
      }
    }
  }

  private JsonNode getArchivedFile(Path p, String zipEntryName) {
    ZipFile zf = null;
    try {
      zf = new ZipFile(p.toFile());
      InputStream in = zf.getInputStream(zf.getEntry(zipEntryName));
      return JsonMapper.MAPPER.readTree(in);
    } catch (IOException e) {
      e.printStackTrace();
    } finally {
      if (zf != null) {
        try {
          zf.close();
        } catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
    return null;
  }

  private void importUserIntoMongo(Path p) {
    System.out.println("Import user:" + p);
    String baseName = FilenameUtils.getBaseName(p.toString());
    // Deserialize json files
    JsonNode content = getArchivedFile(p, ImportExportConstants.CONTENT_SUFFIX);
    // Import user into Mongo
    CedarUser cedarUser = null;
    try {
      cedarUser = JsonMapper.MAPPER.treeToValue(content, CedarUser.class);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
    }
    try {
      userService.createUser(cedarUser);
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void importUserIntoNeo(Path p) {
    System.out.println("Import user:" + p);
    String baseName = FilenameUtils.getBaseName(p.toString());
    // Deserialize json files
    JsonNode node = getArchivedFile(p, ImportExportConstants.NODE_SUFFIX);
    //Import user into Neo
    neo4jGraphSession.createUser(node);
  }


  private void importGroup(Path p) {
    System.out.println("Import group:" + p);
    String baseName = FilenameUtils.getBaseName(p.toString());
    // Deserialize json files
    JsonNode node = getArchivedFile(p, ImportExportConstants.NODE_SUFFIX);
    //Import group into Neo
    neo4jGraphSession.createGroup(node);
    JsonNode incomingArcs = getArchivedFile(p, ImportExportConstants.INCOMING_SUFFIX);
    createArcs(incomingArcs);
  }

  private void importResource(Path p) {
    System.out.println("Import resource:" + p);
    String baseName = FilenameUtils.getBaseName(p.toString());
    JsonNode node = getArchivedFile(p, ImportExportConstants.NODE_SUFFIX);
    FolderServerNode fsNode = importNodeIntoNeo(p, node);
    if (fsNode.getType() != CedarNodeType.FOLDER) {
      JsonNode content = getArchivedFile(p, ImportExportConstants.CONTENT_SUFFIX);
      importResourceIntoMongo(p, content, fsNode.getType());
    }
    JsonNode incomingArcs = getArchivedFile(p, ImportExportConstants.INCOMING_SUFFIX);
    createArcs(incomingArcs);
  }

  private FolderServerNode importNodeIntoNeo(Path p, JsonNode node) {
    System.out.println("Import node    :" + p);
    //Import resource into Neo
    return neo4jGraphSession.createNode(node);
  }

  private void importResourceIntoMongo(Path p, JsonNode content, CedarNodeType type) {
    try {
      System.out.println("Import " + type + ":" + p);
      if (type == CedarNodeType.FIELD) {
        templateFieldService.createTemplateField(content);
      } else if (type == CedarNodeType.ELEMENT) {
        templateElementService.createTemplateElement(content);
      } else if (type == CedarNodeType.TEMPLATE) {
        templateService.createTemplate(content);
      } else if (type == CedarNodeType.INSTANCE) {
        templateInstanceService.createTemplateInstance(content);
      } else {
        System.out.println("Unknown resource type:" + type);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  private void createArcs(JsonNode incomingArcs) {
    for (final JsonNode arc : incomingArcs) {
      String sourceId = arc.get("sourceId").textValue();
      String targetId = arc.get("targetId").textValue();
      String label = arc.get("label").textValue();
      neo4jGraphSession.createArc(sourceId, RelationLabel.forValue(label), targetId);
    }
  }

}
