package org.metadatacenter.admin.task;

import org.junit.jupiter.api.Test;
import org.metadatacenter.admin.util.AdminOutput;
import org.metadatacenter.admin.util.TaskExecutor;
import org.metadatacenter.config.CedarConfig;
import org.metadatacenter.config.environment.CedarConfigEnvironmentDescriptor;
import org.metadatacenter.config.environment.CedarEnvironmentVariable;
import org.metadatacenter.config.environment.CedarEnvironmentVariableProvider;
import org.metadatacenter.model.ServerName;
import org.metadatacenter.model.SystemComponent;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * env-listFor prints the environment variables a component needs, so an operator can provision a host for it. The
 * component is named by its server, such as group, or by its use case, such as keycloak-event-listener. The task runs
 * the way cedarat runs every task, through the executor.
 */
class EnvListForTest {

  private static final CedarConfig CONFIG =
      CedarConfig.getInstance(CedarEnvironmentVariableProvider.getFor(SystemComponent.ADMIN_TOOL));

  @Test
  void aServerIsListedByItsName() {
    Printed printed = run("group");
    assertEquals(0, printed.status());
    assertTrue(printed.lines().containsAll(names(SystemComponent.getFor(ServerName.GROUP))), printed.text());
  }

  @Test
  void aUseCaseIsListedByItsName() {
    Printed printed = run("keycloak-event-listener");
    assertEquals(0, printed.status());
    Set<String> expected = names(SystemComponent.KEYCLOAK_EVENT_LISTENER);
    assertFalse(expected.isEmpty());
    assertTrue(printed.lines().containsAll(expected), printed.text());
  }

  @Test
  void aMissingOrUnknownComponentIsRefused() {
    assertEquals(-1, run().status(), "the component is required");
    assertEquals(-2, run("no-such-component").status());
  }

  private static Set<String> names(SystemComponent component) {
    return CedarConfigEnvironmentDescriptor.getVariableNamesFor(component).stream()
        .map(CedarEnvironmentVariable::getName).collect(Collectors.toSet());
  }

  private record Printed(int status, String text) {
    Set<String> lines() {
      return Arrays.stream(text.split("\\R")).map(String::trim).collect(Collectors.toSet());
    }
  }

  private static Printed run(String... component) {
    String[] args = new String[component.length + 1];
    args[0] = "env-listFor";
    System.arraycopy(component, 0, args, 1, component.length);
    return captured(() -> TaskExecutor.executeOneTask(CONFIG, "env-listFor", new AdminOutput(), args));
  }

  private static Printed captured(IntSupplier task) {
    PrintStream original = System.out;
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
    try {
      int status = task.getAsInt();
      return new Printed(status, buffer.toString(StandardCharsets.UTF_8));
    } finally {
      System.setOut(original);
    }
  }
}
