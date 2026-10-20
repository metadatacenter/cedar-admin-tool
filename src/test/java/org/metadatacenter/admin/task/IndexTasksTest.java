package org.metadatacenter.admin.task;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.metadatacenter.admin.StubHttpServer;
import org.metadatacenter.admin.util.AdminOutput;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentSource;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.util.json.JsonMapper;
import org.metadatacenter.util.test.EmbeddedCedarNeo4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Four tasks ask the resource server to rebuild or empty the search and rules indexes, as the administrator. The
 * request either starts the work, and the task succeeds, or the server refuses it, and the task fails, so that a
 * script or system-reset that runs it stops there. Here the resource server's address points at a local stand-in.
 */
@Tag("integration")
class IndexTasksTest {

  private record Case(String key, Supplier<AbstractCedarAdminTaskWithAdminUser> task, String path, Boolean force) {
  }

  @SuppressWarnings("deprecation")
  private static final List<Case> CASES = List.of(
      new Case("search-regenerateIndex", SearchRegenerateIndex::new, "/command/regenerate-search-index", true),
      new Case("search-generateEmptyIndex", SearchGenerateEmptyIndex::new, "/command/generate-empty-search-index", null),
      new Case("rules-regenerateIndex", RulesRegenerateIndex::new, "/command/regenerate-rules-index", true),
      new Case("rules-generateEmptyIndex", RulesGenerateEmptyIndex::new, "/command/generate-empty-rules-index", null));

  private static StubHttpServer resourceServer;
  private static Map<String, String> environmentBefore;
  private static CedarConfig cedarConfig;

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
  }

  @AfterAll
  static void restoreTheEnvironment() {
    CedarEnvironmentSource.setOverride(environmentBefore);
    resourceServer.close();
  }

  @Test
  void eachTaskSucceedsWhenTheServerStartsTheWork() throws Exception {
    for (Case c : CASES) {
      resourceServer.answer("POST", c.path(), 200, "{}");
      assertEquals(0, run(c), c.key());

      StubHttpServer.Request request = resourceServer.requests("POST", c.path()).get(0);
      assertEquals("apiKey " + cedarConfig.getAdminUserConfig().getApiKey(), request.authorization(), c.key());
      JsonNode body = JsonMapper.STRICT_MAPPER.readTree(request.body());
      assertEquals(c.force() == null ? 0 : 1, body.size(), c.key() + ": " + request.body());
      if (c.force() != null) {
        assertEquals(c.force(), body.get("force").asBoolean(), c.key());
      }
    }
  }

  @Test
  void eachTaskFailsWhenTheServerRefuses() throws Exception {
    for (Case c : CASES) {
      resourceServer.answer("POST", c.path(), 503, "{}");
      assertEquals(-1, run(c), c.key());
    }
  }

  private static int run(Case c) throws Exception {
    return Console.run(() -> {
      AbstractCedarAdminTaskWithAdminUser task = c.task().get();
      task.setArguments(new String[]{c.key()});
      task.injectConfig(cedarConfig);
      task.setOutput(new AdminOutput());
      task.init();
      return task.execute();
    }).result();
  }
}
