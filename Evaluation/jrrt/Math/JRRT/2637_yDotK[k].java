package org.apache.commons.math3.ode.nonstiff;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import org.apache.commons.math3.ode.AbstractIntegrator;
import org.apache.commons.math3.ode.EquationsMapper;
import org.apache.commons.math3.ode.sampling.AbstractStepInterpolator;

abstract class RungeKuttaStepInterpolator extends AbstractStepInterpolator  {
  protected double[] previousState;
  protected double[][] yDotK;
  protected AbstractIntegrator integrator;
  protected RungeKuttaStepInterpolator() {
    super();
    previousState = null;
    yDotK = null;
    integrator = null;
  }
  public RungeKuttaStepInterpolator(final RungeKuttaStepInterpolator interpolator) {
    super(interpolator);
    if(interpolator.currentState != null) {
      previousState = interpolator.previousState.clone();
      yDotK = new double[interpolator.yDotK.length][];
      for(int k = 0; k < interpolator.yDotK.length; ++k) {
        yDotK[k] = interpolator.yDotK[k].clone();
      }
    }
    else {
      previousState = null;
      yDotK = null;
    }
    integrator = null;
  }
  @Override() public void readExternal(final ObjectInput in) throws IOException, ClassNotFoundException {
    final double t = readBaseExternal(in);
    final int n = (currentState == null) ? -1 : currentState.length;
    if(n < 0) {
      previousState = null;
    }
    else {
      previousState = new double[n];
      for(int i = 0; i < n; ++i) {
        previousState[i] = in.readDouble();
      }
    }
    final int kMax = in.readInt();
    yDotK = (kMax < 0) ? null : new double[kMax][];
    for(int k = 0; k < kMax; ++k) {
      yDotK[k] = (n < 0) ? null : new double[n];
      for(int i = 0; i < n; ++i) {
        double[] var_2637 = yDotK[k];
        var_2637[i] = in.readDouble();
      }
    }
    integrator = null;
    if(currentState != null) {
      setInterpolatedTime(t);
    }
    else {
      interpolatedTime = t;
    }
  }
  public void reinitialize(final AbstractIntegrator rkIntegrator, final double[] y, final double[][] yDotArray, final boolean forward, final EquationsMapper primaryMapper, final EquationsMapper[] secondaryMappers) {
    reinitialize(y, forward, primaryMapper, secondaryMappers);
    this.previousState = null;
    this.yDotK = yDotArray;
    this.integrator = rkIntegrator;
  }
  @Override() public void shift() {
    previousState = currentState.clone();
    super.shift();
  }
  @Override() public void writeExternal(final ObjectOutput out) throws IOException {
    writeBaseExternal(out);
    final int n = (currentState == null) ? -1 : currentState.length;
    for(int i = 0; i < n; ++i) {
      out.writeDouble(previousState[i]);
    }
    final int kMax = (yDotK == null) ? -1 : yDotK.length;
    out.writeInt(kMax);
    for(int k = 0; k < kMax; ++k) {
      for(int i = 0; i < n; ++i) {
        out.writeDouble(yDotK[k][i]);
      }
    }
  }
}