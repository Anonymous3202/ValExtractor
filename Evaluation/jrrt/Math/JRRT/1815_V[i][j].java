package org.apache.commons.math3.linear;
import org.apache.commons.math3.exception.NumberIsTooLargeException;
import org.apache.commons.math3.exception.util.LocalizedFormats;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.Precision;

public class SingularValueDecomposition  {
  final private static double EPS = 0x1.0p-52D;
  final private static double TINY = 0x1.0p-966D;
  final private double[] singularValues;
  final private int m;
  final private int n;
  final private boolean transposed;
  final private RealMatrix cachedU;
  private RealMatrix cachedUt;
  private RealMatrix cachedS;
  final private RealMatrix cachedV;
  private RealMatrix cachedVt;
  final private double tol;
  public SingularValueDecomposition(final RealMatrix matrix) {
    super();
    final double[][] A;
    if(matrix.getRowDimension() < matrix.getColumnDimension()) {
      transposed = true;
      A = matrix.transpose().getData();
      m = matrix.getColumnDimension();
      n = matrix.getRowDimension();
    }
    else {
      transposed = false;
      A = matrix.getData();
      m = matrix.getRowDimension();
      n = matrix.getColumnDimension();
    }
    singularValues = new double[n];
    final double[][] U = new double[m][n];
    final double[][] V = new double[n][n];
    final double[] e = new double[n];
    final double[] work = new double[m];
    final int nct = FastMath.min(m - 1, n);
    final int nrt = FastMath.max(0, n - 2);
    for(int k = 0; k < FastMath.max(nct, nrt); k++) {
      if(k < nct) {
        singularValues[k] = 0;
        for(int i = k; i < m; i++) {
          singularValues[k] = FastMath.hypot(singularValues[k], A[i][k]);
        }
        if(singularValues[k] != 0) {
          if(A[k][k] < 0) {
            singularValues[k] = -singularValues[k];
          }
          for(int i = k; i < m; i++) {
            A[i][k] /= singularValues[k];
          }
          A[k][k] += 1;
        }
        singularValues[k] = -singularValues[k];
      }
      for(int j = k + 1; j < n; j++) {
        if(k < nct && singularValues[k] != 0) {
          double t = 0;
          for(int i = k; i < m; i++) {
            t += A[i][k] * A[i][j];
          }
          t = -t / A[k][k];
          for(int i = k; i < m; i++) {
            A[i][j] += t * A[i][k];
          }
        }
        e[j] = A[k][j];
      }
      if(k < nct) {
        for(int i = k; i < m; i++) {
          U[i][k] = A[i][k];
        }
      }
      if(k < nrt) {
        e[k] = 0;
        for(int i = k + 1; i < n; i++) {
          e[k] = FastMath.hypot(e[k], e[i]);
        }
        if(e[k] != 0) {
          if(e[k + 1] < 0) {
            e[k] = -e[k];
          }
          for(int i = k + 1; i < n; i++) {
            e[i] /= e[k];
          }
          e[k + 1] += 1;
        }
        e[k] = -e[k];
        if(k + 1 < m && e[k] != 0) {
          for(int i = k + 1; i < m; i++) {
            work[i] = 0;
          }
          for(int j = k + 1; j < n; j++) {
            for(int i = k + 1; i < m; i++) {
              work[i] += e[j] * A[i][j];
            }
          }
          for(int j = k + 1; j < n; j++) {
            final double t = -e[j] / e[k + 1];
            for(int i = k + 1; i < m; i++) {
              A[i][j] += t * work[i];
            }
          }
        }
        for(int i = k + 1; i < n; i++) {
          V[i][k] = e[i];
        }
      }
    }
    int p = n;
    if(nct < n) {
      singularValues[nct] = A[nct][nct];
    }
    if(m < p) {
      singularValues[p - 1] = 0;
    }
    if(nrt + 1 < p) {
      e[nrt] = A[nrt][p - 1];
    }
    e[p - 1] = 0;
    for(int j = nct; j < n; j++) {
      for(int i = 0; i < m; i++) {
        U[i][j] = 0;
      }
      U[j][j] = 1;
    }
    for(int k = nct - 1; k >= 0; k--) {
      if(singularValues[k] != 0) {
        for(int j = k + 1; j < n; j++) {
          double t = 0;
          for(int i = k; i < m; i++) {
            t += U[i][k] * U[i][j];
          }
          t = -t / U[k][k];
          for(int i = k; i < m; i++) {
            U[i][j] += t * U[i][k];
          }
        }
        for(int i = k; i < m; i++) {
          U[i][k] = -U[i][k];
        }
        U[k][k] = 1 + U[k][k];
        for(int i = 0; i < k - 1; i++) {
          U[i][k] = 0;
        }
      }
      else {
        for(int i = 0; i < m; i++) {
          U[i][k] = 0;
        }
        U[k][k] = 1;
      }
    }
    for(int k = n - 1; k >= 0; k--) {
      if(k < nrt && e[k] != 0) {
        for(int j = k + 1; j < n; j++) {
          double t = 0;
          for(int i = k + 1; i < n; i++) {
            t += V[i][k] * V[i][j];
          }
          t = -t / V[k + 1][k];
          for(int i = k + 1; i < n; i++) {
            V[i][j] += t * V[i][k];
          }
        }
      }
      for(int i = 0; i < n; i++) {
        V[i][k] = 0;
      }
      V[k][k] = 1;
    }
    final int pp = p - 1;
    int iter = 0;
    while(p > 0){
      int k;
      int kase;
      for(k = p - 2; k >= 0; k--) {
        final double threshold = TINY + EPS * (FastMath.abs(singularValues[k]) + FastMath.abs(singularValues[k + 1]));
        if(!(FastMath.abs(e[k]) > threshold)) {
          e[k] = 0;
          break ;
        }
      }
      if(k == p - 2) {
        kase = 4;
      }
      else {
        int ks;
        for(ks = p - 1; ks >= k; ks--) {
          if(ks == k) {
            break ;
          }
          final double t = (ks != p ? FastMath.abs(e[ks]) : 0) + (ks != k + 1 ? FastMath.abs(e[ks - 1]) : 0);
          if(FastMath.abs(singularValues[ks]) <= TINY + EPS * t) {
            singularValues[ks] = 0;
            break ;
          }
        }
        if(ks == k) {
          kase = 3;
        }
        else 
          if(ks == p - 1) {
            kase = 1;
          }
          else {
            kase = 2;
            k = ks;
          }
      }
      k++;
      switch (kase){
        case 1:
        {
          double f = e[p - 2];
          e[p - 2] = 0;
          for(int j = p - 2; j >= k; j--) {
            double t = FastMath.hypot(singularValues[j], f);
            final double cs = singularValues[j] / t;
            final double sn = f / t;
            singularValues[j] = t;
            if(j != k) {
              f = -sn * e[j - 1];
              e[j - 1] = cs * e[j - 1];
            }
            for(int i = 0; i < n; i++) {
              t = cs * V[i][j] + sn * V[i][p - 1];
              double var_1815 = V[i][j];
              V[i][p - 1] = -sn * var_1815 + cs * V[i][p - 1];
              V[i][j] = t;
            }
          }
        }
        break ;
        case 2:
        {
          double f = e[k - 1];
          e[k - 1] = 0;
          for(int j = k; j < p; j++) {
            double t = FastMath.hypot(singularValues[j], f);
            final double cs = singularValues[j] / t;
            final double sn = f / t;
            singularValues[j] = t;
            f = -sn * e[j];
            e[j] = cs * e[j];
            for(int i = 0; i < m; i++) {
              t = cs * U[i][j] + sn * U[i][k - 1];
              U[i][k - 1] = -sn * U[i][j] + cs * U[i][k - 1];
              U[i][j] = t;
            }
          }
        }
        break ;
        case 3:
        {
          final double maxPm1Pm2 = FastMath.max(FastMath.abs(singularValues[p - 1]), FastMath.abs(singularValues[p - 2]));
          final double scale = FastMath.max(FastMath.max(FastMath.max(maxPm1Pm2, FastMath.abs(e[p - 2])), FastMath.abs(singularValues[k])), FastMath.abs(e[k]));
          final double sp = singularValues[p - 1] / scale;
          final double spm1 = singularValues[p - 2] / scale;
          final double epm1 = e[p - 2] / scale;
          final double sk = singularValues[k] / scale;
          final double ek = e[k] / scale;
          final double b = ((spm1 + sp) * (spm1 - sp) + epm1 * epm1) / 2.0D;
          final double c = (sp * epm1) * (sp * epm1);
          double shift = 0;
          if(b != 0 || c != 0) {
            shift = FastMath.sqrt(b * b + c);
            if(b < 0) {
              shift = -shift;
            }
            shift = c / (b + shift);
          }
          double f = (sk + sp) * (sk - sp) + shift;
          double g = sk * ek;
          for(int j = k; j < p - 1; j++) {
            double t = FastMath.hypot(f, g);
            double cs = f / t;
            double sn = g / t;
            if(j != k) {
              e[j - 1] = t;
            }
            f = cs * singularValues[j] + sn * e[j];
            e[j] = cs * e[j] - sn * singularValues[j];
            g = sn * singularValues[j + 1];
            singularValues[j + 1] = cs * singularValues[j + 1];
            for(int i = 0; i < n; i++) {
              t = cs * V[i][j] + sn * V[i][j + 1];
              V[i][j + 1] = -sn * V[i][j] + cs * V[i][j + 1];
              V[i][j] = t;
            }
            t = FastMath.hypot(f, g);
            cs = f / t;
            sn = g / t;
            singularValues[j] = t;
            f = cs * e[j] + sn * singularValues[j + 1];
            singularValues[j + 1] = -sn * e[j] + cs * singularValues[j + 1];
            g = sn * e[j + 1];
            e[j + 1] = cs * e[j + 1];
            if(j < m - 1) {
              for(int i = 0; i < m; i++) {
                t = cs * U[i][j] + sn * U[i][j + 1];
                U[i][j + 1] = -sn * U[i][j] + cs * U[i][j + 1];
                U[i][j] = t;
              }
            }
          }
          e[p - 2] = f;
          iter = iter + 1;
        }
        break ;
        default:
        {
          if(singularValues[k] <= 0) {
            singularValues[k] = singularValues[k] < 0 ? -singularValues[k] : 0;
            for(int i = 0; i <= pp; i++) {
              V[i][k] = -V[i][k];
            }
          }
          while(k < pp){
            if(singularValues[k] >= singularValues[k + 1]) {
              break ;
            }
            double t = singularValues[k];
            singularValues[k] = singularValues[k + 1];
            singularValues[k + 1] = t;
            if(k < n - 1) {
              for(int i = 0; i < n; i++) {
                t = V[i][k + 1];
                V[i][k + 1] = V[i][k];
                V[i][k] = t;
              }
            }
            if(k < m - 1) {
              for(int i = 0; i < m; i++) {
                t = U[i][k + 1];
                U[i][k + 1] = U[i][k];
                U[i][k] = t;
              }
            }
            k++;
          }
          iter = 0;
          p--;
        }
        break ;
      }
    }
    tol = FastMath.max(m * singularValues[0] * EPS, FastMath.sqrt(Precision.SAFE_MIN));
    if(!transposed) {
      cachedU = MatrixUtils.createRealMatrix(U);
      cachedV = MatrixUtils.createRealMatrix(V);
    }
    else {
      cachedU = MatrixUtils.createRealMatrix(V);
      cachedV = MatrixUtils.createRealMatrix(U);
    }
  }
  public DecompositionSolver getSolver() {
    return new Solver(singularValues, getUT(), getV(), getRank() == m, tol);
  }
  public RealMatrix getCovariance(final double minSingularValue) {
    final int p = singularValues.length;
    int dimension = 0;
    while(dimension < p && singularValues[dimension] >= minSingularValue){
      ++dimension;
    }
    if(dimension == 0) {
      throw new NumberIsTooLargeException(LocalizedFormats.TOO_LARGE_CUTOFF_SINGULAR_VALUE, minSingularValue, singularValues[0], true);
    }
    final double[][] data = new double[dimension][p];
    getVT().walkInOptimizedOrder(new DefaultRealMatrixPreservingVisitor() {
        @Override() public void visit(final int row, final int column, final double value) {
          data[row][column] = value / singularValues[row];
        }
    }, 0, dimension - 1, 0, p - 1);
    RealMatrix jv = new Array2DRowRealMatrix(data, false);
    return jv.transpose().multiply(jv);
  }
  public RealMatrix getS() {
    if(cachedS == null) {
      cachedS = MatrixUtils.createRealDiagonalMatrix(singularValues);
    }
    return cachedS;
  }
  public RealMatrix getU() {
    return cachedU;
  }
  public RealMatrix getUT() {
    if(cachedUt == null) {
      cachedUt = getU().transpose();
    }
    return cachedUt;
  }
  public RealMatrix getV() {
    return cachedV;
  }
  public RealMatrix getVT() {
    if(cachedVt == null) {
      cachedVt = getV().transpose();
    }
    return cachedVt;
  }
  public double getConditionNumber() {
    return singularValues[0] / singularValues[n - 1];
  }
  public double getInverseConditionNumber() {
    return singularValues[n - 1] / singularValues[0];
  }
  public double getNorm() {
    return singularValues[0];
  }
  public double[] getSingularValues() {
    return singularValues.clone();
  }
  public int getRank() {
    int r = 0;
    for(int i = 0; i < singularValues.length; i++) {
      if(singularValues[i] > tol) {
        r++;
      }
    }
    return r;
  }
  
  private static class Solver implements DecompositionSolver  {
    final private RealMatrix pseudoInverse;
    private boolean nonSingular;
    private Solver(final double[] singularValues, final RealMatrix uT, final RealMatrix v, final boolean nonSingular, final double tol) {
      super();
      final double[][] suT = uT.getData();
      for(int i = 0; i < singularValues.length; ++i) {
        final double a;
        if(singularValues[i] > tol) {
          a = 1 / singularValues[i];
        }
        else {
          a = 0;
        }
        final double[] suTi = suT[i];
        for(int j = 0; j < suTi.length; ++j) {
          suTi[j] *= a;
        }
      }
      pseudoInverse = v.multiply(new Array2DRowRealMatrix(suT, false));
      this.nonSingular = nonSingular;
    }
    public RealMatrix getInverse() {
      return pseudoInverse;
    }
    public RealMatrix solve(final RealMatrix b) {
      return pseudoInverse.multiply(b);
    }
    public RealVector solve(final RealVector b) {
      return pseudoInverse.operate(b);
    }
    public boolean isNonSingular() {
      return nonSingular;
    }
  }
}