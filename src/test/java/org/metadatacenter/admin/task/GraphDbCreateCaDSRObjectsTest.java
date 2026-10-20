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
import org.metadatacenter.model.folderserver.basic.FolderServerCategory;
import org.metadatacenter.rest.context.CedarRequestContext;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.CategoryServiceSession;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.util.test.EmbeddedCedarNeo4j;
import org.metadatacenter.util.test.TestAuthUtil;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * graphDb-createCaDSRObjects prepares CEDAR for the caDSR updater: it gives Keycloak's caDSR administrator a CEDAR
 * user record and a home folder, and creates the caDSR category under the root category, so that the updater can
 * file NCI's categories beneath it. Running it again changes nothing.
 */
@Tag("integration")
class GraphDbCreateCaDSRObjectsTest {

  private static CedarConfig cedarConfig;

  @BeforeAll
  static void startTheGraph() {
    EmbeddedCedarNeo4j.startRedirectAndSeed(SystemComponent.ADMIN_TOOL);
    cedarConfig = CedarConfig.getInstance(CedarEnvironmentVariableProvider.getFor(SystemComponent.ADMIN_TOOL));
  }

  @Test
  void theCaDsrAdministratorGetsAUserAHomeFolderAndTheCaDsrCategory() throws Exception {
    UserRepresentation caDSRAdmin = keycloakUser(cedarConfig.getCaDSRAdminUserConfig().getUserName());

    assertEquals(0, run(List.of(keycloakUser("someone-else"), caDSRAdmin)));
    assertEquals(0, run(List.of(caDSRAdmin)), "a second run succeeds too");

    CedarUser user = CedarDataServices.getInstance().getNeoUserService()
        .findUser(CedarUserId.build(cedarConfig.getLinkedDataUtil().getUserId(caDSRAdmin.getId())));
    assertNotNull(user, "the caDSR administrator should have a user record");
    CedarRequestContext context = CedarRequestContextFactory.fromUser(user);
    assertNotNull(CedarDataServices.getInstance().getFolderServiceSession(context).findHomeFolderOf());

    CategoryServiceSession categories = CedarDataServices.getInstance().getCategoryServiceSession(context);
    FolderServerCategory root = categories.getRootCategory();
    assertNotNull(root);
    FolderServerCategory caDSR = categories.getCategoryByParentAndName(root.getResourceId(),
        cedarConfig.getFolderStructureConfig().getCaDSRRootCategory().getName());
    assertNotNull(caDSR, "the caDSR category should sit under the root category");
  }

  @Test
  void aKeycloakWithoutTheCaDsrAdministratorIsRefused() {
    assertEquals(-1, run(List.of(keycloakUser("someone-else"))));
  }

  private static int run(List<UserRepresentation> keycloakUsers) {
    // The task acts as Keycloak's administrator, whom the seeded graph already holds.
    UserRepresentation admin = keycloakUser(cedarConfig.getAdminUserConfig().getUserName());
    String seededAdminId = TestAuthUtil.getAdminUser(cedarConfig).getId();
    admin.setId(seededAdminId.substring(seededAdminId.lastIndexOf('/') + 1));
    GraphDbCreateCaDSRObjects task = new GraphDbCreateCaDSRObjects() {
      @Override
      public void init() {
      }

      @Override
      protected List<UserRepresentation> listAllUsersFromKeycloak() {
        return keycloakUsers;
      }

      @Override
      protected UserRepresentation getAdminUserFromKeycloak() {
        return admin;
      }
    };
    task.setArguments(new String[]{"graphDb-createCaDSRObjects"});
    task.injectConfig(cedarConfig);
    task.setOutput(new AdminOutput());
    task.init();
    return task.execute();
  }

  private static UserRepresentation keycloakUser(String userName) {
    UserRepresentation user = new UserRepresentation();
    // The same name gives the same identifier, as Keycloak's would, so a second run finds the first run's user.
    user.setId(UUID.nameUUIDFromBytes(userName.getBytes()).toString());
    user.setUsername(userName);
    user.setFirstName(userName);
    user.setLastName("Tester");
    user.setEmail(userName + "@example.org");
    user.setCreatedTimestamp(System.currentTimeMillis());
    return user;
  }
}
