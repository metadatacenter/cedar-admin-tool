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
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.idm.UserRepresentation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public abstract class AbstractKeycloakReadingTask extends AbstractCedarAdminTask {

  protected String adminUserUUID;
  protected String keycloakBaseURI;
  protected String keycloakRealmName;
  protected String cedarAdminUserPassword;
  protected String keycloakClientId;
  protected String cedarAdminUserName;
  private Logger logger = LoggerFactory.getLogger(AbstractKeycloakReadingTask.class);

  protected UserRepresentation getAdminUserFromKeycloak() {

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

}