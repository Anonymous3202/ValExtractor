package org.apache.commons.math3.optim.nonlinear.scalar.gradient;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.solvers.BrentSolver;
import org.apache.commons.math3.analysis.solvers.UnivariateSolver;
import org.apache.commons.math3.exception.MathInternalError;
import org.apache.commons.math3.exception.MathIllegalStateException;
import org.apache.commons.math3.exception.TooManyEvaluationsException;
import org.apache.commons.math3.exception.MathUnsupportedOperationException;
import org.apache.commons.math3.exception.util.LocalizedFormats;
import org.apache.commons.math3.optim.OptimizationData;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.ConvergenceChecker;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.GradientMultivariateOptimizer;
import org.apache.commons.math3.util.FastMath;

public class NonLinearConjugateGradientOptimizer extends GradientMultivariateOptimizer  {
  final private Formula updateFormula;
  final private Preconditioner preconditioner;
  final private UnivariateSolver solver;
  private double initialStep = 1;
  public NonLinearConjugateGradientOptimizer(final Formula updateFormula, ConvergenceChecker<PointValuePair> checker) {
    this(updateFormula, checker, new BrentSolver(), new IdentityPreconditioner());
  }
  public NonLinearConjugateGradientOptimizer(final Formula updateFormula, ConvergenceChecker<PointValuePair> checker, final UnivariateSolver lineSearchSolver) {
    this(updateFormula, checker, lineSearchSolver, new IdentityPreconditioner());
  }
  public NonLinearConjugateGradientOptimizer(final Formula updateFormula, ConvergenceChecker<PointValuePair> checker, final UnivariateSolver lineSearchSolver, final Preconditioner preconditioner) {
    super(checker);
    this.updateFormula = updateFormula;
    solver = lineSearchSolver;
    this.preconditioner = preconditioner;
    initialStep = 1;
  }
  @Override() protected PointValuePair doOptimize() {
    final ConvergenceChecker<PointValuePair> checker = getConvergenceChecker();
    final double[] point = getStartPoint();
    final GoalType goal = getGoalType();
    final int n = point.length;
    double[] r = computeObjectiveGradient(point);
    if(goal == GoalType.MINIMIZE) {
      for(int i = 0; i < n; i++) {
        r[i] = -r[i];
      }
    }
    double[] steepestDescent = preconditioner.precondition(point, r);
    double[] searchDirection = steepestDescent.clone();
    double delta = 0;
    for(int i = 0; i < n; ++i) {
      delta += r[i] * searchDirection[i];
    }
    PointValuePair current = null;
    int maxEval = getMaxEvaluations();
    while(true){
      incrementIterationCount();
      final double objective = computeObjectiveValue(point);
      PointValuePair previous = current;
      current = new PointValuePair(point, objective);
      if(previous != null && checker.converged(getIterations(), previous, current)) {
        return current;
      }
      final UnivariateFunction lsf = new LineSearchFunction(point, searchDirection);
      final double uB = findUpperBound(lsf, 0, initialStep);
      final double step = solver.solve(maxEval, lsf, 0, uB, 1e-15D);
      maxEval -= solver.getEvaluations();
      for(int i = 0; i < point.length; ++i) {
        point[i] += step * searchDirection[i];
      }
      r = computeObjectiveGradient(point);
      if(goal == GoalType.MINIMIZE) {
        for(int i = 0; i < n; ++i) {
          r[i] = -r[i];
        }
      }
      final double deltaOld = delta;
      final double[] newSteepestDescent = preconditioner.precondition(point, r);
      delta = 0;
      for(int i = 0; i < n; ++i) {
        delta += r[i] * newSteepestDescent[i];
      }
      final double beta;
      switch (updateFormula){
        case FLETCHER_REEVES:
        beta = delta / deltaOld;
        break ;
        case POLAK_RIBIERE:
        double deltaMid = 0;
        for(int i = 0; i < r.length; ++i) {
          deltaMid += r[i] * steepestDescent[i];
        }
        beta = (delta - deltaMid) / deltaOld;
        break ;
        default:
        throw new MathInternalError();
      }
      steepestDescent = newSteepestDescent;
      if(getIterations() % n == 0 || beta < 0) {
        searchDirection = steepestDescent.clone();
      }
      else {
        for(int i = 0; i < n; ++i) {
          searchDirection[i] = steepestDescent[i] + beta * searchDirection[i];
        }
      }
    }
  }
  @Override() public PointValuePair optimize(OptimizationData ... optData) throws TooManyEvaluationsException {
    return super.optimize(optData);
  }
  private double findUpperBound(final UnivariateFunction f, final double a, final double h) {
    final double yA = f.value(a);
    double yB = yA;
    for(double step = h; step < Double.MAX_VALUE; step *= FastMath.max(2, yA / yB)) {
      final double b = a + step;
      yB = f.value(b);
      if(yA * yB <= 0) {
        return b;
      }
    }
    throw new MathIllegalStateException(LocalizedFormats.UNABLE_TO_BRACKET_OPTIMUM_IN_LINE_SEARCH);
  }
  private void checkParameters() {
    if(getLowerBound() != null || getUpperBound() != null) {
      throw new MathUnsupportedOperationException(LocalizedFormats.CONSTRAINT);
    }
  }
  @Override() protected void parseOptimizationData(OptimizationData ... optData) {
    super.parseOptimizationData(optData);
    for (OptimizationData data : optData) {
      if(data instanceof BracketingStep) {
        initialStep = ((BracketingStep)data).getBracketingStep();
        break ;
      }
    }
    checkParameters();
  }
  
  public static class BracketingStep implements OptimizationData  {
    final private double initialStep;
    public BracketingStep(double step) {
      super();
      initialStep = step;
    }
    public double getBracketingStep() {
      return initialStep;
    }
  }
  public static enum Formula {
    FLETCHER_REEVES(),

    POLAK_RIBIERE(),

  ;
  private Formula() {
  }
  }
  
  public static class IdentityPreconditioner implements Preconditioner  {
    public double[] precondition(double[] variables, double[] r) {
      return r.clone();
    }
  }
  
  private class LineSearchFunction implements UnivariateFunction  {
    final private double[] currentPoint;
    final private double[] searchDirection;
    public LineSearchFunction(double[] point, double[] direction) {
      super();
      currentPoint = point.clone();
      searchDirection = direction.clone();
    }
    public double value(double x) {
      final double[] shiftedPoint = currentPoint.clone();
      for(int i = 0; i < shiftedPoint.length; ++i) {
        double var_2988 = searchDirection[i];
        shiftedPoint[i] += x * var_2988;
      }
      final double[] gradient = computeObjectiveGradient(shiftedPoint);
      double dotProduct = 0;
      for(int i = 0; i < gradient.length; ++i) {
        dotProduct += gradient[i] * searchDirection[i];
      }
      return dotProduct;
    }
  }
}