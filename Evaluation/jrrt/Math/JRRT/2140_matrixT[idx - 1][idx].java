package org.apache.commons.math3.linear;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.exception.MathArithmeticException;
import org.apache.commons.math3.exception.MathUnsupportedOperationException;
import org.apache.commons.math3.exception.MaxCountExceededException;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.exception.util.LocalizedFormats;
import org.apache.commons.math3.util.Precision;
import org.apache.commons.math3.util.FastMath;

public class EigenDecomposition  {
  final private static double EPSILON = 1e-12D;
  private byte maxIter = 30;
  private double[] main;
  private double[] secondary;
  private TriDiagonalTransformer transformer;
  private double[] realEigenvalues;
  private double[] imagEigenvalues;
  private ArrayRealVector[] eigenvectors;
  private RealMatrix cachedV;
  private RealMatrix cachedD;
  private RealMatrix cachedVt;
  final private boolean isSymmetric;
  public EigenDecomposition(final RealMatrix matrix) throws MathArithmeticException {
    super();
    final double symTol = 10 * matrix.getRowDimension() * matrix.getColumnDimension() * Precision.EPSILON;
    isSymmetric = MatrixUtils.isSymmetric(matrix, symTol);
    if(isSymmetric) {
      transformToTridiagonal(matrix);
      findEigenVectors(transformer.getQ().getData());
    }
    else {
      final SchurTransformer t = transformToSchur(matrix);
      findEigenVectorsFromSchur(t);
    }
  }
  @Deprecated() public EigenDecomposition(final RealMatrix matrix, final double splitTolerance) throws MathArithmeticException {
    this(matrix);
  }
  public EigenDecomposition(final double[] main, final double[] secondary) {
    super();
    isSymmetric = true;
    this.main = main.clone();
    this.secondary = secondary.clone();
    transformer = null;
    final int size = main.length;
    final double[][] z = new double[size][size];
    for(int i = 0; i < size; i++) {
      z[i][i] = 1.0D;
    }
    findEigenVectors(z);
  }
  @Deprecated() public EigenDecomposition(final double[] main, final double[] secondary, final double splitTolerance) {
    this(main, secondary);
  }
  private Complex cdiv(final double xr, final double xi, final double yr, final double yi) {
    return new Complex(xr, xi).divide(new Complex(yr, yi));
  }
  public DecompositionSolver getSolver() {
    if(hasComplexEigenvalues()) {
      throw new MathUnsupportedOperationException();
    }
    return new Solver(realEigenvalues, imagEigenvalues, eigenvectors);
  }
  public RealMatrix getD() {
    if(cachedD == null) {
      cachedD = MatrixUtils.createRealDiagonalMatrix(realEigenvalues);
      for(int i = 0; i < imagEigenvalues.length; i++) {
        if(Precision.compareTo(imagEigenvalues[i], 0.0D, EPSILON) > 0) {
          cachedD.setEntry(i, i + 1, imagEigenvalues[i]);
        }
        else 
          if(Precision.compareTo(imagEigenvalues[i], 0.0D, EPSILON) < 0) {
            cachedD.setEntry(i, i - 1, imagEigenvalues[i]);
          }
      }
    }
    return cachedD;
  }
  public RealMatrix getSquareRoot() {
    if(!isSymmetric) {
      throw new MathUnsupportedOperationException();
    }
    final double[] sqrtEigenValues = new double[realEigenvalues.length];
    for(int i = 0; i < realEigenvalues.length; i++) {
      final double eigen = realEigenvalues[i];
      if(eigen <= 0) {
        throw new MathUnsupportedOperationException();
      }
      sqrtEigenValues[i] = FastMath.sqrt(eigen);
    }
    final RealMatrix sqrtEigen = MatrixUtils.createRealDiagonalMatrix(sqrtEigenValues);
    final RealMatrix v = getV();
    final RealMatrix vT = getVT();
    return v.multiply(sqrtEigen).multiply(vT);
  }
  public RealMatrix getV() {
    if(cachedV == null) {
      final int m = eigenvectors.length;
      cachedV = MatrixUtils.createRealMatrix(m, m);
      for(int k = 0; k < m; ++k) {
        cachedV.setColumnVector(k, eigenvectors[k]);
      }
    }
    return cachedV;
  }
  public RealMatrix getVT() {
    if(cachedVt == null) {
      final int m = eigenvectors.length;
      cachedVt = MatrixUtils.createRealMatrix(m, m);
      for(int k = 0; k < m; ++k) {
        cachedVt.setRowVector(k, eigenvectors[k]);
      }
    }
    return cachedVt;
  }
  public RealVector getEigenvector(final int i) {
    return eigenvectors[i].copy();
  }
  private SchurTransformer transformToSchur(final RealMatrix matrix) {
    final SchurTransformer schurTransform = new SchurTransformer(matrix);
    final double[][] matT = schurTransform.getT().getData();
    realEigenvalues = new double[matT.length];
    imagEigenvalues = new double[matT.length];
    for(int i = 0; i < realEigenvalues.length; i++) {
      if(i == (realEigenvalues.length - 1) || Precision.equals(matT[i + 1][i], 0.0D, EPSILON)) {
        realEigenvalues[i] = matT[i][i];
      }
      else {
        final double x = matT[i + 1][i + 1];
        final double p = 0.5D * (matT[i][i] - x);
        final double z = FastMath.sqrt(FastMath.abs(p * p + matT[i + 1][i] * matT[i][i + 1]));
        realEigenvalues[i] = x + p;
        imagEigenvalues[i] = z;
        realEigenvalues[i + 1] = x + p;
        imagEigenvalues[i + 1] = -z;
        i++;
      }
    }
    return schurTransform;
  }
  public boolean hasComplexEigenvalues() {
    for(int i = 0; i < imagEigenvalues.length; i++) {
      if(!Precision.equals(imagEigenvalues[i], 0.0D, EPSILON)) {
        return true;
      }
    }
    return false;
  }
  public double getDeterminant() {
    double determinant = 1;
    for (double lambda : realEigenvalues) {
      determinant *= lambda;
    }
    return determinant;
  }
  public double getImagEigenvalue(final int i) {
    return imagEigenvalues[i];
  }
  public double getRealEigenvalue(final int i) {
    return realEigenvalues[i];
  }
  public double[] getImagEigenvalues() {
    return imagEigenvalues.clone();
  }
  public double[] getRealEigenvalues() {
    return realEigenvalues.clone();
  }
  private void findEigenVectors(final double[][] householderMatrix) {
    final double[][] z = householderMatrix.clone();
    final int n = main.length;
    realEigenvalues = new double[n];
    imagEigenvalues = new double[n];
    final double[] e = new double[n];
    for(int i = 0; i < n - 1; i++) {
      realEigenvalues[i] = main[i];
      e[i] = secondary[i];
    }
    realEigenvalues[n - 1] = main[n - 1];
    e[n - 1] = 0;
    double maxAbsoluteValue = 0;
    for(int i = 0; i < n; i++) {
      if(FastMath.abs(realEigenvalues[i]) > maxAbsoluteValue) {
        maxAbsoluteValue = FastMath.abs(realEigenvalues[i]);
      }
      if(FastMath.abs(e[i]) > maxAbsoluteValue) {
        maxAbsoluteValue = FastMath.abs(e[i]);
      }
    }
    if(maxAbsoluteValue != 0) {
      for(int i = 0; i < n; i++) {
        if(FastMath.abs(realEigenvalues[i]) <= Precision.EPSILON * maxAbsoluteValue) {
          realEigenvalues[i] = 0;
        }
        if(FastMath.abs(e[i]) <= Precision.EPSILON * maxAbsoluteValue) {
          e[i] = 0;
        }
      }
    }
    for(int j = 0; j < n; j++) {
      int its = 0;
      int m;
      do {
        for(m = j; m < n - 1; m++) {
          double delta = FastMath.abs(realEigenvalues[m]) + FastMath.abs(realEigenvalues[m + 1]);
          if(FastMath.abs(e[m]) + delta == delta) {
            break ;
          }
        }
        if(m != j) {
          if(its == maxIter) {
            throw new MaxCountExceededException(LocalizedFormats.CONVERGENCE_FAILED, maxIter);
          }
          its++;
          double q = (realEigenvalues[j + 1] - realEigenvalues[j]) / (2 * e[j]);
          double t = FastMath.sqrt(1 + q * q);
          if(q < 0.0D) {
            q = realEigenvalues[m] - realEigenvalues[j] + e[j] / (q - t);
          }
          else {
            q = realEigenvalues[m] - realEigenvalues[j] + e[j] / (q + t);
          }
          double u = 0.0D;
          double s = 1.0D;
          double c = 1.0D;
          int i;
          for(i = m - 1; i >= j; i--) {
            double p = s * e[i];
            double h = c * e[i];
            if(FastMath.abs(p) >= FastMath.abs(q)) {
              c = q / p;
              t = FastMath.sqrt(c * c + 1.0D);
              e[i + 1] = p * t;
              s = 1.0D / t;
              c = c * s;
            }
            else {
              s = p / q;
              t = FastMath.sqrt(s * s + 1.0D);
              e[i + 1] = q * t;
              c = 1.0D / t;
              s = s * c;
            }
            if(e[i + 1] == 0.0D) {
              realEigenvalues[i + 1] -= u;
              e[m] = 0.0D;
              break ;
            }
            q = realEigenvalues[i + 1] - u;
            t = (realEigenvalues[i] - q) * s + 2.0D * c * h;
            u = s * t;
            realEigenvalues[i + 1] = q + u;
            q = c * t - h;
            for(int ia = 0; ia < n; ia++) {
              p = z[ia][i + 1];
              z[ia][i + 1] = s * z[ia][i] + c * p;
              z[ia][i] = c * z[ia][i] - s * p;
            }
          }
          if(t == 0.0D && i >= j) {
            continue ;
          }
          realEigenvalues[j] -= u;
          e[j] = q;
          e[m] = 0.0D;
        }
      }while(m != j);
    }
    for(int i = 0; i < n; i++) {
      int k = i;
      double p = realEigenvalues[i];
      for(int j = i + 1; j < n; j++) {
        if(realEigenvalues[j] > p) {
          k = j;
          p = realEigenvalues[j];
        }
      }
      if(k != i) {
        realEigenvalues[k] = realEigenvalues[i];
        realEigenvalues[i] = p;
        for(int j = 0; j < n; j++) {
          p = z[j][i];
          z[j][i] = z[j][k];
          z[j][k] = p;
        }
      }
    }
    maxAbsoluteValue = 0;
    for(int i = 0; i < n; i++) {
      if(FastMath.abs(realEigenvalues[i]) > maxAbsoluteValue) {
        maxAbsoluteValue = FastMath.abs(realEigenvalues[i]);
      }
    }
    if(maxAbsoluteValue != 0.0D) {
      for(int i = 0; i < n; i++) {
        if(FastMath.abs(realEigenvalues[i]) < Precision.EPSILON * maxAbsoluteValue) {
          realEigenvalues[i] = 0;
        }
      }
    }
    eigenvectors = new ArrayRealVector[n];
    final double[] tmp = new double[n];
    for(int i = 0; i < n; i++) {
      for(int j = 0; j < n; j++) {
        tmp[j] = z[j][i];
      }
      eigenvectors[i] = new ArrayRealVector(tmp);
    }
  }
  private void findEigenVectorsFromSchur(final SchurTransformer schur) throws MathArithmeticException {
    final double[][] matrixT = schur.getT().getData();
    final double[][] matrixP = schur.getP().getData();
    final int n = matrixT.length;
    double norm = 0.0D;
    for(int i = 0; i < n; i++) {
      for(int j = FastMath.max(i - 1, 0); j < n; j++) {
        norm = norm + FastMath.abs(matrixT[i][j]);
      }
    }
    if(Precision.equals(norm, 0.0D, EPSILON)) {
      throw new MathArithmeticException(LocalizedFormats.ZERO_NORM);
    }
    double r = 0.0D;
    double s = 0.0D;
    double z = 0.0D;
    for(int idx = n - 1; idx >= 0; idx--) {
      double p = realEigenvalues[idx];
      double q = imagEigenvalues[idx];
      if(Precision.equals(q, 0.0D)) {
        int l = idx;
        matrixT[idx][idx] = 1.0D;
        for(int i = idx - 1; i >= 0; i--) {
          double w = matrixT[i][i] - p;
          r = 0.0D;
          for(int j = l; j <= idx; j++) {
            r = r + matrixT[i][j] * matrixT[j][idx];
          }
          if(Precision.compareTo(imagEigenvalues[i], 0.0D, EPSILON) < 0.0D) {
            z = w;
            s = r;
          }
          else {
            l = i;
            if(Precision.equals(imagEigenvalues[i], 0.0D)) {
              if(w != 0.0D) {
                matrixT[i][idx] = -r / w;
              }
              else {
                matrixT[i][idx] = -r / (Precision.EPSILON * norm);
              }
            }
            else {
              double x = matrixT[i][i + 1];
              double y = matrixT[i + 1][i];
              q = (realEigenvalues[i] - p) * (realEigenvalues[i] - p) + imagEigenvalues[i] * imagEigenvalues[i];
              double t = (x * s - z * r) / q;
              matrixT[i][idx] = t;
              if(FastMath.abs(x) > FastMath.abs(z)) {
                matrixT[i + 1][idx] = (-r - w * t) / x;
              }
              else {
                matrixT[i + 1][idx] = (-s - y * t) / z;
              }
            }
            double t = FastMath.abs(matrixT[i][idx]);
            if((Precision.EPSILON * t) * t > 1) {
              for(int j = i; j <= idx; j++) {
                matrixT[j][idx] = matrixT[j][idx] / t;
              }
            }
          }
        }
      }
      else 
        if(q < 0.0D) {
          int l = idx - 1;
          if(FastMath.abs(matrixT[idx][idx - 1]) > FastMath.abs(matrixT[idx - 1][idx])) {
            matrixT[idx - 1][idx - 1] = q / matrixT[idx][idx - 1];
            matrixT[idx - 1][idx] = -(matrixT[idx][idx] - p) / matrixT[idx][idx - 1];
          }
          else {
            double var_2140 = matrixT[idx - 1][idx];
            final Complex result = cdiv(0.0D, -var_2140, matrixT[idx - 1][idx - 1] - p, q);
            matrixT[idx - 1][idx - 1] = result.getReal();
            matrixT[idx - 1][idx] = result.getImaginary();
          }
          matrixT[idx][idx - 1] = 0.0D;
          matrixT[idx][idx] = 1.0D;
          for(int i = idx - 2; i >= 0; i--) {
            double ra = 0.0D;
            double sa = 0.0D;
            for(int j = l; j <= idx; j++) {
              ra = ra + matrixT[i][j] * matrixT[j][idx - 1];
              sa = sa + matrixT[i][j] * matrixT[j][idx];
            }
            double w = matrixT[i][i] - p;
            if(Precision.compareTo(imagEigenvalues[i], 0.0D, EPSILON) < 0.0D) {
              z = w;
              r = ra;
              s = sa;
            }
            else {
              l = i;
              if(Precision.equals(imagEigenvalues[i], 0.0D)) {
                final Complex c = cdiv(-ra, -sa, w, q);
                matrixT[i][idx - 1] = c.getReal();
                matrixT[i][idx] = c.getImaginary();
              }
              else {
                double x = matrixT[i][i + 1];
                double y = matrixT[i + 1][i];
                double vr = (realEigenvalues[i] - p) * (realEigenvalues[i] - p) + imagEigenvalues[i] * imagEigenvalues[i] - q * q;
                final double vi = (realEigenvalues[i] - p) * 2.0D * q;
                if(Precision.equals(vr, 0.0D) && Precision.equals(vi, 0.0D)) {
                  vr = Precision.EPSILON * norm * (FastMath.abs(w) + FastMath.abs(q) + FastMath.abs(x) + FastMath.abs(y) + FastMath.abs(z));
                }
                final Complex c = cdiv(x * r - z * ra + q * sa, x * s - z * sa - q * ra, vr, vi);
                matrixT[i][idx - 1] = c.getReal();
                matrixT[i][idx] = c.getImaginary();
                if(FastMath.abs(x) > (FastMath.abs(z) + FastMath.abs(q))) {
                  matrixT[i + 1][idx - 1] = (-ra - w * matrixT[i][idx - 1] + q * matrixT[i][idx]) / x;
                  matrixT[i + 1][idx] = (-sa - w * matrixT[i][idx] - q * matrixT[i][idx - 1]) / x;
                }
                else {
                  final Complex c2 = cdiv(-r - y * matrixT[i][idx - 1], -s - y * matrixT[i][idx], z, q);
                  matrixT[i + 1][idx - 1] = c2.getReal();
                  matrixT[i + 1][idx] = c2.getImaginary();
                }
              }
              double t = FastMath.max(FastMath.abs(matrixT[i][idx - 1]), FastMath.abs(matrixT[i][idx]));
              if((Precision.EPSILON * t) * t > 1) {
                for(int j = i; j <= idx; j++) {
                  matrixT[j][idx - 1] = matrixT[j][idx - 1] / t;
                  matrixT[j][idx] = matrixT[j][idx] / t;
                }
              }
            }
          }
        }
    }
    for(int i = 0; i < n; i++) {
      if(i < 0 | i > n - 1) {
        for(int j = i; j < n; j++) {
          matrixP[i][j] = matrixT[i][j];
        }
      }
    }
    for(int j = n - 1; j >= 0; j--) {
      for(int i = 0; i <= n - 1; i++) {
        z = 0.0D;
        for(int k = 0; k <= FastMath.min(j, n - 1); k++) {
          z = z + matrixP[i][k] * matrixT[k][j];
        }
        matrixP[i][j] = z;
      }
    }
    eigenvectors = new ArrayRealVector[n];
    final double[] tmp = new double[n];
    for(int i = 0; i < n; i++) {
      for(int j = 0; j < n; j++) {
        tmp[j] = matrixP[j][i];
      }
      eigenvectors[i] = new ArrayRealVector(tmp);
    }
  }
  private void transformToTridiagonal(final RealMatrix matrix) {
    transformer = new TriDiagonalTransformer(matrix);
    main = transformer.getMainDiagonalRef();
    secondary = transformer.getSecondaryDiagonalRef();
  }
  
