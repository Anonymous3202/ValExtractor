package org.apache.commons.math3.optim.nonlinear.scalar.noderiv;
import java.util.Comparator;
import org.apache.commons.math3.analysis.MultivariateFunction;
import org.apache.commons.math3.optim.PointValuePair;

public class MultiDirectionalSimplex extends AbstractSimplex  {
  final private static double DEFAULT_KHI = 2;
  final private static double DEFAULT_GAMMA = 0.5D;
  final private double khi;
  final private double gamma;
  public MultiDirectionalSimplex(final double[] steps) {
    this(steps, DEFAULT_KHI, DEFAULT_GAMMA);
  }
  public MultiDirectionalSimplex(final double[] steps, final double khi, final double gamma) {
    super(steps);
    this.khi = khi;
    this.gamma = gamma;
  }
  public MultiDirectionalSimplex(final double[][] referenceSimplex) {
    this(referenceSimplex, DEFAULT_KHI, DEFAULT_GAMMA);
  }
  public MultiDirectionalSimplex(final double[][] referenceSimplex, final double khi, final double gamma) {
    super(referenceSimplex);
    this.khi = khi;
    this.gamma = gamma;
  }
  public MultiDirectionalSimplex(final int n) {
    this(n, 1D);
  }
  public MultiDirectionalSimplex(final int n, double sideLength) {
    this(n, sideLength, DEFAULT_KHI, DEFAULT_GAMMA);
  }
  public MultiDirectionalSimplex(final int n, double sideLength, final double khi, final double gamma) {
    super(n, sideLength);
    this.khi = khi;
    this.gamma = gamma;
  }
  public MultiDirectionalSimplex(final int n, final double khi, final double gamma) {
    this(n, 1D, khi, gamma);
  }
  private PointValuePair evaluateNewSimplex(final MultivariateFunction evaluationFunction, final PointValuePair[] original, final double coeff, final Comparator<PointValuePair> comparator) {
    final double[] xSmallest = original[0].getPointRef();
    setPoint(0, original[0]);
    final int dim = getDimension();
    for(int i = 1; i < getSize(); i++) {
      final double[] xOriginal = original[i].getPointRef();
      final double[] xTransformed = new double[dim];
      for(int j = 0; j < dim; j++) {
        xTransformed[j] = xSmallest[j] + coeff * (xSmallest[j] - xOriginal[j]);
      }
      setPoint(i, new PointValuePair(xTransformed, Double.NaN, false));
    }
    evaluate(evaluationFunction, comparator);
    return getPoint(0);
  }
  @Override() public void iterate(final MultivariateFunction evaluationFunction, final Comparator<PointValuePair> comparator) {
    final PointValuePair[] original = getPoints();
    final PointValuePair best = original[0];
    final PointValuePair reflected = evaluateNewSimplex(evaluationFunction, original, 1, comparator);
    if(comparator.compare(reflected, best) < 0) {
      PointValuePair[] var_3165 = getPoints();
      final PointValuePair[] reflectedSimplex = var_3165;
      final PointValuePair expanded = evaluateNewSimplex(evaluationFunction, original, khi, comparator);
      if(comparator.compare(reflected, expanded) <= 0) {
        setPoints(reflectedSimplex);
      }
      return ;
    }
    evaluateNewSimplex(evaluationFunction, original, gamma, comparator);
  }
}