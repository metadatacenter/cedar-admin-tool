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
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.constant.KeycloakConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;


public class GetAdminUserKeycloakProfile implements CedarAdminTask {

  private String adminUserUUID;
  private String keycloakBaseURI;
  private String keycloakRealmName;
  private String cedarAdminUserName;
  private String cedarAdminUserPassword;
  private String keycloakClientId;
  private Logger logger = LoggerFactory.getLogger(GetAdminUserKeycloakProfile.class);
  private static List<String> description;

  static {
    description = new ArrayList<>();
    description.add("Reads cedar-admin user details from Keycloak.");
  }

  @Override
  public void setArguments(String[] args) {

  }

  @Override
  public void init(CedarConfig config) {
    adminUserUUID = config.getKeycloakConfig().getAdminUser().getUuid();

    cedarAdminUserName = config.getKeycloakConfig().getAdminUser().getUserName();
    cedarAdminUserPassword = config.getKeycloakConfig().getAdminUser().getPassword();
    keycloakClientId = config.getKeycloakConfig().getClientId();

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


  @Override
  public int execute() {
    UserRepresentation userRepresentation = getAdminUserFromKeycloak();
    if (userRepresentation == null) {
      logger.error(cedarAdminUserName + " user was not found on Keycloak");
    } else {
      logger.debug(cedarAdminUserName + " user was found on Keycloak");
      System.out.println(userRepresentation.getFirstName());
      System.out.println(userRepresentation.getLastName());
      System.out.println(userRepresentation.getId());
      System.out.println(userRepresentation.getEmail());
    }
    return 0;
  }

  @Override
  public List<String> getDescription() {
    return description;
  }

}
