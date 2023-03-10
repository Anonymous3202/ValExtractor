package org.apache.commons.math3.linear;
import java.util.Arrays;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.util.FastMath;

public class QRDecomposition  {
  private double[][] qrt;
  private double[] rDiag;
  private RealMatrix cachedQ;
  private RealMatrix cachedQT;
  private RealMatrix cachedR;
  private RealMatrix cachedH;
  final private double threshold;
  public QRDecomposition(RealMatrix matrix) {
    this(matrix, 0D);
  }
  public QRDecomposition(RealMatrix matrix, double threshold) {
    super();
    this.threshold = threshold;
    final int m = matrix.getRowDimension();
    final int n = matrix.getColumnDimension();
    qrt = matrix.transpose().getData();
    rDiag = new double[FastMath.min(m, n)];
    cachedQ = null;
    cachedQT = null;
    cachedR = null;
    cachedH = null;
    decompose(qrt);
  }
  public DecompositionSolver getSolver() {
    return new Solver(qrt, rDiag, threshold);
  }
  public RealMatrix getH() {
    if(cachedH == null) {
      final int n = qrt.length;
      final int m = qrt[0].length;
      double[][] ha = new double[m][n];
      for(int i = 0; i < m; ++i) {
        for(int j = 0; j < FastMath.min(i + 1, n); ++j) {
          ha[i][j] = qrt[j][i] / -rDiag[j];
        }
      }
      cachedH = MatrixUtils.createRealMatrix(ha);
    }
    return cachedH;
  }
  public RealMatrix getQ() {
    if(cachedQ == null) {
      cachedQ = getQT().transpose();
    }
    return cachedQ;
  }
  public RealMatrix getQT() {
    if(cachedQT == null) {
      final int n = qrt.length;
      final int m = qrt[0].length;
      double[][] qta = new double[m][m];
      for(int minor = m - 1; minor >= FastMath.min(m, n); minor--) {
        qta[minor][minor] = 1.0D;
      }
      for(int minor = FastMath.min(m, n) - 1; minor >= 0; minor--) {
        final double[] qrtMinor = qrt[minor];
        qta[minor][minor] = 1.0D;
        if(qrtMinor[minor] != 0.0D) {
          for(int col = minor; col < m; col++) {
            double alpha = 0;
            for(int row = minor; row < m; row++) {
              alpha -= qta[col][row] * qrtMinor[row];
            }
            alpha /= rDiag[minor] * qrtMinor[minor];
            for(int row = minor; row < m; row++) {
              qta[col][row] += -alpha * qrtMinor[row];
            }
          }
        }
      }
      cachedQT = MatrixUtils.createRealMatrix(qta);
    }
    return cachedQT;
  }
  public RealMatrix getR() {
    if(cachedR == null) {
      final int n = qrt.length;
      final int m = qrt[0].length;
      double[][] ra = new double[m][n];
      for(int row = FastMath.min(m, n) - 1; row >= 0; row--) {
        ra[row][row] = rDiag[row];
        for(int col = row + 1; col < n; col++) {
          ra[row][col] = qrt[col][row];
        }
      }
      cachedR = MatrixUtils.createRealMatrix(ra);
    }
    return cachedR;
  }
  protected void decompose(double[][] matrix) {
    for(int minor = 0; minor < FastMath.min(qrt.length, qrt[0].length); minor++) {
      performHouseholderReflection(minor, qrt);
    }
  }
  protected void performHouseholderReflection(int minor, double[][] matrix) {
    final double[] qrtMinor = qrt[minor];
    double xNormSqr = 0;
    for(int row = minor; row < qrtMinor.length; row++) {
      final double c = qrtMinor[row];
      xNormSqr += c * c;
    }
    double var_2165 = qrtMinor[minor];
    final double a = (var_2165 > 0) ? -FastMath.sqrt(xNormSqr) : FastMath.sqrt(xNormSqr);
    rDiag[minor] = a;
    if(a != 0.0D) {
      qrtMinor[minor] -= a;
      for(int col = minor + 1; col < qrt.length; col++) {
        final double[] qrtCol = qrt[col];
        double alpha = 0;
        for(int row = minor; row < qrtCol.length; row++) {
          alpha -= qrtCol[row] * qrtMinor[row];
        }
        alpha /= a * qrtMinor[minor];
        for(int row = minor; row < qrtCol.length; row++) {
          qrtCol[row] -= alpha * qrtMinor[row];
        }
      }
    }
  }
  
