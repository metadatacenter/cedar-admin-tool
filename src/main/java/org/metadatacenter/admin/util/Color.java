package org.metadatacenter.admin.util;

public enum Color {
  RED(31),
  GREEN(32),
  YELLOW(33),
  WHITE(37),
  BRIGHT(1),
  NOBRIGHT(21),
  CLEAR(0);

  private int value;

  Color(int value) {
    this.value = value;
  }

  public int getValue() {
    return value;
  }
}
