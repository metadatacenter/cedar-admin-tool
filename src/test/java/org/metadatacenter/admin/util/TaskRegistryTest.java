package org.metadatacenter.admin.util;

import org.junit.jupiter.api.Test;
import org.metadatacenter.admin.task.ICedarAdminTask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * cedarat finds a command's task in the registry by the name the operator types, creates it with no
 * arguments, and prints its description in the usage list. A task that cannot be created, or that
 * describes nothing, fails only when an operator reaches for it.
 */
class TaskRegistryTest {

  @Test
  void everyRegisteredCommandCreatesATaskThatDescribesItself() throws Exception {
    assertFalse(TaskRegistry.getTaskKeys().isEmpty());
    for (String key : TaskRegistry.getTaskKeys()) {
      ICedarAdminTask task = TaskRegistry.getTaskForKey(key);
      assertNotNull(task, key);
      assertEquals(TaskRegistry.getTaskClassForKey(key), task.getClass(), key);
      assertFalse(task.getDescription().isEmpty(), key + " should describe itself in the usage list");
    }
  }

  @Test
  void anUnknownCommandHasNoTask() {
    assertNull(TaskRegistry.getTaskClassForKey("no-such-command"));
  }
}
