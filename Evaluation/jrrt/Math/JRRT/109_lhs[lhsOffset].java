package org.apache.commons.math3.analysis.differentiation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.exception.MathArithmeticException;
import org.apache.commons.math3.exception.MathInternalError;
import org.apache.commons.math3.exception.NotPositiveException;
import org.apache.commons.math3.exception.NumberIsTooLargeException;
import org.apache.commons.math3.util.CombinatoricsUtils;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.util.MathArrays;

public class DSCompiler  {
  private static AtomicReference<DSCompiler[][]> compilers = new AtomicReference<DSCompiler[][]>(null);
  final private int parameters;
  final private int order;
  final private int[][] sizes;
  final private int[][] derivativesIndirection;
  final private int[] lowerIndirection;
  final private int[][][] multIndirection;
  final private int[][][] compIndirection;
  private DSCompiler(final int parameters, final int order, final DSCompiler valueCompiler, final DSCompiler derivativeCompiler) throws NumberIsTooLargeException {
    super();
    this.parameters = parameters;
    this.order = order;
    this.sizes = compileSizes(parameters, order, valueCompiler);
    this.derivativesIndirection = compileDerivativesIndirection(parameters, order, valueCompiler, derivativeCompiler);
    this.lowerIndirection = compileLowerIndirection(parameters, order, valueCompiler, derivativeCompiler);
    this.multIndirection = compileMultiplicationIndirection(parameters, order, valueCompiler, derivativeCompiler, lowerIndirection);
    this.compIndirection = compileCompositionIndirection(parameters, order, valueCompiler, derivativeCompiler, sizes, derivativesIndirection);
  }
  public static DSCompiler getCompiler(int parameters, int order) throws NumberIsTooLargeException {
    final DSCompiler[][] cache = compilers.get();
    if(cache != null && cache.length > parameters && cache[parameters].length > order && cache[parameters][order] != null) {
      return cache[parameters][order];
    }
    final int maxParameters = FastMath.max(parameters, cache == null ? 0 : cache.length);
    final int maxOrder = FastMath.max(order, cache == null ? 0 : cache[0].length);
    final DSCompiler[][] newCache = new DSCompiler[maxParameters + 1][maxOrder + 1];
    if(cache != null) {
      for(int i = 0; i < cache.length; ++i) {
        System.arraycopy(cache[i], 0, newCache[i], 0, cache[i].length);
      }
    }
    for(int diag = 0; diag <= parameters + order; ++diag) {
      for(int o = FastMath.max(0, diag - parameters); o <= FastMath.min(order, diag); ++o) {
        final int p = diag - o;
        if(newCache[p][o] == null) {
          final DSCompiler valueCompiler = (p == 0) ? null : newCache[p - 1][o];
          final DSCompiler derivativeCompiler = (o == 0) ? null : newCache[p][o - 1];
          newCache[p][o] = new DSCompiler(p, o, valueCompiler, derivativeCompiler);
        }
      }
    }
    compilers.compareAndSet(cache, newCache);
    return newCache[parameters][order];
  }
  public double taylor(final double[] ds, final int dsOffset, final double ... delta) throws MathArithmeticException {
    double value = 0;
    for(int i = getSize() - 1; i >= 0; --i) {
      final int[] orders = getPartialDerivativeOrders(i);
      double term = ds[dsOffset + i];
      for(int k = 0; k < orders.length; ++k) {
        if(orders[k] > 0) {
          try {
            term *= FastMath.pow(delta[k], orders[k]) / CombinatoricsUtils.factorial(orders[k]);
          }
          catch (NotPositiveException e) {
            throw new MathInternalError(e);
          }
        }
      }
      value += term;
    }
    return value;
  }
  private static int convertIndex(final int index, final int srcP, final int[][] srcDerivativesIndirection, final int destP, final int destO, final int[][] destSizes) throws NumberIsTooLargeException {
    int[] orders = new int[destP];
    System.arraycopy(srcDerivativesIndirection[index], 0, orders, 0, FastMath.min(srcP, destP));
    return getPartialDerivativeIndex(destP, destO, destSizes, orders);
  }
  public int getFreeParameters() {
    return parameters;
  }
  public int getOrder() {
    return order;
  }
  private static int getPartialDerivativeIndex(final int parameters, final int order, final int[][] sizes, final int ... orders) throws NumberIsTooLargeException {
    int index = 0;
    int m = order;
    int ordersSum = 0;
    for(int i = parameters - 1; i >= 0; --i) {
      int derivativeOrder = orders[i];
      ordersSum += derivativeOrder;
      if(ordersSum > order) {
        throw new NumberIsTooLargeException(ordersSum, order, true);
      }
      while(derivativeOrder-- > 0){
        index += sizes[i][m--];
      }
    }
    return index;
  }
  public int getPartialDerivativeIndex(final int ... orders) throws DimensionMismatchException, NumberIsTooLargeException {
    if(orders.length != getFreeParameters()) {
      throw new DimensionMismatchException(orders.length, getFreeParameters());
    }
    return getPartialDerivativeIndex(parameters, order, sizes, orders);
  }
  public int getSize() {
    return sizes[parameters][order];
  }
  private static int[] compileLowerIndirection(final int parameters, final int order, final DSCompiler valueCompiler, final DSCompiler derivativeCompiler) {
    if(parameters == 0 || order <= 1) {
      return new int[]{ 0 } ;
    }
    final int vSize = valueCompiler.lowerIndirection.length;
    final int dSize = derivativeCompiler.lowerIndirection.length;
    final int[] lowerIndirection = new int[vSize + dSize];
    System.arraycopy(valueCompiler.lowerIndirection, 0, lowerIndirection, 0, vSize);
    for(int i = 0; i < dSize; ++i) {
      lowerIndirection[vSize + i] = valueCompiler.getSize() + derivativeCompiler.lowerIndirection[i];
    }
    return lowerIndirection;
  }
  public int[] getPartialDerivativeOrders(final int index) {
    return derivativesIndirection[index];
  }
  private static int[][] compileDerivativesIndirection(final int parameters, final int order, final DSCompiler valueCompiler, final DSCompiler derivativeCompiler) {
    if(parameters == 0 || order == 0) {
      return new int[1][parameters];
    }
    final int vSize = valueCompiler.derivativesIndirection.length;
    final int dSize = derivativeCompiler.derivativesIndirection.length;
    final int[][] derivativesIndirection = new int[vSize + dSize][parameters];
    for(int i = 0; i < vSize; ++i) {
      System.arraycopy(valueCompiler.derivativesIndirection[i], 0, derivativesIndirection[i], 0, parameters - 1);
    }
    for(int i = 0; i < dSize; ++i) {
      System.arraycopy(derivativeCompiler.derivativesIndirection[i], 0, derivativesIndirection[vSize + i], 0, parameters);
      derivativesIndirection[vSize + i][parameters - 1]++;
    }
    return derivativesIndirection;
  }
  private static int[][] compileSizes(final int parameters, final int order, final DSCompiler valueCompiler) {
    final int[][] sizes = new int[parameters + 1][order + 1];
    if(parameters == 0) {
      Arrays.fill(sizes[0], 1);
    }
    else {
      System.arraycopy(valueCompiler.sizes, 0, sizes, 0, parameters);
      sizes[parameters][0] = 1;
      for(int i = 0; i < order; ++i) {
        sizes[parameters][i + 1] = sizes[parameters][i] + sizes[parameters - 1][i + 1];
      }
    }
    return sizes;
  }
  private static int[][][] compileCompositionIndirection(final int parameters, final int order, final DSCompiler valueCompiler, final DSCompiler derivativeCompiler, final int[][] sizes, final int[][] derivativesIndirection) throws NumberIsTooLargeException {
    if((parameters == 0) || (order == 0)) {
      return new int[][][]{ { { 1, 0 }  }  } ;
    }
    final int vSize = valueCompiler.compIndirection.length;
    final int dSize = derivativeCompiler.compIndirection.length;
    final int[][][] compIndirection = new int[vSize + dSize][][];
    System.arraycopy(valueCompiler.compIndirection, 0, compIndirection, 0, vSize);
    for(int i = 0; i < dSize; ++i) {
      List<int[]> row = new ArrayList<int[]>();
      for (int[] term : derivativeCompiler.compIndirection[i]) {
        int[] derivedTermF = new int[term.length + 1];
        derivedTermF[0] = term[0];
        derivedTermF[1] = term[1] + 1;
        int[] orders = new int[parameters];
        orders[parameters - 1] = 1;
        derivedTermF[term.length] = getPartialDerivativeIndex(parameters, order, sizes, orders);
        for(int j = 2; j < term.length; ++j) {
          derivedTermF[j] = convertIndex(term[j], parameters, derivativeCompiler.derivativesIndirection, parameters, order, sizes);
        }
        Arrays.sort(derivedTermF, 2, derivedTermF.length);
        row.add(derivedTermF);
        for(int l = 2; l < term.length; ++l) {
          int[] derivedTermG = new int[term.length];
          derivedTermG[0] = term[0];
          derivedTermG[1] = term[1];
          for(int j = 2; j < term.length; ++j) {
            derivedTermG[j] = convertIndex(term[j], parameters, derivativeCompiler.derivativesIndirection, parameters, order, sizes);
            if(j == l) {
              System.arraycopy(derivativesIndirection[derivedTermG[j]], 0, orders, 0, parameters);
              orders[parameters - 1]++;
              derivedTermG[j] = getPartialDerivativeIndex(parameters, order, sizes, orders);
            }
          }
          Arrays.sort(derivedTermG, 2, derivedTermG.length);
          row.add(derivedTermG);
        }
      }
      final List<int[]> combined = new ArrayList<int[]>(row.size());
      for(int j = 0; j < row.size(); ++j) {
        final int[] termJ = row.get(j);
        if(termJ[0] > 0) {
          for(int k = j + 1; k < row.size(); ++k) {
            final int[] termK = row.get(k);
            boolean equals = termJ.length == termK.length;
            for(int l = 1; equals && l < termJ.length; ++l) {
              equals &= termJ[l] == termK[l];
            }
            if(equals) {
              termJ[0] += termK[0];
              termK[0] = 0;
            }
          }
          combined.add(termJ);
        }
      }
      compIndirection[vSize + i] = combined.toArray(new int[combined.size()][]);
    }
    return compIndirection;
  }
  private static int[][][] compileMultiplicationIndirection(final int parameters, final int order, final DSCompiler valueCompiler, final DSCompiler derivativeCompiler, final int[] lowerIndirection) {
    if((parameters == 0) || (order == 0)) {
      return new int[][][]{ { { 1, 0, 0 }  }  } ;
    }
    final int vSize = valueCompiler.multIndirection.length;
    final int dSize = derivativeCompiler.multIndirection.length;
    final int[][][] multIndirection = new int[vSize + dSize][][];
    System.arraycopy(valueCompiler.multIndirection, 0, multIndirection, 0, vSize);
    for(int i = 0; i < dSize; ++i) {
      final int[][] dRow = derivativeCompiler.multIndirection[i];
      List<int[]> row = new ArrayList<int[]>(dRow.length * 2);
      for(int j = 0; j < dRow.length; ++j) {
        row.add(new int[]{ dRow[j][0], lowerIndirection[dRow[j][1]], vSize + dRow[j][2] } );
        row.add(new int[]{ dRow[j][0], vSize + dRow[j][1], lowerIndirection[dRow[j][2]] } );
      }
      final List<int[]> combined = new ArrayList<int[]>(row.size());
      for(int j = 0; j < row.size(); ++j) {
        final int[] termJ = row.get(j);
        if(termJ[0] > 0) {
          for(int k = j + 1; k < row.size(); ++k) {
            final int[] termK = row.get(k);
            if(termJ[1] == termK[1] && termJ[2] == termK[2]) {
              termJ[0] += termK[0];
              termK[0] = 0;
            }
          }
          combined.add(termJ);
        }
      }
      multIndirection[vSize + i] = combined.toArray(new int[combined.size()][]);
    }
    return multIndirection;
  }
  public void acos(final double[] operand, final int operandOffset, final double[] result, final int resultOffset) {
    double[] function = new double[1 + order];
    final double x = operand[operandOffset];
    function[0] = FastMath.acos(x);
    if(order > 0) {
      final double[] p = new double[order];
      p[0] = -1;
      final double x2 = x * x;
      final double f = 1.0D / (1 - x2);
      double coeff = FastMath.sqrt(f);
      function[1] = coeff * p[0];
      for(int n = 2; n <= order; ++n) {
        double v = 0;
        p[n - 1] = (n - 1) * p[n - 2];
        for(int k = n - 1; k >= 0; k -= 2) {
          v = v * x2 + p[k];
          if(k > 2) {
            p[k - 2] = (k - 1) * p[k - 1] + (2 * n - k) * p[k - 3];
          }
          else 
            if(k == 2) {
              p[0] = p[1];
            }
        }
        if((n & 0x1) == 0) {
          v *= x;
        }
        coeff *= f;
        function[n] = coeff * v;
      }
    }
    compose(operand, operandOffset, function, result, resultOffset);
  }
  public void acosh(final double[] operand, final int operandOffset, final double[] result, final int resultOffset) {
    double[] function = new double[1 + order];
    final double x = operand[operandOffset];
    function[0] = FastMath.acosh(x);
    if(order > 0) {
      final double[] p = new double[order];
      p[0] = 1;
      final double x2 = x * x;
      final double f = 1.0D / (x2 - 1);
      double coeff = FastMath.sqrt(f);
      function[1] = coeff * p[0];
      for(int n = 2; n <= order; ++n) {
        double v = 0;
        p[n - 1] = (1 - n) * p[n - 2];
        for(int k = n - 1; k >= 0; k -= 2) {
          v = v * x2 + p[k];
          if(k > 2) {
            p[k - 2] = (1 - k) * p[k - 1] + (k - 2 * n) * p[k - 3];
          }
          else 
            if(k == 2) {
              p[0] = -p[1];
            }
        }
        if((n & 0x1) == 0) {
          v *= x;
        }
        coeff *= f;
        function[n] = coeff * v;
      }
    }
    compose(operand, operandOffset, function, result, resultOffset);
  }
  public void add(final double[] lhs, final int lhsOffset, final double[] rhs, final int rhsOffset, final double[] result, final int resultOffset) {
    for(int i = 0; i < getSize(); ++i) {
      result[resultOffset + i] = lhs[lhsOffset + i] + rhs[rhsOffset + i];
    }
  }
  public void asin(final double[] operand, final int operandOffset, final double[] result, final int resultOffset) {
    double[] function = new double[1 + order];
    final double x = operand[operandOffset];
    function[0] = FastMath.asin(x);
    if(order > 0) {
      final double[] p = new double[order];
      p[0] = 1;
      final double x2 = x * x;
      final double f = 1.0D / (1 - x2);
      double coeff = FastMath.sqrt(f);
      function[1] = coeff * p[0];
      for(int n = 2; n <= order; ++n) {
        double v = 0;
        p[n - 1] = (n - 1) * p[n - 2];
        for(int k = n - 1; k >= 0; k -= 2) {
          v = v * x2 + p[k];
          if(k > 2) {
            p[k - 2] = (k - 1) * p[k - 1] + (2 * n - k) * p[k - 3];
          }
          else 
            if(k == 2) {
              p[0] = p[1];
            }
        }
        if((n & 0x1) == 0) {
          v *= x;
        }
        coeff *= f;
        function[n] = coeff * v;
      }
    }
    compose(operand, operandOffset, function, result, resultOffset);
  }
  public void asinh(final double[] operand, final int operandOffset, final double[] result, final int resultOffset) {
    double[] function = new double[1 + order];
    final double x = operand[operandOffset];
    function[0] = FastMath.asinh(x);
    if(order > 0) {
      final double[] p = new double[order];
      p[0] = 1;
      final double x2 = x * x;
      final double f = 1.0D / (1 + x2);
      double coeff = FastMath.sqrt(f);
      function[1] = coeff * p[0];
      for(int n = 2; n <= order; ++n) {
        double v = 0;
        p[n - 1] = (1 - n) * p[n - 2];
        for(int k = n - 1; k >= 0; k -= 2) {
          v = v * x2 + p[k];
          if(k > 2) {
            p[k - 2] = (k - 1) * p[k - 1] + (k - 2 * n) * p[k - 3];
          }
          else 
            if(k == 2) {
              p[0] = p[1];
            }
        }
        if((n & 0x1) == 0) {
          v *= x;
        }
        coeff *= f;
        function[n] = coeff * v;
      }
    }
    compose(operand, operandOffset, function, result, resultOffset);
  }
  public void atan(final double[] operand, final int operandOffset, final double[] result, final int resultOffset) {
    double[] function = new double[1 + order];
    final double x = operand[operandOffset];
    function[0] = FastMath.atan(x);
    if(order > 0) {
      final double[] q = new double[order];
      q[0] = 1;
      final double x2 = x * x;
      final double f = 1.0D / (1 + x2);
      double coeff = f;
      function[1] = coeff * q[0];
      for(int n = 2; n <= order; ++n) {
        double v = 0;
        q[n - 1] = -n * q[n - 2];
        for(int k = n - 1; k >= 0; k -= 2) {
          v = v * x2 + q[k];
          if(k > 2) {
            q[k - 2] = (k - 1) * q[k - 1] + (k - 1 - 2 * n) * q[k - 3];
          }
          else 
            if(k == 2) {
              q[0] = q[1];
            }
        }
        if((n & 0x1) == 0) {
          v *= x;
        }
        coeff *= f;
        function[n] = coeff * v;
      }
    }
    compose(operand, operandOffset, function, result, resultOffset);
  }
  public void atan2(final double[] y, final int yOffset, final double[] x, final int xOffset, final double[] result, final int resultOffset) {
    double[] tmp1 = new double[getSize()];
    multiply(x, xOffset, x, xOffset, tmp1, 0);
    double[] tmp2 = new double[getSize()];
    multiply(y, yOffset, y, yOffset, tmp2, 0);
    add(tmp1, 0, tmp2, 0, tmp2, 0);
    rootN(tmp2, 0, 2, tmp1, 0);
    if(x[xOffset] >= 0) {
      add(tmp1, 0, x, xOffset, tmp2, 0);
      divide(y, yOffset, tmp2, 0, tmp1, 0);
      atan(tmp1, 0, tmp2, 0);
      for(int i = 0; i < tmp2.length; ++i) {
        result[resultOffset + i] = 2 * tmp2[i];
      }
    }
    else {
      subtract(tmp1, 0, x, xOffset, tmp2, 0);
      divide(y, yOffset, tmp2, 0, tmp1, 0);
      atan(tmp1, 0, tmp2, 0);
      result[resultOffset] = ((tmp2[0] <= 0) ? -FastMath.PI : FastMath.PI) - 2 * tmp2[0];
      for(int i = 1; i < tmp2.length; ++i) {
        result[resultOffset + i] = -2 * tmp2[i];
      }
    }
    result[resultOffset] = FastMath.atan2(y[yOffset], x[xOffset]);
  }
  public void atanh(final double[] operand, final int operandOffset, final double[] result, final int resultOffset) {
    double[] function = new double[1 + order];
    final double x = operand[operandOffset];
    function[0] = FastMath.atanh(x);
    if(order > 0) {
      final double[] q = new double[order];
      q[0] = 1;
      final double x2 = x * x;
      final double f = 1.0D / (1 - x2);
      double coeff = f;
      function[1] = coeff * q[0];
      for(int n = 2; n <= order; ++n) {
        double v = 0;
        q[n - 1] = n * q[n - 2];
        for(int k = n - 1; k >= 0; k -= 2) {
          v = v * x2 + q[k];
          if(k > 2) {
            q[k - 2] = (k - 1) * q[k - 1] + (2 * n - k + 1) * q[k - 3];
          }
          else 
            if(k == 2) {
              q[0] = q[1];
            }
        }
        if((n & 0x1) == 0) {
          v *= x;
        }
        coeff *= f;
        function[n] = coeff * v;
      }
    }
    compose(operand, operandOffset, function, result, resultOffset);
  }
  public void checkCompatibility(final DSCompiler compiler) throws DimensionMismatchException {
    if(parameters != compiler.parameters) {
      throw new DimensionMismatchException(parameters, compiler.parameters);
    }
    if(order != compiler.order) {
      throw new DimensionMismatchException(order, compiler.order);
    }
  }
  public void compose(final double[] operand, final int operandOffset, final double[] f, final double[] result, final int resultOffset) {
    for(int i = 0; i < compIndirection.length; ++i) {
      final int[][] mappingI = compIndirection[i];
      double r = 0;
      for(int j = 0; j < mappingI.length; ++j) {
        final int[] mappingIJ = mappingI[j];
        double product = mappingIJ[0] * f[mappingIJ[1]];
        for(int k = 2; k < mappingIJ.length; ++k) {
          product *= operand[operandOffset + mappingIJ[k]];
        }
        r += product;
      }
      result[resultOffset + i] = r;
    }
  }
  public void cos(final double[] operand, final int operandOffset, final double[] result, final int resultOffset) {
    double[] function = new double[1 + order];
    function[0] = FastMath.cos(operand[operandOffset]);
    if(order > 0) {
      function[1] = -FastMath.sin(operand[operandOffset]);
      for(int i = 2; i <= order; ++i) {
        function[i] = -function[i - 2];
      }
    }
    compose(operand, operandOffset, function, result, resultOffset);
  }
  public void cosh(final double[] operand, final int operandOffset, final double[] result, final int resultOffset) {
    double[] function = new double[1 + order];
    function[0] = FastMath.cosh(operand[operandOffset]);
    if(order > 0) {
      function[1] = FastMath.sinh(operand[operandOffset]);
      for(int i = 2; i <= order; ++i) {
        function[i] = function[i - 2];
      }
    }
    compose(operand, operandOffset, function, result, resultOffset);
  }
  public void divide(final double[] lhs, final int lhsOffset, final double[] rhs, final int rhsOffset, final double[] result, final int resultOffset) {
    final double[] reciprocal = new double[getSize()];
    pow(rhs, lhsOffset, -1, reciprocal, 0);
    multiply(lhs, lhsOffset, reciprocal, 0, result, resultOffset);
  }
  public void exp(final double[] operand, final int operandOffset, final double[] result, final int resultOffset) {
    double[] function = new double[1 + order];
    Arrays.fill(function, FastMath.exp(operand[operandOffset]));
    compose(operand, operandOffset, function, result, resultOffset);
  }
  public void expm1(final double[] operand, final int operandOffset, final double[] result, final int resultOffset) {
    double[] function = new double[1 + order];
    function[0] = FastMath.expm1(operand[operandOffset]);
    Arrays.fill(function, 1, 1 + order, FastMath.exp(operand[operandOffset]));
    compose(operand, operandOffset, function, result, resultOffset);
  }
  public void linearCombination(final double a1, final double[] c1, final int offset1, final double a2, final double[] c2, final int offset2, final double a3, final double[] c3, final int offset3, final double a4, final double[] c4, final int offset4, final double[] result, final int resultOffset) {
    for(int i = 0; i < getSize(); ++i) {
      result[resultOffset + i] = MathArrays.linearCombination(a1, c1[offset1 + i], a2, c2[offset2 + i], a3, c3[offset3 + i], a4, c4[offset4 + i]);
    }
  }
  public void linearCombination(final double a1, final double[] c1, final int offset1, final double a2, final double[] c2, final int offset2, final double a3, final double[] c3, final int offset3, final double[] result, final int resultOffset) {
    for(int i = 0; i < getSize(); ++i) {
      result[resultOffset + i] = MathArrays.linearCombination(a1, c1[offset1 + i], a2, c2[offset2 + i], a3, c3[offset3 + i]);
    }
  }
  public void linearCombination(final double a1, final double[] c1, final int offset1, final double a2, final double[] c2, final int offset2, final double[] result, final int resultOffset) {
    for(int i = 0; i < getSize(); ++i) {
      result[resultOffset + i] = MathArrays.linearCombination(a1, c1[offset1 + i], a2, c2[offset2 + i]);
    }
  }
  public void log(final double[] operand, final int operandOffset, final double[] result, final int resultOffset) {
    double[] function = new double[1 + order];
    function[0] = FastMath.log(operand[operandOffset]);
    if(order > 0) {
      double inv = 1.0D / operand[operandOffset];
      double xk = inv;
      for(int i = 1; i <= order; ++i) {
        function[i] = xk;
        xk *= -i * inv;
      }
    }
    compose(operand, operandOffset, function, result, resultOffset);
  }
  public void log10(final double[] operand, final int operandOffset, final double[] result, final int resultOffset) {
    double[] function = new double[1 + order];
    function[0] = FastMath.log10(operand[operandOffset]);
    if(order > 0) {
      double inv = 1.0D / operand[operandOffset];
      double xk = inv / FastMath.log(10.0D);
      for(int i = 1; i <= order; ++i) {
        function[i] = xk;
        xk *= -i * inv;
      }
    }
    compose(operand, operandOffset, function, result, resultOffset);
  }
  public void log1p(final double[] operand, final int operandOffset, final double[] result, final int resultOffset) {
    double[] function = new double[1 + order];
    function[0] = FastMath.log1p(operand[operandOffset]);
    if(order > 0) {
      double inv = 1.0D / (1.0D + operand[operandOffset]);
      double xk = inv;
      for(int i = 1; i <= order; ++i) {
        function[i] = xk;
        xk *= -i * inv;
      }
    }
    compose(operand, operandOffset, function, result, resultOffset);
  }
  public void multiply(final double[] lhs, final int lhsOffset, final double[] rhs, final int rhsOffset, final double[] result, final int resultOffset) {
    for(int i = 0; i < multIndirection.length; ++i) {
      final int[][] mappingI = multIndirection[i];
      double r = 0;
      for(int j = 0; j < mappingI.length; ++j) {
        r += mappingI[j][0] * lhs[lhsOffset + mappingI[j][1]] * rhs[rhsOffset + mappingI[j][2]];
      }
      result[resultOffset + i] = r;
    }
  }
  public void pow(final double a, final double[] operand, final int operandOffset, final double[] result, final int resultOffset) {
    final double[] function = new double[1 + order];
    if(a == 0) {
      if(operand[operandOffset] == 0) {
        function[0] = 1;
        double infinity = Double.POSITIVE_INFINITY;
        for(int i = 1; i < function.length; ++i) {
          infinity = -infinity;
          function[i] = infinity;
        }
      }
      else 
        if(operand[operandOffset] < 0) {
          Arrays.fill(function, Double.NaN);
        }
    }
    else {
      function[0] = FastMath.pow(a, operand[operandOffset]);
      final double lnA = FastMath.log(a);
      for(int i = 1; i < function.length; ++i) {
        function[i] = lnA * function[i - 1];
      }
    }
    compose(operand, operandOffset, function, result, resultOffset);
  }
  public void pow(final double[] operand, final int operandOffset, final double p, final double[] result, final int resultOffset) {
    double[] function = new double[1 + order];
    double xk = FastMath.pow(operand[operandOffset], p - order);
    for(int i = order; i > 0; --i) {
      function[i] = xk;
      xk *= operand[operandOffset];
    }
    function[0] = xk;
    double coefficient = p;
    for(int i = 1; i <= order; ++i) {
      function[i] *= coefficient;
      coefficient *= p - i;
    }
    compose(operand, operandOffset, function, result, resultOffset);
  }
  public void pow(final double[] x, final int xOffset, final double[] y, final int yOffset, final double[] result, final int resultOffset) {
    final double[] logX = new double[getSize()];
    log(x, xOffset, logX, 0);
    final double[] yLogX = new double[getSize()];
    multiply(logX, 0, y, yOffset, yLogX, 0);
    exp(yLogX, 0, result, resultOffset);
  }
  public void pow(final double[] operand, final int operandOffset, final int n, final double[] result, final int resultOffset) {
    if(n == 0) {
      result[resultOffset] = 1.0D;
      Arrays.fill(result, resultOffset + 1, resultOffset + getSize(), 0);
      return ;
    }
    double[] function = new double[1 + order];
    if(n > 0) {
      final int maxOrder = FastMath.min(order, n);
      double xk = FastMath.pow(operand[operandOffset], n - maxOrder);
      for(int i = maxOrder; i > 0; --i) {
        function[i] = xk;
        xk *= operand[operandOffset];
      }
      function[0] = xk;
    }
    else {
      final double inv = 1.0D / operand[operandOffset];
      double xk = FastMath.pow(inv, -n);
      for(int i = 0; i <= order; ++i) {
        function[i] = xk;
        xk *= inv;
      }
    }
    double coefficient = n;
    for(int i = 1; i <= order; ++i) {
      function[i] *= coefficient;
      coefficient *= n - i;
    }
    compose(operand, operandOffset, function, result, resultOffset);
  }
  public void remainder(final double[] lhs, final int lhsOffset, final double[] rhs, final int rhsOffset, final double[] result, final int resultOffset) {
    double var_109 = lhs[lhsOffset];
    final double rem = FastMath.IEEEremainder(var_109, rhs[rhsOffset]);
    final double k = FastMath.rint((lhs[lhsOffset] - rem) / rhs[rhsOffset]);
    result[resultOffset] = rem;
    for(int i = 1; i < getSize(); ++i) {
      result[resultOffset + i] = lhs[lhsOffset + i] - k * rhs[rhsOffset + i];
    }
  }
  public void rootN(final double[] operand, final int operandOffset, final int n, final double[] result, final int resultOffset) {
    double[] function = new double[1 + order];
    double xk;
    if(n == 2) {
      function[0] = FastMath.sqrt(operand[operandOffset]);
      xk = 0.5D / function[0];
    }
    else 
      if(n == 3) {
        function[0] = FastMath.cbrt(operand[operandOffset]);
        xk = 1.0D / (3.0D * function[0] * function[0]);
      }
      else {
        function[0] = FastMath.pow(operand[operandOffset], 1.0D / n);
        xk = 1.0D / (n * FastMath.pow(function[0], n - 1));
      }
    final double nReciprocal = 1.0D / n;
    final double xReciprocal = 1.0D / operand[operandOffset];
    for(int i = 1; i <= order; ++i) {
      function[i] = xk;
      xk *= xReciprocal * (nReciprocal - i);
    }
    compose(operand, operandOffset, function, result, resultOffset);
  }
  public void sin(final double[] operand, final int operandOffset, final double[] result, final int resultOffset) {
    double[] function = new double[1 + order];
    function[0] = FastMath.sin(operand[operandOffset]);
    if(order > 0) {
      function[1] = FastMath.cos(operand[operandOffset]);
      for(int i = 2; i <= order; ++i) {
        function[i] = -function[i - 2];
      }
    }
    compose(operand, operandOffset, function, result, resultOffset);
  }
  public void sinh(final double[] operand, final int operandOffset, final double[] result, final int resultOffset) {
    double[] function = new double[1 + order];
    function[0] = FastMath.sinh(operand[operandOffset]);
    if(order > 0) {
      function[1] = FastMath.cosh(operand[operandOffset]);
      for(int i = 2; i <= order; ++i) {
        function[i] = function[i - 2];
      }
    }
    compose(operand, operandOffset, function, result, resultOffset);
  }
  public void subtract(final double[] lhs, final int lhsOffset, final double[] rhs, final int rhsOffset, final double[] result, final int resultOffset) {
    for(int i = 0; i < getSize(); ++i) {
      result[resultOffset + i] = lhs[lhsOffset + i] - rhs[rhsOffset + i];
    }
  }
  public void tan(final double[] operand, final int operandOffset, final double[] result, final int resultOffset) {
    final double[] function = new double[1 + order];
    final double t = FastMath.tan(operand[operandOffset]);
    function[0] = t;
    if(order > 0) {
      final double[] p = new double[order + 2];
      p[1] = 1;
      final double t2 = t * t;
      for(int n = 1; n <= order; ++n) {
        double v = 0;
        p[n + 1] = n * p[n];
        for(int k = n + 1; k >= 0; k -= 2) {
          v = v * t2 + p[k];
          if(k > 2) {
            p[k - 2] = (k - 1) * p[k - 1] + (k - 3) * p[k - 3];
          }
          else 
            if(k == 2) {
              p[0] = p[1];
            }
        }
        if((n & 0x1) == 0) {
          v *= t;
        }
        function[n] = v;
      }
    }
    compose(operand, operandOffset, function, result, resultOffset);
  }
  public void tanh(final double[] operand, final int operandOffset, final double[] result, final int resultOffset) {
    final double[] function = new double[1 + order];
    final double t = FastMath.tanh(operand[operandOffset]);
    function[0] = t;
    if(order > 0) {
      final double[] p = new double[order + 2];
      p[1] = 1;
      final double t2 = t * t;
      for(int n = 1; n <= order; ++n) {
        double v = 0;
        p[n + 1] = -n * p[n];
        for(int k = n + 1; k >= 0; k -= 2) {
          v = v * t2 + p[k];
          if(k > 2) {
            p[k - 2] = (k - 1) * p[k - 1] - (k - 3) * p[k - 3];
          }
          else 
            if(k == 2) {
              p[0] = p[1];
            }
        }
        if((n & 0x1) == 0) {
          v *= t;
        }
        function[n] = v;
      }
    }
    compose(operand, operandOffset, function, result, resultOffset);
  }
}