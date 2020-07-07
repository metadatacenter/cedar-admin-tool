package org.metadatacenter.admin;

import org.metadatacenter.admin.task.ICedarAdminTask;
import org.metadatacenter.admin.util.AdminOutput;
import org.metadatacenter.admin.util.Color;
import org.metadatacenter.admin.util.TaskExecutor;
import org.metadatacenter.admin.util.TaskRegistry;
import org.metadatacenter.bridge.CedarDataServices;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.rest.context.CedarRequestContextFactory;
import org.metadatacenter.server.logging.AppLogger;
import org.metadatacenter.server.logging.AppLoggerQueueService;

import java.lang.reflect.InvocationTargetException;
import java.util.Map;

public class CedarAdmin {

  static final AdminOutput out;

  static {
    out = new AdminOutput();
  }

  private static void showTitle() {
    out.println("CEDAR Admin Tool");
  }

  private static void showUsageAndExit() {
    showTitle();
    out.printTitle("Usage:");
    out.printIndented("cedar-admin-tool command parameters...");
    out.printTitle("Available command details:");
    for (String key : TaskRegistry.getTaskKeys()) {
      out.printIndented(key, Color.BRIGHT);
      ICedarAdminTask t = null;
      try {
        t = TaskRegistry.getTaskForKey(key);
      } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
        out.error(e);
      }
      if (t != null) {
        for (String desc : t.getDescription()) {
          out.printIndented("* " + desc, 2);
        }
        out.println();
      }
    }
    out.printTitle("Available commands:");
    for (String key : TaskRegistry.getTaskKeys()) {
      out.printIndented(key, Color.BRIGHT);
      ICedarAdminTask t = null;
      try {
        t = TaskRegistry.getTaskForKey(key);
      } catch (InstantiationException | IllegalAccessException | NoSuchMethodException | InvocationTargetException e) {
        out.error(e);
      }
    }
    System.exit(-1);
  }

  public static void main(String[] args) {
    if (args == null || args.length == 0) {
      showUsageAndExit();
    } else {
      String firstArg = args[0];
      if (firstArg == null || firstArg.trim().length() == 0) {
        showUsageAndExit();
      } else {
        Class<? extends ICedarAdminTask> taskClass = TaskRegistry.getTaskClassForKey(firstArg);
        if (taskClass == null) {
          out.error("Unknown command: " + firstArg + "\n");
          showUsageAndExit();
        } else {
          out.info("Reading config");
          SystemComponent systemComponent = SystemComponent.ADMIN_TOOL;
          Map<String, String> environment = CedarEnvironmentVariableProvider.getFor(systemComponent);
          CedarConfig cedarConfig = CedarConfig.getInstance(environment);

          AppLoggerQueueService appLoggerQueueService =
              new AppLoggerQueueService(cedarConfig.getCacheConfig().getPersistent());
          AppLogger.initLoggerQueueService(appLoggerQueueService, systemComponent);

          CedarRequestContextFactory.init(cedarConfig.getLinkedDataUtil());
          out.info("Building data services");
          CedarDataServices.initializeMongoClientFactoryForDocuments(
              cedarConfig.getArtifactServerConfig().getMongoConnection());
          CedarDataServices.initializeNeo4jServices(cedarConfig);
          String version = System.getProperty("java.version");
          out.info("Java version:" + version);
          out.info("Executing task");
          System.exit(TaskExecutor.executeOneTask(cedarConfig, firstArg, out, args));
        }
      }
    }
  }

}
