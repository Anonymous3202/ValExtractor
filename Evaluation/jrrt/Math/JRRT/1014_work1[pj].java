package org.apache.commons.math3.fitting.leastsquares;
import java.util.Arrays;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.exception.ConvergenceException;
import org.apache.commons.math3.exception.util.LocalizedFormats;
import org.apache.commons.math3.optim.ConvergenceChecker;
import org.apache.commons.math3.optim.PointVectorValuePair;
import org.apache.commons.math3.util.Precision;
import org.apache.commons.math3.util.FastMath;

public class LevenbergMarquardtOptimizer extends AbstractLeastSquaresOptimizer<LevenbergMarquardtOptimizer>  {
  final private static double TWO_EPS = 2 * Precision.EPSILON;
  private double initialStepBoundFactor = 100;
  private double costRelativeTolerance = 1e-10D;
  private double parRelativeTolerance = 1e-10D;
  private double orthoTolerance = 1e-10D;
  private double qrRankingThreshold = Precision.SAFE_MIN;
  private double lmPar;
  private double[] lmDir;
  protected LevenbergMarquardtOptimizer() {
    super();
  }
  protected LevenbergMarquardtOptimizer(LevenbergMarquardtOptimizer other) {
    super(other);
    this.initialStepBoundFactor = other.initialStepBoundFactor;
    this.costRelativeTolerance = other.costRelativeTolerance;
    this.parRelativeTolerance = other.parRelativeTolerance;
    this.orthoTolerance = other.orthoTolerance;
    this.qrRankingThreshold = other.qrRankingThreshold;
    lmPar = 0;
    lmDir = null;
  }
  private InternalData qrDecomposition(RealMatrix jacobian, int solvedCols) throws ConvergenceException {
    final double[][] weightedJacobian = jacobian.scalarMultiply(-1).getData();
    final int nR = weightedJacobian.length;
    final int nC = weightedJacobian[0].length;
    final int[] permutation = new int[nC];
    final double[] diagR = new double[nC];
    final double[] jacNorm = new double[nC];
    final double[] beta = new double[nC];
    for(int k = 0; k < nC; ++k) {
      permutation[k] = k;
      double norm2 = 0;
      for(int i = 0; i < nR; ++i) {
        double akk = weightedJacobian[i][k];
        norm2 += akk * akk;
      }
      jacNorm[k] = FastMath.sqrt(norm2);
    }
    for(int k = 0; k < nC; ++k) {
      int nextColumn = -1;
      double ak2 = Double.NEGATIVE_INFINITY;
      for(int i = k; i < nC; ++i) {
        double norm2 = 0;
        for(int j = k; j < nR; ++j) {
          double aki = weightedJacobian[j][permutation[i]];
          norm2 += aki * aki;
        }
        if(Double.isInfinite(norm2) || Double.isNaN(norm2)) {
          throw new ConvergenceException(LocalizedFormats.UNABLE_TO_PERFORM_QR_DECOMPOSITION_ON_JACOBIAN, nR, nC);
        }
        if(norm2 > ak2) {
          nextColumn = i;
          ak2 = norm2;
        }
      }
      if(ak2 <= qrRankingThreshold) {
        return new InternalData(weightedJacobian, permutation, k, diagR, jacNorm, beta);
      }
      int pk = permutation[nextColumn];
      permutation[nextColumn] = permutation[k];
      permutation[k] = pk;
      double akk = weightedJacobian[k][pk];
      double alpha = (akk > 0) ? -FastMath.sqrt(ak2) : FastMath.sqrt(ak2);
      double betak = 1.0D / (ak2 - akk * alpha);
      beta[pk] = betak;
      diagR[pk] = alpha;
      weightedJacobian[k][pk] -= alpha;
      for(int dk = nC - 1 - k; dk > 0; --dk) {
        double gamma = 0;
        for(int j = k; j < nR; ++j) {
          gamma += weightedJacobian[j][pk] * weightedJacobian[j][permutation[k + dk]];
        }
        gamma *= betak;
        for(int j = k; j < nR; ++j) {
          weightedJacobian[j][permutation[k + dk]] -= gamma * weightedJacobian[j][pk];
        }
      }
    }
    return new InternalData(weightedJacobian, permutation, solvedCols, diagR, jacNorm, beta);
  }
  public static LevenbergMarquardtOptimizer create() {
    return new LevenbergMarquardtOptimizer();
  }
  @Override() public LevenbergMarquardtOptimizer shallowCopy() {
    return new LevenbergMarquardtOptimizer(this);
  }
  public LevenbergMarquardtOptimizer withCostRelativeTolerance(double newCostRelativeTolerance) {
    this.costRelativeTolerance = newCostRelativeTolerance;
    return self();
  }
  public LevenbergMarquardtOptimizer withInitialStepBoundFactor(double newInitialStepBoundFactor) {
    this.initialStepBoundFactor = newInitialStepBoundFactor;
    return self();
  }
  public LevenbergMarquardtOptimizer withOrthoTolerance(double newOrthoTolerance) {
    this.orthoTolerance = newOrthoTolerance;
    return self();
  }
  public LevenbergMarquardtOptimizer withParameterRelativeTolerance(double parameterRelativeTolerance) {
    this.parRelativeTolerance = parameterRelativeTolerance;
    return self();
  }
  public LevenbergMarquardtOptimizer withRankingThreshold(double rankingThreshold) {
    this.qrRankingThreshold = rankingThreshold;
    return self();
  }
  @Override() protected PointVectorValuePair doOptimize() {
    final int nR = getTarget().length;
    final double[] currentPoint = getStart();
    final int nC = currentPoint.length;
    final int solvedCols = FastMath.min(nR, nC);
    lmDir = new double[nC];
    lmPar = 0;
    double delta = 0;
    double xNorm = 0;
    double[] diag = new double[nC];
    double[] oldX = new double[nC];
    double[] oldRes = new double[nR];
    double[] oldObj = new double[nR];
    double[] qtf = new double[nR];
    double[] work1 = new double[nC];
    double[] work2 = new double[nC];
    double[] work3 = new double[nC];
    final RealMatrix weightMatrixSqrt = getWeightSquareRoot();
    double[] currentObjective = computeObjectiveValue(currentPoint);
    double[] currentResiduals = computeResiduals(currentObjective);
    PointVectorValuePair current = new PointVectorValuePair(currentPoint, currentObjective);
    double currentCost = computeCost(currentResiduals);
    boolean firstIteration = true;
    final ConvergenceChecker<PointVectorValuePair> checker = getConvergenceChecker();
    while(true){
      incrementIterationCount();
      final PointVectorValuePair previous = current;
      final InternalData internalData = qrDecomposition(computeWeightedJacobian(currentPoint), solvedCols);
      final double[][] weightedJacobian = internalData.weightedJacobian;
      final int[] permutation = internalData.permutation;
      final double[] diagR = internalData.diagR;
      final double[] jacNorm = internalData.jacNorm;
      double[] weightedResidual = weightMatrixSqrt.operate(currentResiduals);
      for(int i = 0; i < nR; i++) {
        qtf[i] = weightedResidual[i];
      }
      qTy(qtf, internalData);
      for(int k = 0; k < solvedCols; ++k) {
        int pk = permutation[k];
        weightedJacobian[k][pk] = diagR[pk];
      }
      if(firstIteration) {
        xNorm = 0;
        for(int k = 0; k < nC; ++k) {
          double dk = jacNorm[k];
          if(dk == 0) {
            dk = 1.0D;
          }
          double xk = dk * currentPoint[k];
          xNorm += xk * xk;
          diag[k] = dk;
        }
        xNorm = FastMath.sqrt(xNorm);
        delta = (xNorm == 0) ? initialStepBoundFactor : (initialStepBoundFactor * xNorm);
      }
      double maxCosine = 0;
      if(currentCost != 0) {
        for(int j = 0; j < solvedCols; ++j) {
          int pj = permutation[j];
          double s = jacNorm[pj];
          if(s != 0) {
            double sum = 0;
            for(int i = 0; i <= j; ++i) {
              sum += weightedJacobian[i][pj] * qtf[i];
            }
            maxCosine = FastMath.max(maxCosine, FastMath.abs(sum) / (s * currentCost));
          }
        }
      }
      if(maxCosine <= orthoTolerance) {
        return current;
      }
      for(int j = 0; j < nC; ++j) {
        diag[j] = FastMath.max(diag[j], jacNorm[j]);
      }
      for(double ratio = 0; ratio < 1.0e-4D; ) {
        for(int j = 0; j < solvedCols; ++j) {
          int pj = permutation[j];
          oldX[pj] = currentPoint[pj];
        }
        final double previousCost = currentCost;
        double[] tmpVec = weightedResidual;
        weightedResidual = oldRes;
        oldRes = tmpVec;
        tmpVec = currentObjective;
        currentObjective = oldObj;
        oldObj = tmpVec;
        determineLMParameter(qtf, delta, diag, internalData, solvedCols, work1, work2, work3);
        double lmNorm = 0;
        for(int j = 0; j < solvedCols; ++j) {
          int pj = permutation[j];
          lmDir[pj] = -lmDir[pj];
          currentPoint[pj] = oldX[pj] + lmDir[pj];
          double s = diag[pj] * lmDir[pj];
          lmNorm += s * s;
        }
        lmNorm = FastMath.sqrt(lmNorm);
        if(firstIteration) {
          delta = FastMath.min(delta, lmNorm);
        }
        currentObjective = computeObjectiveValue(currentPoint);
        currentResiduals = computeResiduals(currentObjective);
        current = new PointVectorValuePair(currentPoint, currentObjective);
        currentCost = computeCost(currentResiduals);
        double actRed = -1.0D;
        if(0.1D * currentCost < previousCost) {
          double r = currentCost / previousCost;
          actRed = 1.0D - r * r;
        }
        for(int j = 0; j < solvedCols; ++j) {
          int pj = permutation[j];
          double dirJ = lmDir[pj];
          work1[j] = 0;
          for(int i = 0; i <= j; ++i) {
            work1[i] += weightedJacobian[i][pj] * dirJ;
          }
        }
        double coeff1 = 0;
        for(int j = 0; j < solvedCols; ++j) {
          coeff1 += work1[j] * work1[j];
        }
        double pc2 = previousCost * previousCost;
        coeff1 = coeff1 / pc2;
        double coeff2 = lmPar * lmNorm * lmNorm / pc2;
        double preRed = coeff1 + 2 * coeff2;
        double dirDer = -(coeff1 + coeff2);
        ratio = (preRed == 0) ? 0 : (actRed / preRed);
        if(ratio <= 0.25D) {
          double tmp = (actRed < 0) ? (0.5D * dirDer / (dirDer + 0.5D * actRed)) : 0.5D;
          if((0.1D * currentCost >= previousCost) || (tmp < 0.1D)) {
            tmp = 0.1D;
          }
          delta = tmp * FastMath.min(delta, 10.0D * lmNorm);
          lmPar /= tmp;
        }
        else 
          if((lmPar == 0) || (ratio >= 0.75D)) {
            delta = 2 * lmNorm;
            lmPar *= 0.5D;
          }
        if(ratio >= 1.0e-4D) {
          firstIteration = false;
          xNorm = 0;
          for(int k = 0; k < nC; ++k) {
            double xK = diag[k] * currentPoint[k];
            xNorm += xK * xK;
          }
          xNorm = FastMath.sqrt(xNorm);
          if(checker != null && checker.converged(getIterations(), previous, current)) {
            return current;
          }
        }
        else {
          currentCost = previousCost;
          for(int j = 0; j < solvedCols; ++j) {
            int pj = permutation[j];
            currentPoint[pj] = oldX[pj];
          }
          tmpVec = weightedResidual;
          weightedResidual = oldRes;
          oldRes = tmpVec;
          tmpVec = currentObjective;
          currentObjective = oldObj;
          oldObj = tmpVec;
          current = new PointVectorValuePair(currentPoint, currentObjective);
        }
        if((FastMath.abs(actRed) <= costRelativeTolerance && preRed <= costRelativeTolerance && ratio <= 2.0D) || delta <= parRelativeTolerance * xNorm) {
          return current;
        }
        if(FastMath.abs(actRed) <= TWO_EPS && preRed <= TWO_EPS && ratio <= 2.0D) {
          throw new ConvergenceException(LocalizedFormats.TOO_SMALL_COST_RELATIVE_TOLERANCE, costRelativeTolerance);
        }
        else 
          if(delta <= TWO_EPS * xNorm) {
            throw new ConvergenceException(LocalizedFormats.TOO_SMALL_PARAMETERS_RELATIVE_TOLERANCE, parRelativeTolerance);
          }
          else 
            if(maxCosine <= TWO_EPS) {
              throw new ConvergenceException(LocalizedFormats.TOO_SMALL_ORTHOGONALITY_TOLERANCE, orthoTolerance);
            }
      }
    }
  }
  public double getCostRelativeTolerance() {
    return costRelativeTolerance;
  }
  public double getInitialStepBoundFactor() {
    return initialStepBoundFactor;
  }
  public double getOrthoTolerance() {
    return orthoTolerance;
  }
  public double getParameterRelativeTolerance() {
    return parRelativeTolerance;
  }
  public double getRankingThreshold() {
    return qrRankingThreshold;
  }
  private void determineLMDirection(double[] qy, double[] diag, double[] lmDiag, InternalData internalData, int solvedCols, double[] work) {
    final int[] permutation = internalData.permutation;
    final double[][] weightedJacobian = internalData.weightedJacobian;
    final double[] diagR = internalData.diagR;
    for(int j = 0; j < solvedCols; ++j) {
      int pj = permutation[j];
      for(int i = j + 1; i < solvedCols; ++i) {
        weightedJacobian[i][pj] = weightedJacobian[j][permutation[i]];
      }
      lmDir[j] = diagR[pj];
      work[j] = qy[j];
    }
    for(int j = 0; j < solvedCols; ++j) {
      int pj = permutation[j];
      double dpj = diag[pj];
      if(dpj != 0) {
        Arrays.fill(lmDiag, j + 1, lmDiag.length, 0);
      }
      lmDiag[j] = dpj;
      double qtbpj = 0;
      for(int k = j; k < solvedCols; ++k) {
        int pk = permutation[k];
        if(lmDiag[k] != 0) {
          final double sin;
          final double cos;
          double rkk = weightedJacobian[k][pk];
          if(FastMath.abs(rkk) < FastMath.abs(lmDiag[k])) {
            final double cotan = rkk / lmDiag[k];
            sin = 1.0D / FastMath.sqrt(1.0D + cotan * cotan);
            cos = sin * cotan;
          }
          else {
            final double tan = lmDiag[k] / rkk;
            cos = 1.0D / FastMath.sqrt(1.0D + tan * tan);
            sin = cos * tan;
          }
          weightedJacobian[k][pk] = cos * rkk + sin * lmDiag[k];
          final double temp = cos * work[k] + sin * qtbpj;
          qtbpj = -sin * work[k] + cos * qtbpj;
          work[k] = temp;
          for(int i = k + 1; i < solvedCols; ++i) {
            double rik = weightedJacobian[i][pk];
            final double temp2 = cos * rik + sin * lmDiag[i];
            lmDiag[i] = -sin * rik + cos * lmDiag[i];
            weightedJacobian[i][pk] = temp2;
          }
        }
      }
      lmDiag[j] = weightedJacobian[j][permutation[j]];
      weightedJacobian[j][permutation[j]] = lmDir[j];
    }
    int nSing = solvedCols;
    for(int j = 0; j < solvedCols; ++j) {
      if((lmDiag[j] == 0) && (nSing == solvedCols)) {
        nSing = j;
      }
      if(nSing < solvedCols) {
        work[j] = 0;
      }
    }
    if(nSing > 0) {
      for(int j = nSing - 1; j >= 0; --j) {
        int pj = permutation[j];
        double sum = 0;
        for(int i = j + 1; i < nSing; ++i) {
          sum += weightedJacobian[i][pj] * work[i];
        }
        work[j] = (work[j] - sum) / lmDiag[j];
      }
    }
    for(int j = 0; j < lmDir.length; ++j) {
      lmDir[permutation[j]] = work[j];
    }
  }
  private void determineLMParameter(double[] qy, double delta, double[] diag, InternalData internalData, int solvedCols, double[] work1, double[] work2, double[] work3) {
    final double[][] weightedJacobian = internalData.weightedJacobian;
    final int[] permutation = internalData.permutation;
    final int rank = internalData.rank;
    final double[] diagR = internalData.diagR;
    final int nC = weightedJacobian[0].length;
    for(int j = 0; j < rank; ++j) {
      lmDir[permutation[j]] = qy[j];
    }
    for(int j = rank; j < nC; ++j) {
      lmDir[permutation[j]] = 0;
    }
    for(int k = rank - 1; k >= 0; --k) {
      int pk = permutation[k];
      double ypk = lmDir[pk] / diagR[pk];
      for(int i = 0; i < k; ++i) {
        lmDir[permutation[i]] -= ypk * weightedJacobian[i][pk];
      }
      lmDir[pk] = ypk;
    }
    double dxNorm = 0;
    for(int j = 0; j < solvedCols; ++j) {
      int pj = permutation[j];
      double s = diag[pj] * lmDir[pj];
      work1[pj] = s;
      dxNorm += s * s;
    }
    dxNorm = FastMath.sqrt(dxNorm);
    double fp = dxNorm - delta;
    if(fp <= 0.1D * delta) {
      lmPar = 0;
      return ;
    }
    double sum2;
    double parl = 0;
    if(rank == solvedCols) {
      for(int j = 0; j < solvedCols; ++j) {
        int pj = permutation[j];
        work1[pj] *= diag[pj] / dxNorm;
      }
      sum2 = 0;
      for(int j = 0; j < solvedCols; ++j) {
        int pj = permutation[j];
        double sum = 0;
        for(int i = 0; i < j; ++i) {
          sum += weightedJacobian[i][pj] * work1[permutation[i]];
        }
        double s = (work1[pj] - sum) / diagR[pj];
        work1[pj] = s;
        sum2 += s * s;
      }
      parl = fp / (delta * sum2);
    }
    sum2 = 0;
    for(int j = 0; j < solvedCols; ++j) {
      int pj = permutation[j];
      double sum = 0;
      for(int i = 0; i <= j; ++i) {
        sum += weightedJacobian[i][pj] * qy[i];
      }
      sum /= diag[pj];
      sum2 += sum * sum;
    }
    double gNorm = FastMath.sqrt(sum2);
    double paru = gNorm / delta;
    if(paru == 0) {
      paru = Precision.SAFE_MIN / FastMath.min(delta, 0.1D);
    }
    lmPar = FastMath.min(paru, FastMath.max(lmPar, parl));
    if(lmPar == 0) {
      lmPar = gNorm / dxNorm;
    }
    for(int countdown = 10; countdown >= 0; --countdown) {
      if(lmPar == 0) {
        lmPar = FastMath.max(Precision.SAFE_MIN, 0.001D * paru);
      }
      double sPar = FastMath.sqrt(lmPar);
      for(int j = 0; j < solvedCols; ++j) {
        int pj = permutation[j];
        work1[pj] = sPar * diag[pj];
      }
      determineLMDirection(qy, work1, work2, internalData, solvedCols, work3);
      dxNorm = 0;
      for(int j = 0; j < solvedCols; ++j) {
        int pj = permutation[j];
        double s = diag[pj] * lmDir[pj];
        work3[pj] = s;
        dxNorm += s * s;
      }
      dxNorm = FastMath.sqrt(dxNorm);
      double previousFP = fp;
      fp = dxNorm - delta;
      if(FastMath.abs(fp) <= 0.1D * delta || (parl == 0 && fp <= previousFP && previousFP < 0)) {
        return ;
      }
      for(int j = 0; j < solvedCols; ++j) {
        int pj = permutation[j];
        work1[pj] = work3[pj] * diag[pj] / dxNorm;
      }
      for(int j = 0; j < solvedCols; ++j) {
        int pj = permutation[j];
        work1[pj] /= work2[j];
        double var_1014 = work1[pj];
        double tmp = var_1014;
        for(int i = j + 1; i < solvedCols; ++i) {
          work1[permutation[i]] -= weightedJacobian[i][pj] * tmp;
        }
      }
      sum2 = 0;
      for(int j = 0; j < solvedCols; ++j) {
        double s = work1[permutation[j]];
        sum2 += s * s;
      }
      double correction = fp / (delta * sum2);
      if(fp > 0) {
        parl = FastMath.max(parl, lmPar);
      }
      else 
        if(fp < 0) {
          paru = FastMath.min(paru, lmPar);
        }
      lmPar = FastMath.max(parl, lmPar + correction);
    }
    return ;
  }
  private void qTy(double[] y, InternalData internalData) {
    final double[][] weightedJacobian = internalData.weightedJacobian;
    final int[] permutation = internalData.permutation;
    final double[] beta = internalData.beta;
    final int nR = weightedJacobian.length;
    final int nC = weightedJacobian[0].length;
    for(int k = 0; k < nC; ++k) {
      int pk = permutation[k];
      double gamma = 0;
      for(int i = k; i < nR; ++i) {
        gamma += weightedJacobian[i][pk] * y[i];
      }
      gamma *= beta[pk];
      for(int i = k; i < nR; ++i) {
        y[i] -= gamma * weightedJacobian[i][pk];
      }
    }
  }
  
  private static class InternalData  {
    final private double[][] weightedJacobian;
    final private int[] permutation;
    final private int rank;
    final private double[] diagR;
    final private double[] jacNorm;
    final private double[] beta;
    InternalData(double[][] weightedJacobian, int[] permutation, int rank, double[] diagR, double[] jacNorm, double[] beta) {
      super();
      this.weightedJacobian = weightedJacobian;
      this.permutation = permutation;
      this.rank = rank;
      this.diagR = diagR;
      this.jacNorm = jacNorm;
      this.beta = beta;
    }
  }
}