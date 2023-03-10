package org.apache.commons.math3.ode.nonstiff;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.exception.MaxCountExceededException;
import org.apache.commons.math3.exception.NoBracketingException;
import org.apache.commons.math3.exception.NumberIsTooSmallException;
import org.apache.commons.math3.ode.ExpandableStatefulODE;
import org.apache.commons.math3.util.FastMath;

abstract public class EmbeddedRungeKuttaIntegrator extends AdaptiveStepsizeIntegrator  {
  final private boolean fsal;
  final private double[] c;
  final private double[][] a;
  final private double[] b;
  final private RungeKuttaStepInterpolator prototype;
  final private double exp;
  private double safety;
  private double minReduction;
  private double maxGrowth;
  protected EmbeddedRungeKuttaIntegrator(final String name, final boolean fsal, final double[] c, final double[][] a, final double[] b, final RungeKuttaStepInterpolator prototype, final double minStep, final double maxStep, final double scalAbsoluteTolerance, final double scalRelativeTolerance) {
    super(name, minStep, maxStep, scalAbsoluteTolerance, scalRelativeTolerance);
    this.fsal = fsal;
    this.c = c;
    this.a = a;
    this.b = b;
    this.prototype = prototype;
    exp = -1.0D / getOrder();
    setSafety(0.9D);
    setMinReduction(0.2D);
    setMaxGrowth(10.0D);
  }
  protected EmbeddedRungeKuttaIntegrator(final String name, final boolean fsal, final double[] c, final double[][] a, final double[] b, final RungeKuttaStepInterpolator prototype, final double minStep, final double maxStep, final double[] vecAbsoluteTolerance, final double[] vecRelativeTolerance) {
    super(name, minStep, maxStep, vecAbsoluteTolerance, vecRelativeTolerance);
    this.fsal = fsal;
    this.c = c;
    this.a = a;
    this.b = b;
    this.prototype = prototype;
    exp = -1.0D / getOrder();
    setSafety(0.9D);
    setMinReduction(0.2D);
    setMaxGrowth(10.0D);
  }
  abstract protected double estimateError(double[][] yDotK, double[] y0, double[] y1, double h);
  public double getMaxGrowth() {
    return maxGrowth;
  }
  public double getMinReduction() {
    return minReduction;
  }
  public double getSafety() {
    return safety;
  }
  abstract public int getOrder();
  @Override() public void integrate(final ExpandableStatefulODE equations, final double t) throws NumberIsTooSmallException, DimensionMismatchException, MaxCountExceededException, NoBracketingException {
    sanityChecks(equations, t);
    setEquations(equations);
    final boolean forward = t > equations.getTime();
    final double[] y0 = equations.getCompleteState();
    final double[] y = y0.clone();
    final int stages = c.length + 1;
    final double[][] yDotK = new double[stages][y.length];
    final double[] yTmp = y0.clone();
    final double[] yDotTmp = new double[y.length];
    final RungeKuttaStepInterpolator interpolator = (RungeKuttaStepInterpolator)prototype.copy();
    interpolator.reinitialize(this, yTmp, yDotK, forward, equations.getPrimaryMapper(), equations.getSecondaryMappers());
    interpolator.storeTime(equations.getTime());
    stepStart = equations.getTime();
    double hNew = 0;
    boolean firstTime = true;
    initIntegration(equations.getTime(), y0, t);
    isLastStep = false;
    do {
      interpolator.shift();
      double error = 10;
      while(error >= 1.0D){
        if(firstTime || !fsal) {
          computeDerivatives(stepStart, y, yDotK[0]);
        }
        if(firstTime) {
          final double[] scale = new double[mainSetDimension];
          if(vecAbsoluteTolerance == null) {
            for(int i = 0; i < scale.length; ++i) {
              scale[i] = scalAbsoluteTolerance + scalRelativeTolerance * FastMath.abs(y[i]);
            }
          }
          else {
            for(int i = 0; i < scale.length; ++i) {
              scale[i] = vecAbsoluteTolerance[i] + vecRelativeTolerance[i] * FastMath.abs(y[i]);
            }
          }
          hNew = initializeStep(forward, getOrder(), scale, stepStart, y, yDotK[0], yTmp, yDotK[1]);
          firstTime = false;
        }
        stepSize = hNew;
        if(forward) {
          if(stepStart + stepSize >= t) {
            stepSize = t - stepStart;
          }
        }
        else {
          if(stepStart + stepSize <= t) {
            stepSize = t - stepStart;
          }
        }
        for(int k = 1; k < stages; ++k) {
          for(int j = 0; j < y0.length; ++j) {
            double var_2665 = yDotK[0][j];
            double sum = a[k - 1][0] * var_2665;
            for(int l = 1; l < k; ++l) {
              sum += a[k - 1][l] * yDotK[l][j];
            }
            yTmp[j] = y[j] + stepSize * sum;
          }
          computeDerivatives(stepStart + c[k - 1] * stepSize, yTmp, yDotK[k]);
        }
        for(int j = 0; j < y0.length; ++j) {
          double sum = b[0] * yDotK[0][j];
          for(int l = 1; l < stages; ++l) {
            sum += b[l] * yDotK[l][j];
          }
          yTmp[j] = y[j] + stepSize * sum;
        }
        error = estimateError(yDotK, y, yTmp, stepSize);
        if(error >= 1.0D) {
          final double factor = FastMath.min(maxGrowth, FastMath.max(minReduction, safety * FastMath.pow(error, exp)));
          hNew = filterStep(stepSize * factor, forward, false);
        }
      }
      interpolator.storeTime(stepStart + stepSize);
      System.arraycopy(yTmp, 0, y, 0, y0.length);
      System.arraycopy(yDotK[stages - 1], 0, yDotTmp, 0, y0.length);
      stepStart = acceptStep(interpolator, y, yDotTmp, t);
      System.arraycopy(y, 0, yTmp, 0, y.length);
      if(!isLastStep) {
        interpolator.storeTime(stepStart);
        if(fsal) {
          System.arraycopy(yDotTmp, 0, yDotK[0], 0, y0.length);
        }
        final double factor = FastMath.min(maxGrowth, FastMath.max(minReduction, safety * FastMath.pow(error, exp)));
        final double scaledH = stepSize * factor;
        final double nextT = stepStart + scaledH;
        final boolean nextIsLast = forward ? (nextT >= t) : (nextT <= t);
        hNew = filterStep(scaledH, forward, nextIsLast);
        final double filteredNextT = stepStart + hNew;
        final boolean filteredNextIsLast = forward ? (filteredNextT >= t) : (filteredNextT <= t);
        if(filteredNextIsLast) {
          hNew = t - stepStart;
        }
      }
    }while(!isLastStep);
    equations.setTime(stepStart);
    equations.setCompleteState(y);
    resetInternalState();
  }
  public void setMaxGrowth(final double maxGrowth) {
    this.maxGrowth = maxGrowth;
  }
  public void setMinReduction(final double minReduction) {
    this.minReduction = minReduction;
  }
  public void setSafety(final double safety) {
    this.safety = safety;
  }
}