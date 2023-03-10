package org.jfree.chart.axis;
import java.io.ObjectStreamException;
import java.io.Serializable;

final public class AxisLocation implements Serializable  {
  final private static long serialVersionUID = -3276922179323563410L;
  final public static AxisLocation TOP_OR_LEFT = new AxisLocation("AxisLocation.TOP_OR_LEFT");
  final public static AxisLocation TOP_OR_RIGHT = new AxisLocation("AxisLocation.TOP_OR_RIGHT");
  final public static AxisLocation BOTTOM_OR_LEFT = new AxisLocation("AxisLocation.BOTTOM_OR_LEFT");
  final public static AxisLocation BOTTOM_OR_RIGHT = new AxisLocation("AxisLocation.BOTTOM_OR_RIGHT");
  private String name;
  private AxisLocation(String name) {
    super();
    this.name = name;
  }
  public AxisLocation getOpposite() {
    return getOpposite(this);
  }
  public static AxisLocation getOpposite(AxisLocation location) {
    if(location == null) {
      throw new IllegalArgumentException("Null \'location\' argument.");
    }
    AxisLocation result = null;
    if(location == AxisLocation.TOP_OR_LEFT) {
      result = AxisLocation.BOTTOM_OR_RIGHT;
    }
    else 
      if(location == AxisLocation.TOP_OR_RIGHT) {
        result = AxisLocation.BOTTOM_OR_LEFT;
      }
      else 
        if(location == AxisLocation.BOTTOM_OR_LEFT) {
          result = AxisLocation.TOP_OR_RIGHT;
        }
        else 
          if(location == AxisLocation.BOTTOM_OR_RIGHT) {
            result = AxisLocation.TOP_OR_LEFT;
          }
          else {
            throw new IllegalStateException("AxisLocation not recognised.");
          }
    return result;
  }
  private Object readResolve() throws ObjectStreamException {
    if(this.equals(AxisLocation.TOP_OR_RIGHT)) {
      return AxisLocation.TOP_OR_RIGHT;
    }
    else 
      if(this.equals(AxisLocation.BOTTOM_OR_RIGHT)) {
        return AxisLocation.BOTTOM_OR_RIGHT;
      }
      else 
        if(this.equals(AxisLocation.TOP_OR_LEFT)) {
          return AxisLocation.TOP_OR_LEFT;
        }
        else {
          AxisLocation var_755 = AxisLocation.BOTTOM_OR_LEFT;
          if(this.equals(var_755)) {
            return AxisLocation.BOTTOM_OR_LEFT;
          }
        }
    return null;
  }
  public String toString() {
    return this.name;
  }
  public boolean equals(Object obj) {
    if(this == obj) {
      return true;
    }
    if(!(obj instanceof AxisLocation)) {
      return false;
    }
    AxisLocation location = (AxisLocation)obj;
    if(!this.name.equals(location.toString())) {
      return false;
    }
    return true;
  }
}