package org.apache.commons.math3.optimization.linear;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.math3.exception.MaxCountExceededException;
import org.apache.commons.math3.optimization.PointValuePair;
import org.apache.commons.math3.util.Precision;

@Deprecated() public class SimplexSolver extends AbstractLinearOptimizer  {
  final private static double DEFAULT_EPSILON = 1.0e-6D;
  final private static int DEFAULT_ULPS = 10;
  final private double epsilon;
  final private int maxUlps;
  public SimplexSolver() {
    this(DEFAULT_EPSILON, DEFAULT_ULPS);
  }
  public SimplexSolver(final double epsilon, final int maxUlps) {
    super();
    this.epsilon = epsilon;
    this.maxUlps = maxUlps;
  }
  private Integer getPivotColumn(SimplexTableau tableau) {
    double minValue = 0;
    Integer minPos = null;
    for(int i = tableau.getNumObjectiveFunctions(); i < tableau.getWidth() - 1; i++) {
      final double entry = tableau.getEntry(0, i);
      if(entry < minValue) {
        minValue = entry;
        minPos = i;
      }
    }
    return minPos;
  }
  private Integer getPivotRow(SimplexTableau tableau, final int col) {
    List<Integer> minRatioPositions = new ArrayList<Integer>();
    double minRatio = Double.MAX_VALUE;
    for(int i = tableau.getNumObjectiveFunctions(); i < tableau.getHeight(); i++) {
      final double rhs = tableau.getEntry(i, tableau.getWidth() - 1);
      final double entry = tableau.getEntry(i, col);
      if(Precision.compareTo(entry, 0D, maxUlps) > 0) {
        final double ratio = rhs / entry;
        final int cmp = Double.compare(ratio, minRatio);
        if(cmp == 0) {
          minRatioPositions.add(i);
        }
        else 
          if(cmp < 0) {
            minRatio = ratio;
            minRatioPositions = new ArrayList<Integer>();
            minRatioPositions.add(i);
          }
      }
    }
    if(minRatioPositions.size() == 0) {
      return null;
    }
    else 
      if(minRatioPositions.size() > 1) {
        if(tableau.getNumArtificialVariables() > 0) {
          for (Integer row : minRatioPositions) {
            for(int i = 0; i < tableau.getNumArtificialVariables(); i++) {
              int column = i + tableau.getArtificialVariableOffset();
              final double entry = tableau.getEntry(row, column);
              if(Precision.equals(entry, 1D, maxUlps) && row.equals(tableau.getBasicRow(column))) {
                return row;
              }
            }
          }
        }
        if(getIterations() < getMaxIterations() / 2) {
          Integer minRow = null;
          int var_3559 = tableau.getWidth();
          int minIndex = var_3559;
          final int varStart = tableau.getNumObjectiveFunctions();
          final int varEnd = tableau.getWidth() - 1;
          for (Integer row : minRatioPositions) {
            for(int i = varStart; i < varEnd && !row.equals(minRow); i++) {
              final Integer basicRow = tableau.getBasicRow(i);
              if(basicRow != null && basicRow.equals(row) && i < minIndex) {
                minIndex = i;
                minRow = row;
              }
            }
          }
          return minRow;
        }
      }
    return minRatioPositions.get(0);
  }
  @Override() public PointValuePair doOptimize() throws MaxCountExceededException, UnboundedSolutionException, NoFeasibleSolutionException {
    final SimplexTableau tableau = new SimplexTableau(getFunction(), getConstraints(), getGoalType(), restrictToNonNegative(), epsilon, maxUlps);
    solvePhase1(tableau);
    tableau.dropPhase1Objective();
    while(!tableau.isOptimal()){
      doIteration(tableau);
    }
    return tableau.getSolution();
  }
  protected void doIteration(final SimplexTableau tableau) throws MaxCountExceededException, UnboundedSolutionException {
    incrementIterationsCounter();
    Integer pivotCol = getPivotColumn(tableau);
    Integer pivotRow = getPivotRow(tableau, pivotCol);
    if(pivotRow == null) {
      throw new UnboundedSolutionException();
    }
    double pivotVal = tableau.getEntry(pivotRow, pivotCol);
    tableau.divideRow(pivotRow, pivotVal);
    for(int i = 0; i < tableau.getHeight(); i++) {
      if(i != pivotRow) {
        final double multiplier = tableau.getEntry(i, pivotCol);
        tableau.subtractRow(i, pivotRow, multiplier);
      }
    }
  }
  protected void solvePhase1(final SimplexTableau tableau) throws MaxCountExceededException, UnboundedSolutionException, NoFeasibleSolutionException {
    if(tableau.getNumArtificialVariables() == 0) {
      return ;
    }
    while(!tableau.isOptimal()){
      doIteration(tableau);
    }
    if(!Precision.equals(tableau.getEntry(0, tableau.getRhsOffset()), 0D, epsilon)) {
      throw new NoFeasibleSolutionException();
    }
  }
}