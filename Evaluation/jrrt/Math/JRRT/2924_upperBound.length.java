package org.apache.commons.math3.optim;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.exception.NumberIsTooSmallException;
import org.apache.commons.math3.exception.NumberIsTooLargeException;
abstract public class BaseMultivariateOptimizer<PAIR extends java.lang.Object> extends BaseOptimizer<PAIR>  {
  private double[] start;
  private double[] lowerBound;
  private double[] upperBound;
  protected BaseMultivariateOptimizer(ConvergenceChecker<PAIR> checker) {
    super(checker);
  }
  @Override() public PAIR optimize(OptimizationData ... optData) {
    return super.optimize(optData);
  }
  public double[] getLowerBound() {
    return lowerBound == null ? null : lowerBound.clone();
  }
  public double[] getStartPoint() {
    return start == null ? null : start.clone();
  }
  public double[] getUpperBound() {
    return upperBound == null ? null : upperBound.clone();
  }
  private void checkParameters() {
    if(start != null) {
      final int dim = start.length;
      if(lowerBound != null) {
        if(lowerBound.length != dim) {
          throw new DimensionMismatchException(lowerBound.length, dim);
        }
        for(int i = 0; i < dim; i++) {
          final double v = start[i];
          final double lo = lowerBound[i];
          if(v < lo) {
            throw new NumberIsTooSmallException(v, lo, true);
          }
        }
      }
      if(upperBound != null) {
        int var_2924 = upperBound.length;
        if(var_2924 != dim) {
          throw new DimensionMismatchException(upperBound.length, dim);
        }
        for(int i = 0; i < dim; i++) {
          final double v = start[i];
          final double hi = upperBound[i];
          if(v > hi) {
            throw new NumberIsTooLargeException(v, hi, true);
          }
        }
      }
    }
  }
  @Override() protected void parseOptimizationData(OptimizationData ... optData) {
    super.parseOptimizationData(optData);
    for (OptimizationData data : optData) {
      if(data instanceof InitialGuess) {
        start = ((InitialGuess)data).getInitialGuess();
        continue ;
      }
      if(data instanceof SimpleBounds) {
        final SimpleBounds bounds = (SimpleBounds)data;
        lowerBound = bounds.getLower();
        upperBound = bounds.getUpper();
        continue ;
      }
    }
    checkParameters();
  }
}