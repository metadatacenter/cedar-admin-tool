package org.metadatacenter.admin.task;

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
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.security.model.user.CedarSuperRole;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.CedarUserUIPreferences;
import org.metadatacenter.server.security.util.CedarUserUtil;
import org.metadatacenter.util.json.JsonMapper;
import org.metadatacenter.util.test.EmbeddedCedarNeo4j;

import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The tasks that bring CEDAR's user records in line with Keycloak, which owns accounts and their
 * roles. Each reads Keycloak's users through one method, which the tests answer with fixtures, and
 * writes to the graph, which is real.
 */
@Tag("integration")
class UserProfileTasksTest {

  private static CedarConfig cedarConfig;

  @BeforeAll
  static void startTheGraph() {
    EmbeddedCedarNeo4j.startRedirectAndSeed(SystemComponent.ADMIN_TOOL);
    cedarConfig = CedarConfig.getInstance(CedarEnvironmentVariableProvider.getFor(SystemComponent.ADMIN_TOOL));
  }

  /**
   * graphDb-createAllUsers gives every Keycloak user with a CEDAR role a user record, membership of
   * Everybody and a home folder. A user without a recognized role is left out, and a second run
   * changes nothing for users that exist.
   */
  @Test
  void createAllUsersCreatesTheUsersKeycloakGivesARole() throws Exception {
    UserRepresentation normal = keycloakUser("Nora", CedarSuperRole.NORMAL.getValue());
    UserRepresentation admin = keycloakUser("Ada", CedarSuperRole.BUILT_IN_ADMIN.getValue());
    UserRepresentation unknown = keycloakUser("Una", "someOtherRealmRole");

    assertEquals(0, createAllUsers(List.of(normal, admin, unknown)).execute());
    assertEquals(0, createAllUsers(List.of(normal, admin, unknown)).execute(), "a second run should succeed too");

    for (UserRepresentation created : List.of(normal, admin)) {
      CedarUser user = user(created);
      assertNotNull(user, created.getFirstName() + " should have a user record");
      assertEquals(created.getEmail(), user.getEmail());
      assertNotNull(CedarDataServices.getInstance().getFolderServiceSession(CedarRequestContextFactory.fromUser(user))
          .findHomeFolderOf(), created.getFirstName() + " should have a home folder");
    }
    assertEquals(new HashSet<>(CedarUserUtil.getRolesForType(cedarConfig.getBlueprintUserProfile(), CedarSuperRole.BUILT_IN_ADMIN)),
        new HashSet<>(user(admin).getRoles()), "the administrator should hold the administrator's roles");
    assertNull(user(unknown), "a user without a CEDAR role should not be created");
  }

  /** userProfile-updateAll-updatePermissions rewrites each user's roles from the ones Keycloak now gives. */
  @Test
  void updatePermissionsTakesEachUsersRolesFromKeycloak() throws Exception {
    UserRepresentation promoted = keycloakUser("Pat", CedarSuperRole.NORMAL.getValue());
    assertEquals(0, createAllUsers(List.of(promoted)).execute());
    var normalRoles = new HashSet<>(user(promoted).getRoles());

    promoted.setRealmRoles(List.of(CedarSuperRole.BUILT_IN_ADMIN.getValue()));
    UserProfileUpdateAllUpdatePermissions update = new UserProfileUpdateAllUpdatePermissions() {
      @Override
      public void init() {
      }

      @Override
      protected List<UserRepresentation> listAllUsersFromKeycloak() {
        return List.of(promoted);
      }
    };
    prepare(update, "userProfile-updateAll-updatePermissions");
    assertEquals(0, update.execute());

    var roles = new HashSet<>(user(promoted).getRoles());
    assertEquals(new HashSet<>(CedarUserUtil.getRolesForType(cedarConfig.getBlueprintUserProfile(), CedarSuperRole.BUILT_IN_ADMIN)),
        roles, "the user's roles should now be the administrator's");
    assertNotEquals(normalRoles, roles);
  }

  /** userProfile-reset puts one user's interface preferences back to the blueprint's. */
  @Test
  void resetUiPreferencesRestoresTheBlueprint() throws Exception {
    UserRepresentation person = keycloakUser("Ren", CedarSuperRole.NORMAL.getValue());
    assertEquals(0, createAllUsers(List.of(person)).execute());
    CedarUser user = user(person);
    CedarUserUIPreferences blueprint = JsonMapper.TOLERANT_MAPPER.readValue(
        JsonMapper.STRICT_MAPPER.writeValueAsString(cedarConfig.getBlueprintUserProfile().getUiPreferences()),
        CedarUserUIPreferences.class);
    CedarUserUIPreferences changed = JsonMapper.TOLERANT_MAPPER.readValue(
        JsonMapper.STRICT_MAPPER.writeValueAsString(user.getUiPreferences()), CedarUserUIPreferences.class);
    changed.getFolderView().setSortBy("notTheBlueprintsSortField");
    CedarDataServices.getInstance().getNeoUserService().replaceUiPreferences(user.getResourceId(), changed);
    assertNotEquals(json(blueprint), json(user(person).getUiPreferences()), "the fixture should differ from the blueprint");

    UserProfileResetUIPreferences reset = new UserProfileResetUIPreferences() {
      @Override
      public void init() {
      }
    };
    prepare(reset, "userProfile-reset");
    assertEquals(-1, reset.execute(), "the user's UUID is required");
    prepare(reset, "userProfile-reset", person.getId());
    assertEquals(0, reset.execute());

    assertEquals(json(blueprint), json(user(person).getUiPreferences()));
  }

  private static GraphDbCreateAllUsers createAllUsers(List<UserRepresentation> keycloakUsers) {
    GraphDbCreateAllUsers task = new GraphDbCreateAllUsers() {
      @Override
      public void init() {
      }

      @Override
      protected List<UserRepresentation> listAllUsersFromKeycloak() {
        return keycloakUsers;
      }
    };
    prepare(task, "graphDb-createAllUsers");
    return task;
  }

  private static void prepare(AbstractCedarAdminTask task, String... arguments) {
    task.setArguments(arguments);
    task.injectConfig(cedarConfig);
    task.setOutput(new AdminOutput());
    task.init();
  }

  private static UserRepresentation keycloakUser(String name, String realmRole) {
    UserRepresentation user = new UserRepresentation();
    user.setId(UUID.randomUUID().toString());
    user.setUsername(name.toLowerCase() + "-" + user.getId().substring(0, 8));
    user.setFirstName(name);
    user.setLastName("Tester");
    user.setEmail(user.getUsername() + "@example.org");
    user.setRealmRoles(List.of(realmRole));
    user.setCreatedTimestamp(System.currentTimeMillis());
    return user;
  }

  private static CedarUser user(UserRepresentation keycloakUser) throws Exception {
    return CedarDataServices.getInstance().getNeoUserService()
        .findUser(CedarUserId.build(cedarConfig.getLinkedDataUtil().getUserId(keycloakUser.getId())));
  }

  private static String json(Object value) {
    return JsonMapper.STRICT_MAPPER.valueToTree(value).toString();
  }
}
