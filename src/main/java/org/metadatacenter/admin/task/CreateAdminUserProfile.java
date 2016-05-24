package org.metadatacenter.admin.task;

import org.codehaus.jackson.JsonProcessingException;
import org.codehaus.jackson.jaxrs.JacksonJaxbJsonProvider;
import org.codehaus.jackson.jaxrs.JacksonJsonProvider;
import org.codehaus.jackson.map.DeserializationContext;
import org.codehaus.jackson.map.DeserializationProblemHandler;
import org.codehaus.jackson.map.JsonDeserializer;
import org.codehaus.jackson.map.ObjectMapper;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.adapters.KeycloakDeploymentBuilder;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.idm.UserRepresentation;
import org.metadatacenter.admin.config.CedarConfig;
import org.metadatacenter.constant.KeycloakConstants;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.security.util.CedarUserUtil;
import org.metadatacenter.server.service.UserService;
import org.metadatacenter.server.service.mongodb.UserServiceMongoDB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;


public class CreateAdminUserProfile implements CedarAdminTask {

  private String adminUserUUID;
  private String mongoDatabaseName;
  private String usersCollectionName;
  private String keycloakBaseURI;
  private String keycloakRealmName;
  private String cedarAdminUserName;
  private String cedarAdminUserPassword;
  private String keycloakClientId;
  private static UserService userService;
  private Logger logger = LoggerFactory.getLogger(CreateAdminUserProfile.class);

  @Override
  public void setArguments(String[] args) {

  }

  @Override
  public void init(CedarConfig config) {
    adminUserUUID = config.getKeycloakConfig().getAdminUser().getUuid();

    mongoDatabaseName = config.getMongoConfig().getDatabaseName();
    usersCollectionName = config.getMongoConfig().getCollections().get(CedarNodeType.USER.getValue());

    cedarAdminUserName = config.getKeycloakConfig().getAdminUser().getUserName();
    cedarAdminUserPassword = config.getKeycloakConfig().getAdminUser().getPassword();
    keycloakClientId = config.getKeycloakConfig().getClientId();

    userService = new UserServiceMongoDB(mongoDatabaseName, usersCollectionName);

    InputStream keycloakConfig = Thread.currentThread().getContextClassLoader().getResourceAsStream(KeycloakConstants
        .JSON);
    KeycloakDeployment keycloakDeployment = KeycloakDeploymentBuilder.build(keycloakConfig);

    keycloakRealmName = keycloakDeployment.getRealm();
    keycloakBaseURI = keycloakDeployment.getAuthServerBaseUrl();
  }

  private UserRepresentation getAdminUserFromKeycloak() {

    ObjectMapper m = new ObjectMapper();
    JacksonJsonProvider jacksonJsonProvider =
        new JacksonJaxbJsonProvider();
    jacksonJsonProvider.setMapper(m);
    m.getDeserializationConfig().addHandler(new DeserializationProblemHandler() {
      @Override
      public boolean handleUnknownProperty(DeserializationContext ctxt, JsonDeserializer<?> deserializer, Object
          beanOrClass, String propertyName) throws IOException, JsonProcessingException {
        if ("access_token".equals(propertyName)) {
          if (beanOrClass instanceof AccessTokenResponse) {
            logger.info("Found token, injecting it.");
            AccessTokenResponse atr = (AccessTokenResponse) beanOrClass;
            String text = ctxt.getParser().getText();
            atr.setToken(text);
          }
          return true;
        } else {
          boolean success = super.handleUnknownProperty(ctxt, deserializer, beanOrClass, propertyName);
          if (success) {
            logger.info("Skipping property:" + propertyName + "=>" + ctxt.getParser().getText());
          }
          return true;
        }
      }
    });

    ResteasyClient resteasyClient = new ResteasyClientBuilder().connectionPoolSize(10).register(jacksonJsonProvider)
        .build();

    Keycloak kc = KeycloakBuilder.builder()
        .serverUrl(keycloakBaseURI)
        .realm(keycloakRealmName)
        .username(cedarAdminUserName)
        .password(cedarAdminUserPassword)
        .clientId(keycloakClientId)
        .resteasyClient(resteasyClient)
        .build();

    UserResource userResource = kc.realm(keycloakRealmName).users().get(adminUserUUID);
    return userResource.toRepresentation();
  }


  private void createAdminUserProfileInMongoDb(UserRepresentation userRepresentation) {
    CedarUser user = CedarUserUtil.createUserFromBlueprint(adminUserUUID,
        userRepresentation.getFirstName() + " " + userRepresentation.getLastName());

    try {
      CedarUser u = userService.createUser(user);
    } catch (IOException e) {
      logger.error("Error while creating " + cedarAdminUserName + " user", e);
    }
  }

  @Override
  public int execute() {
    UserRepresentation userRepresentation = getAdminUserFromKeycloak();
    if (userRepresentation == null) {
      logger.error(cedarAdminUserName + " user was not found on Keycloak");
    } else {
      createAdminUserProfileInMongoDb(userRepresentation);
    }
    return 0;
  }


}
