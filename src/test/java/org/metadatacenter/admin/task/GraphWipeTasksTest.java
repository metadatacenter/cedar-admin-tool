package org.metadatacenter.admin.task;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.keycloak.representations.idm.UserRepresentation;
import org.metadatacenter.admin.util.AdminOutput;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.security.KeycloakUtils;
import org.metadatacenter.server.security.model.user.CedarSuperRole;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.util.test.EmbeddedCedarMongo;
import org.metadatacenter.util.test.EmbeddedCedarNeo4j;
import org.metadatacenter.util.test.TestAuthUtil;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The graph's two wipes, and system-reset, which runs a wipe of each store before rebuilding them. Each asks the
 * operator to confirm, and removes nothing without it. The wipes act as Keycloak's administrator, whom a local
 * Keycloak returns.
 * <p>
 * Every other class in this JVM shares the seeded graph, so the graph is rebuilt and seeded again afterwards.
 */
@Tag("integration")
class GraphWipeTasksTest {

  private static CedarConfig cedarConfig;
  private static StubKeycloak keycloak;

  @BeforeAll
  static void startTheStores() throws Exception {
    EmbeddedCedarMongo.startAndRedirectEnvironment();
    EmbeddedCedarNeo4j.startRedirectAndSeed(SystemComponent.ADMIN_TOOL);
    cedarConfig = CedarConfig.getInstance(CedarEnvironmentVariableProvider.getFor(SystemComponent.ADMIN_TOOL));
    CedarDataServices.initializeMongoClientFactoryForDocuments(cedarConfig.getArtifactServerConfig().getMongoConnection());
    keycloak = new StubKeycloak(KeycloakUtils.initKeycloak(cedarConfig).getKeycloakRealmName());
    String adminId = TestAuthUtil.getAdminUser(cedarConfig).getId();
    UserRepresentation admin = new UserRepresentation();
    admin.setId(adminId.substring(adminId.lastIndexOf('/') + 1));
    admin.setUsername(cedarConfig.getAdminUserConfig().getUserName());
    admin.setFirstName("Admin");
    admin.setLastName("Tester");
    admin.setEmail("admin@example.org");
    admin.setRealmRoles(List.of(CedarSuperRole.BUILT_IN_ADMIN.getValue()));
    admin.setCreatedTimestamp(System.currentTimeMillis());
    keycloak.user(admin);
  }

  @AfterAll
  static void rebuildTheSharedGraph() {
    keycloak.close();
    EmbeddedCedarNeo4j.stopAndReset();
    EmbeddedCedarNeo4j.startRedirectAndSeed(SystemComponent.ADMIN_TOOL);
  }

  @Test
  void eachWipeRemovesWhatItNamesOnceConfirmed() throws Exception {
    assertEquals(-1, run(new GraphDbWipeCategories(), "no"));
    assertNotNull(rootCategory(), "a declined wipe keeps the categories");

    assertEquals(0, run(new GraphDbWipeCategories(), AbstractCedarAdminTask.CONFIRM));
    assertNull(rootCategory(), "the categories are gone");
    assertNotNull(testUser1(), "the users are not categories");

    // system-reset wipes the artifact store first, which asks again; declining it stops the reset there.
    assertEquals(-1, run(new SystemReset(), AbstractCedarAdminTask.CONFIRM, "no"));
    assertNotNull(testUser1(), "a reset stopped at its first step leaves the graph alone");

    assertEquals(0, run(new GraphDbWipeAll(), AbstractCedarAdminTask.CONFIRM));
    assertNull(testUser1(), "every CEDAR node is gone");
  }

  private static int run(AbstractKeycloakReadingTask task, String... typed) throws Exception {
    return Console.run(() -> {
      task.setArguments(new String[]{"task"});
      task.injectConfig(cedarConfig);
      task.setOutput(new AdminOutput());
      task.init();
      if (task.kcInfo != null) {
        task.kcInfo.setKeycloakBaseURI(keycloak.url());
      }
      return task.execute();
    }, typed).result();
  }

  private static Object rootCategory() throws Exception {
    CedarUser admin = TestAuthUtil.getAdminUser(cedarConfig);
    return CedarDataServices.getInstance().getCategoryServiceSession(CedarRequestContextFactory.fromUser(admin))
        .getRootCategory();
  }

  private static CedarUser testUser1() throws Exception {
    return CedarDataServices.getInstance().getNeoUserService().findUser(TestAuthUtil.getTestUser1(cedarConfig).getResourceId());
  }
}