  private static class Solver implements DecompositionSolver  {
    final private double[][] qrt;
    final private double[] rDiag;
    final private double threshold;
    private Solver(final double[][] qrt, final double[] rDiag, final double threshold) {
      super();
      this.qrt = qrt;
      this.rDiag = rDiag;
      this.threshold = threshold;
    }
    public RealMatrix getInverse() {
      return solve(MatrixUtils.createRealIdentityMatrix(rDiag.length));
    }
    public RealMatrix solve(RealMatrix b) {
      final int n = qrt.length;
      final int m = qrt[0].length;
      if(b.getRowDimension() != m) {
        throw new DimensionMismatchException(b.getRowDimension(), m);
      }
      if(!isNonSingular()) {
        throw new SingularMatrixException();
      }
      final int columns = b.getColumnDimension();
      final int blockSize = BlockRealMatrix.BLOCK_SIZE;
      final int cBlocks = (columns + blockSize - 1) / blockSize;
      final double[][] xBlocks = BlockRealMatrix.createBlocksLayout(n, columns);
      final double[][] y = new double[b.getRowDimension()][blockSize];
      final double[] alpha = new double[blockSize];
      for(int kBlock = 0; kBlock < cBlocks; ++kBlock) {
        final int kStart = kBlock * blockSize;
        final int kEnd = FastMath.min(kStart + blockSize, columns);
        final int kWidth = kEnd - kStart;
        b.copySubMatrix(0, m - 1, kStart, kEnd - 1, y);
        for(int minor = 0; minor < FastMath.min(m, n); minor++) {
          final double[] qrtMinor = qrt[minor];
          final double factor = 1.0D / (rDiag[minor] * qrtMinor[minor]);
          Arrays.fill(alpha, 0, kWidth, 0.0D);
          for(int row = minor; row < m; ++row) {
            final double d = qrtMinor[row];
            final double[] yRow = y[row];
            for(int k = 0; k < kWidth; ++k) {
              alpha[k] += d * yRow[k];
            }
          }
          for(int k = 0; k < kWidth; ++k) {
            alpha[k] *= factor;
          }
          for(int row = minor; row < m; ++row) {
            final double d = qrtMinor[row];
            final double[] yRow = y[row];
            for(int k = 0; k < kWidth; ++k) {
              yRow[k] += alpha[k] * d;
            }
          }
        }
        for(int j = rDiag.length - 1; j >= 0; --j) {
          final int jBlock = j / blockSize;
          final int jStart = jBlock * blockSize;
          final double factor = 1.0D / rDiag[j];
          final double[] yJ = y[j];
          final double[] xBlock = xBlocks[jBlock * cBlocks + kBlock];
          int index = (j - jStart) * kWidth;
          for(int k = 0; k < kWidth; ++k) {
            yJ[k] *= factor;
            xBlock[index++] = yJ[k];
          }
          final double[] qrtJ = qrt[j];
          for(int i = 0; i < j; ++i) {
            final double rIJ = qrtJ[i];
            final double[] yI = y[i];
            for(int k = 0; k < kWidth; ++k) {
              yI[k] -= yJ[k] * rIJ;
            }
          }
        }
      }
      return new BlockRealMatrix(n, columns, xBlocks, false);
    }
    public RealVector solve(RealVector b) {
      final int n = qrt.length;
      final int m = qrt[0].length;
      if(b.getDimension() != m) {
        throw new DimensionMismatchException(b.getDimension(), m);
      }
      if(!isNonSingular()) {
        throw new SingularMatrixException();
      }
      final double[] x = new double[n];
      final double[] y = b.toArray();
      for(int minor = 0; minor < FastMath.min(m, n); minor++) {
        final double[] qrtMinor = qrt[minor];
        double dotProduct = 0;
        for(int row = minor; row < m; row++) {
          dotProduct += y[row] * qrtMinor[row];
        }
        dotProduct /= rDiag[minor] * qrtMinor[minor];
        for(int row = minor; row < m; row++) {
          y[row] += dotProduct * qrtMinor[row];
        }
      }
      for(int row = rDiag.length - 1; row >= 0; --row) {
        y[row] /= rDiag[row];
        final double yRow = y[row];
        final double[] qrtRow = qrt[row];
        x[row] = yRow;
        for(int i = 0; i < row; i++) {
          y[i] -= yRow * qrtRow[i];
        }
      }
      return new ArrayRealVector(x, false);
    }
    public boolean isNonSingular() {
      for (double diag : rDiag) {
        if(FastMath.abs(diag) <= threshold) {
          return false;
        }
      }
      return true;
    }
  }
}