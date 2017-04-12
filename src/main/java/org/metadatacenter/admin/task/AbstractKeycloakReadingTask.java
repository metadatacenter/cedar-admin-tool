package org.metadatacenter.admin.task;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.DeserializationProblemHandler;
import com.fasterxml.jackson.jaxrs.json.JacksonJaxbJsonProvider;
import com.fasterxml.jackson.jaxrs.json.JacksonJsonProvider;
import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.keycloak.adapters.KeycloakDeployment;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.KeycloakBuilder;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.representations.AccessTokenResponse;
import org.keycloak.representations.idm.UserRepresentation;
import org.metadatacenter.admin.util.AdminOutput;
import org.metadatacenter.server.security.KeycloakDeploymentProvider;

import java.io.IOException;
import java.util.List;

public abstract class AbstractKeycloakReadingTask extends AbstractCedarAdminTask {

  protected String keycloakBaseURI;
  protected String keycloakRealmName;
  protected String keycloakClientId;
  protected String cedarAdminUserName;
  protected String cedarAdminUserPassword;
  protected String cedarAdminUserApiKey;

  protected void initKeycloak() {
    cedarAdminUserName = cedarConfig.getAdminUserConfig().getUserName();
    cedarAdminUserPassword = cedarConfig.getAdminUserConfig().getPassword();
    cedarAdminUserApiKey = cedarConfig.getAdminUserConfig().getApiKey();
    keycloakClientId = cedarConfig.getKeycloakConfig().getClientId();

    KeycloakDeploymentProvider keycloakDeploymentProvider = new KeycloakDeploymentProvider();
    KeycloakDeployment keycloakDeployment = keycloakDeploymentProvider.buildDeployment(cedarConfig.getKeycloakConfig());

    keycloakRealmName = keycloakDeployment.getRealm();
    keycloakBaseURI = keycloakDeployment.getAuthServerBaseUrl();
  }

  private JacksonJsonProvider getCustomizedJacksonJsonProvider() {
    ObjectMapper m = new ObjectMapper();
    JacksonJsonProvider jacksonJsonProvider = new JacksonJaxbJsonProvider();
    jacksonJsonProvider.setMapper(m);

    m.addHandler(new DeserializationProblemHandler() {
      @Override
      public boolean handleUnknownProperty(DeserializationContext ctxt, JsonParser jp, JsonDeserializer<?>
          deserializer, Object beanOrClass, String propertyName) throws IOException {
        out.info("Run into unknown property:" + propertyName + "=>" + ctxt.getParser().getText());
        if ("access_token".equals(propertyName)) {
          if (beanOrClass instanceof AccessTokenResponse) {
            AccessTokenResponse atr = (AccessTokenResponse) beanOrClass;
            String text = ctxt.getParser().getText();
            atr.setToken(text);
          }
        } else {
          super.handleUnknownProperty(ctxt, jp, deserializer, beanOrClass, propertyName);
        }
        return true;
      }
    });
    return jacksonJsonProvider;
  }

  protected Keycloak buildKeycloak() {
    JacksonJsonProvider jacksonJsonProvider = getCustomizedJacksonJsonProvider();

    ResteasyClient resteasyClient = new ResteasyClientBuilder().connectionPoolSize(10).register(jacksonJsonProvider)
        .build();

    return KeycloakBuilder.builder()
        .serverUrl(keycloakBaseURI)
        .realm(keycloakRealmName)
        .username(cedarAdminUserName)
        .password(cedarAdminUserPassword)
        .clientId(keycloakClientId)
        .resteasyClient(resteasyClient)
        .build();
  }

  protected UserRepresentation getUserFromKeycloak(String userUUID) {
    Keycloak kc = buildKeycloak();
    UserResource userResource = kc.realm(keycloakRealmName).users().get(userUUID);
    return userResource.toRepresentation();
  }

  protected List<UserRepresentation> listAllUsersFromKeycloak() {
    Keycloak kc = buildKeycloak();
    return kc.realm(keycloakRealmName).users().search(null, null, null);
  }

  protected void printOutUser(AdminOutput out, UserRepresentation ur) {
    out.println("First name: " + ur.getFirstName());
    out.println("Last name : " + ur.getLastName());
    out.println("UUID      : " + ur.getId());
    out.println("Username  : " + ur.getUsername());
    out.println("Email     : " + ur.getEmail());
    out.println("Enabled   : " + ur.isEnabled());
  }

}