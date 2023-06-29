package org.metadatacenter.admin.task;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.metadatacenter.admin.util.Color;
import org.metadatacenter.config.BlueprintUIPreferences;
import org.metadatacenter.id.CedarUserId;
import org.metadatacenter.server.jsonld.LinkedDataUtil;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.model.user.CedarUserUIPreferences;
import org.metadatacenter.server.service.UserService;
import org.metadatacenter.util.json.JsonMapper;

public class UserProfileResetUIPreferences extends AbstractKeycloakReadingTask {

  public UserProfileResetUIPreferences() {
    description.add("Reset user profile UI settings in Neo4j.");
  }

  @Override
  public void init() {
    initKeycloak(cedarConfig);

    out.println();
    out.println("Data from config:", Color.YELLOW);
    out.printIndented("keycloakClientId      : " + kcInfo.getKeycloakClientId());
    out.printIndented("keycloakRealmName     : " + kcInfo.getKeycloakRealmName());
    out.printIndented("keycloakBaseURI       : " + kcInfo.getKeycloakBaseURI());
  }

  @Override
  public int execute() {
    if (arguments.size() != 2) {
      out.error("A user UUID must be passed for this task:");
      out.info("Usage:");
      out.info("$ cedarat userProfile-reset UUID");
      return -1;
    }

    String uuid = arguments.get(1);
    LinkedDataUtil ldu = cedarConfig.getLinkedDataUtil();
    String userTextId = ldu.getUserId(uuid);
    CedarUserId userId = CedarUserId.build(userTextId);

    out.println();
    out.println("Data from Neo4j:", Color.YELLOW);

    UserService userService = getNeoUserService();

    CedarUser user = null;
    boolean exceptionWhileReading = false;
    try {
      user = userService.findUser(userId);
    } catch (Exception e) {
      out.error("Error while reading user for UUID: " + kcInfo.getCedarAdminUserApiKey(), e);
      exceptionWhileReading = true;
    }

    if (user == null && !exceptionWhileReading) {
      out.printIndented(userId.getId() + " user was not found in neo4j", Color.RED);
    } else {
      out.printIndented(user.getEmail() + " user was found in neo4j", Color.GREEN);
      String serializedOld = "";
      try {
        serializedOld = JsonMapper.PRETTY_MAPPER.writeValueAsString(user.getUiPreferences());
      } catch (JsonProcessingException e) {
        out.error("Error while serializing old ui preferences", e);
      }
      String serializedNew = "";
      try {
        BlueprintUIPreferences blueprintUiPreferences = cedarConfig.getBlueprintUserProfile().getUiPreferences();
        String serializedBlue = JsonMapper.MAPPER.writeValueAsString(blueprintUiPreferences);
        CedarUserUIPreferences uiPreferences = JsonMapper.MAPPER.readValue(serializedBlue,
            CedarUserUIPreferences.class);
        user.setUiPreferences(uiPreferences);
        userService.updateUser(user);
        serializedNew = JsonMapper.PRETTY_MAPPER.writeValueAsString(user.getUiPreferences());
      } catch (JsonProcessingException e) {
        out.error("Error while serializing new ui preferences", e);
      }

      out.printSeparator();
      out.println("Old UI preferences:\n" + serializedOld);
      out.printSeparator();
      out.println("New UI preferences:\n" + serializedNew);
      out.printSeparator();
    }

    return 0;
  }

}
