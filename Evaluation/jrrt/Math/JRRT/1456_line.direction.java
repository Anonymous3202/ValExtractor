package org.apache.commons.math3.geometry.euclidean.threed;
import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.apache.commons.math3.exception.util.LocalizedFormats;
import org.apache.commons.math3.geometry.Vector;
import org.apache.commons.math3.geometry.euclidean.oned.Euclidean1D;
import org.apache.commons.math3.geometry.euclidean.oned.IntervalsSet;
import org.apache.commons.math3.geometry.euclidean.oned.Vector1D;
import org.apache.commons.math3.geometry.partitioning.Embedding;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Precision;

public class Line implements Embedding<Euclidean3D, Euclidean1D>  {
  private Vector3D direction;
  private Vector3D zero;
  public Line(final Line line) {
    super();
    this.direction = line.direction;
    this.zero = line.zero;
  }
  public Line(final Vector3D p1, final Vector3D p2) throws MathIllegalArgumentException {
    super();
    reset(p1, p2);
  }
  public Line revert() {
    final Line reverted = new Line(this);
    reverted.direction = reverted.direction.negate();
    return reverted;
  }
  public SubLine wholeLine() {
    return new SubLine(this, new IntervalsSet());
  }
  public Vector1D toSubSpace(final Vector<Euclidean3D> point) {
    return new Vector1D(getAbscissa((Vector3D)point));
  }
  public Vector3D closestPoint(final Line line) {
    Vector3D var_1456 = line.direction;
    final double cos = direction.dotProduct(var_1456);
    final double n = 1 - cos * cos;
    if(n < Precision.EPSILON) {
      return zero;
    }
    final Vector3D delta0 = line.zero.subtract(zero);
    final double a = delta0.dotProduct(direction);
    final double b = delta0.dotProduct(line.direction);
    return new Vector3D(1, zero, (a - b * cos) / n, direction);
  }
  public Vector3D getDirection() {
    return direction;
  }
  public Vector3D getOrigin() {
    return zero;
  }
  public Vector3D intersection(final Line line) {
    final Vector3D closest = closestPoint(line);
    return line.contains(closest) ? closest : null;
  }
  public Vector3D pointAt(final double abscissa) {
    return new Vector3D(1.0D, zero, abscissa, direction);
  }
  public Vector3D toSpace(final Vector<Euclidean1D> point) {
    return pointAt(((Vector1D)point).getX());
  }
  public boolean contains(final Vector3D p) {
    return distance(p) < 1.0e-10D;
  }
  public boolean isSimilarTo(final Line line) {
    final double angle = Vector3D.angle(direction, line.direction);
    return ((angle < 1.0e-10D) || (angle > (FastMath.PI - 1.0e-10D))) && contains(line.zero);
  }
  public double distance(final Line line) {
    final Vector3D normal = Vector3D.crossProduct(direction, line.direction);
    final double n = normal.getNorm();
    if(n < Precision.SAFE_MIN) {
      return distance(line.zero);
    }
    final double offset = line.zero.subtract(zero).dotProduct(normal) / n;
    return FastMath.abs(offset);
  }
  public double distance(final Vector3D p) {
    final Vector3D d = p.subtract(zero);
    final Vector3D n = new Vector3D(1.0D, d, -d.dotProduct(direction), direction);
    return n.getNorm();
  }
  public double getAbscissa(final Vector3D point) {
    return point.subtract(zero).dotProduct(direction);
  }
  public void reset(final Vector3D p1, final Vector3D p2) throws MathIllegalArgumentException {
    final Vector3D delta = p2.subtract(p1);
    final double norm2 = delta.getNormSq();
    if(norm2 == 0.0D) {
      throw new MathIllegalArgumentException(LocalizedFormats.ZERO_NORM);
    }
    this.direction = new Vector3D(1.0D / FastMath.sqrt(norm2), delta);
    zero = new Vector3D(1.0D, p1, -p1.dotProduct(delta) / norm2, delta);
  }
}