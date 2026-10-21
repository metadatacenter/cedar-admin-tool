package org.metadatacenter.admin.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.client.MongoClient;
import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.metadatacenter.admin.task.importexport.ImportExportConstants;
import org.metadatacenter.admin.util.AdminOutput;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.MongoConfig;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;
import org.metadatacenter.model.folderserver.basic.FolderServerField;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.basic.FolderServerSchemaArtifact;
import org.metadatacenter.model.folderserver.basic.FolderServerTemplate;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.service.mongodb.TemplateFieldServiceMongoDB;
import org.metadatacenter.server.service.mongodb.TemplateServiceMongoDB;
import org.metadatacenter.util.json.JsonMapper;
import org.metadatacenter.util.test.EmbeddedCedarMongo;
import org.metadatacenter.util.test.EmbeddedCedarNeo4j;
import org.metadatacenter.util.test.TestAuthUtil;
import org.metadatacenter.server.neo4j.Neo4jConfig;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.neo4j.util.Neo4JUtil;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Session;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * impex-importAll replaces an installation's graph and artifact store with an export of another, or of itself. Its
 * one promise is that nothing is lost or altered on the way, so an installation exported, imported and exported
 * again gives the same export: every user, group, folder and artifact, each artifact's document, and every arc.
 * <p>
 * The installation is the seeded graph, with a folder holding a field and a template, and a group, added to it. The
 * import replaces the shared graph, so the graph is rebuilt and seeded again afterwards.
 */
@Tag("integration")
class ImpexRoundTripTest {

  private static CedarConfig cedarConfig;
  private static TemplateServiceMongoDB templates;
  private static TemplateFieldServiceMongoDB fields;

  @BeforeAll
  static void startTheStores() throws Exception {
    EmbeddedCedarMongo.startAndRedirectEnvironment();
    EmbeddedCedarNeo4j.startRedirectAndSeed(SystemComponent.ADMIN_TOOL);
    cedarConfig = CedarConfig.getInstance(CedarEnvironmentVariableProvider.getFor(SystemComponent.ADMIN_TOOL));
    CedarDataServices.initializeMongoClientFactoryForDocuments(cedarConfig.getArtifactServerConfig().getMongoConnection());
    MongoClient client = CedarDataServices.getInstance().getMongoClientFactoryForDocuments().getClient();
    MongoConfig store = cedarConfig.getArtifactServerConfig();
    templates = new TemplateServiceMongoDB(client, store.getDatabaseName(),
        store.getMongoCollectionName(CedarResourceType.TEMPLATE));
    fields = new TemplateFieldServiceMongoDB(client, store.getDatabaseName(),
        store.getMongoCollectionName(CedarResourceType.FIELD));
  }

  @AfterAll
  static void rebuildTheSharedGraph() {
    EmbeddedCedarNeo4j.stopAndReset();
    EmbeddedCedarNeo4j.startRedirectAndSeed(SystemComponent.ADMIN_TOOL);
  }

  @Test
  void anExportedInstallationImportsBackAsItWas() throws Exception {
    CedarRequestContext user1 = CedarRequestContextFactory.fromUser(TestAuthUtil.getTestUser1(cedarConfig));
    FolderServiceSession folders = CedarDataServices.getInstance().getFolderServiceSession(user1);
    FolderServerFolder folder = new FolderServerFolder();
    folder.setName("Round Trip");
    folder.setDescription("Exported and imported by ImpexRoundTripTest");
    CedarFolderId folderId = cedarConfig.getLinkedDataUtil().buildNewLinkedDataIdObject(CedarFolderId.class);
    folders.createFolderAsChildOfId(folder, folders.findHomeFolderOf().getResourceId(), folderId);
    String fieldId = artifact(folders, folderId, new FolderServerField(), CedarResourceType.FIELD, "Round Trip Field");
    String templateId = artifact(folders, folderId, new FolderServerTemplate(), CedarResourceType.TEMPLATE,
        "Round Trip Template");
    fields.createTemplateField(document(fieldId, "Round Trip Field"));
    templates.createTemplate(document(templateId, "Round Trip Template"));
    CedarDataServices.getInstance().getGroupServiceSession(user1).createGroup("Round Trippers", "A group to keep");

    Path exportDir = Path.of(cedarConfig.getImportExportConfig().getExportDir());
    Path importDir = Path.of(cedarConfig.getImportExportConfig().getImportDir());
    FileUtils.deleteDirectory(importDir.toFile());
    export();
    FileUtils.moveDirectory(exportDir.toFile(), importDir.toFile());

    Console.Run<Integer> imported = Console.run(() -> {
      ImpexImportAll task = new ImpexImportAll();
      task.setArguments(new String[]{"impex-importAll"});
      task.injectConfig(cedarConfig);
      task.setOutput(new AdminOutput());
      task.init();
      return task.execute();
    }, AbstractCedarAdminTask.CONFIRM);
    assertEquals(0, imported.result(), imported.printed());

    export();
    Map<String, Map<String, JsonNode>> before = read(importDir);
    Map<String, Map<String, JsonNode>> after = read(exportDir);
    assertTrue(before.keySet().stream().anyMatch(path -> path.startsWith("groups/")), "the export holds groups");
    assertEquals(before.keySet(), after.keySet(), "the second export holds the same archives");
    List<String> differences = new ArrayList<>();
    for (String archive : before.keySet()) {
      if (!before.get(archive).equals(after.get(archive))) {
        differences.add(archive + "\n  before: " + before.get(archive) + "\n  after:  " + after.get(archive));
      }
    }
    assertEquals(List.of(), differences, "every archive should come back as it was");

    // The export leaves a folder's parent to its CONTAINS arc, and the import records it on the folder again.
    Neo4jConfig neo4j = Neo4jConfig.fromCedarConfig(cedarConfig);
    try (Driver driver = GraphDatabase.driver(neo4j.getUri(), AuthTokens.basic(neo4j.getUserName(), neo4j.getUserPassword()));
         Session session = driver.session()) {
      String id = Neo4JUtil.escapePropertyName(NodeProperty.ID.getValue());
      String parent = Neo4JUtil.escapePropertyName(NodeProperty.PARENT_FOLDER_ID.getValue());
      var counts = session.run("MATCH (p)-[:CONTAINS]->(f) WHERE f." + parent + " IS NOT NULL OR f.resourceType = 'folder' "
          + "RETURN count(f) AS folders, sum(CASE WHEN f." + parent + " = p." + id + " THEN 1 ELSE 0 END) AS recorded").single();
      assertTrue(counts.get("folders").asLong() > 0);
      assertEquals(counts.get("folders").asLong(), counts.get("recorded").asLong(),
          "every contained folder records its parent");
    }
  }