  private static class Solver implements DecompositionSolver  {
    private double[] realEigenvalues;
    private double[] imagEigenvalues;
    final private ArrayRealVector[] eigenvectors;
    private Solver(final double[] realEigenvalues, final double[] imagEigenvalues, final ArrayRealVector[] eigenvectors) {
      super();
      this.realEigenvalues = realEigenvalues;
      this.imagEigenvalues = imagEigenvalues;
      this.eigenvectors = eigenvectors;
    }
    public RealMatrix getInverse() {
      if(!isNonSingular()) {
        throw new SingularMatrixException();
      }
      final int m = realEigenvalues.length;
      final double[][] invData = new double[m][m];
      for(int i = 0; i < m; ++i) {
        final double[] invI = invData[i];
        for(int j = 0; j < m; ++j) {
          double invIJ = 0;
          for(int k = 0; k < m; ++k) {
            final double[] vK = eigenvectors[k].getDataRef();
            invIJ += vK[i] * vK[j] / realEigenvalues[k];
          }
          invI[j] = invIJ;
        }
      }
      return MatrixUtils.createRealMatrix(invData);
    }
    public RealMatrix solve(RealMatrix b) {
      if(!isNonSingular()) {
        throw new SingularMatrixException();
      }
      final int m = realEigenvalues.length;
      if(b.getRowDimension() != m) {
        throw new DimensionMismatchException(b.getRowDimension(), m);
      }
      final int nColB = b.getColumnDimension();
      final double[][] bp = new double[m][nColB];
      final double[] tmpCol = new double[m];
      for(int k = 0; k < nColB; ++k) {
        for(int i = 0; i < m; ++i) {
          tmpCol[i] = b.getEntry(i, k);
          bp[i][k] = 0;
        }
        for(int i = 0; i < m; ++i) {
          final ArrayRealVector v = eigenvectors[i];
          final double[] vData = v.getDataRef();
          double s = 0;
          for(int j = 0; j < m; ++j) {
            s += v.getEntry(j) * tmpCol[j];
          }
          s /= realEigenvalues[i];
          for(int j = 0; j < m; ++j) {
            bp[j][k] += s * vData[j];
          }
        }
      }
      return new Array2DRowRealMatrix(bp, false);
    }
    public RealVector solve(final RealVector b) {
      if(!isNonSingular()) {
        throw new SingularMatrixException();
      }
      final int m = realEigenvalues.length;
      if(b.getDimension() != m) {
        throw new DimensionMismatchException(b.getDimension(), m);
      }
      final double[] bp = new double[m];
      for(int i = 0; i < m; ++i) {
        final ArrayRealVector v = eigenvectors[i];
        final double[] vData = v.getDataRef();
        final double s = v.dotProduct(b) / realEigenvalues[i];
        for(int j = 0; j < m; ++j) {
          bp[j] += s * vData[j];
        }
      }
      return new ArrayRealVector(bp, false);
    }
    public boolean isNonSingular() {
      for(int i = 0; i < realEigenvalues.length; ++i) {
        if(realEigenvalues[i] == 0 && imagEigenvalues[i] == 0) {
          return false;
        }
      }
      return true;
    }
  }
}