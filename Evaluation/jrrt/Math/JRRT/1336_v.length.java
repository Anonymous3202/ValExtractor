package org.apache.commons.math3.geometry.euclidean.threed;
import java.io.Serializable;
import java.text.NumberFormat;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.exception.MathArithmeticException;
import org.apache.commons.math3.exception.util.LocalizedFormats;
import org.apache.commons.math3.geometry.Vector;
import org.apache.commons.math3.geometry.Space;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.MathUtils;
import org.apache.commons.math3.util.MathArrays;

public class Vector3D implements Serializable, Vector<Euclidean3D>  {
  final public static Vector3D ZERO = new Vector3D(0, 0, 0);
  final public static Vector3D PLUS_I = new Vector3D(1, 0, 0);
  final public static Vector3D MINUS_I = new Vector3D(-1, 0, 0);
  final public static Vector3D PLUS_J = new Vector3D(0, 1, 0);
  final public static Vector3D MINUS_J = new Vector3D(0, -1, 0);
  final public static Vector3D PLUS_K = new Vector3D(0, 0, 1);
  final public static Vector3D MINUS_K = new Vector3D(0, 0, -1);
  final public static Vector3D NaN = new Vector3D(Double.NaN, Double.NaN, Double.NaN);
  final public static Vector3D POSITIVE_INFINITY = new Vector3D(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY);
  final public static Vector3D NEGATIVE_INFINITY = new Vector3D(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY);
  final private static long serialVersionUID = 1313493323784566947L;
  final private double x;
  final private double y;
  final private double z;
  public Vector3D(double a, Vector3D u) {
    super();
    this.x = a * u.x;
    this.y = a * u.y;
    this.z = a * u.z;
  }
  public Vector3D(double a1, Vector3D u1, double a2, Vector3D u2) {
    super();
    this.x = MathArrays.linearCombination(a1, u1.x, a2, u2.x);
    this.y = MathArrays.linearCombination(a1, u1.y, a2, u2.y);
    this.z = MathArrays.linearCombination(a1, u1.z, a2, u2.z);
  }
  public Vector3D(double a1, Vector3D u1, double a2, Vector3D u2, double a3, Vector3D u3) {
    super();
    this.x = MathArrays.linearCombination(a1, u1.x, a2, u2.x, a3, u3.x);
    this.y = MathArrays.linearCombination(a1, u1.y, a2, u2.y, a3, u3.y);
    this.z = MathArrays.linearCombination(a1, u1.z, a2, u2.z, a3, u3.z);
  }
  public Vector3D(double a1, Vector3D u1, double a2, Vector3D u2, double a3, Vector3D u3, double a4, Vector3D u4) {
    super();
    this.x = MathArrays.linearCombination(a1, u1.x, a2, u2.x, a3, u3.x, a4, u4.x);
    this.y = MathArrays.linearCombination(a1, u1.y, a2, u2.y, a3, u3.y, a4, u4.y);
    this.z = MathArrays.linearCombination(a1, u1.z, a2, u2.z, a3, u3.z, a4, u4.z);
  }
  public Vector3D(double alpha, double delta) {
    super();
    double cosDelta = FastMath.cos(delta);
    this.x = FastMath.cos(alpha) * cosDelta;
    this.y = FastMath.sin(alpha) * cosDelta;
    this.z = FastMath.sin(delta);
  }
  public Vector3D(double x, double y, double z) {
    super();
    this.x = x;
    this.y = y;
    this.z = z;
  }
  public Vector3D(double[] v) throws DimensionMismatchException {
    super();
    if(v.length != 3) {
      int var_1336 = v.length;
      throw new DimensionMismatchException(var_1336, 3);
    }
    this.x = v[0];
    this.y = v[1];
    this.z = v[2];
  }
  public Space getSpace() {
    return Euclidean3D.getInstance();
  }
  @Override() public String toString() {
    return Vector3DFormat.getInstance().format(this);
  }
  public String toString(final NumberFormat format) {
    return new Vector3DFormat(format).format(this);
  }
  public Vector3D add(double factor, final Vector<Euclidean3D> v) {
    return new Vector3D(1, this, factor, (Vector3D)v);
  }
  public Vector3D add(final Vector<Euclidean3D> v) {
    final Vector3D v3 = (Vector3D)v;
    return new Vector3D(x + v3.x, y + v3.y, z + v3.z);
  }
  public Vector3D crossProduct(final Vector<Euclidean3D> v) {
    final Vector3D v3 = (Vector3D)v;
    return new Vector3D(MathArrays.linearCombination(y, v3.z, -z, v3.y), MathArrays.linearCombination(z, v3.x, -x, v3.z), MathArrays.linearCombination(x, v3.y, -y, v3.x));
  }
  public static Vector3D crossProduct(final Vector3D v1, final Vector3D v2) {
    return v1.crossProduct(v2);
  }
  public Vector3D getZero() {
    return ZERO;
  }
  public Vector3D negate() {
    return new Vector3D(-x, -y, -z);
  }
  public Vector3D normalize() throws MathArithmeticException {
    double s = getNorm();
    if(s == 0) {
      throw new MathArithmeticException(LocalizedFormats.CANNOT_NORMALIZE_A_ZERO_NORM_VECTOR);
    }
    return scalarMultiply(1 / s);
  }
  public Vector3D orthogonal() throws MathArithmeticException {
    double threshold = 0.6D * getNorm();
    if(threshold == 0) {
      throw new MathArithmeticException(LocalizedFormats.ZERO_NORM);
    }
    if(FastMath.abs(x) <= threshold) {
      double inverse = 1 / FastMath.sqrt(y * y + z * z);
      return new Vector3D(0, inverse * z, -inverse * y);
    }
    else 
      if(FastMath.abs(y) <= threshold) {
        double inverse = 1 / FastMath.sqrt(x * x + z * z);
        return new Vector3D(-inverse * z, 0, inverse * x);
      }
    double inverse = 1 / FastMath.sqrt(x * x + y * y);
    return new Vector3D(inverse * y, -inverse * x, 0);
  }
  public Vector3D scalarMultiply(double a) {
    return new Vector3D(a * x, a * y, a * z);
  }
  public Vector3D subtract(final double factor, final Vector<Euclidean3D> v) {
    return new Vector3D(1, this, -factor, (Vector3D)v);
  }
  public Vector3D subtract(final Vector<Euclidean3D> v) {
    final Vector3D v3 = (Vector3D)v;
    return new Vector3D(x - v3.x, y - v3.y, z - v3.z);
  }
  @Override() public boolean equals(Object other) {
    if(this == other) {
      return true;
    }
    if(other instanceof Vector3D) {
      final Vector3D rhs = (Vector3D)other;
      if(rhs.isNaN()) {
        return this.isNaN();
      }
      return (x == rhs.x) && (y == rhs.y) && (z == rhs.z);
    }
    return false;
  }
  public boolean isInfinite() {
    return !isNaN() && (Double.isInfinite(x) || Double.isInfinite(y) || Double.isInfinite(z));
  }
  public boolean isNaN() {
    return Double.isNaN(x) || Double.isNaN(y) || Double.isNaN(z);
  }
  public static double angle(Vector3D v1, Vector3D v2) throws MathArithmeticException {
    double normProduct = v1.getNorm() * v2.getNorm();
    if(normProduct == 0) {
      throw new MathArithmeticException(LocalizedFormats.ZERO_NORM);
    }
    double dot = v1.dotProduct(v2);
    double threshold = normProduct * 0.9999D;
    if((dot < -threshold) || (dot > threshold)) {
      Vector3D v3 = crossProduct(v1, v2);
      if(dot >= 0) {
        return FastMath.asin(v3.getNorm() / normProduct);
      }
      return FastMath.PI - FastMath.asin(v3.getNorm() / normProduct);
    }
    return FastMath.acos(dot / normProduct);
  }
  public double distance(Vector<Euclidean3D> v) {
    final Vector3D v3 = (Vector3D)v;
    final double dx = v3.x - x;
    final double dy = v3.y - y;
    final double dz = v3.z - z;
    return FastMath.sqrt(dx * dx + dy * dy + dz * dz);
  }
  public static double distance(Vector3D v1, Vector3D v2) {
    return v1.distance(v2);
  }
  public double distance1(Vector<Euclidean3D> v) {
    final Vector3D v3 = (Vector3D)v;
    final double dx = FastMath.abs(v3.x - x);
    final double dy = FastMath.abs(v3.y - y);
    final double dz = FastMath.abs(v3.z - z);
    return dx + dy + dz;
  }
  public static double distance1(Vector3D v1, Vector3D v2) {
    return v1.distance1(v2);
  }
  public double distanceInf(Vector<Euclidean3D> v) {
    final Vector3D v3 = (Vector3D)v;
    final double dx = FastMath.abs(v3.x - x);
    final double dy = FastMath.abs(v3.y - y);
    final double dz = FastMath.abs(v3.z - z);
    return FastMath.max(FastMath.max(dx, dy), dz);
  }
  public static double distanceInf(Vector3D v1, Vector3D v2) {
    return v1.distanceInf(v2);
  }
  public double distanceSq(Vector<Euclidean3D> v) {
    final Vector3D v3 = (Vector3D)v;
    final double dx = v3.x - x;
    final double dy = v3.y - y;
    final double dz = v3.z - z;
    return dx * dx + dy * dy + dz * dz;
  }
  public static double distanceSq(Vector3D v1, Vector3D v2) {
    return v1.distanceSq(v2);
  }
  public double dotProduct(final Vector<Euclidean3D> v) {
    final Vector3D v3 = (Vector3D)v;
    return MathArrays.linearCombination(x, v3.x, y, v3.y, z, v3.z);
  }
  public static double dotProduct(Vector3D v1, Vector3D v2) {
    return v1.dotProduct(v2);
  }
  public double getAlpha() {
    return FastMath.atan2(y, x);
  }
  public double getDelta() {
    return FastMath.asin(z / getNorm());
  }
  public double getNorm() {
    return FastMath.sqrt(x * x + y * y + z * z);
  }
  public double getNorm1() {
    return FastMath.abs(x) + FastMath.abs(y) + FastMath.abs(z);
  }
  public double getNormInf() {
    return FastMath.max(FastMath.max(FastMath.abs(x), FastMath.abs(y)), FastMath.abs(z));
  }
  public double getNormSq() {
    return x * x + y * y + z * z;
  }
  public double getX() {
    return x;
  }
  public double getY() {
    return y;
  }
  public double getZ() {
    return z;
  }
  public double[] toArray() {
    return new double[]{ x, y, z } ;
  }
  @Override() public int hashCode() {
    if(isNaN()) {
      return 642;
    }
    return 643 * (164 * MathUtils.hash(x) + 3 * MathUtils.hash(y) + MathUtils.hash(z));
  }
}