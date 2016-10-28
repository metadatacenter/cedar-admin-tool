package org.metadatacenter.admin.task;

import org.metadatacenter.admin.util.AdminOutput;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.model.CedarNodeType;
import org.metadatacenter.server.FolderServiceSession;
import org.metadatacenter.server.neo4j.Neo4JProxy;
import org.metadatacenter.server.neo4j.Neo4JUserSession;
import org.metadatacenter.server.neo4j.Neo4jConfig;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.service.UserService;
import org.metadatacenter.server.service.mongodb.UserServiceMongoDB;

import java.io.Console;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class AbstractCedarAdminTask implements ICedarAdminTask {

  protected List<String> arguments;
  protected List<String> description = new ArrayList<>();
  protected AdminOutput out;
  protected CedarConfig cedarConfig;
  public static final String CONFIRM = "yes";

  @Override
  public void setArguments(String[] args) {
    arguments = new ArrayList<>();
    arguments.addAll(Arrays.asList(args));
  }

  @Override
  public void injectConfig(CedarConfig cedarConfig) {
    this.cedarConfig = cedarConfig;
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

  protected UserService getUserService() {
    return new UserServiceMongoDB(
        cedarConfig.getMongoConfig().getDatabaseName(),
        cedarConfig.getMongoConfig().getCollections().get(CedarNodeType.USER.getValue()));
  }

  protected Neo4JProxy buildNeo4JProxy() {
    Neo4jConfig neoConfig = Neo4jConfig.fromCedarConfig(cedarConfig);

    String genericIdPrefix = cedarConfig.getLinkedDataConfig().getBase();
    String usersIdPrefix = cedarConfig.getLinkedDataConfig().getUsersBase();
    return new Neo4JProxy(neoConfig, genericIdPrefix, usersIdPrefix);
  }

  protected FolderServiceSession buildNeo4JSession(Neo4JProxy neo4JProxy, CedarUser user) {
    return buildNeo4JSession(neo4JProxy, user, false);
  }

  protected FolderServiceSession buildNeo4JSession(Neo4JProxy neo4JProxy, CedarUser user, boolean createHome) {
    UserService userService = getUserService();
    return Neo4JUserSession.get(neo4JProxy, userService, user, createHome);
  }

  protected FolderServiceSession buildNeo4JSession(CedarUser user) {
    Neo4JProxy neo4JProxy = buildNeo4JProxy();
    UserService userService = getUserService();
    return Neo4JUserSession.get(neo4JProxy, userService, user, false);
  }

  protected boolean getConfirmInput(String message) {
    Console c = System.console();
    if (c == null) {
      return false;
    }
    out.warn("You need to confirm your intent by entering '" + CONFIRM + "'!");
    out.warn(message);
    String yes = c.readLine("Do you really want to perform the operation: ");
    boolean proceed =CONFIRM.equals(yes);
    if (!proceed) {
      out.error("You chose not to continue with the process! Process finished.");
    }
    return proceed;
  }

}