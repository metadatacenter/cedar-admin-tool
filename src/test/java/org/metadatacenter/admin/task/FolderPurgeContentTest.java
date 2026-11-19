package org.metadatacenter.admin.task;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.metadatacenter.admin.util.AdminOutput;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.MongoConfig;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.id.CedarTemplateId;
import org.metadatacenter.id.CedarUntypedArtifactId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;
import org.metadatacenter.model.folderserver.basic.FolderServerElement;
import org.metadatacenter.model.folderserver.basic.FolderServerField;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.basic.FolderServerInstance;
import org.metadatacenter.model.folderserver.basic.FolderServerSchemaArtifact;
import org.metadatacenter.model.folderserver.basic.FolderServerTemplate;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.neo4j.cypher.NodeProperty;
import org.metadatacenter.server.service.mongodb.TemplateServiceMongoDB;
import org.metadatacenter.util.json.JsonMapper;
import org.metadatacenter.util.test.EmbeddedCedarMongo;
import org.metadatacenter.util.test.EmbeddedCedarNeo4j;
import org.metadatacenter.util.test.TestAuthUtil;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * folder-purgeContent permanently deletes every artifact and subfolder under a folder, at any depth, from the graph
 * and from the artifact store, after the operator confirms. It must delete those and nothing else, keep the folder
 * itself, and leave everything in place on each of its refusals. It refuses the root, system and special folders, and
 * a folder holding a user's home folder at any depth.
 */
@Tag("integration")
@ExtendWith(FreshStores.class)
class FolderPurgeContentTest {

