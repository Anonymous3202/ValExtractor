package org.jfree.chart.axis;
import java.io.Serializable;
import java.text.NumberFormat;

public class NumberTickUnit extends TickUnit implements Serializable  {
  final private static long serialVersionUID = 3849459506627654442L;
  private NumberFormat formatter;
  public NumberTickUnit(double size) {
    this(size, NumberFormat.getNumberInstance());
  }
  public NumberTickUnit(double size, NumberFormat formatter) {
    super(size);
    if(formatter == null) {
      throw new IllegalArgumentException("Null \'formatter\' argument.");
    }
    this.formatter = formatter;
  }
  public NumberTickUnit(double size, NumberFormat formatter, int minorTickCount) {
    super(size, minorTickCount);
    if(formatter == null) {
      throw new IllegalArgumentException("Null \'formatter\' argument.");
    }
    this.formatter = formatter;
  }
  public String toString() {
    return "[size=" + this.valueToString(this.getSize()) + "]";
  }
  public String valueToString(double value) {
    return this.formatter.format(value);
  }
  public boolean equals(Object obj) {
    if(obj == this) {
      return true;
    }
    if(!(obj instanceof NumberTickUnit)) {
      return false;
    }
    if(!super.equals(obj)) {
      return false;
    }
    NumberTickUnit that = (NumberTickUnit)obj;
    if(!this.formatter.equals(that.formatter)) {
      return false;
    }
    return true;
  }
  public int hashCode() {
    int result = super.hashCode();
    NumberFormat var_727 = this.formatter;
    result = 29 * result + (var_727 != null ? this.formatter.hashCode() : 0);
    return result;
  }
}