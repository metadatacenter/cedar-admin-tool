package org.metadatacenter.admin.util;

public class AdminOutput {

  public void println(String s) {
    System.out.println(s);
  }


  public void println(Object o) {
    if (o == null) {
      println("NULL");
    } else {
      println(o.toString());
    }
  }

  public void error(String s) {
    System.out.println((char)27 + "[31m");
    System.out.println("ERROR: " + s);
    System.out.println((char)27 + "[0m");
  }

  public void error(String s, Exception e) {
    error(s);
    error(e);
  }

  public void error(Exception e) {
    e.printStackTrace();
  }

  public void warn(String s) {
    System.out.println((char)27 + "[33m");
    System.out.println("WARNING: " + s);
    System.out.println((char)27 + "[0m");
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
    printIndented(s, 1);
  }

  public void printIndented(String s, int times) {
    for (int i = 0; i < times; i++) {
      System.out.print("\t");
    }
    System.out.println(s);
  }

  public void printTitle(String s) {
    System.out.println();
    System.out.println((char)27 + "[1m");
    System.out.println(s);
    System.out.println((char)27 + "[0m");
  }

}
