package org.apache.commons.math3.optimization.general;
import org.apache.commons.math3.analysis.DifferentiableMultivariateVectorFunction;
import org.apache.commons.math3.analysis.FunctionUtils;
import org.apache.commons.math3.analysis.differentiation.DerivativeStructure;
import org.apache.commons.math3.analysis.differentiation.MultivariateDifferentiableVectorFunction;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.exception.NumberIsTooSmallException;
import org.apache.commons.math3.exception.util.LocalizedFormats;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.DiagonalMatrix;
import org.apache.commons.math3.linear.DecompositionSolver;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.QRDecomposition;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.apache.commons.math3.optimization.OptimizationData;
import org.apache.commons.math3.optimization.InitialGuess;
import org.apache.commons.math3.optimization.Target;
import org.apache.commons.math3.optimization.Weight;
import org.apache.commons.math3.optimization.ConvergenceChecker;
import org.apache.commons.math3.optimization.DifferentiableMultivariateVectorOptimizer;
import org.apache.commons.math3.optimization.PointVectorValuePair;
import org.apache.commons.math3.optimization.direct.BaseAbstractMultivariateVectorOptimizer;
import org.apache.commons.math3.util.FastMath;

abstract @Deprecated() public class AbstractLeastSquaresOptimizer extends BaseAbstractMultivariateVectorOptimizer<DifferentiableMultivariateVectorFunction> implements DifferentiableMultivariateVectorOptimizer  {
  @Deprecated() final private static double DEFAULT_SINGULARITY_THRESHOLD = 1e-14D;
  @Deprecated() protected double[][] weightedResidualJacobian;
  @Deprecated() protected int cols;
  @Deprecated() protected int rows;
  @Deprecated() protected double[] point;
  @Deprecated() protected double[] objective;
  @Deprecated() protected double[] weightedResiduals;
  @Deprecated() protected double cost;
  private MultivariateDifferentiableVectorFunction jF;
  private int jacobianEvaluations;
  private RealMatrix weightMatrixSqrt;
  @Deprecated() protected AbstractLeastSquaresOptimizer() {
    super();
  }
  protected AbstractLeastSquaresOptimizer(ConvergenceChecker<PointVectorValuePair> checker) {
    super(checker);
  }
  @Override() @Deprecated() public PointVectorValuePair optimize(int maxEval, final DifferentiableMultivariateVectorFunction f, final double[] target, final double[] weights, final double[] startPoint) {
    return optimizeInternal(maxEval, FunctionUtils.toMultivariateDifferentiableVectorFunction(f), new Target(target), new Weight(weights), new InitialGuess(startPoint));
  }
  @Deprecated() public PointVectorValuePair optimize(final int maxEval, final MultivariateDifferentiableVectorFunction f, final double[] target, final double[] weights, final double[] startPoint) {
    return optimizeInternal(maxEval, f, new Target(target), new Weight(weights), new InitialGuess(startPoint));
  }
  @Deprecated() protected PointVectorValuePair optimizeInternal(final int maxEval, final MultivariateDifferentiableVectorFunction f, OptimizationData ... optData) {
    return super.optimizeInternal(maxEval, FunctionUtils.toDifferentiableMultivariateVectorFunction(f), optData);
  }
  protected RealMatrix computeWeightedJacobian(double[] params) {
    ++jacobianEvaluations;
    final DerivativeStructure[] dsPoint = new DerivativeStructure[params.length];
    final int nC = params.length;
    for(int i = 0; i < nC; ++i) {
      dsPoint[i] = new DerivativeStructure(nC, 1, i, params[i]);
    }
    final DerivativeStructure[] dsValue = jF.value(dsPoint);
    final int nR = getTarget().length;
    if(dsValue.length != nR) {
      int var_3488 = dsValue.length;
      throw new DimensionMismatchException(var_3488, nR);
    }
    final double[][] jacobianData = new double[nR][nC];
    for(int i = 0; i < nR; ++i) {
      int[] orders = new int[nC];
      for(int j = 0; j < nC; ++j) {
        orders[j] = 1;
        jacobianData[i][j] = dsValue[i].getPartialDerivative(orders);
        orders[j] = 0;
      }
    }
    return weightMatrixSqrt.multiply(MatrixUtils.createRealMatrix(jacobianData));
  }
  public RealMatrix getWeightSquareRoot() {
    return weightMatrixSqrt.copy();
  }
  private RealMatrix squareRoot(RealMatrix m) {
    if(m instanceof DiagonalMatrix) {
      final int dim = m.getRowDimension();
      final RealMatrix sqrtM = new DiagonalMatrix(dim);
      for(int i = 0; i < dim; i++) {
        sqrtM.setEntry(i, i, FastMath.sqrt(m.getEntry(i, i)));
      }
      return sqrtM;
    }
    else {
      final EigenDecomposition dec = new EigenDecomposition(m);
      return dec.getSquareRoot();
    }
  }
  protected double computeCost(double[] residuals) {
    final ArrayRealVector r = new ArrayRealVector(residuals);
    return FastMath.sqrt(r.dotProduct(getWeight().operate(r)));
  }
  public double getChiSquare() {
    return cost * cost;
  }
  public double getRMS() {
    return FastMath.sqrt(getChiSquare() / rows);
  }
  protected double[] computeResiduals(double[] objectiveValue) {
    final double[] target = getTarget();
    if(objectiveValue.length != target.length) {
      throw new DimensionMismatchException(target.length, objectiveValue.length);
    }
    final double[] residuals = new double[target.length];
    for(int i = 0; i < target.length; i++) {
      residuals[i] = target[i] - objectiveValue[i];
    }
    return residuals;
  }
  public double[] computeSigma(double[] params, double covarianceSingularityThreshold) {
    final int nC = params.length;
    final double[] sig = new double[nC];
    final double[][] cov = computeCovariances(params, covarianceSingularityThreshold);
    for(int i = 0; i < nC; ++i) {
      sig[i] = FastMath.sqrt(cov[i][i]);
    }
    return sig;
  }
  @Deprecated() public double[] guessParametersErrors() {
    if(rows <= cols) {
      throw new NumberIsTooSmallException(LocalizedFormats.NO_DEGREES_OF_FREEDOM, rows, cols, false);
    }
    double[] errors = new double[cols];
    final double c = FastMath.sqrt(getChiSquare() / (rows - cols));
    double[][] covar = computeCovariances(point, 1e-14D);
    for(int i = 0; i < errors.length; ++i) {
      errors[i] = FastMath.sqrt(covar[i][i]) * c;
    }
    return errors;
  }
  public double[][] computeCovariances(double[] params, double threshold) {
    final RealMatrix j = computeWeightedJacobian(params);
    final RealMatrix jTj = j.transpose().multiply(j);
    final DecompositionSolver solver = new QRDecomposition(jTj, threshold).getSolver();
    return solver.getInverse().getData();
  }
  @Deprecated() public double[][] getCovariances() {
    return getCovariances(DEFAULT_SINGULARITY_THRESHOLD);
  }
  @Deprecated() public double[][] getCovariances(double threshold) {
    return computeCovariances(point, threshold);
  }
  public int getJacobianEvaluations() {
    return jacobianEvaluations;
  }
  protected void setCost(double cost) {
    this.cost = cost;
  }
  @Override() protected void setUp() {
    super.setUp();
    jacobianEvaluations = 0;
    weightMatrixSqrt = squareRoot(getWeight());
    jF = FunctionUtils.toMultivariateDifferentiableVectorFunction((DifferentiableMultivariateVectorFunction)getObjectiveFunction());
    point = getStartPoint();
    rows = getTarget().length;
    cols = point.length;
  }
  @Deprecated() protected void updateJacobian() {
    final RealMatrix weightedJacobian = computeWeightedJacobian(point);
    weightedResidualJacobian = weightedJacobian.scalarMultiply(-1).getData();
  }
  @Deprecated() protected void updateResidualsAndCost() {
    objective = computeObjectiveValue(point);
    final double[] res = computeResiduals(objective);
    cost = computeCost(res);
    final ArrayRealVector residuals = new ArrayRealVector(res);
    weightedResiduals = weightMatrixSqrt.operate(residuals).toArray();
  }
}