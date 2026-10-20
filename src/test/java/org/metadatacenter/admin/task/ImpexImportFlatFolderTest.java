package org.metadatacenter.admin.task;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.metadatacenter.admin.StubHttpServer;
import org.metadatacenter.admin.task.importflatfolder.ImpexImportFlatFolder;
import org.metadatacenter.admin.util.AdminOutput;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentSource;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.util.json.JsonMapper;
import org.metadatacenter.util.test.EmbeddedCedarNeo4j;
import org.metadatacenter.util.test.TestAuthUtil;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * impex-importFlatFolder posts the artifacts in a local folder, each a pair of files holding its node and its
 * document, into a folder of a running installation, on behalf of an owner who becomes their creator. A file without
 * its pair is skipped, and a document the resource server refuses fails the task. Here the resource server's address
 * points at a local stand-in.
 */
@Tag("integration")
class ImpexImportFlatFolderTest {

  private static StubHttpServer resourceServer;
  private static Map<String, String> environmentBefore;
  private static CedarConfig cedarConfig;
  private static CedarUser owner;
  private static String targetFolderId;

  @BeforeAll
  static void pointAtAStandInResourceServer() throws Exception {
    EmbeddedCedarNeo4j.startRedirectAndSeed(SystemComponent.ADMIN_TOOL);
    resourceServer = new StubHttpServer();
    environmentBefore = CedarEnvironmentSource.hasOverride() ? CedarEnvironmentSource.getAll() : null;
    Map<String, String> environment = new HashMap<>(CedarEnvironmentSource.getAll());
    environment.put("CEDAR_RESOURCE_SERVER_HOST", resourceServer.host());
    environment.put("CEDAR_RESOURCE_HTTP_PORT", String.valueOf(resourceServer.port()));
    CedarEnvironmentSource.setOverride(environment);
    cedarConfig = CedarConfig.getInstance(CedarEnvironmentVariableProvider.getFor(SystemComponent.ADMIN_TOOL));
    owner = TestAuthUtil.getAdminUser(cedarConfig);
    targetFolderId = CedarDataServices.getInstance()
        .getFolderServiceSession(CedarRequestContextFactory.fromUser(TestAuthUtil.getTestUser1(cedarConfig)))
        .findHomeFolderOf().getId();
  }

  @AfterAll
  static void restoreTheEnvironment() {
    CedarEnvironmentSource.setOverride(environmentBefore);
    resourceServer.close();
  }

  @Test
  void eachCompletePairIsPostedOnTheOwnersBehalf(@TempDir Path source) throws Exception {
    pair(source, "t1", "template");
    Files.writeString(source.resolve("orphan-content.json"), "{\"@id\": \"orphan\"}");
    resourceServer.answer("POST", "/templates", 201, "{}");

    String ownerUuid = owner.getId().substring(owner.getId().lastIndexOf('/') + 1);
    assertEquals(0, run(source.toString(), targetFolderId, ownerUuid), "the owner is named by UUID");

    List<StubHttpServer.Request> posts = resourceServer.requests("POST", "/templates");
    assertEquals(1, posts.size(), "the file without its pair is skipped");
    StubHttpServer.Request post = posts.get(0);
    assertEquals(owner.getFirstApiKeyAuthHeader(), post.authorization());
    assertTrue(post.query().startsWith("folder_id="), post.query());
    JsonNode body = JsonMapper.STRICT_MAPPER.readTree(post.body());
    assertFalse(body.has("_id") || body.has("parentId"), "the store's own fields are dropped: " + body);
    assertEquals(owner.getId(), body.get("pav:createdBy").asText(), "the owner becomes the creator");
    assertEquals("Imported t1", body.get("schema:name").asText());
  }

  @Test
  void aRefusedDocumentFailsTheTask(@TempDir Path source) throws Exception {
    pair(source, "t2", "template");
    resourceServer.answer("POST", "/templates", 400, "{\"message\": \"invalid\"}");

    assertEquals(-6, run(source.toString(), targetFolderId, owner.getId()), "the owner may be named by identifier");
  }

  @Test
  void eachMissingOrUnknownArgumentIsRefused(@TempDir Path source) throws Exception {
    Path file = Files.writeString(source.resolve("a-file"), "");
    String ownerId = owner.getId();
    assertEquals(-1, run(source.toString(), targetFolderId));
    assertEquals(-2, run(source.resolve("missing").toString(), targetFolderId, ownerId));
    assertEquals(-3, run(file.toString(), targetFolderId, ownerId));
    assertEquals(-4, run(source.toString(), "https://repo.metadatacenter.orgx/folders/00000000-0000-0000-0000-000000000000",
        ownerId));
    assertEquals(-5, run(source.toString(), targetFolderId, "00000000-0000-0000-0000-00000000dead"));
  }

  private static void pair(Path dir, String name, String resourceType) throws Exception {
    Files.writeString(dir.resolve(name + "-resource.json"), "{\"resourceType\": \"" + resourceType + "\"}");
    Files.writeString(dir.resolve(name + "-content.json"), "{\"_id\": \"mongo-id\", \"parentId\": \"p\", "
        + "\"@id\": \"https://repo.metadatacenter.orgx/templates/" + name + "\", \"schema:name\": \"Imported " + name + "\"}");
  }

  private static int run(String... arguments) throws Exception {
    String[] args = new String[arguments.length + 1];
    args[0] = "impex-importFlatFolder";
    System.arraycopy(arguments, 0, args, 1, arguments.length);
    return Console.run(() -> {
      ImpexImportFlatFolder task = new ImpexImportFlatFolder();
      task.setArguments(args);
      task.injectConfig(cedarConfig);
      task.setOutput(new AdminOutput());
      task.init();
      return task.execute();
    }).result();
  }
}
