package org.metadatacenter.admin.util;

public class AdminOutput {


  public void println(Object o) {
    if (o == null) {
      println("NULL");
    } else {
      println(o.toString());
    }
  }

  private void println(String s) {
    println(s, null);
  }

  private void println(String s, Color color) {
    if (color != null) {
      print((char) 27 + "[" + color.getValue() + "m");
    }
    print(s);
    print("\n");
    if (color != null) {
      print((char) 27 + "[" + Color.CLEAR.getValue() + "m");
    }
  }

  private void print(String s) {
    System.out.print(s);
  }

  //--

  public void error(String s) {
    println();
    println("ERROR: " + s, Color.RED);
    println();
  }

  public void error(String s, Exception e) {
    error(s);
    error(e);
  }

  public void error(Exception e) {
    e.printStackTrace();
  }

  public void warn(String s) {
    println();
    println("WARNING: " + s, Color.YELLOW);
    println();
  }

  public void info(String s) {
    System.out.println("INFO: " + s);
  }

  public void println() {
    System.out.println("\n");
  }

  public void printSeparator() {
    System.out.println("----------------------------------------------------");
  }

  public void printIndented(String s) {
    printIndented(s, 1, null);
  }

  public void printIndented(String s, int times) {
    printIndented(s, times, null);
  }

  public void printIndented(String s, Color color) {
    printIndented(s, 1, color);
  }

  public void printIndented(String s, int times, Color color) {
    for (int i = 0; i < times; i++) {
      print("\t");
    }
    println(s, color);
  }


  public void printTitle(String s) {
    println();
    println(s, Color.BRIGHT);
  }

}
