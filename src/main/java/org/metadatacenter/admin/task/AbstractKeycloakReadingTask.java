package org.metadatacenter.admin.task;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.metadatacenter.admin.util.AdminOutput;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.constant.CedarConstants;
import org.metadatacenter.server.security.KeycloakUtilInfo;
import org.metadatacenter.server.security.KeycloakUtils;

import java.security.KeyStore;
import java.security.KeyStoreException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractKeycloakReadingTask extends AbstractCedarAdminTask {

  protected KeycloakUtilInfo kcInfo;

  protected void initKeycloak(CedarConfig cedarConfig) {
    kcInfo = KeycloakUtils.initKeycloak(cedarConfig);
  }

  protected UserRepresentation getUserFromKeycloak(String userUUID) {
    Keycloak kc = KeycloakUtils.buildKeycloak(kcInfo);
    UserResource userResource = kc.realm(kcInfo.getKeycloakRealmName()).users().get(userUUID);
    try {
      return userResource.toRepresentation();
    } catch (Exception e) {
      out.error("Error while reading userResource from Keycloak");
      out.error(e);
      return null;
    }
  }

  protected List<UserRepresentation> listAllUsersFromKeycloak() {
    Keycloak kc = KeycloakUtils.buildKeycloak(kcInfo);
    RealmResource realm = kc.realm(kcInfo.getKeycloakRealmName());
    out.info("Start reading the users from Keycloak");
    List<UserRepresentation> users = new ArrayList<>();
    int batchSize = 50;
    boolean readMore = true;
    while (readMore) {
      List<UserRepresentation> partialUsers = realm.users().search(null, users.size(), batchSize);
      out.info("Read " + partialUsers.size() + " users from " + users.size());
      if (partialUsers.size() > 0) {
        users.addAll(partialUsers);
      } else {
        readMore = false;
      }
    }
    out.info("Read all users from Keycloak");
    out.info("Read roles for users");
    int i = 0;
    for (UserRepresentation ur : users) {
      UserResource userResource = realm.users().get(ur.getId());
      List<RoleRepresentation> roleRepresentations = userResource.roles().realmLevel().listEffective();
      List<String> realmRoles = new ArrayList<>();
      for (RoleRepresentation rr : roleRepresentations) {
        realmRoles.add(rr.getName());
      }
      ur.setRealmRoles(realmRoles);
      if (i % batchSize == 0) {
        out.info("Read roles for " + i + " users");
      }
      i++;
    }
    return users;
  }

  protected void printOutUser(AdminOutput out, UserRepresentation ur) {
    Instant createdInstant = Instant.ofEpochMilli(ur.getCreatedTimestamp());
    out.println("First name : " + ur.getFirstName());
    out.println("Last name  : " + ur.getLastName());
    out.println("UUID       : " + ur.getId());
    out.println("Username   : " + ur.getUsername());
    out.println("Email      : " + ur.getEmail());
    out.println("Enabled    : " + ur.isEnabled());
    out.println("Realm roles: " + ur.getRealmRoles());
    out.println("Created    : " + CedarConstants.xsdDateTimeFormatter.format(createdInstant));
  }

}
