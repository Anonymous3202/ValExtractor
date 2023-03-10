package org.apache.commons.math3.analysis.function;
import org.apache.commons.math3.analysis.DifferentiableUnivariateFunction;
import org.apache.commons.math3.analysis.FunctionUtils;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.differentiation.DerivativeStructure;
import org.apache.commons.math3.analysis.differentiation.UnivariateDifferentiableFunction;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.util.FastMath;

public class Sinc implements UnivariateDifferentiableFunction, DifferentiableUnivariateFunction  {
  final private static double SHORTCUT = 6.0e-3D;
  final private boolean normalized;
  public Sinc() {
    this(false);
  }
  public Sinc(boolean normalized) {
    super();
    this.normalized = normalized;
  }
  public DerivativeStructure value(final DerivativeStructure t) throws DimensionMismatchException {
    final double scaledX = (normalized ? FastMath.PI : 1) * t.getValue();
    final double scaledX2 = scaledX * scaledX;
    double[] f = new double[t.getOrder() + 1];
    if(FastMath.abs(scaledX) <= SHORTCUT) {
      for(int i = 0; i < f.length; ++i) {
        final int k = i / 2;
        if((i & 0x1) == 0) {
          f[i] = (((k & 0x1) == 0) ? 1 : -1) * (1.0D / (i + 1) - scaledX2 * (1.0D / (2 * i + 6) - scaledX2 / (24 * i + 120)));
        }
        else {
          f[i] = (((k & 0x1) == 0) ? -scaledX : scaledX) * (1.0D / (i + 2) - scaledX2 * (1.0D / (6 * i + 24) - scaledX2 / (120 * i + 720)));
        }
      }
    }
    else {
      final double inv = 1 / scaledX;
      final double cos = FastMath.cos(scaledX);
      final double sin = FastMath.sin(scaledX);
      f[0] = inv * sin;
      final double[] sc = new double[f.length];
      sc[0] = 1;
      double coeff = inv;
      for(int n = 1; n < f.length; ++n) {
        double s = 0;
        double c = 0;
        final int kStart;
        if((n & 0x1) == 0) {
          sc[n] = 0;
          kStart = n;
        }
        else {
          sc[n] = sc[n - 1];
          c = sc[n];
          kStart = n - 1;
        }
        for(int k = kStart; k > 1; k -= 2) {
          double var_179 = sc[k];
          sc[k] = (k - n) * var_179 - sc[k - 1];
          s = s * scaledX2 + sc[k];
          sc[k - 1] = (k - 1 - n) * sc[k - 1] + sc[k - 2];
          c = c * scaledX2 + sc[k - 1];
        }
        sc[0] *= -n;
        s = s * scaledX2 + sc[0];
        coeff *= inv;
        f[n] = coeff * (s * sin + c * scaledX * cos);
      }
    }
    if(normalized) {
      double scale = FastMath.PI;
      for(int i = 1; i < f.length; ++i) {
        f[i] *= scale;
        scale *= FastMath.PI;
      }
    }
    return t.compose(f);
  }
  @Deprecated() public UnivariateFunction derivative() {
    return FunctionUtils.toDifferentiableUnivariateFunction(this).derivative();
  }
  public double value(final double x) {
    final double scaledX = normalized ? FastMath.PI * x : x;
    if(FastMath.abs(scaledX) <= SHORTCUT) {
      final double scaledX2 = scaledX * scaledX;
      return ((scaledX2 - 20) * scaledX2 + 120) / 120;
    }
    else {
      return FastMath.sin(scaledX) / scaledX;
    }
  }
}