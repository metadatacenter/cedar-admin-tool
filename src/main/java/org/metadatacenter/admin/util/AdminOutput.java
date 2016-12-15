package org.metadatacenter.admin.util;

public class AdminOutput {


  public void println(Object object) {
    if (object == null) {
      println("NULL");
    } else {
      println(object.toString());
    }
  }

  private void println(String message) {
    println(message, null);
  }

  public void println(String message, Color color) {
    if (color != null) {
      print((char) 27 + "[" + color.getValue() + "m");
    }
    print(message);
    print("\n");
    if (color != null) {
      print((char) 27 + "[" + Color.CLEAR.getValue() + "m");
    }
  }

  private void print(String message) {
    System.out.print(message);
  }

  //--

  public void error(String message) {
    println();
    println("ERROR: " + message, Color.RED);
    println();
  }

  public void error(String message, Exception e) {
    error(message);
    error(e);
  }

  public void error(Exception e) {
    e.printStackTrace();
  }

  public void warn(String message) {
    println();
    println("WARNING: " + message, Color.YELLOW);
    println();
  }

  public void info(String message) {
    System.out.println("INFO: " + message);
  }

  public void println() {
    System.out.println("\n");
  }

  public void printSeparator() {
    System.out.println("----------------------------------------------------");
  }

  public void printIndented(String message) {
    printIndented(message, 1, null);
  }

  public void printIndented(String message, int times) {
    printIndented(message, times, null);
  }

  public void printIndented(String message, Color color) {
    printIndented(message, 1, color);
  }

  public void printIndented(String message, int times, Color color) {
    for (int i = 0; i < times; i++) {
      print("\t");
    }
    println(message, color);
  }


  public void printTitle(String message) {
    println();
    println(message, Color.BRIGHT);
  }

}
