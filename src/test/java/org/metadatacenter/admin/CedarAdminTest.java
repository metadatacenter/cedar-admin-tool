package org.metadatacenter.admin;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.metadatacenter.model.SystemComponent;
import org.metadatacenter.util.test.EmbeddedCedarMongo;
import org.metadatacenter.util.test.EmbeddedCedarNeo4j;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.function.IntSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * cedarat runs the command its first argument names, after reading the configuration and connecting to the stores,
 * and exits with that command's status. Without a command it knows, it prints its usage and exits with -1.
 */
@Tag("integration")
class CedarAdminTest {

  @Test
  void withoutAKnownCommandTheUsageIsPrinted() {
    for (String[] args : new String[][]{null, {}, {"  "}, {"no-such-command"}}) {
      Printed printed = captured(() -> CedarAdmin.run(args));
      assertEquals(-1, printed.status());
      assertTrue(printed.text().contains("Available commands:"), printed.text());
      assertTrue(printed.text().contains("env-listFor"), "the usage lists every command");
    }
  }

  @Test
  void aKnownCommandRunsAndItsStatusIsReturned() {
    // The tool connects to the stores before it runs the command, so they must exist.
    EmbeddedCedarMongo.startAndRedirectEnvironment();
    EmbeddedCedarNeo4j.startRedirectAndSeed(SystemComponent.ADMIN_TOOL);

    assertEquals(0, captured(() -> CedarAdmin.run(new String[]{"env-listFor", "group"})).status());
    assertEquals(-2, captured(() -> CedarAdmin.run(new String[]{"env-listFor", "no-such-component"})).status());
  }

  private record Printed(int status, String text) {
  }

  private static Printed captured(IntSupplier run) {
    PrintStream original = System.out;
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
    try {
      return new Printed(run.getAsInt(), buffer.toString(StandardCharsets.UTF_8));
    } finally {
      System.setOut(original);
    }
  }
}
