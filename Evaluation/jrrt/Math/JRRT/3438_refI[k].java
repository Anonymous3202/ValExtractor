package org.apache.commons.math3.optimization.direct;
import java.util.Arrays;
import java.util.Comparator;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.exception.NotStrictlyPositiveException;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.exception.ZeroException;
import org.apache.commons.math3.exception.OutOfRangeException;
import org.apache.commons.math3.exception.NullArgumentException;
import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.apache.commons.math3.exception.util.LocalizedFormats;
import org.apache.commons.math3.optimization.PointValuePair;
import org.apache.commons.math3.optimization.OptimizationData;

abstract @Deprecated() public class AbstractSimplex implements OptimizationData  {
  private PointValuePair[] simplex;
  private double[][] startConfiguration;
  final private int dimension;
  protected AbstractSimplex(final double[] steps) {
    super();
    if(steps == null) {
      throw new NullArgumentException();
    }
    if(steps.length == 0) {
      throw new ZeroException();
    }
    dimension = steps.length;
    startConfiguration = new double[dimension][dimension];
    for(int i = 0; i < dimension; i++) {
      final double[] vertexI = startConfiguration[i];
      for(int j = 0; j < i + 1; j++) {
        if(steps[j] == 0) {
          throw new ZeroException(LocalizedFormats.EQUAL_VERTICES_IN_SIMPLEX);
        }
        System.arraycopy(steps, 0, vertexI, 0, j + 1);
      }
    }
  }
  protected AbstractSimplex(final double[][] referenceSimplex) {
    super();
    if(referenceSimplex.length <= 0) {
      throw new NotStrictlyPositiveException(LocalizedFormats.SIMPLEX_NEED_ONE_POINT, referenceSimplex.length);
    }
    dimension = referenceSimplex.length - 1;
    startConfiguration = new double[dimension][dimension];
    final double[] ref0 = referenceSimplex[0];
    for(int i = 0; i < referenceSimplex.length; i++) {
      final double[] refI = referenceSimplex[i];
      if(refI.length != dimension) {
        throw new DimensionMismatchException(refI.length, dimension);
      }
      for(int j = 0; j < i; j++) {
        final double[] refJ = referenceSimplex[j];
        boolean allEquals = true;
        for(int k = 0; k < dimension; k++) {
          if(refI[k] != refJ[k]) {
            allEquals = false;
            break ;
          }
        }
        if(allEquals) {
          throw new MathIllegalArgumentException(LocalizedFormats.EQUAL_VERTICES_IN_SIMPLEX, i, j);
        }
      }
      if(i > 0) {
        final double[] confI = startConfiguration[i - 1];
        for(int k = 0; k < dimension; k++) {
          double var_3438 = refI[k];
          confI[k] = var_3438 - ref0[k];
        }
      }
    }
  }
  protected AbstractSimplex(int n) {
    this(n, 1D);
  }
  protected AbstractSimplex(int n, double sideLength) {
    this(createHypercubeSteps(n, sideLength));
  }
  public PointValuePair getPoint(int index) {
    if(index < 0 || index >= simplex.length) {
      throw new OutOfRangeException(index, 0, simplex.length - 1);
    }
    return simplex[index];
  }
  public PointValuePair[] getPoints() {
    final PointValuePair[] copy = new PointValuePair[simplex.length];
    System.arraycopy(simplex, 0, copy, 0, simplex.length);
    return copy;
  }
  private static double[] createHypercubeSteps(int n, double sideLength) {
    final double[] steps = new double[n];
    for(int i = 0; i < n; i++) {
      steps[i] = sideLength;
    }
    return steps;
  }
  public int getDimension() {
    return dimension;
  }
  public int getSize() {
    return simplex.length;
  }
  public void build(final double[] startPoint) {
    if(dimension != startPoint.length) {
      throw new DimensionMismatchException(dimension, startPoint.length);
    }
    simplex = new PointValuePair[dimension + 1];
    simplex[0] = new PointValuePair(startPoint, Double.NaN);
    for(int i = 0; i < dimension; i++) {
      final double[] confI = startConfiguration[i];
      final double[] vertexI = new double[dimension];
      for(int k = 0; k < dimension; k++) {
        vertexI[k] = startPoint[k] + confI[k];
      }
      simplex[i + 1] = new PointValuePair(vertexI, Double.NaN);
    }
  }
  public void evaluate(final MultivariateFunction evaluationFunction, final Comparator<PointValuePair> comparator) {
    for(int i = 0; i < simplex.length; i++) {
      final PointValuePair vertex = simplex[i];
      final double[] point = vertex.getPointRef();
      if(Double.isNaN(vertex.getValue())) {
        simplex[i] = new PointValuePair(point, evaluationFunction.value(point), false);
      }
    }
    Arrays.sort(simplex, comparator);
  }
  abstract public void iterate(final MultivariateFunction evaluationFunction, final Comparator<PointValuePair> comparator);
  protected void replaceWorstPoint(PointValuePair pointValuePair, final Comparator<PointValuePair> comparator) {
    for(int i = 0; i < dimension; i++) {
      if(comparator.compare(simplex[i], pointValuePair) > 0) {
        PointValuePair tmp = simplex[i];
        simplex[i] = pointValuePair;
        pointValuePair = tmp;
      }
    }
    simplex[dimension] = pointValuePair;
  }
  protected void setPoint(int index, PointValuePair point) {
    if(index < 0 || index >= simplex.length) {
      throw new OutOfRangeException(index, 0, simplex.length - 1);
    }
    simplex[index] = point;
  }
  protected void setPoints(PointValuePair[] points) {
    if(points.length != simplex.length) {
      throw new DimensionMismatchException(points.length, simplex.length);
    }
    simplex = points;
  }
}