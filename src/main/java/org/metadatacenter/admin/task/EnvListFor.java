package org.metadatacenter.admin.task;

import org.metadatacenter.config.environment.CedarConfigEnvironmentDescriptor;
import org.metadatacenter.config.environment.CedarEnvironmentVariable;
import org.metadatacenter.model.ServerName;
import org.metadatacenter.model.SystemComponent;

import java.util.Set;

public class EnvListFor extends AbstractCedarAdminTask {

  public EnvListFor() {
    description.add("Enumerates all environment variables required by a system component passed as parameter");
  }

  @Override
  public void init() {
  }

  @Override
  public int execute() {
    if (arguments.size() != 2) {
      out.error("A system component parameter must be passed for this task:");
      out.info("Usage:");
      out.info("$ cedarat env-listFor group");
      out.info("$ cedarat env-listFor keycloak-event-listener");
      return -1;
    }
    String serverOrUseCase = arguments.get(1);
    SystemComponent component = SystemComponent.getForUseCase(serverOrUseCase);
    if (component == null) {
      ServerName serverName = ServerName.forName(serverOrUseCase);
      if (serverName != null) {
        component = SystemComponent.getFor(serverName);
      }
    }

    if (component == null) {
      out.error("System component not found for use case or server name: '" + serverOrUseCase + "'");
      return -2;
    }

    Set<CedarEnvironmentVariable> variableNamesFor = CedarConfigEnvironmentDescriptor.getVariableNamesFor(component);
    for (CedarEnvironmentVariable var : variableNamesFor) {
      out.println(var.getName());
    }

    return 0;
  }
}