  private static CedarConfig cedarConfig;
  private static CedarRequestContext user1;
  private static TemplateServiceMongoDB templates;

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
    user1 = CedarRequestContextFactory.fromUser(TestAuthUtil.getTestUser1(cedarConfig));
  }

  @Test
  void theFolderLosesItsArtifactsAndNothingElseDoes() throws Exception {
    FolderServiceSession folders = CedarDataServices.getInstance().getFolderServiceSession(user1);
    CedarFolderId home = folders.findHomeFolderOf().getResourceId();
    CedarFolderId purged = folder(folders, home, "Purged Folder");
    List<String> inside = List.of(template(folders, purged, "Purged One"), template(folders, purged, "Purged Two"));
    String outside = template(folders, home, "Kept Outside");

    assertEquals(0, purge(purged.getId(), true));

    for (String id : inside) {
      assertNull(folders.findArtifactById(CedarUntypedArtifactId.build(id)), "the graph should have lost " + id);
      assertNull(templates.findTemplate(id), "the artifact store should have lost " + id);
    }
    assertNotNull(folders.findArtifactById(CedarUntypedArtifactId.build(outside)), "an artifact outside the folder stays");
    assertNotNull(templates.findTemplate(outside), "an artifact outside the folder keeps its document");
    assertNotNull(folders.findFolderById(purged), "the folder itself stays");
  }

  /**
   * The purge removes every kind of artifact at every depth under the folder, each from its own collection, and every
   * subfolder, deepest first, and keeps the folder itself.
   */
  @Test
  void everyArtifactAndSubfolderUnderTheFolderGoes() throws Exception {
    FolderServiceSession folders = CedarDataServices.getInstance().getFolderServiceSession(user1);
    CedarFolderId home = folders.findHomeFolderOf().getResourceId();
    CedarFolderId purged = folder(folders, home, "Mixed Folder");
    CedarFolderId nested = folder(folders, purged, "Nested Folder");
    CedarFolderId deepest = folder(folders, nested, "Deepest Folder");
    CedarFolderId empty = folder(folders, nested, "Empty Folder");
    Map<String, CedarResourceType> inside = new java.util.LinkedHashMap<>();
    for (CedarFolderId parent : List.of(purged, nested, deepest)) {
      inside.putAll(everyKind(folders, parent, folders.findFolderById(parent).getName()));
    }
    String outside = template(folders, home, "Kept Beside");

    assertEquals(0, purge(purged.getId(), true));

    for (Map.Entry<String, CedarResourceType> artifact : inside.entrySet()) {
      assertNull(folders.findArtifactById(CedarUntypedArtifactId.build(artifact.getKey())),
          "the graph should have lost the " + artifact.getValue().getValue() + " " + artifact.getKey());
      assertEquals(0, collection(artifact.getValue()).countDocuments(new org.bson.Document("@id", artifact.getKey())),
          "the artifact store should have lost the " + artifact.getValue().getValue() + " " + artifact.getKey());
    }
    for (CedarFolderId subfolder : List.of(nested, deepest, empty)) {
      assertNull(folders.findFolderById(subfolder), "the graph should have lost the subfolder " + subfolder.getId());
    }
    assertNotNull(folders.findFolderById(purged), "the folder itself stays");
    assertEquals(0, folders.findAllChildArtifactsOfFolder(purged).size(), "the folder is left empty");
    assertNotNull(folders.findArtifactById(CedarUntypedArtifactId.build(outside)), "an artifact beside the folder stays");
    assertNotNull(templates.findTemplate(outside), "an artifact beside the folder keeps its document");
  }

  /** A field, an element, a template and an instance of that template, in the folder. */
  private static Map<String, CedarResourceType> everyKind(FolderServiceSession folders, CedarFolderId parent, String prefix)
      throws Exception {
    Map<String, CedarResourceType> created = new java.util.LinkedHashMap<>();
    String template = template(folders, parent, prefix + " Template");
    created.put(artifact(folders, parent, new FolderServerField(), CedarResourceType.FIELD, prefix + " Field"),
        CedarResourceType.FIELD);
    created.put(artifact(folders, parent, new FolderServerElement(), CedarResourceType.ELEMENT, prefix + " Element"),
        CedarResourceType.ELEMENT);
    created.put(template, CedarResourceType.TEMPLATE);
    created.put(instance(folders, parent, template, prefix + " Instance"), CedarResourceType.INSTANCE);
    return created;
  }

  @Test
  void eachRefusalLeavesTheFolderAsItWas() throws Exception {
    FolderServiceSession folders = CedarDataServices.getInstance().getFolderServiceSession(user1);
    CedarFolderId folder = folder(folders, folders.findHomeFolderOf().getResourceId(), "Refused Folder");
    String kept = template(folders, folder, "Refused One");

    assertEquals(-1, purge(null, true), "a missing folder argument is refused");
    assertEquals(-2, purge("https://repo.metadatacenter.orgx/folders/00000000-0000-0000-0000-000000000000", true),
        "an unknown folder is refused");
    assertEquals(-4, purge(folder.getId(), false), "a declined confirmation deletes nothing");

    assertNotNull(folders.findArtifactById(CedarUntypedArtifactId.build(kept)));
    assertNotNull(templates.findTemplate(kept));
  }

  @Test
  void theUsersFolderIsRefused() throws Exception {
    FolderServiceSession folders = CedarDataServices.getInstance().getFolderServiceSession(user1);
    FolderServerFolder users = folders.findFolderByPath(cedarConfig.getFolderStructureConfig().getUsersFolder().getPath());
    CedarFolderId home = folders.findHomeFolderOf().getResourceId();
    String kept = template(folders, home, "Kept Under Users");

    assertEquals(-5, purge(users.getId(), true));

    assertNotNull(folders.findFolderById(home), "a home folder under the users folder stays");
    assertNotNull(folders.findArtifactById(CedarUntypedArtifactId.build(kept)));
    assertNotNull(templates.findTemplate(kept));
  }

  @Test
  void theRootFolderIsRefused() throws Exception {
    FolderServiceSession folders = CedarDataServices.getInstance().getFolderServiceSession(user1);
    FolderServerFolder root = folders.findFolderByPath(cedarConfig.getFolderStructureConfig().getRootFolder().getPath());
    String kept = template(folders, folders.findHomeFolderOf().getResourceId(), "Kept Under Root");

    assertEquals(-5, purge(root.getId(), true));

    assertNotNull(templates.findTemplate(kept));
  }

  /** A user's home folder anywhere under the folder stops the purge before anything is deleted. */
  @Test
  void aFolderHoldingAUsersHomeFolderIsRefusedWithNothingDeleted() throws Exception {
    FolderServiceSession folders = CedarDataServices.getInstance().getFolderServiceSession(user1);
    CedarFolderId purged = folder(folders, folders.findHomeFolderOf().getResourceId(), "Holding A Home");
    String direct = template(folders, purged, "Direct Template");
    CedarFolderId nested = folder(folders, purged, "Around A Home");
    FolderServerFolder homeLike = new FolderServerFolder();
    homeLike.setName("Someone's Home");
    homeLike.setDescription("Created by FolderPurgeContentTest");
    homeLike.setUserHome(true);
    CedarFolderId home = cedarConfig.getLinkedDataUtil().buildNewLinkedDataIdObject(CedarFolderId.class);
    assertNotNull(folders.createFolderAsChildOfId(homeLike, nested, home));
    String insideHome = template(folders, home, "Template In A Home");

    assertEquals(-5, purge(purged.getId(), true));

    for (String id : List.of(direct, insideHome)) {
      assertNotNull(folders.findArtifactById(CedarUntypedArtifactId.build(id)), "the graph keeps " + id);
      assertNotNull(templates.findTemplate(id), "the artifact store keeps " + id);
    }
    for (CedarFolderId kept : List.of(purged, nested, home)) {
      assertNotNull(folders.findFolderById(kept), "the folder " + kept.getId() + " stays");
    }
  }

  /** A folder the graph marks as special, such as a shared folder, is refused as a target and inside the subtree. */
  @Test
  void aSpecialFolderIsRefusedAsTargetAndInsideTheSubtree() throws Exception {
    FolderServiceSession folders = CedarDataServices.getInstance().getFolderServiceSession(user1);
    CedarFolderId outer = folder(folders, folders.findHomeFolderOf().getResourceId(), "Around A Shared Folder");
    CedarFolderId shared = folder(folders, outer, "Shared Folder");
    assertNotNull(folders.updateFolderById(shared, Map.of(NodeProperty.SPECIAL_FOLDER, "shared")));
    String kept = template(folders, shared, "Shared Template");

    assertEquals(-5, purge(shared.getId(), true), "the special folder itself is refused");
    assertEquals(-5, purge(outer.getId(), true), "a folder holding the special folder is refused");

    assertNotNull(folders.findFolderById(shared));
    assertNotNull(templates.findTemplate(kept));
  }

  private static com.mongodb.client.MongoCollection<org.bson.Document> collection(CedarResourceType type) {
    MongoConfig store = cedarConfig.getArtifactServerConfig();
    return CedarDataServices.getInstance().getMongoClientFactoryForDocuments().getClient()
        .getDatabase(store.getDatabaseName()).getCollection(store.getMongoCollectionName(type));
  }

  /** An artifact of the type, in the folder, with a document in its collection. */
  private static String artifact(FolderServiceSession folders, CedarFolderId parent, FolderServerSchemaArtifact artifact,
                                 CedarResourceType type, String name) {
    artifact.setId(cedarConfig.getLinkedDataUtil().buildNewLinkedDataId(type));
    artifact.setName(name);
    artifact.setDescription("Created by FolderPurgeContentTest");
    artifact.setVersion("1.0.0");
    artifact.setPublicationStatus("bibo:draft");
    artifact.setLatestVersion(true);
    artifact.setLatestDraftVersion(true);
    artifact.setLatestPublishedVersion(false);
    String id = folders.createResourceAsChildOfId(artifact, parent).getId();
    collection(type).insertOne(new org.bson.Document("@id", id).append("schema:name", name));
    return id;
  }

  private static String instance(FolderServiceSession folders, CedarFolderId parent, String templateId, String name) {
    FolderServerInstance instance = new FolderServerInstance();
    instance.setId(cedarConfig.getLinkedDataUtil().buildNewLinkedDataId(CedarResourceType.INSTANCE));
    instance.setName(name);
    instance.setDescription("Created by FolderPurgeContentTest");
    instance.setIsBasedOn(CedarTemplateId.build(templateId));
    String id = folders.createResourceAsChildOfId(instance, parent).getId();
    collection(CedarResourceType.INSTANCE).insertOne(new org.bson.Document("@id", id).append("schema:name", name));
    return id;
  }

  private static int purge(String folderId, boolean confirmed) {
    FolderPurgeContent task = new FolderPurgeContent() {
      @Override
      public void init() {
        initMongoCollectionNames();
      }

      @Override
      protected boolean confirmPurge(String folderName, String id, int artifactCount, int subfolderCount) {
        return confirmed;
      }
    };
    task.setArguments(folderId == null ? new String[]{"folder-purgeContent"} : new String[]{"folder-purgeContent", folderId});
    task.injectConfig(cedarConfig);
    task.setOutput(new AdminOutput());
    task.init();
    return task.execute();
  }

  private static CedarFolderId folder(FolderServiceSession folders, CedarFolderId parent, String name) {
    FolderServerFolder folder = new FolderServerFolder();
    folder.setName(name);
    folder.setDescription("Created by FolderPurgeContentTest");
    CedarFolderId id = cedarConfig.getLinkedDataUtil().buildNewLinkedDataIdObject(CedarFolderId.class);
    assertNotNull(folders.createFolderAsChildOfId(folder, parent, id));
    return id;
  }

  private static String template(FolderServiceSession folders, CedarFolderId parent, String name) throws Exception {
    FolderServerTemplate template = new FolderServerTemplate();
    template.setId(cedarConfig.getLinkedDataUtil().buildNewLinkedDataId(CedarResourceType.TEMPLATE));
    template.setName(name);
    template.setDescription("Created by FolderPurgeContentTest");
    template.setVersion("1.0.0");
    template.setPublicationStatus("bibo:draft");
    template.setLatestVersion(true);
    template.setLatestDraftVersion(true);
    template.setLatestPublishedVersion(false);
    FolderServerArtifact created = folders.createResourceAsChildOfId(template, parent);
    assertNotNull(created, name + " should have been created");
    ObjectNode document = JsonMapper.STRICT_MAPPER.createObjectNode();
    document.put("@id", created.getId());
    document.put("schema:name", name);
    templates.createTemplate(document);
    return created.getId();
  }
}
