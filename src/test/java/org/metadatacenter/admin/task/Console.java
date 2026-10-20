package org.metadatacenter.admin.task;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;

/**
 * Plays the operator at the console: answers each confirmation prompt with the next of the given lines, and keeps
 * what the task prints.
 */
final class Console {

  record Run<T>(T result, String printed) {
  }

  private Console() {
  }

  static <T> Run<T> run(Callable<T> task, String... typed) throws Exception {
    InputStream originalIn = System.in;
    PrintStream originalOut = System.out;
    ByteArrayOutputStream printed = new ByteArrayOutputStream();
    System.setIn(new LinePerRead(typed));
    System.setOut(new PrintStream(printed, true, StandardCharsets.UTF_8));
    try {
      T result = task.call();
      return new Run<>(result, printed.toString(StandardCharsets.UTF_8));
    } catch (Exception | Error e) {
      // A task that throws leaves its output as the only account of what it did, so it is not swallowed.
      originalOut.print(printed.toString(StandardCharsets.UTF_8));
      throw e;
    } finally {
      System.setIn(originalIn);
      System.setOut(originalOut);
    }
  }

  /**
   * Each prompt reads the console through a reader of its own, which would take every buffered line at once. Handing
   * out one line per read leaves the next line for the next prompt, as a terminal does.
   */
  private static final class LinePerRead extends InputStream {

    private final String[] lines;
    private int next;

    LinePerRead(String[] lines) {
      this.lines = lines;
    }

    @Override
    public int read() {
      byte[] one = new byte[1];
      return read(one, 0, 1) == -1 ? -1 : one[0];
    }

    @Override
    public int read(byte[] buffer, int offset, int length) {
      if (next >= lines.length) {
        return -1;
      }
      byte[] line = (lines[next++] + "\n").getBytes(StandardCharsets.UTF_8);
      int count = Math.min(length, line.length);
      System.arraycopy(line, 0, buffer, offset, count);
      return count;
    }
  }
}