  /** The import acts as the export's administrator, so an export without one is refused before anything is wiped. */
  @Test
  void anExportWithoutTheAdministratorIsRefusedBeforeAnythingChanges() throws Exception {
    Path importDir = Path.of(cedarConfig.getImportExportConfig().getImportDir());
    FileUtils.deleteDirectory(importDir.toFile());
    Files.createDirectories(importDir.resolve("users"));

    Console.Run<Integer> refused = Console.run(() -> {
      ImpexImportAll task = new ImpexImportAll();
      task.setArguments(new String[]{"impex-importAll"});
      task.injectConfig(cedarConfig);
      task.setOutput(new AdminOutput());
      task.init();
      return task.execute();
    });

    assertEquals(-1, refused.result());
    assertTrue(refused.printed().contains("holds no user with the configured administrator's API key"), refused.printed());
    assertTrue(CedarDataServices.getInstance().getNeoUserService()
        .findUser(TestAuthUtil.getTestUser1(cedarConfig).getResourceId()) != null, "the graph is untouched");
  }

  private static void export() throws Exception {
    FileUtils.deleteDirectory(Path.of(cedarConfig.getImportExportConfig().getExportDir()).toFile());
    ImpexExportAll export = new ImpexExportAll();
    export.setArguments(new String[]{"impex-exportAll"});
    export.injectConfig(cedarConfig);
    export.setOutput(new AdminOutput());
    export.init();
    assertEquals(0, (int) Console.run(export::execute).result());
  }

  /** Each archive's parts, by its path within the export; arcs are compared as a set, as their order is not kept. */
  private static Map<String, Map<String, JsonNode>> read(Path dir) throws Exception {
    Map<String, Map<String, JsonNode>> archives = new TreeMap<>();
    List<Path> zips;
    try (Stream<Path> files = Files.walk(dir)) {
      zips = files.filter(f -> f.toString().endsWith(".zip")).toList();
    }
    for (Path zip : zips) {
      Map<String, JsonNode> parts = new TreeMap<>();
      try (ZipFile file = new ZipFile(zip.toFile())) {
        for (var entry : Collections.list(file.entries())) {
          JsonNode part = JsonMapper.STRICT_MAPPER.readTree(file.getInputStream(entry));
          if (entry.getName().equals(ImportExportConstants.INCOMING_SUFFIX)) {
            Set<JsonNode> arcs = new HashSet<>();
            part.forEach(arcs::add);
            part = JsonMapper.STRICT_MAPPER.valueToTree(arcs.stream().map(JsonNode::toString).sorted().toList());
          }
          parts.put(entry.getName(), part);
        }
      }
      archives.put(dir.relativize(zip).toString(), parts);
    }
    return archives;
  }

  private static String artifact(FolderServiceSession folders, CedarFolderId parent, FolderServerSchemaArtifact artifact,
                                 CedarResourceType type, String name) {
    artifact.setId(cedarConfig.getLinkedDataUtil().buildNewLinkedDataId(type));
    artifact.setName(name);
    artifact.setDescription("Exported and imported by ImpexRoundTripTest");
    artifact.setVersion("1.0.0");
    artifact.setPublicationStatus("bibo:draft");
    artifact.setLatestVersion(true);
    artifact.setLatestDraftVersion(true);
    artifact.setLatestPublishedVersion(false);
    FolderServerArtifact created = folders.createResourceAsChildOfId(artifact, parent);
    return created.getId();
  }

  private static JsonNode document(String id, String name) {
    ObjectNode document = JsonMapper.STRICT_MAPPER.createObjectNode();
    document.put("@id", id);
    document.put("schema:name", name);
    return document;
  }
}
