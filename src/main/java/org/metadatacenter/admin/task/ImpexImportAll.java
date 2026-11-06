package org.metadatacenter.admin.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.io.filefilter.FileFileFilter;
import org.bson.BsonDocument;
import org.bson.Document;
import org.metadatacenter.admin.task.importexport.ImportExportConstants;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.MongoConfig;
import org.metadatacenter.exception.security.CedarAccessException;
import org.metadatacenter.id.CedarUntypedResourceId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.RelationLabel;
import org.metadatacenter.model.folderserver.basic.FileSystemResource;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.AdminServiceSession;
import org.metadatacenter.server.GraphServiceSession;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ImpexImportAll extends AbstractNeo4JAccessTask {

  public static final String DEFAULT_SORT = "name";

  private static final Set<CedarResourceType> IMPORTED_TYPES = EnumSet.of(CedarResourceType.FOLDER,
      CedarResourceType.FIELD, CedarResourceType.ELEMENT, CedarResourceType.TEMPLATE, CedarResourceType.INSTANCE);

  private GraphServiceSession neo4jGraphSession;
  private ObjectMapper prettyMapper;
  private List<CedarResourceType> resourceTypeList;
  private List<String> sortList;
  private static TemplateFieldService<String, JsonNode> templateFieldService;
  private static TemplateElementService<String, JsonNode> templateElementService;
  private static TemplateService<String, JsonNode> templateService;
  private static TemplateInstanceService<String, JsonNode> templateInstanceService;


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
    Path importPath = Paths.get(importDir);
    Path userImportPath = importPath.resolve("users");
    Path groupImportPath = importPath.resolve("groups");
    Path resourceImportPath = importPath.resolve("resources");

    // The import acts throughout as the export's administrator, recognized by the configured API key, so it needs an
    // administrator neither in the graph it replaces nor in the one it builds.
    CedarRequestContext administrator = exportedAdministrator(userImportPath);
    if (administrator == null) {
      out.error("The import holds no user with the configured administrator's API key. Nothing was changed.");
      return -1;
    }

    // The import empties both stores before it reads an artifact's archive, so an archive it cannot complete would
    // stop it with the installation half rebuilt. Every resource archive is therefore read first.
    List<String> incomplete = new ArrayList<>();
    processFolder(resourceImportPath, p -> {
      String reason = whyIncomplete(p);
      if (reason != null) {
        incomplete.add(p + ": " + reason);
      }
    });
    if (!incomplete.isEmpty()) {
      incomplete.forEach(out::error);
      out.error((incomplete.size() == 1 ? "1 resource archive cannot" : incomplete.size() + " resource archives cannot")
          + " be imported. Nothing was changed.");
      return -1;
    }

    String mongoDatabaseName = cedarConfig.getArtifactServerConfig().getDatabaseName();
    if (!confirmReplacement(importDir, mongoDatabaseName)) {
      return -1;
    }

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

    MongoClient mongoClientForDocuments = CedarDataServices.getInstance().getMongoClientFactoryForDocuments().getClient();

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

    linkedDataUtil = cedarConfig.getLinkedDataUtil();

    out.info("Deleting data from MongoDB");
    deleteAllMongoData();

    out.info("Deleting everything from Neo4J");
    AdminServiceSession adminSession = CedarDataServices.getInstance().getAdminServiceSession(administrator);
    adminSession.wipeAllData();

    neo4jGraphSession = CedarDataServices.getInstance().getGraphServiceSession(administrator);

    out.info("Importing users");
    processFolder(userImportPath, this::importUser);

    out.info("Importing groups");
    processFolder(groupImportPath, this::importGroup);

    out.info("Importing resources");
    processFolder(resourceImportPath, this::importResource);

    // An arc joins two nodes, either of which an archive read later may hold, so the arcs wait for every node.
    out.info("Importing arcs");
    processFolder(userImportPath, this::importIncomingArcs);
    processFolder(groupImportPath, this::importIncomingArcs);
    processFolder(resourceImportPath, this::importIncomingArcs);

    // A folder records its parent's identifier, which the export leaves to the CONTAINS arcs.
    out.info("Recording each folder's parent");
    adminSession.backfillFolderParentIds();

    return 0;
  }

  protected boolean confirmReplacement(String importDir, String mongoDatabaseName) {
    return getConfirmInput("Deleting every artifact document from MongoDB database '" + mongoDatabaseName +
        "' and all CEDAR data from Neo4j before importing from '" + importDir + "'...");
  }

  private void deleteAllMongoData() {
    String mongoDatabaseNameForDocuments = cedarConfig.getArtifactServerConfig().getDatabaseName();
    MongoClient mongoClientForDocuments = CedarDataServices.getInstance().getMongoClientFactoryForDocuments().getClient();

    String templateFieldsCollectionName = cedarConfig.getArtifactServerConfig().getCollections().get(CedarResourceType.FIELD.getValue());
    String templateElementsCollectionName = cedarConfig.getArtifactServerConfig().getCollections().get(CedarResourceType.ELEMENT.getValue());
    String templateInstancesCollectionName = cedarConfig.getArtifactServerConfig().getCollections().get(CedarResourceType.INSTANCE.getValue());
    String templatesCollectionName = cedarConfig.getArtifactServerConfig().getCollections().get(CedarResourceType.TEMPLATE.getValue());

    emptyCollection(mongoClientForDocuments, mongoDatabaseNameForDocuments, templateFieldsCollectionName);
    emptyCollection(mongoClientForDocuments, mongoDatabaseNameForDocuments, templateElementsCollectionName);
    emptyCollection(mongoClientForDocuments, mongoDatabaseNameForDocuments, templateInstancesCollectionName);
    emptyCollection(mongoClientForDocuments, mongoDatabaseNameForDocuments, templatesCollectionName);
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
      return JsonMapper.STRICT_MAPPER.readTree(in);
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

  /**
   * Why the import could not complete a resource archive, or null when it can. The archive must hold a graph node
   * of a type the import creates and, unless the node is a folder, the artifact's document. An export writes an
   * artifact whose document is missing from the artifact store with JSON null in its place.
   */
  private static String whyIncomplete(Path archive) {
    try (ZipFile zip = new ZipFile(archive.toFile())) {
      JsonNode node = archivedPart(zip, ImportExportConstants.NODE_SUFFIX);
      CedarResourceType type = node == null ? null :
          CedarResourceType.forValue(node.path(NodeProperty.Label.RESOURCE_TYPE).asText());
      if (type == null || !IMPORTED_TYPES.contains(type)) {
        return "it holds no graph node of a folder, field, element, template or instance";
      }
      if (type != CedarResourceType.FOLDER && !(archivedPart(zip, ImportExportConstants.CONTENT_SUFFIX) instanceof ObjectNode)) {
        return "it holds no document for its " + type.getValue();
      }
      return null;
    } catch (IOException e) {
      return "it cannot be read (" + e.getMessage() + ")";
    }
  }

  private static JsonNode archivedPart(ZipFile zip, String name) throws IOException {
    ZipEntry entry = zip.getEntry(name);
    if (entry == null) {
      return null;
    }
    try (InputStream in = zip.getInputStream(entry)) {
      return JsonMapper.STRICT_MAPPER.readTree(in);
    }
  }

  private void importUser(Path p) {
    System.out.println("Import user:" + p);
    neo4jGraphSession.createUser(getArchivedFile(p, ImportExportConstants.NODE_SUFFIX));
  }

  private CedarRequestContext exportedAdministrator(Path userImportPath) {
    String adminApiKey = cedarConfig.getAdminUserConfig().getApiKey();
    List<CedarUser> administrators = new ArrayList<>();
    processFolder(userImportPath, p -> {
      try {
        CedarUser user = JsonMapper.TOLERANT_MAPPER.treeToValue(
            getArchivedFile(p, ImportExportConstants.CONTENT_SUFFIX), CedarUser.class);
        if (user.getApiKeys().stream().anyMatch(key -> adminApiKey.equals(key.getKey()))) {
          administrators.add(user);
        }
      } catch (JsonProcessingException e) {
        throw new IllegalStateException("Unreadable user in " + p, e);
      }
    });
    if (administrators.isEmpty()) {
      return null;
    }
    try {
      return CedarRequestContextFactory.fromUser(administrators.get(0));
    } catch (CedarAccessException e) {
      throw new IllegalStateException("The exported administrator cannot act", e);
    }
  }

  private void importGroup(Path p) {
    System.out.println("Import group:" + p);
    neo4jGraphSession.createGroup(getArchivedFile(p, ImportExportConstants.NODE_SUFFIX));
  }

  private void importResource(Path p) {
    System.out.println("Import artifact:" + p);
    JsonNode node = getArchivedFile(p, ImportExportConstants.NODE_SUFFIX);
    FileSystemResource fsNode = importNodeIntoNeo(p, node);
    if (fsNode.getType() != CedarResourceType.FOLDER) {
      JsonNode content = getArchivedFile(p, ImportExportConstants.CONTENT_SUFFIX);
      importResourceIntoMongo(p, content, fsNode.getType());
    }
  }

  private void importIncomingArcs(Path p) {
    createArcs(getArchivedFile(p, ImportExportConstants.INCOMING_SUFFIX));
  }

  private FileSystemResource importNodeIntoNeo(Path p, JsonNode node) {
    System.out.println("Import resource    :" + p);
    //Import artifact into Neo
    return neo4jGraphSession.createFilesystemResource(node);
  }

  private void importResourceIntoMongo(Path p, JsonNode content, CedarResourceType type) {
    try {
      System.out.println("Import " + type + ":" + p);
      if (type == CedarResourceType.FIELD) {
        templateFieldService.createTemplateField(content);
      } else if (type == CedarResourceType.ELEMENT) {
        templateElementService.createTemplateElement(content);
      } else if (type == CedarResourceType.TEMPLATE) {
        templateService.createTemplate(content);
      } else if (type == CedarResourceType.INSTANCE) {
        templateInstanceService.createTemplateInstance(content);
      } else {
        System.out.println("Unknown artifact type:" + type);
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
      neo4jGraphSession.createArc(CedarUntypedResourceId.build(sourceId), RelationLabel.forValue(label), CedarUntypedResourceId.build(targetId));
    }
  }

}
