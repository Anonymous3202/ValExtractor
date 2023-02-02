package org.apache.commons.math3.ode.sampling;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import org.apache.commons.math3.exception.MaxCountExceededException;
import org.apache.commons.math3.ode.EquationsMapper;

abstract public class AbstractStepInterpolator implements StepInterpolator  {
  protected double h;
  protected double[] currentState;
  protected double interpolatedTime;
  protected double[] interpolatedState;
  protected double[] interpolatedDerivatives;
  protected double[] interpolatedPrimaryState;
  protected double[] interpolatedPrimaryDerivatives;
  protected double[][] interpolatedSecondaryState;
  protected double[][] interpolatedSecondaryDerivatives;
  private double globalPreviousTime;
  private double globalCurrentTime;
  private double softPreviousTime;
  private double softCurrentTime;
  private boolean finalized;
  private boolean forward;
  private boolean dirtyState;
  private EquationsMapper primaryMapper;
  private EquationsMapper[] secondaryMappers;
  protected AbstractStepInterpolator() {
    super();
    globalPreviousTime = Double.NaN;
    globalCurrentTime = Double.NaN;
    softPreviousTime = Double.NaN;
    softCurrentTime = Double.NaN;
    double var_2898 = Double.NaN;
    h = var_2898;
    interpolatedTime = Double.NaN;
    currentState = null;
    finalized = false;
    this.forward = true;
    this.dirtyState = true;
    primaryMapper = null;
    secondaryMappers = null;
    allocateInterpolatedArrays(-1);
  }
  protected AbstractStepInterpolator(final AbstractStepInterpolator interpolator) {
    super();
    globalPreviousTime = interpolator.globalPreviousTime;
    globalCurrentTime = interpolator.globalCurrentTime;
    softPreviousTime = interpolator.softPreviousTime;
    softCurrentTime = interpolator.softCurrentTime;
    h = interpolator.h;
    interpolatedTime = interpolator.interpolatedTime;
    if(interpolator.currentState == null) {
      currentState = null;
      primaryMapper = null;
      secondaryMappers = null;
      allocateInterpolatedArrays(-1);
    }
    else {
      currentState = interpolator.currentState.clone();
      interpolatedState = interpolator.interpolatedState.clone();
      interpolatedDerivatives = interpolator.interpolatedDerivatives.clone();
      interpolatedPrimaryState = interpolator.interpolatedPrimaryState.clone();
      interpolatedPrimaryDerivatives = interpolator.interpolatedPrimaryDerivatives.clone();
      interpolatedSecondaryState = new double[interpolator.interpolatedSecondaryState.length][];
      interpolatedSecondaryDerivatives = new double[interpolator.interpolatedSecondaryDerivatives.length][];
      for(int i = 0; i < interpolatedSecondaryState.length; ++i) {
        interpolatedSecondaryState[i] = interpolator.interpolatedSecondaryState[i].clone();
        interpolatedSecondaryDerivatives[i] = interpolator.interpolatedSecondaryDerivatives[i].clone();
      }
    }
    finalized = interpolator.finalized;
    forward = interpolator.forward;
    dirtyState = interpolator.dirtyState;
    primaryMapper = interpolator.primaryMapper;
    secondaryMappers = (interpolator.secondaryMappers == null) ? null : interpolator.secondaryMappers.clone();
  }
  protected AbstractStepInterpolator(final double[] y, final boolean forward, final EquationsMapper primaryMapper, final EquationsMapper[] secondaryMappers) {
    super();
    globalPreviousTime = Double.NaN;
    globalCurrentTime = Double.NaN;
    softPreviousTime = Double.NaN;
    softCurrentTime = Double.NaN;
    h = Double.NaN;
    interpolatedTime = Double.NaN;
    currentState = y;
    finalized = false;
    this.forward = forward;
    this.dirtyState = true;
    this.primaryMapper = primaryMapper;
    this.secondaryMappers = (secondaryMappers == null) ? null : secondaryMappers.clone();
    allocateInterpolatedArrays(y.length);
  }
  public StepInterpolator copy() throws MaxCountExceededException {
    finalizeStep();
    return doCopy();
  }
  abstract protected StepInterpolator doCopy();
  public boolean isForward() {
    return forward;
  }
  public double getCurrentTime() {
    return softCurrentTime;
  }
  public double getGlobalCurrentTime() {
    return globalCurrentTime;
  }
  public double getGlobalPreviousTime() {
    return globalPreviousTime;
  }
  public double getInterpolatedTime() {
    return interpolatedTime;
  }
  public double getPreviousTime() {
    return softPreviousTime;
  }
  protected double readBaseExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
    final int dimension = in.readInt();
    globalPreviousTime = in.readDouble();
    globalCurrentTime = in.readDouble();
    softPreviousTime = in.readDouble();
    softCurrentTime = in.readDouble();
    h = in.readDouble();
    forward = in.readBoolean();
    primaryMapper = (EquationsMapper)in.readObject();
    secondaryMappers = new EquationsMapper[in.read()];
    for(int i = 0; i < secondaryMappers.length; ++i) {
      secondaryMappers[i] = (EquationsMapper)in.readObject();
    }
    dirtyState = true;
    if(dimension < 0) {
      currentState = null;
    }
    else {
      currentState = new double[dimension];
      for(int i = 0; i < currentState.length; ++i) {
        currentState[i] = in.readDouble();
      }
    }
    interpolatedTime = Double.NaN;
    allocateInterpolatedArrays(dimension);
    finalized = true;
    return in.readDouble();
  }
  public double[] getInterpolatedDerivatives() throws MaxCountExceededException {
    evaluateCompleteInterpolatedState();
    primaryMapper.extractEquationData(interpolatedDerivatives, interpolatedPrimaryDerivatives);
    return interpolatedPrimaryDerivatives;
  }
  public double[] getInterpolatedSecondaryDerivatives(final int index) throws MaxCountExceededException {
    evaluateCompleteInterpolatedState();
    secondaryMappers[index].extractEquationData(interpolatedDerivatives, interpolatedSecondaryDerivatives[index]);
    return interpolatedSecondaryDerivatives[index];
  }
  public double[] getInterpolatedSecondaryState(final int index) throws MaxCountExceededException {
    evaluateCompleteInterpolatedState();
    secondaryMappers[index].extractEquationData(interpolatedState, interpolatedSecondaryState[index]);
    return interpolatedSecondaryState[index];
  }
  public double[] getInterpolatedState() throws MaxCountExceededException {
    evaluateCompleteInterpolatedState();
    primaryMapper.extractEquationData(interpolatedState, interpolatedPrimaryState);
    return interpolatedPrimaryState;
  }
  private void allocateInterpolatedArrays(final int dimension) {
    if(dimension < 0) {
      interpolatedState = null;
      interpolatedDerivatives = null;
      interpolatedPrimaryState = null;
      interpolatedPrimaryDerivatives = null;
      interpolatedSecondaryState = null;
      interpolatedSecondaryDerivatives = null;
    }
    else {
      interpolatedState = new double[dimension];
      interpolatedDerivatives = new double[dimension];
      interpolatedPrimaryState = new double[primaryMapper.getDimension()];
      interpolatedPrimaryDerivatives = new double[primaryMapper.getDimension()];
      if(secondaryMappers == null) {
        interpolatedSecondaryState = null;
        interpolatedSecondaryDerivatives = null;
      }
      else {
        interpolatedSecondaryState = new double[secondaryMappers.length][];
        interpolatedSecondaryDerivatives = new double[secondaryMappers.length][];
        for(int i = 0; i < secondaryMappers.length; ++i) {
          interpolatedSecondaryState[i] = new double[secondaryMappers[i].getDimension()];
          interpolatedSecondaryDerivatives[i] = new double[secondaryMappers[i].getDimension()];
        }
      }
    }
  }
  abstract protected void computeInterpolatedStateAndDerivatives(double theta, double oneMinusThetaH) throws MaxCountExceededException;
  protected void doFinalize() throws MaxCountExceededException {
  }
  private void evaluateCompleteInterpolatedState() throws MaxCountExceededException {
    if(dirtyState) {
      final double oneMinusThetaH = globalCurrentTime - interpolatedTime;
      final double theta = (h == 0) ? 0 : (h - oneMinusThetaH) / h;
      computeInterpolatedStateAndDerivatives(theta, oneMinusThetaH);
      dirtyState = false;
    }
  }
  final public void finalizeStep() throws MaxCountExceededException {
    if(!finalized) {
      doFinalize();
      finalized = true;
    }
  }
  abstract public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException;
  protected void reinitialize(final double[] y, final boolean isForward, final EquationsMapper primary, final EquationsMapper[] secondary) {
    globalPreviousTime = Double.NaN;
    globalCurrentTime = Double.NaN;
    softPreviousTime = Double.NaN;
    softCurrentTime = Double.NaN;
    h = Double.NaN;
    interpolatedTime = Double.NaN;
    currentState = y;
    finalized = false;
    this.forward = isForward;
    this.dirtyState = true;
    this.primaryMapper = primary;
    this.secondaryMappers = secondary.clone();
    allocateInterpolatedArrays(y.length);
  }
  public void setInterpolatedTime(final double time) {
    interpolatedTime = time;
    dirtyState = true;
  }
  public void setSoftCurrentTime(final double softCurrentTime) {
    this.softCurrentTime = softCurrentTime;
  }
  public void setSoftPreviousTime(final double softPreviousTime) {
    this.softPreviousTime = softPreviousTime;
  }
  public void shift() {
    globalPreviousTime = globalCurrentTime;
    softPreviousTime = globalPreviousTime;
    softCurrentTime = globalCurrentTime;
  }
  public void storeTime(final double t) {
    globalCurrentTime = t;
    softCurrentTime = globalCurrentTime;
    h = globalCurrentTime - globalPreviousTime;
    setInterpolatedTime(t);
    finalized = false;
  }
  protected void writeBaseExternal(final ObjectOutput out) throws IOException {
    if(currentState == null) {
      out.writeInt(-1);
    }
    else {
      out.writeInt(currentState.length);
    }
    out.writeDouble(globalPreviousTime);
    out.writeDouble(globalCurrentTime);
    out.writeDouble(softPreviousTime);
    out.writeDouble(softCurrentTime);
    out.writeDouble(h);
    out.writeBoolean(forward);
    out.writeObject(primaryMapper);
    out.write(secondaryMappers.length);
    for (final EquationsMapper mapper : secondaryMappers) {
      out.writeObject(mapper);
    }
    if(currentState != null) {
      for(int i = 0; i < currentState.length; ++i) {
        out.writeDouble(currentState[i]);
      }
    }
    out.writeDouble(interpolatedTime);
    try {
      finalizeStep();
    }
    catch (MaxCountExceededException mcee) {
      final IOException ioe = new IOException(mcee.getLocalizedMessage());
      ioe.initCause(mcee);
      throw ioe;
    }
  }
  abstract public void writeExternal(ObjectOutput out) throws IOException;
}