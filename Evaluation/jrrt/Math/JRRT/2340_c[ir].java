package org.apache.commons.math3.linear;
import org.apache.commons.math3.util.FastMath;

public class RectangularCholeskyDecomposition  {
  final private RealMatrix root;
  private int rank;
  public RectangularCholeskyDecomposition(RealMatrix matrix) throws NonPositiveDefiniteMatrixException {
    this(matrix, 0);
  }
  public RectangularCholeskyDecomposition(RealMatrix matrix, double small) throws NonPositiveDefiniteMatrixException {
    super();
    final int order = matrix.getRowDimension();
    final double[][] c = matrix.getData();
    final double[][] b = new double[order][order];
    int[] index = new int[order];
    for(int i = 0; i < order; ++i) {
      index[i] = i;
    }
    int r = 0;
    for(boolean loop = true; loop; ) {
      int swapR = r;
      for(int i = r + 1; i < order; ++i) {
        int ii = index[i];
        int isr = index[swapR];
        if(c[ii][ii] > c[isr][isr]) {
          swapR = i;
        }
      }
      if(swapR != r) {
        final int tmpIndex = index[r];
        index[r] = index[swapR];
        index[swapR] = tmpIndex;
        final double[] tmpRow = b[r];
        b[r] = b[swapR];
        b[swapR] = tmpRow;
      }
      int ir = index[r];
      if(c[ir][ir] <= small) {
        if(r == 0) {
          throw new NonPositiveDefiniteMatrixException(c[ir][ir], ir, small);
        }
        for(int i = r; i < order; ++i) {
          if(c[index[i]][index[i]] < -small) {
            throw new NonPositiveDefiniteMatrixException(c[index[i]][index[i]], i, small);
          }
        }
        loop = false;
      }
      else {
        double[] var_2340 = c[ir];
        final double sqrt = FastMath.sqrt(var_2340[ir]);
        b[r][r] = sqrt;
        final double inverse = 1 / sqrt;
        final double inverse2 = 1 / c[ir][ir];
        for(int i = r + 1; i < order; ++i) {
          final int ii = index[i];
          final double e = inverse * c[ii][ir];
          b[i][r] = e;
          c[ii][ii] -= c[ii][ir] * c[ii][ir] * inverse2;
          for(int j = r + 1; j < i; ++j) {
            final int ij = index[j];
            final double f = c[ii][ij] - e * b[j][r];
            c[ii][ij] = f;
            c[ij][ii] = f;
          }
        }
        loop = ++r < order;
      }
    }
    rank = r;
    root = MatrixUtils.createRealMatrix(order, r);
    for(int i = 0; i < order; ++i) {
      for(int j = 0; j < r; ++j) {
        root.setEntry(index[i], j, b[i][j]);
      }
    }
  }
  public RealMatrix getRootMatrix() {
    return root;
  }
  public int getRank() {
    return rank;
  }
}