package org.metadatacenter.admin.task;

import org.metadatacenter.admin.util.AdminOutput;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.model.CedarResourceType;
import org.metadatacenter.server.jsonld.LinkedDataUtil;
import org.metadatacenter.server.service.UserService;

import java.io.BufferedReader;
import java.io.Console;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class AbstractCedarAdminTask implements ICedarAdminTask {

  protected List<String> arguments;
  protected final List<String> description = new ArrayList<>();
  protected AdminOutput out;
  protected CedarConfig cedarConfig;
  protected LinkedDataUtil linkedDataUtil;
  public static final String CONFIRM = "yes";

  protected String mongoDatabaseName;
  protected String templateElementsCollectionName;
  protected String templateFieldsCollectionName;
  protected String templateInstancesCollectionName;
  protected String templatesCollectionName;
  protected String usersCollectionName;

  @Override
  public void setArguments(String[] args) {
    arguments = new ArrayList<>();
    arguments.addAll(Arrays.asList(args));
  }

  @Override
  public void injectConfig(CedarConfig cedarConfig) {
    this.cedarConfig = cedarConfig;
    this.linkedDataUtil = cedarConfig.getLinkedDataUtil();
  }

  @Override
  public List<String> getDescription() {
    return description;
  }

  public List<String> getArguments() {
    return arguments;
  }

  @Override
  public void setOutput(AdminOutput out) {
    this.out = out;
  }

  protected UserService getNeoUserService() {
    return CedarDataServices.getNeoUserService();
  }

  protected boolean getConfirmInput(String message) {
    out.warn(message);
    out.warn("You need to confirm your intent by entering '" + CONFIRM + "'!");
    String yes = null;
    Console c = System.console();
    if (c != null) {
      out.info("Got console.");
      yes = c.readLine("Do you really want to perform the operation: ");
    } else {
      out.warn("Unable to get console. Reading System.in.");
      BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
      try {
        yes = br.readLine();
      } catch (IOException e) {
        e.printStackTrace();
      }
    }
    boolean proceed = CONFIRM.equals(yes);
    if (!proceed) {
      out.error("You chose not to continue with the process! Process finished.");
    }
    return proceed;
  }

  protected void initMongoCollectionNames() {
    mongoDatabaseName = cedarConfig.getArtifactServerConfig().getDatabaseName();
    templateFieldsCollectionName = cedarConfig.getArtifactServerConfig().getCollections().get(CedarResourceType.FIELD
        .getValue());
    templateElementsCollectionName = cedarConfig.getArtifactServerConfig().getCollections().get(CedarResourceType.ELEMENT
        .getValue
            ());
    templatesCollectionName = cedarConfig.getArtifactServerConfig().getCollections().get(CedarResourceType.TEMPLATE
        .getValue());
    templateInstancesCollectionName = cedarConfig.getArtifactServerConfig().getCollections().get(CedarResourceType.INSTANCE
        .getValue());
    usersCollectionName = cedarConfig.getUserServerConfig().getCollections().get(CedarResourceType.USER.getValue());
  }

}
