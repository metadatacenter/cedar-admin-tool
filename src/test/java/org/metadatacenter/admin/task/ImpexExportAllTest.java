package org.metadatacenter.admin.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.client.MongoClient;
import org.apache.commons.io.FileUtils;
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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * impex-exportAll writes a whole installation out as a directory of archives: one per user, one per
 * group, and one per folder and artifact, nested as the folder tree is. An artifact's archive holds
 * its document from the artifact store, its graph node, and the arcs that point at it, which is
 * what places it in its folder.
 * <p>
 * Here a folder holding a field and a template is added to the seeded graph and the artifact store,
 * and the export is checked for every user and group, and for the template's three parts.
 */
@Tag("integration")
class ImpexExportAllTest {

  private static CedarConfig cedarConfig;
  private static CedarRequestContext user1;
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
    user1 = CedarRequestContextFactory.fromUser(TestAuthUtil.getTestUser1(cedarConfig));
  }

  @Test
  void theExportHoldsEveryUserAndGroupAndEachArtifactsThreeParts() throws Exception {
    FolderServiceSession folders = CedarDataServices.getInstance().getFolderServiceSession(user1);
    FolderServerFolder folder = new FolderServerFolder();
    folder.setName("Export Folder");
    folder.setDescription("Exported by ImpexExportAllTest");
    CedarFolderId folderId = cedarConfig.getLinkedDataUtil().buildNewLinkedDataIdObject(CedarFolderId.class);
    assertNotNull(folders.createFolderAsChildOfId(folder, folders.findHomeFolderOf().getResourceId(), folderId));
    String fieldId = artifact(folders, folderId, new FolderServerField(), CedarResourceType.FIELD, "Export Field");
    String templateId = artifact(folders, folderId, new FolderServerTemplate(), CedarResourceType.TEMPLATE, "Export Template");
    fields.createTemplateField(document(fieldId, "Export Field"));
    templates.createTemplate(document(templateId, "Export Template"));

    Path exportDir = Path.of(cedarConfig.getImportExportConfig().getExportDir());
    FileUtils.deleteDirectory(exportDir.toFile());
    ImpexExportAll export = new ImpexExportAll() {
      @Override
      public void init() {
      }
    };
    export.injectConfig(cedarConfig);
    export.setOutput(new AdminOutput());
    export.init();
    assertEquals(0, export.execute());

    assertEquals(CedarDataServices.getInstance().getNeoUserService().findAll().size(),
        archives(exportDir.resolve("users")).size(), "the export should hold one archive per user");
    assertTrue(archives(exportDir.resolve("groups")).size() >= 1, "the export should hold the Everybody group");

    Path templateArchive = archiveOf(exportDir.resolve("resources"),
        cedarConfig.getLinkedDataUtil().getUUID(templateId, CedarResourceType.TEMPLATE));
    Map<String, JsonNode> parts = read(templateArchive);
    assertEquals(templates.findTemplate(templateId), parts.get(ImportExportConstants.CONTENT_SUFFIX),
        "the archive should hold the template's document as the artifact store has it");
    assertEquals(templateId, parts.get(ImportExportConstants.NODE_SUFFIX).get("@id").asText());
    assertEquals("Export Template", parts.get(ImportExportConstants.NODE_SUFFIX).get("schema:name").asText());
    boolean inItsFolder = false;
    for (JsonNode arc : parts.get(ImportExportConstants.INCOMING_SUFFIX)) {
      inItsFolder |= folderId.getId().equals(arc.get("sourceId").asText()) && "CONTAINS".equals(arc.get("label").asText());
    }
    assertTrue(inItsFolder, "the template's archive should hold the arc from its folder: " + parts);

    // The artifact's archive sits inside its folder's directory, so the tree can be rebuilt in order.
    String folderUuid = cedarConfig.getLinkedDataUtil().getUUID(folderId.getId(), CedarResourceType.FOLDER);
    assertTrue(templateArchive.toString().contains("/" + folderUuid + "/"),
        "the template should be exported under its folder: " + templateArchive);
  }

  private static String artifact(FolderServiceSession folders, CedarFolderId parent, FolderServerSchemaArtifact artifact,
                                 CedarResourceType type, String name) {
    artifact.setId(cedarConfig.getLinkedDataUtil().buildNewLinkedDataId(type));
    artifact.setName(name);
    artifact.setDescription("Exported by ImpexExportAllTest");
    artifact.setVersion("1.0.0");
    artifact.setPublicationStatus("bibo:draft");
    artifact.setLatestVersion(true);
    artifact.setLatestDraftVersion(true);
    artifact.setLatestPublishedVersion(false);
    FolderServerArtifact created = folders.createResourceAsChildOfId(artifact, parent);
    assertNotNull(created, name + " should have been created");
    return created.getId();
  }

  private static JsonNode document(String id, String name) {
    ObjectNode document = JsonMapper.STRICT_MAPPER.createObjectNode();
    document.put("@id", id);
    document.put("schema:name", name);
    return document;
  }

  private static List<Path> archives(Path dir) throws Exception {
    try (Stream<Path> files = Files.walk(dir)) {
      return files.filter(f -> f.toString().endsWith(".zip")).toList();
    }
  }

  private static Path archiveOf(Path dir, String uuid) throws Exception {
    return archives(dir).stream().filter(f -> f.getFileName().toString().equals(uuid + ".zip")).findFirst()
        .orElseThrow(() -> new AssertionError("no archive for " + uuid));
  }

  private static Map<String, JsonNode> read(Path archive) throws Exception {
    try (ZipFile zip = new ZipFile(archive.toFile())) {
      Map<String, JsonNode> parts = new java.util.HashMap<>();
      for (var entry : java.util.Collections.list(zip.entries())) {
        parts.put(entry.getName(), JsonMapper.STRICT_MAPPER.readTree(zip.getInputStream(entry)));
      }
      return parts;
    }
  }
}
