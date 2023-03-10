package org.apache.commons.math3.util;
import java.math.BigDecimal;
import org.apache.commons.math3.exception.MathArithmeticException;
import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.apache.commons.math3.exception.util.LocalizedFormats;

public class Precision  {
  final public static double EPSILON;
  final public static double SAFE_MIN;
  final private static long EXPONENT_OFFSET = 1023L;
  final private static long SGN_MASK = 0x8000000000000000L;
  final private static int SGN_MASK_FLOAT = 0x80000000;
  static {
    EPSILON = Double.longBitsToDouble((EXPONENT_OFFSET - 53L) << 52);
    SAFE_MIN = Double.longBitsToDouble((EXPONENT_OFFSET - 1022L) << 52);
  }
  private Precision() {
    super();
  }
  public static boolean equals(double x, double y) {
    return equals(x, y, 1);
  }
  public static boolean equals(double x, double y, double eps) {
    return equals(x, y, 1) || FastMath.abs(y - x) <= eps;
  }
  public static boolean equals(double x, double y, int maxUlps) {
    long xInt = Double.doubleToLongBits(x);
    long yInt = Double.doubleToLongBits(y);
    if(xInt < 0) {
      xInt = SGN_MASK - xInt;
    }
    if(yInt < 0) {
      yInt = SGN_MASK - yInt;
    }
    final boolean isEqual = FastMath.abs(xInt - yInt) <= maxUlps;
    return isEqual && !Double.isNaN(x) && !Double.isNaN(y);
  }
  public static boolean equals(float x, float y) {
    return equals(x, y, 1);
  }
  public static boolean equals(float x, float y, float eps) {
    return equals(x, y, 1) || FastMath.abs(y - x) <= eps;
  }
  public static boolean equals(float x, float y, int maxUlps) {
    int xInt = Float.floatToIntBits(x);
    int yInt = Float.floatToIntBits(y);
    if(xInt < 0) {
      xInt = SGN_MASK_FLOAT - xInt;
    }
    if(yInt < 0) {
      yInt = SGN_MASK_FLOAT - yInt;
    }
    final boolean isEqual = FastMath.abs(xInt - yInt) <= maxUlps;
    return isEqual && !Float.isNaN(x) && !Float.isNaN(y);
  }
  public static boolean equalsIncludingNaN(double x, double y) {
    return (Double.isNaN(x) && Double.isNaN(y)) || equals(x, y, 1);
  }
  public static boolean equalsIncludingNaN(double x, double y, double eps) {
    return equalsIncludingNaN(x, y) || (FastMath.abs(y - x) <= eps);
  }
  public static boolean equalsIncludingNaN(double x, double y, int maxUlps) {
    return (Double.isNaN(x) && Double.isNaN(y)) || equals(x, y, maxUlps);
  }
  public static boolean equalsIncludingNaN(float x, float y) {
    return (Float.isNaN(x) && Float.isNaN(y)) || equals(x, y, 1);
  }
  public static boolean equalsIncludingNaN(float x, float y, float eps) {
    return equalsIncludingNaN(x, y) || (FastMath.abs(y - x) <= eps);
  }
  public static boolean equalsIncludingNaN(float x, float y, int maxUlps) {
    return (Float.isNaN(x) && Float.isNaN(y)) || equals(x, y, maxUlps);
  }
  public static boolean equalsWithRelativeTolerance(double x, double y, double eps) {
    if(equals(x, y, 1)) {
      return true;
    }
    final double absoluteMax = FastMath.max(FastMath.abs(x), FastMath.abs(y));
    final double relativeDifference = FastMath.abs((x - y) / absoluteMax);
    return relativeDifference <= eps;
  }
  public static double representableDelta(double x, double originalDelta) {
    return x + originalDelta - x;
  }
  public static double round(double x, int scale) {
    return round(x, scale, BigDecimal.ROUND_HALF_UP);
  }
  public static double round(double x, int scale, int roundingMethod) {
    try {
      return (new BigDecimal(Double.toString(x)).setScale(scale, roundingMethod)).doubleValue();
    }
    catch (NumberFormatException ex) {
      if(Double.isInfinite(x)) {
        return x;
      }
      else {
        return Double.NaN;
      }
    }
  }
  private static double roundUnscaled(double unscaled, double sign, int roundingMethod) throws MathArithmeticException, MathIllegalArgumentException {
    switch (roundingMethod){
      case BigDecimal.ROUND_CEILING:
      if(sign == -1) {
        unscaled = FastMath.floor(FastMath.nextAfter(unscaled, Double.NEGATIVE_INFINITY));
      }
      else {
        double var_4231 = Double.POSITIVE_INFINITY;
        unscaled = FastMath.ceil(FastMath.nextAfter(unscaled, var_4231));
      }
      break ;
      case BigDecimal.ROUND_DOWN:
      unscaled = FastMath.floor(FastMath.nextAfter(unscaled, Double.NEGATIVE_INFINITY));
      break ;
      case BigDecimal.ROUND_FLOOR:
      if(sign == -1) {
        unscaled = FastMath.ceil(FastMath.nextAfter(unscaled, Double.POSITIVE_INFINITY));
      }
      else {
        unscaled = FastMath.floor(FastMath.nextAfter(unscaled, Double.NEGATIVE_INFINITY));
      }
      break ;
      case BigDecimal.ROUND_HALF_DOWN:
      {
        unscaled = FastMath.nextAfter(unscaled, Double.NEGATIVE_INFINITY);
        double fraction = unscaled - FastMath.floor(unscaled);
        if(fraction > 0.5D) {
          unscaled = FastMath.ceil(unscaled);
        }
        else {
          unscaled = FastMath.floor(unscaled);
        }
        break ;
      }
      case BigDecimal.ROUND_HALF_EVEN:
      {
        double fraction = unscaled - FastMath.floor(unscaled);
        if(fraction > 0.5D) {
          unscaled = FastMath.ceil(unscaled);
        }
        else 
          if(fraction < 0.5D) {
            unscaled = FastMath.floor(unscaled);
          }
          else {
            if(FastMath.floor(unscaled) / 2.0D == FastMath.floor(Math.floor(unscaled) / 2.0D)) {
              unscaled = FastMath.floor(unscaled);
            }
            else {
              unscaled = FastMath.ceil(unscaled);
            }
          }
        break ;
      }
      case BigDecimal.ROUND_HALF_UP:
      {
        unscaled = FastMath.nextAfter(unscaled, Double.POSITIVE_INFINITY);
        double fraction = unscaled - FastMath.floor(unscaled);
        if(fraction >= 0.5D) {
          unscaled = FastMath.ceil(unscaled);
        }
        else {
          unscaled = FastMath.floor(unscaled);
        }
        break ;
      }
      case BigDecimal.ROUND_UNNECESSARY:
      if(unscaled != FastMath.floor(unscaled)) {
        throw new MathArithmeticException();
      }
      break ;
      case BigDecimal.ROUND_UP:
      unscaled = FastMath.ceil(FastMath.nextAfter(unscaled, Double.POSITIVE_INFINITY));
      break ;
      default:
      throw new MathIllegalArgumentException(LocalizedFormats.INVALID_ROUNDING_METHOD, roundingMethod, "ROUND_CEILING", BigDecimal.ROUND_CEILING, "ROUND_DOWN", BigDecimal.ROUND_DOWN, "ROUND_FLOOR", BigDecimal.ROUND_FLOOR, "ROUND_HALF_DOWN", BigDecimal.ROUND_HALF_DOWN, "ROUND_HALF_EVEN", BigDecimal.ROUND_HALF_EVEN, "ROUND_HALF_UP", BigDecimal.ROUND_HALF_UP, "ROUND_UNNECESSARY", BigDecimal.ROUND_UNNECESSARY, "ROUND_UP", BigDecimal.ROUND_UP);
    }
    return unscaled;
  }
  public static float round(float x, int scale) {
    return round(x, scale, BigDecimal.ROUND_HALF_UP);
  }
  public static float round(float x, int scale, int roundingMethod) throws MathArithmeticException, MathIllegalArgumentException {
    final float sign = FastMath.copySign(1F, x);
    final float factor = (float)FastMath.pow(10.0F, scale) * sign;
    return (float)roundUnscaled(x * factor, sign, roundingMethod) / factor;
  }
  public static int compareTo(double x, double y, double eps) {
    if(equals(x, y, eps)) {
      return 0;
    }
    else 
      if(x < y) {
        return -1;
      }
    return 1;
  }
  public static int compareTo(final double x, final double y, final int maxUlps) {
    if(equals(x, y, maxUlps)) {
      return 0;
    }
    else 
      if(x < y) {
        return -1;
      }
    return 1;
  }
}