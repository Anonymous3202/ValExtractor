package org.apache.commons.math3.linear;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.util.FastMath;

public class LUDecomposition  {
  final private static double DEFAULT_TOO_SMALL = 1e-11D;
  final private double[][] lu;
  final private int[] pivot;
  private boolean even;
  private boolean singular;
  private RealMatrix cachedL;
  private RealMatrix cachedU;
  private RealMatrix cachedP;
  public LUDecomposition(RealMatrix matrix) {
    this(matrix, DEFAULT_TOO_SMALL);
  }
  public LUDecomposition(RealMatrix matrix, double singularityThreshold) {
    super();
    if(!matrix.isSquare()) {
      throw new NonSquareMatrixException(matrix.getRowDimension(), matrix.getColumnDimension());
    }
    final int m = matrix.getColumnDimension();
    lu = matrix.getData();
    pivot = new int[m];
    cachedL = null;
    cachedU = null;
    cachedP = null;
    for(int row = 0; row < m; row++) {
      pivot[row] = row;
    }
    even = true;
    singular = false;
    for(int col = 0; col < m; col++) {
      for(int row = 0; row < col; row++) {
        final double[] luRow = lu[row];
        double sum = luRow[col];
        for(int i = 0; i < row; i++) {
          sum -= luRow[i] * lu[i][col];
        }
        luRow[col] = sum;
      }
      int max = col;
      double largest = Double.NEGATIVE_INFINITY;
      for(int row = col; row < m; row++) {
        final double[] luRow = lu[row];
        double sum = luRow[col];
        for(int i = 0; i < col; i++) {
          sum -= luRow[i] * lu[i][col];
        }
        luRow[col] = sum;
        if(FastMath.abs(sum) > largest) {
          largest = FastMath.abs(sum);
          max = row;
        }
      }
      if(FastMath.abs(lu[max][col]) < singularityThreshold) {
        singular = true;
        return ;
      }
      if(max != col) {
        double tmp = 0;
        final double[] luMax = lu[max];
        final double[] luCol = lu[col];
        for(int i = 0; i < m; i++) {
          tmp = luMax[i];
          luMax[i] = luCol[i];
          luCol[i] = tmp;
        }
        int temp = pivot[max];
        pivot[max] = pivot[col];
        pivot[col] = temp;
        even = !even;
      }
      final double luDiag = lu[col][col];
      for(int row = col + 1; row < m; row++) {
        lu[row][col] /= luDiag;
      }
    }
  }
  public DecompositionSolver getSolver() {
    return new Solver(lu, pivot, singular);
  }
  public RealMatrix getL() {
    if((cachedL == null) && !singular) {
      final int m = pivot.length;
      cachedL = MatrixUtils.createRealMatrix(m, m);
      for(int i = 0; i < m; ++i) {
        final double[] luI = lu[i];
        for(int j = 0; j < i; ++j) {
          cachedL.setEntry(i, j, luI[j]);
        }
        cachedL.setEntry(i, i, 1.0D);
      }
    }
    return cachedL;
  }
  public RealMatrix getP() {
    if((cachedP == null) && !singular) {
      final int m = pivot.length;
      cachedP = MatrixUtils.createRealMatrix(m, m);
      for(int i = 0; i < m; ++i) {
        cachedP.setEntry(i, pivot[i], 1.0D);
      }
    }
    return cachedP;
  }
  public RealMatrix getU() {
    if((cachedU == null) && !singular) {
      final int m = pivot.length;
      cachedU = MatrixUtils.createRealMatrix(m, m);
      for(int i = 0; i < m; ++i) {
        final double[] luI = lu[i];
        for(int j = i; j < m; ++j) {
          cachedU.setEntry(i, j, luI[j]);
        }
      }
    }
    return cachedU;
  }
  public double getDeterminant() {
    if(singular) {
      return 0;
    }
    else {
      final int m = pivot.length;
      double determinant = even ? 1 : -1;
      for(int i = 0; i < m; i++) {
        determinant *= lu[i][i];
      }
      return determinant;
    }
  }
  public int[] getPivot() {
    return pivot.clone();
  }
  
  private static class Solver implements DecompositionSolver  {
    final private double[][] lu;
    final private int[] pivot;
    final private boolean singular;
    private Solver(final double[][] lu, final int[] pivot, final boolean singular) {
      super();
      this.lu = lu;
      this.pivot = pivot;
      this.singular = singular;
    }
    public RealMatrix getInverse() {
      return solve(MatrixUtils.createRealIdentityMatrix(pivot.length));
    }
    public RealMatrix solve(RealMatrix b) {
      final int m = pivot.length;
      if(b.getRowDimension() != m) {
        throw new DimensionMismatchException(b.getRowDimension(), m);
      }
      if(singular) {
        throw new SingularMatrixException();
      }
      final int nColB = b.getColumnDimension();
      final double[][] bp = new double[m][nColB];
      for(int row = 0; row < m; row++) {
        final double[] bpRow = bp[row];
        final int pRow = pivot[row];
        for(int col = 0; col < nColB; col++) {
          bpRow[col] = b.getEntry(pRow, col);
        }
      }
      for(int col = 0; col < m; col++) {
        final double[] bpCol = bp[col];
        for(int i = col + 1; i < m; i++) {
          final double[] bpI = bp[i];
          final double luICol = lu[i][col];
          for(int j = 0; j < nColB; j++) {
            bpI[j] -= bpCol[j] * luICol;
          }
        }
      }
      for(int col = m - 1; col >= 0; col--) {
        final double[] bpCol = bp[col];
        final double luDiag = lu[col][col];
        for(int j = 0; j < nColB; j++) {
          bpCol[j] /= luDiag;
        }
        for(int i = 0; i < col; i++) {
          final double[] bpI = bp[i];
          final double luICol = lu[i][col];
          for(int j = 0; j < nColB; j++) {
            bpI[j] -= bpCol[j] * luICol;
          }
        }
      }
      return new Array2DRowRealMatrix(bp, false);
    }
    public RealVector solve(RealVector b) {
      final int m = pivot.length;
      if(b.getDimension() != m) {
        throw new DimensionMismatchException(b.getDimension(), m);
      }
      if(singular) {
        throw new SingularMatrixException();
      }
      final double[] bp = new double[m];
      for(int row = 0; row < m; row++) {
        bp[row] = b.getEntry(pivot[row]);
      }
      for(int col = 0; col < m; col++) {
        final double bpCol = bp[col];
        for(int i = col + 1; i < m; i++) {
          bp[i] -= bpCol * lu[i][col];
        }
      }
      for(int col = m - 1; col >= 0; col--) {
        bp[col] /= lu[col][col];
        final double bpCol = bp[col];
        for(int i = 0; i < col; i++) {
          double[] var_2286 = lu[i];
          bp[i] -= bpCol * var_2286[col];
        }
      }
      return new ArrayRealVector(bp, false);
    }
    public boolean isNonSingular() {
      return !singular;
    }
  }
}