package com.google.javascript.rhino.jstype;
public enum BooleanLiteralSet {
  EMPTY(),

  TRUE(),

  FALSE(),

  BOTH(),

;
  private BooleanLiteralSet fromOrdinal(int ordinal) {
    switch (ordinal){
      case 0:
      return EMPTY;
      case 1:
      return TRUE;
      case 2:
      return FALSE;
      case 3:
      return BOTH;
      default:
      throw new IllegalArgumentException("Ordinal: " + ordinal);
    }
  }
  public BooleanLiteralSet intersection(BooleanLiteralSet that) {
    return fromOrdinal(this.ordinal() & that.ordinal());
  }
  public BooleanLiteralSet union(BooleanLiteralSet that) {
    return fromOrdinal(this.ordinal() | that.ordinal());
  }
  public boolean contains(boolean literalValue) {
    int var_2639 = this.ordinal();
    switch (var_2639){
      case 0:
      return false;
      case 1:
      return literalValue;
      case 2:
      return !literalValue;
      case 3:
      return true;
      default:
      throw new IndexOutOfBoundsException("Ordinal: " + this.ordinal());
    }
  }
  public static BooleanLiteralSet get(boolean literalValue) {
    return literalValue ? TRUE : FALSE;
  }
private BooleanLiteralSet() {
}
}