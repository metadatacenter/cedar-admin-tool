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
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.server.security.KeycloakUtils;
import org.metadatacenter.server.security.model.user.CedarSuperRole;
import org.metadatacenter.util.test.EmbeddedCedarNeo4j;
import org.metadatacenter.util.test.TestAuthUtil;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keycloak owns CEDAR's accounts, and several tasks read them through its admin REST interface: every user, a page
 * at a time, with each user's roles; one user by identifier; and the administrator by name. A local Keycloak stands
 * in for the real one, and each task is pointed at it after reading its configuration.
 */
@Tag("integration")
class KeycloakReadingTasksTest {

  private static CedarConfig cedarConfig;
  private static StubKeycloak keycloak;

  @BeforeAll
  static void startTheGraphAndKeycloak() throws Exception {
    EmbeddedCedarNeo4j.startRedirectAndSeed(SystemComponent.ADMIN_TOOL);
    cedarConfig = CedarConfig.getInstance(CedarEnvironmentVariableProvider.getFor(SystemComponent.ADMIN_TOOL));
    keycloak = new StubKeycloak(KeycloakUtils.initKeycloak(cedarConfig).getKeycloakRealmName());
  }

  @AfterAll
  static void stopKeycloak() {
    keycloak.close();
  }

  /** userProfile-listAll prints every user, reading past the first page of fifty, with each user's roles. */
  @Test
  void everyUserIsListedWithItsRoles() throws Exception {
    for (int i = 0; i < 51; i++) {
      keycloak.user(user("listed-" + i, CedarSuperRole.NORMAL.getValue()));
    }
    int pagesBefore = keycloak.pagesRead();

    Console.Run<Integer> run = Console.run(() -> pointedAtKeycloak(new UserProfileListAll()).execute());

    assertEquals(0, run.result());
    assertTrue(keycloak.pagesRead() - pagesBefore >= 2, "the second page should be read");
    for (int i = 0; i < 51; i++) {
      assertTrue(run.printed().contains("Username   : listed-" + i + "\n"), "listed-" + i);
    }
    assertTrue(run.printed().contains("Realm roles: [" + CedarSuperRole.NORMAL.getValue() + "]"));
  }

  /** graphDb-createAllUsers, reading Keycloak itself, creates the users it finds there. */
  @Test
  void theUsersKeycloakHoldsAreCreated() throws Exception {
    UserRepresentation newcomer = user("newcomer", CedarSuperRole.NORMAL.getValue());
    keycloak.user(newcomer);

    assertEquals(0, Console.run(() -> pointedAtKeycloak(new GraphDbCreateAllUsers()).execute()).result());

    assertNotNull(CedarDataServices.getInstance().getNeoUserService()
        .findUser(CedarUserId.build(cedarConfig.getLinkedDataUtil().getUserId(newcomer.getId()))));
  }

  /**
   * userProfile-getAdmin shows the administrator as the configuration, the graph and Keycloak each know it. The
   * password and the API key it shows are masked.
   */
  @Test
  void theAdministratorIsShownFromEachSourceWithItsSecretsMasked() throws Exception {
    String adminId = TestAuthUtil.getAdminUser(cedarConfig).getId();
    String adminUuid = adminId.substring(adminId.lastIndexOf('/') + 1);

    Console.Run<Integer> missing = Console.run(() -> pointedAtKeycloak(new UserProfileGetAdmin()).execute());
    assertEquals(0, missing.result());
    assertTrue(missing.printed().contains("user was found in neo4j"), missing.printed());
    assertTrue(missing.printed().contains("user was not found on Keycloak"), missing.printed());

    UserRepresentation admin = user(cedarConfig.getAdminUserConfig().getUserName(), CedarSuperRole.BUILT_IN_ADMIN.getValue());
    admin.setId(adminUuid);
    keycloak.user(admin);
    Console.Run<Integer> found = Console.run(() -> pointedAtKeycloak(new UserProfileGetAdmin()).execute());
    assertTrue(found.printed().contains("user was found on Keycloak"), found.printed());
    assertTrue(found.printed().contains("UUID       : " + adminUuid));

    Console.Run<UserProfileGetAdmin> init = Console.run(() -> {
      UserProfileGetAdmin task = new UserProfileGetAdmin();
      prepare(task);
      return task;
    });
    String apiKey = cedarConfig.getAdminUserConfig().getApiKey();
    assertFalse(init.printed().contains("cedarAdminUserApiKey  : " + apiKey), "the API key is masked");
    assertTrue(init.printed().contains("cedarAdminUserApiKey  : " + AbstractCedarAdminTask.masked(apiKey, 4)));
    assertEquals("********", AbstractCedarAdminTask.masked("abcdefgh", 4), "a short secret is hidden whole");
    assertEquals("********ijkl", AbstractCedarAdminTask.masked("abcdefghijkl", 4));
    assertEquals("(not set)", AbstractCedarAdminTask.masked("", 4));
    String password = cedarConfig.getAdminUserConfig().getPassword();
    if (password != null && !password.isEmpty()) {
      assertFalse(init.printed().contains("cedarAdminUserPassword: " + password), "the password is masked");
    }
  }

  private static <T extends AbstractKeycloakReadingTask> T pointedAtKeycloak(T task) {
    prepare(task);
    task.kcInfo.setKeycloakBaseURI(keycloak.url());
    return task;
  }

  private static void prepare(AbstractCedarAdminTask task) {
    task.setArguments(new String[]{"task"});
    task.injectConfig(cedarConfig);
    task.setOutput(new AdminOutput());
    task.init();
  }

  private static UserRepresentation user(String userName, String role) {
    UserRepresentation user = new UserRepresentation();
    user.setId(UUID.randomUUID().toString());
    user.setUsername(userName);
    user.setFirstName(userName);
    user.setLastName("Tester");
    user.setEmail(userName + "@example.org");
    user.setRealmRoles(List.of(role));
    user.setCreatedTimestamp(System.currentTimeMillis());
    return user;
  }
}
