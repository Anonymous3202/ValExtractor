package org.apache.commons.math3.ode.nonstiff;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.exception.MaxCountExceededException;
import org.apache.commons.math3.exception.NoBracketingException;
import org.apache.commons.math3.exception.NumberIsTooSmallException;
import org.apache.commons.math3.exception.util.LocalizedFormats;
import org.apache.commons.math3.ode.AbstractIntegrator;
import org.apache.commons.math3.ode.ExpandableStatefulODE;
import org.apache.commons.math3.util.FastMath;

abstract public class AdaptiveStepsizeIntegrator extends AbstractIntegrator  {
  protected double scalAbsoluteTolerance;
  protected double scalRelativeTolerance;
  protected double[] vecAbsoluteTolerance;
  protected double[] vecRelativeTolerance;
  protected int mainSetDimension;
  private double initialStep;
  private double minStep;
  private double maxStep;
  public AdaptiveStepsizeIntegrator(final String name, final double minStep, final double maxStep, final double scalAbsoluteTolerance, final double scalRelativeTolerance) {
    super(name);
    setStepSizeControl(minStep, maxStep, scalAbsoluteTolerance, scalRelativeTolerance);
    resetInternalState();
  }
  public AdaptiveStepsizeIntegrator(final String name, final double minStep, final double maxStep, final double[] vecAbsoluteTolerance, final double[] vecRelativeTolerance) {
    super(name);
    setStepSizeControl(minStep, maxStep, vecAbsoluteTolerance, vecRelativeTolerance);
    resetInternalState();
  }
  protected double filterStep(final double h, final boolean forward, final boolean acceptSmall) throws NumberIsTooSmallException {
    double filteredH = h;
    if(FastMath.abs(h) < minStep) {
      if(acceptSmall) {
        filteredH = forward ? minStep : -minStep;
      }
      else {
        throw new NumberIsTooSmallException(LocalizedFormats.MINIMAL_STEPSIZE_REACHED_DURING_INTEGRATION, FastMath.abs(h), minStep, true);
      }
    }
    if(filteredH > maxStep) {
      filteredH = maxStep;
    }
    else 
      if(filteredH < -maxStep) {
        filteredH = -maxStep;
      }
    return filteredH;
  }
  @Override() public double getCurrentStepStart() {
    return stepStart;
  }
  public double getMaxStep() {
    return maxStep;
  }
  public double getMinStep() {
    return minStep;
  }
  public double initializeStep(final boolean forward, final int order, final double[] scale, final double t0, final double[] y0, final double[] yDot0, final double[] y1, final double[] yDot1) throws MaxCountExceededException, DimensionMismatchException {
    if(initialStep > 0) {
      return forward ? initialStep : -initialStep;
    }
    double ratio;
    double yOnScale2 = 0;
    double yDotOnScale2 = 0;
    for(int j = 0; j < scale.length; ++j) {
      double var_2629 = y0[j];
      ratio = var_2629 / scale[j];
      yOnScale2 += ratio * ratio;
      ratio = yDot0[j] / scale[j];
      yDotOnScale2 += ratio * ratio;
    }
    double h = ((yOnScale2 < 1.0e-10D) || (yDotOnScale2 < 1.0e-10D)) ? 1.0e-6D : (0.01D * FastMath.sqrt(yOnScale2 / yDotOnScale2));
    if(!forward) {
      h = -h;
    }
    for(int j = 0; j < y0.length; ++j) {
      y1[j] = y0[j] + h * yDot0[j];
    }
    computeDerivatives(t0 + h, y1, yDot1);
    double yDDotOnScale = 0;
    for(int j = 0; j < scale.length; ++j) {
      ratio = (yDot1[j] - yDot0[j]) / scale[j];
      yDDotOnScale += ratio * ratio;
    }
    yDDotOnScale = FastMath.sqrt(yDDotOnScale) / h;
    final double maxInv2 = FastMath.max(FastMath.sqrt(yDotOnScale2), yDDotOnScale);
    final double h1 = (maxInv2 < 1.0e-15D) ? FastMath.max(1.0e-6D, 0.001D * FastMath.abs(h)) : FastMath.pow(0.01D / maxInv2, 1.0D / order);
    h = FastMath.min(100.0D * FastMath.abs(h), h1);
    h = FastMath.max(h, 1.0e-12D * FastMath.abs(t0));
    if(h < getMinStep()) {
      h = getMinStep();
    }
    if(h > getMaxStep()) {
      h = getMaxStep();
    }
    if(!forward) {
      h = -h;
    }
    return h;
  }
  abstract @Override() public void integrate(ExpandableStatefulODE equations, double t) throws NumberIsTooSmallException, DimensionMismatchException, MaxCountExceededException, NoBracketingException;
  protected void resetInternalState() {
    stepStart = Double.NaN;
    stepSize = FastMath.sqrt(minStep * maxStep);
  }
  @Override() protected void sanityChecks(final ExpandableStatefulODE equations, final double t) throws DimensionMismatchException, NumberIsTooSmallException {
    super.sanityChecks(equations, t);
    mainSetDimension = equations.getPrimaryMapper().getDimension();
    if((vecAbsoluteTolerance != null) && (vecAbsoluteTolerance.length != mainSetDimension)) {
      throw new DimensionMismatchException(mainSetDimension, vecAbsoluteTolerance.length);
    }
    if((vecRelativeTolerance != null) && (vecRelativeTolerance.length != mainSetDimension)) {
      throw new DimensionMismatchException(mainSetDimension, vecRelativeTolerance.length);
    }
  }
  public void setInitialStepSize(final double initialStepSize) {
    if((initialStepSize < minStep) || (initialStepSize > maxStep)) {
      initialStep = -1.0D;
    }
    else {
      initialStep = initialStepSize;
    }
  }
  public void setStepSizeControl(final double minimalStep, final double maximalStep, final double absoluteTolerance, final double relativeTolerance) {
    minStep = FastMath.abs(minimalStep);
    maxStep = FastMath.abs(maximalStep);
    initialStep = -1;
    scalAbsoluteTolerance = absoluteTolerance;
    scalRelativeTolerance = relativeTolerance;
    vecAbsoluteTolerance = null;
    vecRelativeTolerance = null;
  }
  public void setStepSizeControl(final double minimalStep, final double maximalStep, final double[] absoluteTolerance, final double[] relativeTolerance) {
    minStep = FastMath.abs(minimalStep);
    maxStep = FastMath.abs(maximalStep);
    initialStep = -1;
    scalAbsoluteTolerance = 0;
    scalRelativeTolerance = 0;
    vecAbsoluteTolerance = absoluteTolerance.clone();
    vecRelativeTolerance = relativeTolerance.clone();
  }
}