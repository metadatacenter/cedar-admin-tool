package org.metadatacenter.admin.task;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.metadatacenter.admin.util.AdminOutput;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.MongoConfig;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.id.CedarFolderId;
import org.metadatacenter.id.CedarUntypedArtifactId;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.model.folderserver.basic.FolderServerArtifact;
import org.metadatacenter.model.folderserver.basic.FolderServerFolder;
import org.metadatacenter.model.folderserver.basic.FolderServerTemplate;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.service.mongodb.TemplateServiceMongoDB;
import org.metadatacenter.util.json.JsonMapper;
import org.metadatacenter.util.test.EmbeddedCedarMongo;
import org.metadatacenter.util.test.EmbeddedCedarNeo4j;
import org.metadatacenter.util.test.TestAuthUtil;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * folder-purgeContent permanently deletes every artifact in a folder from the graph and from the
 * artifact store, after the operator confirms. It must delete those artifacts and nothing else, and
 * each of its refusals must leave everything in place.
 */
@Tag("integration")
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

  private static int purge(String folderId, boolean confirmed) {
    FolderPurgeContent task = new FolderPurgeContent() {
      @Override
      public void init() {
        initMongoCollectionNames();
      }

      @Override
      protected boolean confirmPurge(String folderName, String id, int childArtifactCount) {
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
