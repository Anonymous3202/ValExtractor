package org.apache.commons.math3.analysis.solvers;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.exception.NumberIsTooLargeException;
import org.apache.commons.math3.exception.NoBracketingException;
import org.apache.commons.math3.exception.TooManyEvaluationsException;

public class MullerSolver extends AbstractUnivariateSolver  {
  final private static double DEFAULT_ABSOLUTE_ACCURACY = 1e-6D;
  public MullerSolver() {
    this(DEFAULT_ABSOLUTE_ACCURACY);
  }
  public MullerSolver(double absoluteAccuracy) {
    super(absoluteAccuracy);
  }
  public MullerSolver(double relativeAccuracy, double absoluteAccuracy) {
    super(relativeAccuracy, absoluteAccuracy);
  }
  @Override() protected double doSolve() throws TooManyEvaluationsException, NumberIsTooLargeException, NoBracketingException {
    final double min = getMin();
    final double max = getMax();
    final double initial = getStartValue();
    final double functionValueAccuracy = getFunctionValueAccuracy();
    verifySequence(min, initial, max);
    final double fMin = computeObjectiveValue(min);
    if(FastMath.abs(fMin) < functionValueAccuracy) {
      return min;
    }
    final double fMax = computeObjectiveValue(max);
    if(FastMath.abs(fMax) < functionValueAccuracy) {
      return max;
    }
    final double fInitial = computeObjectiveValue(initial);
    if(FastMath.abs(fInitial) < functionValueAccuracy) {
      return initial;
    }
    verifyBracketing(min, max);
    if(isBracketing(min, initial)) {
      return solve(min, initial, fMin, fInitial);
    }
    else {
      return solve(initial, max, fInitial, fMax);
    }
  }
  private double solve(double min, double max, double fMin, double fMax) throws TooManyEvaluationsException {
    final double relativeAccuracy = getRelativeAccuracy();
    final double absoluteAccuracy = getAbsoluteAccuracy();
    final double functionValueAccuracy = getFunctionValueAccuracy();
    double x0 = min;
    double y0 = fMin;
    double x2 = max;
    double y2 = fMax;
    double x1 = 0.5D * (x0 + x2);
    double y1 = computeObjectiveValue(x1);
    double oldx = Double.POSITIVE_INFINITY;
    while(true){
      final double d01 = (y1 - y0) / (x1 - x0);
      final double d12 = (y2 - y1) / (x2 - x1);
      final double d012 = (d12 - d01) / (x2 - x0);
      final double c1 = d01 + (x1 - x0) * d012;
      final double delta = c1 * c1 - 4 * y1 * d012;
      final double xplus = x1 + (-2.0D * y1) / (c1 + FastMath.sqrt(delta));
      final double xminus = x1 + (-2.0D * y1) / (c1 - FastMath.sqrt(delta));
      final double x = isSequence(x0, xplus, x2) ? xplus : xminus;
      final double y = computeObjectiveValue(x);
      final double tolerance = FastMath.max(relativeAccuracy * FastMath.abs(x), absoluteAccuracy);
      if(FastMath.abs(x - oldx) <= tolerance || FastMath.abs(y) <= functionValueAccuracy) {
        return x;
      }
      boolean bisect = (x < x1 && (x1 - x0) > 0.95D * (x2 - x0)) || (x > x1 && (x2 - x1) > 0.95D * (x2 - x0)) || (x == x1);
      if(!bisect) {
        x0 = x < x1 ? x0 : x1;
        y0 = x < x1 ? y0 : y1;
        x2 = x > x1 ? x2 : x1;
        y2 = x > x1 ? y2 : y1;
        x1 = x;
        y1 = y;
        oldx = x;
      }
      else {
        double xm = 0.5D * (x0 + x2);
        double ym = computeObjectiveValue(xm);
        if(FastMath.signum(y0) + FastMath.signum(ym) == 0.0D) {
          x2 = xm;
          y2 = ym;
        }
        else {
          x0 = xm;
          y0 = ym;
        }
        x1 = 0.5D * (x0 + x2);
        double var_492 = computeObjectiveValue(x1);
        y1 = var_492;
        oldx = Double.POSITIVE_INFINITY;
      }
    }
  }
}