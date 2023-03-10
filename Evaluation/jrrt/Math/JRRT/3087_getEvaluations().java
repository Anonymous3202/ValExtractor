package org.apache.commons.math3.optim.nonlinear.scalar.noderiv;
import org.apache.commons.math3.exception.MathIllegalStateException;
import org.apache.commons.math3.exception.NumberIsTooSmallException;
import org.apache.commons.math3.exception.OutOfRangeException;
import org.apache.commons.math3.exception.util.LocalizedFormats;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayRealVector;
import org.apache.commons.math3.linear.RealVector;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.nonlinear.scalar.MultivariateOptimizer;

public class BOBYQAOptimizer extends MultivariateOptimizer  {
  final public static int MINIMUM_PROBLEM_DIMENSION = 2;
  final public static double DEFAULT_INITIAL_RADIUS = 10.0D;
  final public static double DEFAULT_STOPPING_RADIUS = 1E-8D;
  final private static double ZERO = 0D;
  final private static double ONE = 1D;
  final private static double TWO = 2D;
  final private static double TEN = 10D;
  final private static double SIXTEEN = 16D;
  final private static double TWO_HUNDRED_FIFTY = 250D;
  final private static double MINUS_ONE = -ONE;
  final private static double HALF = ONE / 2;
  final private static double ONE_OVER_FOUR = ONE / 4;
  final private static double ONE_OVER_EIGHT = ONE / 8;
  final private static double ONE_OVER_TEN = ONE / 10;
  final private static double ONE_OVER_A_THOUSAND = ONE / 1000;
  final private int numberOfInterpolationPoints;
  private double initialTrustRegionRadius;
  final private double stoppingTrustRegionRadius;
  private boolean isMinimize;
  private ArrayRealVector currentBest;
  private double[] boundDifference;
  private int trustRegionCenterInterpolationPointIndex;
  private Array2DRowRealMatrix bMatrix;
  private Array2DRowRealMatrix zMatrix;
  private Array2DRowRealMatrix interpolationPoints;
  private ArrayRealVector originShift;
  private ArrayRealVector fAtInterpolationPoints;
  private ArrayRealVector trustRegionCenterOffset;
  private ArrayRealVector gradientAtTrustRegionCenter;
  private ArrayRealVector lowerDifference;
  private ArrayRealVector upperDifference;
  private ArrayRealVector modelSecondDerivativesParameters;
  private ArrayRealVector newPoint;
  private ArrayRealVector alternativeNewPoint;
  private ArrayRealVector trialStepPoint;
  private ArrayRealVector lagrangeValuesAtNewPoint;
  private ArrayRealVector modelSecondDerivativesValues;
  public BOBYQAOptimizer(int numberOfInterpolationPoints) {
    this(numberOfInterpolationPoints, DEFAULT_INITIAL_RADIUS, DEFAULT_STOPPING_RADIUS);
  }
  public BOBYQAOptimizer(int numberOfInterpolationPoints, double initialTrustRegionRadius, double stoppingTrustRegionRadius) {
    super(null);
    this.numberOfInterpolationPoints = numberOfInterpolationPoints;
    this.initialTrustRegionRadius = initialTrustRegionRadius;
    this.stoppingTrustRegionRadius = stoppingTrustRegionRadius;
  }
  @Override() protected PointValuePair doOptimize() {
    final double[] lowerBound = getLowerBound();
    final double[] upperBound = getUpperBound();
    setup(lowerBound, upperBound);
    isMinimize = (getGoalType() == GoalType.MINIMIZE);
    currentBest = new ArrayRealVector(getStartPoint());
    final double value = bobyqa(lowerBound, upperBound);
    return new PointValuePair(currentBest.getDataRef(), isMinimize ? value : -value);
  }
  private static String caller(int n) {
    final Throwable t = new Throwable();
    final StackTraceElement[] elements = t.getStackTrace();
    final StackTraceElement e = elements[n];
    return e.getMethodName() + " (at line " + e.getLineNumber() + ")";
  }
  private double bobyqa(double[] lowerBound, double[] upperBound) {
    printMethod();
    final int n = currentBest.getDimension();
    for(int j = 0; j < n; j++) {
      final double boundDiff = boundDifference[j];
      lowerDifference.setEntry(j, lowerBound[j] - currentBest.getEntry(j));
      upperDifference.setEntry(j, upperBound[j] - currentBest.getEntry(j));
      if(lowerDifference.getEntry(j) >= -initialTrustRegionRadius) {
        if(lowerDifference.getEntry(j) >= ZERO) {
          currentBest.setEntry(j, lowerBound[j]);
          lowerDifference.setEntry(j, ZERO);
          upperDifference.setEntry(j, boundDiff);
        }
        else {
          currentBest.setEntry(j, lowerBound[j] + initialTrustRegionRadius);
          lowerDifference.setEntry(j, -initialTrustRegionRadius);
          final double deltaOne = upperBound[j] - currentBest.getEntry(j);
          upperDifference.setEntry(j, Math.max(deltaOne, initialTrustRegionRadius));
        }
      }
      else 
        if(upperDifference.getEntry(j) <= initialTrustRegionRadius) {
          if(upperDifference.getEntry(j) <= ZERO) {
            currentBest.setEntry(j, upperBound[j]);
            lowerDifference.setEntry(j, -boundDiff);
            upperDifference.setEntry(j, ZERO);
          }
          else {
            currentBest.setEntry(j, upperBound[j] - initialTrustRegionRadius);
            final double deltaOne = lowerBound[j] - currentBest.getEntry(j);
            final double deltaTwo = -initialTrustRegionRadius;
            lowerDifference.setEntry(j, Math.min(deltaOne, deltaTwo));
            upperDifference.setEntry(j, initialTrustRegionRadius);
          }
        }
    }
    return bobyqb(lowerBound, upperBound);
  }
  private double bobyqb(double[] lowerBound, double[] upperBound) {
    printMethod();
    final int n = currentBest.getDimension();
    final int npt = numberOfInterpolationPoints;
    final int np = n + 1;
    final int nptm = npt - np;
    final int nh = n * np / 2;
    final ArrayRealVector work1 = new ArrayRealVector(n);
    final ArrayRealVector work2 = new ArrayRealVector(npt);
    final ArrayRealVector work3 = new ArrayRealVector(npt);
    double cauchy = Double.NaN;
    double alpha = Double.NaN;
    double dsq = Double.NaN;
    double crvmin = Double.NaN;
    trustRegionCenterInterpolationPointIndex = 0;
    prelim(lowerBound, upperBound);
    double xoptsq = ZERO;
    for(int i = 0; i < n; i++) {
      trustRegionCenterOffset.setEntry(i, interpolationPoints.getEntry(trustRegionCenterInterpolationPointIndex, i));
      final double deltaOne = trustRegionCenterOffset.getEntry(i);
      xoptsq += deltaOne * deltaOne;
    }
    double fsave = fAtInterpolationPoints.getEntry(0);
    final int kbase = 0;
    int ntrits = 0;
    int itest = 0;
    int knew = 0;
    int nfsav = getEvaluations();
    double rho = initialTrustRegionRadius;
    double delta = rho;
    double diffa = ZERO;
    double diffb = ZERO;
    double diffc = ZERO;
    double f = ZERO;
    double beta = ZERO;
    double adelt = ZERO;
    double denom = ZERO;
    double ratio = ZERO;
    double dnorm = ZERO;
    double scaden = ZERO;
    double biglsq = ZERO;
    double distsq = ZERO;
    int state = 20;
    for(; true; ) 
      switch (state){
        case 20:
        {
          printState(20);
          if(trustRegionCenterInterpolationPointIndex != kbase) {
            int ih = 0;
            for(int j = 0; j < n; j++) {
              for(int i = 0; i <= j; i++) {
                if(i < j) {
                  gradientAtTrustRegionCenter.setEntry(j, gradientAtTrustRegionCenter.getEntry(j) + modelSecondDerivativesValues.getEntry(ih) * trustRegionCenterOffset.getEntry(i));
                }
                gradientAtTrustRegionCenter.setEntry(i, gradientAtTrustRegionCenter.getEntry(i) + modelSecondDerivativesValues.getEntry(ih) * trustRegionCenterOffset.getEntry(j));
                ih++;
              }
            }
            if(getEvaluations() > npt) {
              for(int k = 0; k < npt; k++) {
                double temp = ZERO;
                for(int j = 0; j < n; j++) {
                  temp += interpolationPoints.getEntry(k, j) * trustRegionCenterOffset.getEntry(j);
                }
                temp *= modelSecondDerivativesParameters.getEntry(k);
                for(int i = 0; i < n; i++) {
                  gradientAtTrustRegionCenter.setEntry(i, gradientAtTrustRegionCenter.getEntry(i) + temp * interpolationPoints.getEntry(k, i));
                }
              }
            }
          }
        }
        case 60:
        {
          printState(60);
          final ArrayRealVector gnew = new ArrayRealVector(n);
          final ArrayRealVector xbdi = new ArrayRealVector(n);
          final ArrayRealVector s = new ArrayRealVector(n);
          final ArrayRealVector hs = new ArrayRealVector(n);
          final ArrayRealVector hred = new ArrayRealVector(n);
          final double[] dsqCrvmin = trsbox(delta, gnew, xbdi, s, hs, hred);
          dsq = dsqCrvmin[0];
          crvmin = dsqCrvmin[1];
          double deltaOne = delta;
          double deltaTwo = Math.sqrt(dsq);
          dnorm = Math.min(deltaOne, deltaTwo);
          if(dnorm < HALF * rho) {
            ntrits = -1;
            deltaOne = TEN * rho;
            distsq = deltaOne * deltaOne;
            if(getEvaluations() <= nfsav + 2) {
              state = 650;
              break ;
            }
            deltaOne = Math.max(diffa, diffb);
            final double errbig = Math.max(deltaOne, diffc);
            final double frhosq = rho * ONE_OVER_EIGHT * rho;
            if(crvmin > ZERO && errbig > frhosq * crvmin) {
              state = 650;
              break ;
            }
            final double bdtol = errbig / rho;
            for(int j = 0; j < n; j++) {
              double bdtest = bdtol;
              if(newPoint.getEntry(j) == lowerDifference.getEntry(j)) {
                bdtest = work1.getEntry(j);
              }
              if(newPoint.getEntry(j) == upperDifference.getEntry(j)) {
                bdtest = -work1.getEntry(j);
              }
              if(bdtest < bdtol) {
                double curv = modelSecondDerivativesValues.getEntry((j + j * j) / 2);
                for(int k = 0; k < npt; k++) {
                  final double d1 = interpolationPoints.getEntry(k, j);
                  curv += modelSecondDerivativesParameters.getEntry(k) * (d1 * d1);
                }
                bdtest += HALF * curv * rho;
                if(bdtest < bdtol) {
                  state = 650;
                  break ;
                }
              }
            }
            state = 680;
            break ;
          }
          ++ntrits;
        }
        case 90:
        {
          printState(90);
          if(dsq <= xoptsq * ONE_OVER_A_THOUSAND) {
            final double fracsq = xoptsq * ONE_OVER_FOUR;
            double sumpq = ZERO;
            for(int k = 0; k < npt; k++) {
              sumpq += modelSecondDerivativesParameters.getEntry(k);
              double sum = -HALF * xoptsq;
              for(int i = 0; i < n; i++) {
                sum += interpolationPoints.getEntry(k, i) * trustRegionCenterOffset.getEntry(i);
              }
              work2.setEntry(k, sum);
              final double temp = fracsq - HALF * sum;
              for(int i = 0; i < n; i++) {
                work1.setEntry(i, bMatrix.getEntry(k, i));
                lagrangeValuesAtNewPoint.setEntry(i, sum * interpolationPoints.getEntry(k, i) + temp * trustRegionCenterOffset.getEntry(i));
                final int ip = npt + i;
                for(int j = 0; j <= i; j++) {
                  bMatrix.setEntry(ip, j, bMatrix.getEntry(ip, j) + work1.getEntry(i) * lagrangeValuesAtNewPoint.getEntry(j) + lagrangeValuesAtNewPoint.getEntry(i) * work1.getEntry(j));
                }
              }
            }
            for(int m = 0; m < nptm; m++) {
              double sumz = ZERO;
              double sumw = ZERO;
              for(int k = 0; k < npt; k++) {
                sumz += zMatrix.getEntry(k, m);
                lagrangeValuesAtNewPoint.setEntry(k, work2.getEntry(k) * zMatrix.getEntry(k, m));
                sumw += lagrangeValuesAtNewPoint.getEntry(k);
              }
              for(int j = 0; j < n; j++) {
                double sum = (fracsq * sumz - HALF * sumw) * trustRegionCenterOffset.getEntry(j);
                for(int k = 0; k < npt; k++) {
                  sum += lagrangeValuesAtNewPoint.getEntry(k) * interpolationPoints.getEntry(k, j);
                }
                work1.setEntry(j, sum);
                for(int k = 0; k < npt; k++) {
                  bMatrix.setEntry(k, j, bMatrix.getEntry(k, j) + sum * zMatrix.getEntry(k, m));
                }
              }
              for(int i = 0; i < n; i++) {
                final int ip = i + npt;
                final double temp = work1.getEntry(i);
                for(int j = 0; j <= i; j++) {
                  bMatrix.setEntry(ip, j, bMatrix.getEntry(ip, j) + temp * work1.getEntry(j));
                }
              }
            }
            int ih = 0;
            for(int j = 0; j < n; j++) {
              work1.setEntry(j, -HALF * sumpq * trustRegionCenterOffset.getEntry(j));
              for(int k = 0; k < npt; k++) {
                work1.setEntry(j, work1.getEntry(j) + modelSecondDerivativesParameters.getEntry(k) * interpolationPoints.getEntry(k, j));
                interpolationPoints.setEntry(k, j, interpolationPoints.getEntry(k, j) - trustRegionCenterOffset.getEntry(j));
              }
              for(int i = 0; i <= j; i++) {
                modelSecondDerivativesValues.setEntry(ih, modelSecondDerivativesValues.getEntry(ih) + work1.getEntry(i) * trustRegionCenterOffset.getEntry(j) + trustRegionCenterOffset.getEntry(i) * work1.getEntry(j));
                bMatrix.setEntry(npt + i, j, bMatrix.getEntry(npt + j, i));
                ih++;
              }
            }
            for(int i = 0; i < n; i++) {
              originShift.setEntry(i, originShift.getEntry(i) + trustRegionCenterOffset.getEntry(i));
              newPoint.setEntry(i, newPoint.getEntry(i) - trustRegionCenterOffset.getEntry(i));
              lowerDifference.setEntry(i, lowerDifference.getEntry(i) - trustRegionCenterOffset.getEntry(i));
              upperDifference.setEntry(i, upperDifference.getEntry(i) - trustRegionCenterOffset.getEntry(i));
              trustRegionCenterOffset.setEntry(i, ZERO);
            }
            xoptsq = ZERO;
          }
          if(ntrits == 0) {
            state = 210;
            break ;
          }
          state = 230;
          break ;
        }
        case 210:
        {
          printState(210);
          final double[] alphaCauchy = altmov(knew, adelt);
          alpha = alphaCauchy[0];
          cauchy = alphaCauchy[1];
          for(int i = 0; i < n; i++) {
            trialStepPoint.setEntry(i, newPoint.getEntry(i) - trustRegionCenterOffset.getEntry(i));
          }
        }
        case 230:
        {
          printState(230);
          for(int k = 0; k < npt; k++) {
            double suma = ZERO;
            double sumb = ZERO;
            double sum = ZERO;
            for(int j = 0; j < n; j++) {
              suma += interpolationPoints.getEntry(k, j) * trialStepPoint.getEntry(j);
              sumb += interpolationPoints.getEntry(k, j) * trustRegionCenterOffset.getEntry(j);
              sum += bMatrix.getEntry(k, j) * trialStepPoint.getEntry(j);
            }
            work3.setEntry(k, suma * (HALF * suma + sumb));
            lagrangeValuesAtNewPoint.setEntry(k, sum);
            work2.setEntry(k, suma);
          }
          beta = ZERO;
          for(int m = 0; m < nptm; m++) {
            double sum = ZERO;
            for(int k = 0; k < npt; k++) {
              sum += zMatrix.getEntry(k, m) * work3.getEntry(k);
            }
            beta -= sum * sum;
            for(int k = 0; k < npt; k++) {
              lagrangeValuesAtNewPoint.setEntry(k, lagrangeValuesAtNewPoint.getEntry(k) + sum * zMatrix.getEntry(k, m));
            }
          }
          dsq = ZERO;
          double bsum = ZERO;
          double dx = ZERO;
          for(int j = 0; j < n; j++) {
            final double d1 = trialStepPoint.getEntry(j);
            dsq += d1 * d1;
            double sum = ZERO;
            for(int k = 0; k < npt; k++) {
              sum += work3.getEntry(k) * bMatrix.getEntry(k, j);
            }
            bsum += sum * trialStepPoint.getEntry(j);
            final int jp = npt + j;
            for(int i = 0; i < n; i++) {
              sum += bMatrix.getEntry(jp, i) * trialStepPoint.getEntry(i);
            }
            lagrangeValuesAtNewPoint.setEntry(jp, sum);
            bsum += sum * trialStepPoint.getEntry(j);
            dx += trialStepPoint.getEntry(j) * trustRegionCenterOffset.getEntry(j);
          }
          beta = dx * dx + dsq * (xoptsq + dx + dx + HALF * dsq) + beta - bsum;
          lagrangeValuesAtNewPoint.setEntry(trustRegionCenterInterpolationPointIndex, lagrangeValuesAtNewPoint.getEntry(trustRegionCenterInterpolationPointIndex) + ONE);
          if(ntrits == 0) {
            final double d1 = lagrangeValuesAtNewPoint.getEntry(knew);
            denom = d1 * d1 + alpha * beta;
            if(denom < cauchy && cauchy > ZERO) {
              for(int i = 0; i < n; i++) {
                newPoint.setEntry(i, alternativeNewPoint.getEntry(i));
                trialStepPoint.setEntry(i, newPoint.getEntry(i) - trustRegionCenterOffset.getEntry(i));
              }
              cauchy = ZERO;
              state = 230;
              break ;
            }
          }
          else {
            final double delsq = delta * delta;
            scaden = ZERO;
            biglsq = ZERO;
            knew = 0;
            for(int k = 0; k < npt; k++) {
              if(k == trustRegionCenterInterpolationPointIndex) {
                continue ;
              }
              double hdiag = ZERO;
              for(int m = 0; m < nptm; m++) {
                final double d1 = zMatrix.getEntry(k, m);
                hdiag += d1 * d1;
              }
              final double d2 = lagrangeValuesAtNewPoint.getEntry(k);
              final double den = beta * hdiag + d2 * d2;
              distsq = ZERO;
              for(int j = 0; j < n; j++) {
                final double d3 = interpolationPoints.getEntry(k, j) - trustRegionCenterOffset.getEntry(j);
                distsq += d3 * d3;
              }
              final double d4 = distsq / delsq;
              final double temp = Math.max(ONE, d4 * d4);
              if(temp * den > scaden) {
                scaden = temp * den;
                knew = k;
                denom = den;
              }
              final double d5 = lagrangeValuesAtNewPoint.getEntry(k);
              biglsq = Math.max(biglsq, temp * (d5 * d5));
            }
          }
        }
        case 360:
        {
          printState(360);
          for(int i = 0; i < n; i++) {
            final double d3 = lowerBound[i];
            final double d4 = originShift.getEntry(i) + newPoint.getEntry(i);
            final double d1 = Math.max(d3, d4);
            final double d2 = upperBound[i];
            currentBest.setEntry(i, Math.min(d1, d2));
            if(newPoint.getEntry(i) == lowerDifference.getEntry(i)) {
              currentBest.setEntry(i, lowerBound[i]);
            }
            if(newPoint.getEntry(i) == upperDifference.getEntry(i)) {
              currentBest.setEntry(i, upperBound[i]);
            }
          }
          f = computeObjectiveValue(currentBest.toArray());
          if(!isMinimize) 
            f = -f;
          if(ntrits == -1) {
            fsave = f;
            state = 720;
            break ;
          }
          final double fopt = fAtInterpolationPoints.getEntry(trustRegionCenterInterpolationPointIndex);
          double vquad = ZERO;
          int ih = 0;
          for(int j = 0; j < n; j++) {
            vquad += trialStepPoint.getEntry(j) * gradientAtTrustRegionCenter.getEntry(j);
            for(int i = 0; i <= j; i++) {
              double temp = trialStepPoint.getEntry(i) * trialStepPoint.getEntry(j);
              if(i == j) {
                temp *= HALF;
              }
              vquad += modelSecondDerivativesValues.getEntry(ih) * temp;
              ih++;
            }
          }
          for(int k = 0; k < npt; k++) {
            final double d1 = work2.getEntry(k);
            final double d2 = d1 * d1;
            vquad += HALF * modelSecondDerivativesParameters.getEntry(k) * d2;
          }
          final double diff = f - fopt - vquad;
          diffc = diffb;
          diffb = diffa;
          diffa = Math.abs(diff);
          if(dnorm > rho) {
            int var_3087 = getEvaluations();
            nfsav = var_3087;
          }
          if(ntrits > 0) {
            if(vquad >= ZERO) {
              throw new MathIllegalStateException(LocalizedFormats.TRUST_REGION_STEP_FAILED, vquad);
            }
            ratio = (f - fopt) / vquad;
            final double hDelta = HALF * delta;
            if(ratio <= ONE_OVER_TEN) {
              delta = Math.min(hDelta, dnorm);
            }
            else 
              if(ratio <= .7D) {
                delta = Math.max(hDelta, dnorm);
              }
              else {
                delta = Math.max(hDelta, 2 * dnorm);
              }
            if(delta <= rho * 1.5D) {
              delta = rho;
            }
            if(f < fopt) {
              final int ksav = knew;
              final double densav = denom;
              final double delsq = delta * delta;
              scaden = ZERO;
              biglsq = ZERO;
              knew = 0;
              for(int k = 0; k < npt; k++) {
                double hdiag = ZERO;
                for(int m = 0; m < nptm; m++) {
                  final double d1 = zMatrix.getEntry(k, m);
                  hdiag += d1 * d1;
                }
                final double d1 = lagrangeValuesAtNewPoint.getEntry(k);
                final double den = beta * hdiag + d1 * d1;
                distsq = ZERO;
                for(int j = 0; j < n; j++) {
                  final double d2 = interpolationPoints.getEntry(k, j) - newPoint.getEntry(j);
                  distsq += d2 * d2;
                }
                final double d3 = distsq / delsq;
                final double temp = Math.max(ONE, d3 * d3);
                if(temp * den > scaden) {
                  scaden = temp * den;
                  knew = k;
                  denom = den;
                }
                final double d4 = lagrangeValuesAtNewPoint.getEntry(k);
                final double d5 = temp * (d4 * d4);
                biglsq = Math.max(biglsq, d5);
              }
              if(scaden <= HALF * biglsq) {
                knew = ksav;
                denom = densav;
              }
            }
          }
          update(beta, denom, knew);
          ih = 0;
          final double pqold = modelSecondDerivativesParameters.getEntry(knew);
          modelSecondDerivativesParameters.setEntry(knew, ZERO);
          for(int i = 0; i < n; i++) {
            final double temp = pqold * interpolationPoints.getEntry(knew, i);
            for(int j = 0; j <= i; j++) {
              modelSecondDerivativesValues.setEntry(ih, modelSecondDerivativesValues.getEntry(ih) + temp * interpolationPoints.getEntry(knew, j));
              ih++;
            }
          }
          for(int m = 0; m < nptm; m++) {
            final double temp = diff * zMatrix.getEntry(knew, m);
            for(int k = 0; k < npt; k++) {
              modelSecondDerivativesParameters.setEntry(k, modelSecondDerivativesParameters.getEntry(k) + temp * zMatrix.getEntry(k, m));
            }
          }
          fAtInterpolationPoints.setEntry(knew, f);
          for(int i = 0; i < n; i++) {
            interpolationPoints.setEntry(knew, i, newPoint.getEntry(i));
            work1.setEntry(i, bMatrix.getEntry(knew, i));
          }
          for(int k = 0; k < npt; k++) {
            double suma = ZERO;
            for(int m = 0; m < nptm; m++) {
              suma += zMatrix.getEntry(knew, m) * zMatrix.getEntry(k, m);
            }
            double sumb = ZERO;
            for(int j = 0; j < n; j++) {
              sumb += interpolationPoints.getEntry(k, j) * trustRegionCenterOffset.getEntry(j);
            }
            final double temp = suma * sumb;
            for(int i = 0; i < n; i++) {
              work1.setEntry(i, work1.getEntry(i) + temp * interpolationPoints.getEntry(k, i));
            }
          }
          for(int i = 0; i < n; i++) {
            gradientAtTrustRegionCenter.setEntry(i, gradientAtTrustRegionCenter.getEntry(i) + diff * work1.getEntry(i));
          }
          if(f < fopt) {
            trustRegionCenterInterpolationPointIndex = knew;
            xoptsq = ZERO;
            ih = 0;
            for(int j = 0; j < n; j++) {
              trustRegionCenterOffset.setEntry(j, newPoint.getEntry(j));
              final double d1 = trustRegionCenterOffset.getEntry(j);
              xoptsq += d1 * d1;
              for(int i = 0; i <= j; i++) {
                if(i < j) {
                  gradientAtTrustRegionCenter.setEntry(j, gradientAtTrustRegionCenter.getEntry(j) + modelSecondDerivativesValues.getEntry(ih) * trialStepPoint.getEntry(i));
                }
                gradientAtTrustRegionCenter.setEntry(i, gradientAtTrustRegionCenter.getEntry(i) + modelSecondDerivativesValues.getEntry(ih) * trialStepPoint.getEntry(j));
                ih++;
              }
            }
            for(int k = 0; k < npt; k++) {
              double temp = ZERO;
              for(int j = 0; j < n; j++) {
                temp += interpolationPoints.getEntry(k, j) * trialStepPoint.getEntry(j);
              }
              temp *= modelSecondDerivativesParameters.getEntry(k);
              for(int i = 0; i < n; i++) {
                gradientAtTrustRegionCenter.setEntry(i, gradientAtTrustRegionCenter.getEntry(i) + temp * interpolationPoints.getEntry(k, i));
              }
            }
          }
          if(ntrits > 0) {
            for(int k = 0; k < npt; k++) {
              lagrangeValuesAtNewPoint.setEntry(k, fAtInterpolationPoints.getEntry(k) - fAtInterpolationPoints.getEntry(trustRegionCenterInterpolationPointIndex));
              work3.setEntry(k, ZERO);
            }
            for(int j = 0; j < nptm; j++) {
              double sum = ZERO;
              for(int k = 0; k < npt; k++) {
                sum += zMatrix.getEntry(k, j) * lagrangeValuesAtNewPoint.getEntry(k);
              }
              for(int k = 0; k < npt; k++) {
                work3.setEntry(k, work3.getEntry(k) + sum * zMatrix.getEntry(k, j));
              }
            }
            for(int k = 0; k < npt; k++) {
              double sum = ZERO;
              for(int j = 0; j < n; j++) {
                sum += interpolationPoints.getEntry(k, j) * trustRegionCenterOffset.getEntry(j);
              }
              work2.setEntry(k, work3.getEntry(k));
              work3.setEntry(k, sum * work3.getEntry(k));
            }
            double gqsq = ZERO;
            double gisq = ZERO;
            for(int i = 0; i < n; i++) {
              double sum = ZERO;
              for(int k = 0; k < npt; k++) {
                sum += bMatrix.getEntry(k, i) * lagrangeValuesAtNewPoint.getEntry(k) + interpolationPoints.getEntry(k, i) * work3.getEntry(k);
              }
              if(trustRegionCenterOffset.getEntry(i) == lowerDifference.getEntry(i)) {
                final double d1 = Math.min(ZERO, gradientAtTrustRegionCenter.getEntry(i));
                gqsq += d1 * d1;
                final double d2 = Math.min(ZERO, sum);
                gisq += d2 * d2;
              }
              else 
                if(trustRegionCenterOffset.getEntry(i) == upperDifference.getEntry(i)) {
                  final double d1 = Math.max(ZERO, gradientAtTrustRegionCenter.getEntry(i));
                  gqsq += d1 * d1;
                  final double d2 = Math.max(ZERO, sum);
                  gisq += d2 * d2;
                }
                else {
                  final double d1 = gradientAtTrustRegionCenter.getEntry(i);
                  gqsq += d1 * d1;
                  gisq += sum * sum;
                }
              lagrangeValuesAtNewPoint.setEntry(npt + i, sum);
            }
            ++itest;
            if(gqsq < TEN * gisq) {
              itest = 0;
            }
            if(itest >= 3) {
              for(int i = 0, max = Math.max(npt, nh); i < max; i++) {
                if(i < n) {
                  gradientAtTrustRegionCenter.setEntry(i, lagrangeValuesAtNewPoint.getEntry(npt + i));
                }
                if(i < npt) {
                  modelSecondDerivativesParameters.setEntry(i, work2.getEntry(i));
                }
                if(i < nh) {
                  modelSecondDerivativesValues.setEntry(i, ZERO);
                }
                itest = 0;
              }
            }
          }
          if(ntrits == 0) {
            state = 60;
            break ;
          }
          if(f <= fopt + ONE_OVER_TEN * vquad) {
            state = 60;
            break ;
          }
          final double d1 = TWO * delta;
          final double d2 = TEN * rho;
          distsq = Math.max(d1 * d1, d2 * d2);
        }
        case 650:
        {
          printState(650);
          knew = -1;
          for(int k = 0; k < npt; k++) {
            double sum = ZERO;
            for(int j = 0; j < n; j++) {
              final double d1 = interpolationPoints.getEntry(k, j) - trustRegionCenterOffset.getEntry(j);
              sum += d1 * d1;
            }
            if(sum > distsq) {
              knew = k;
              distsq = sum;
            }
          }
          if(knew >= 0) {
            final double dist = Math.sqrt(distsq);
            if(ntrits == -1) {
              delta = Math.min(ONE_OVER_TEN * delta, HALF * dist);
              if(delta <= rho * 1.5D) {
                delta = rho;
              }
            }
            ntrits = 0;
            final double d1 = Math.min(ONE_OVER_TEN * dist, delta);
            adelt = Math.max(d1, rho);
            dsq = adelt * adelt;
            state = 90;
            break ;
          }
          if(ntrits == -1) {
            state = 680;
            break ;
          }
          if(ratio > ZERO) {
            state = 60;
            break ;
          }
          if(Math.max(delta, dnorm) > rho) {
            state = 60;
            break ;
          }
        }
        case 680:
        {
          printState(680);
          if(rho > stoppingTrustRegionRadius) {
            delta = HALF * rho;
            ratio = rho / stoppingTrustRegionRadius;
            if(ratio <= SIXTEEN) {
              rho = stoppingTrustRegionRadius;
            }
            else 
              if(ratio <= TWO_HUNDRED_FIFTY) {
                rho = Math.sqrt(ratio) * stoppingTrustRegionRadius;
              }
              else {
                rho *= ONE_OVER_TEN;
              }
            delta = Math.max(delta, rho);
            ntrits = 0;
            nfsav = getEvaluations();
            state = 60;
            break ;
          }
          if(ntrits == -1) {
            state = 360;
            break ;
          }
        }
        case 720:
        {
          printState(720);
          if(fAtInterpolationPoints.getEntry(trustRegionCenterInterpolationPointIndex) <= fsave) {
            for(int i = 0; i < n; i++) {
              final double d3 = lowerBound[i];
              final double d4 = originShift.getEntry(i) + trustRegionCenterOffset.getEntry(i);
              final double d1 = Math.max(d3, d4);
              final double d2 = upperBound[i];
              currentBest.setEntry(i, Math.min(d1, d2));
              if(trustRegionCenterOffset.getEntry(i) == lowerDifference.getEntry(i)) {
                currentBest.setEntry(i, lowerBound[i]);
              }
              if(trustRegionCenterOffset.getEntry(i) == upperDifference.getEntry(i)) {
                currentBest.setEntry(i, upperBound[i]);
              }
            }
            f = fAtInterpolationPoints.getEntry(trustRegionCenterInterpolationPointIndex);
          }
          return f;
        }
        default:
        {
          throw new MathIllegalStateException(LocalizedFormats.SIMPLE_MESSAGE, "bobyqb");
        }
      }
  }
  private double[] altmov(int knew, double adelt) {
    printMethod();
    final int n = currentBest.getDimension();
    final int npt = numberOfInterpolationPoints;
    final ArrayRealVector glag = new ArrayRealVector(n);
    final ArrayRealVector hcol = new ArrayRealVector(npt);
    final ArrayRealVector work1 = new ArrayRealVector(n);
    final ArrayRealVector work2 = new ArrayRealVector(n);
    for(int k = 0; k < npt; k++) {
      hcol.setEntry(k, ZERO);
    }
    for(int j = 0, max = npt - n - 1; j < max; j++) {
      final double tmp = zMatrix.getEntry(knew, j);
      for(int k = 0; k < npt; k++) {
        hcol.setEntry(k, hcol.getEntry(k) + tmp * zMatrix.getEntry(k, j));
      }
    }
    final double alpha = hcol.getEntry(knew);
    final double ha = HALF * alpha;
    for(int i = 0; i < n; i++) {
      glag.setEntry(i, bMatrix.getEntry(knew, i));
    }
    for(int k = 0; k < npt; k++) {
      double tmp = ZERO;
      for(int j = 0; j < n; j++) {
        tmp += interpolationPoints.getEntry(k, j) * trustRegionCenterOffset.getEntry(j);
      }
      tmp *= hcol.getEntry(k);
      for(int i = 0; i < n; i++) {
        glag.setEntry(i, glag.getEntry(i) + tmp * interpolationPoints.getEntry(k, i));
      }
    }
    double presav = ZERO;
    double step = Double.NaN;
    int ksav = 0;
    int ibdsav = 0;
    double stpsav = 0;
    for(int k = 0; k < npt; k++) {
      if(k == trustRegionCenterInterpolationPointIndex) {
        continue ;
      }
      double dderiv = ZERO;
      double distsq = ZERO;
      for(int i = 0; i < n; i++) {
        final double tmp = interpolationPoints.getEntry(k, i) - trustRegionCenterOffset.getEntry(i);
        dderiv += glag.getEntry(i) * tmp;
        distsq += tmp * tmp;
      }
      double subd = adelt / Math.sqrt(distsq);
      double slbd = -subd;
      int ilbd = 0;
      int iubd = 0;
      final double sumin = Math.min(ONE, subd);
      for(int i = 0; i < n; i++) {
        final double tmp = interpolationPoints.getEntry(k, i) - trustRegionCenterOffset.getEntry(i);
        if(tmp > ZERO) {
          if(slbd * tmp < lowerDifference.getEntry(i) - trustRegionCenterOffset.getEntry(i)) {
            slbd = (lowerDifference.getEntry(i) - trustRegionCenterOffset.getEntry(i)) / tmp;
            ilbd = -i - 1;
          }
          if(subd * tmp > upperDifference.getEntry(i) - trustRegionCenterOffset.getEntry(i)) {
            subd = Math.max(sumin, (upperDifference.getEntry(i) - trustRegionCenterOffset.getEntry(i)) / tmp);
            iubd = i + 1;
          }
        }
        else 
          if(tmp < ZERO) {
            if(slbd * tmp > upperDifference.getEntry(i) - trustRegionCenterOffset.getEntry(i)) {
              slbd = (upperDifference.getEntry(i) - trustRegionCenterOffset.getEntry(i)) / tmp;
              ilbd = i + 1;
            }
            if(subd * tmp < lowerDifference.getEntry(i) - trustRegionCenterOffset.getEntry(i)) {
              subd = Math.max(sumin, (lowerDifference.getEntry(i) - trustRegionCenterOffset.getEntry(i)) / tmp);
              iubd = -i - 1;
            }
          }
      }
      step = slbd;
      int isbd = ilbd;
      double vlag = Double.NaN;
      if(k == knew) {
        final double diff = dderiv - ONE;
        vlag = slbd * (dderiv - slbd * diff);
        final double d1 = subd * (dderiv - subd * diff);
        if(Math.abs(d1) > Math.abs(vlag)) {
          step = subd;
          vlag = d1;
          isbd = iubd;
        }
        final double d2 = HALF * dderiv;
        final double d3 = d2 - diff * slbd;
        final double d4 = d2 - diff * subd;
        if(d3 * d4 < ZERO) {
          final double d5 = d2 * d2 / diff;
          if(Math.abs(d5) > Math.abs(vlag)) {
            step = d2 / diff;
            vlag = d5;
            isbd = 0;
          }
        }
      }
      else {
        vlag = slbd * (ONE - slbd);
        final double tmp = subd * (ONE - subd);
        if(Math.abs(tmp) > Math.abs(vlag)) {
          step = subd;
          vlag = tmp;
          isbd = iubd;
        }
        if(subd > HALF && Math.abs(vlag) < ONE_OVER_FOUR) {
          step = HALF;
          vlag = ONE_OVER_FOUR;
          isbd = 0;
        }
        vlag *= dderiv;
      }
      final double tmp = step * (ONE - step) * distsq;
      final double predsq = vlag * vlag * (vlag * vlag + ha * tmp * tmp);
      if(predsq > presav) {
        presav = predsq;
        ksav = k;
        stpsav = step;
        ibdsav = isbd;
      }
    }
    for(int i = 0; i < n; i++) {
      final double tmp = trustRegionCenterOffset.getEntry(i) + stpsav * (interpolationPoints.getEntry(ksav, i) - trustRegionCenterOffset.getEntry(i));
      newPoint.setEntry(i, Math.max(lowerDifference.getEntry(i), Math.min(upperDifference.getEntry(i), tmp)));
    }
    if(ibdsav < 0) {
      newPoint.setEntry(-ibdsav - 1, lowerDifference.getEntry(-ibdsav - 1));
    }
    if(ibdsav > 0) {
      newPoint.setEntry(ibdsav - 1, upperDifference.getEntry(ibdsav - 1));
    }
    final double bigstp = adelt + adelt;
    int iflag = 0;
    double cauchy = Double.NaN;
    double csave = ZERO;
    while(true){
      double wfixsq = ZERO;
      double ggfree = ZERO;
      for(int i = 0; i < n; i++) {
        final double glagValue = glag.getEntry(i);
        work1.setEntry(i, ZERO);
        if(Math.min(trustRegionCenterOffset.getEntry(i) - lowerDifference.getEntry(i), glagValue) > ZERO || Math.max(trustRegionCenterOffset.getEntry(i) - upperDifference.getEntry(i), glagValue) < ZERO) {
          work1.setEntry(i, bigstp);
          ggfree += glagValue * glagValue;
        }
      }
      if(ggfree == ZERO) {
        return new double[]{ alpha, ZERO } ;
      }
      final double tmp1 = adelt * adelt - wfixsq;
      if(tmp1 > ZERO) {
        step = Math.sqrt(tmp1 / ggfree);
        ggfree = ZERO;
        for(int i = 0; i < n; i++) {
          if(work1.getEntry(i) == bigstp) {
            final double tmp2 = trustRegionCenterOffset.getEntry(i) - step * glag.getEntry(i);
            if(tmp2 <= lowerDifference.getEntry(i)) {
              work1.setEntry(i, lowerDifference.getEntry(i) - trustRegionCenterOffset.getEntry(i));
              final double d1 = work1.getEntry(i);
              wfixsq += d1 * d1;
            }
            else 
              if(tmp2 >= upperDifference.getEntry(i)) {
                work1.setEntry(i, upperDifference.getEntry(i) - trustRegionCenterOffset.getEntry(i));
                final double d1 = work1.getEntry(i);
                wfixsq += d1 * d1;
              }
              else {
                final double d1 = glag.getEntry(i);
                ggfree += d1 * d1;
              }
          }
        }
      }
      double gw = ZERO;
      for(int i = 0; i < n; i++) {
        final double glagValue = glag.getEntry(i);
        if(work1.getEntry(i) == bigstp) {
          work1.setEntry(i, -step * glagValue);
          final double min = Math.min(upperDifference.getEntry(i), trustRegionCenterOffset.getEntry(i) + work1.getEntry(i));
          alternativeNewPoint.setEntry(i, Math.max(lowerDifference.getEntry(i), min));
        }
        else 
          if(work1.getEntry(i) == ZERO) {
            alternativeNewPoint.setEntry(i, trustRegionCenterOffset.getEntry(i));
          }
          else 
            if(glagValue > ZERO) {
              alternativeNewPoint.setEntry(i, lowerDifference.getEntry(i));
            }
            else {
              alternativeNewPoint.setEntry(i, upperDifference.getEntry(i));
            }
        gw += glagValue * work1.getEntry(i);
      }
      double curv = ZERO;
      for(int k = 0; k < npt; k++) {
        double tmp = ZERO;
        for(int j = 0; j < n; j++) {
          tmp += interpolationPoints.getEntry(k, j) * work1.getEntry(j);
        }
        curv += hcol.getEntry(k) * tmp * tmp;
      }
      if(iflag == 1) {
        curv = -curv;
      }
      if(curv > -gw && curv < -gw * (ONE + Math.sqrt(TWO))) {
        final double scale = -gw / curv;
        for(int i = 0; i < n; i++) {
          final double tmp = trustRegionCenterOffset.getEntry(i) + scale * work1.getEntry(i);
          alternativeNewPoint.setEntry(i, Math.max(lowerDifference.getEntry(i), Math.min(upperDifference.getEntry(i), tmp)));
        }
        final double d1 = HALF * gw * scale;
        cauchy = d1 * d1;
      }
      else {
        final double d1 = gw + HALF * curv;
        cauchy = d1 * d1;
      }
      if(iflag == 0) {
        for(int i = 0; i < n; i++) {
          glag.setEntry(i, -glag.getEntry(i));
          work2.setEntry(i, alternativeNewPoint.getEntry(i));
        }
        csave = cauchy;
        iflag = 1;
      }
      else {
        break ;
      }
    }
    if(csave > cauchy) {
      for(int i = 0; i < n; i++) {
        alternativeNewPoint.setEntry(i, work2.getEntry(i));
      }
      cauchy = csave;
    }
    return new double[]{ alpha, cauchy } ;
  }
  private double[] trsbox(double delta, ArrayRealVector gnew, ArrayRealVector xbdi, ArrayRealVector s, ArrayRealVector hs, ArrayRealVector hred) {
    printMethod();
    final int n = currentBest.getDimension();
    final int npt = numberOfInterpolationPoints;
    double dsq = Double.NaN;
    double crvmin = Double.NaN;
    double ds;
    int iu;
    double dhd;
    double dhs;
    double cth;
    double shs;
    double sth;
    double ssq;
    double beta = 0;
    double sdec;
    double blen;
    int iact = -1;
    int nact = 0;
    double angt = 0;
    double qred;
    int isav;
    double temp = 0;
    double xsav = 0;
    double xsum = 0;
    double angbd = 0;
    double dredg = 0;
    double sredg = 0;
    int iterc;
    double resid = 0;
    double delsq = 0;
    double ggsav = 0;
    double tempa = 0;
    double tempb = 0;
    double redmax = 0;
    double dredsq = 0;
    double redsav = 0;
    double gredsq = 0;
    double rednew = 0;
    int itcsav = 0;
    double rdprev = 0;
    double rdnext = 0;
    double stplen = 0;
    double stepsq = 0;
    int itermax = 0;
    iterc = 0;
    nact = 0;
    for(int i = 0; i < n; i++) {
      xbdi.setEntry(i, ZERO);
      if(trustRegionCenterOffset.getEntry(i) <= lowerDifference.getEntry(i)) {
        if(gradientAtTrustRegionCenter.getEntry(i) >= ZERO) {
          xbdi.setEntry(i, MINUS_ONE);
        }
      }
      else 
        if(trustRegionCenterOffset.getEntry(i) >= upperDifference.getEntry(i) && gradientAtTrustRegionCenter.getEntry(i) <= ZERO) {
          xbdi.setEntry(i, ONE);
        }
      if(xbdi.getEntry(i) != ZERO) {
        ++nact;
      }
      trialStepPoint.setEntry(i, ZERO);
      gnew.setEntry(i, gradientAtTrustRegionCenter.getEntry(i));
    }
    delsq = delta * delta;
    qred = ZERO;
    crvmin = MINUS_ONE;
    int state = 20;
    for(; true; ) {
      switch (state){
        case 20:
        {
          printState(20);
          beta = ZERO;
        }
        case 30:
        {
          printState(30);
          stepsq = ZERO;
          for(int i = 0; i < n; i++) {
            if(xbdi.getEntry(i) != ZERO) {
              s.setEntry(i, ZERO);
            }
            else 
              if(beta == ZERO) {
                s.setEntry(i, -gnew.getEntry(i));
              }
              else {
                s.setEntry(i, beta * s.getEntry(i) - gnew.getEntry(i));
              }
            final double d1 = s.getEntry(i);
            stepsq += d1 * d1;
          }
          if(stepsq == ZERO) {
            state = 190;
            break ;
          }
          if(beta == ZERO) {
            gredsq = stepsq;
            itermax = iterc + n - nact;
          }
          if(gredsq * delsq <= qred * 1e-4D * qred) {
            state = 190;
            break ;
          }
          state = 210;
          break ;
        }
        case 50:
        {
          printState(50);
          resid = delsq;
          ds = ZERO;
          shs = ZERO;
          for(int i = 0; i < n; i++) {
            if(xbdi.getEntry(i) == ZERO) {
              final double d1 = trialStepPoint.getEntry(i);
              resid -= d1 * d1;
              ds += s.getEntry(i) * trialStepPoint.getEntry(i);
              shs += s.getEntry(i) * hs.getEntry(i);
            }
          }
          if(resid <= ZERO) {
            state = 90;
            break ;
          }
          temp = Math.sqrt(stepsq * resid + ds * ds);
          if(ds < ZERO) {
            blen = (temp - ds) / stepsq;
          }
          else {
            blen = resid / (temp + ds);
          }
          stplen = blen;
          if(shs > ZERO) {
            stplen = Math.min(blen, gredsq / shs);
          }
          iact = -1;
          for(int i = 0; i < n; i++) {
            if(s.getEntry(i) != ZERO) {
              xsum = trustRegionCenterOffset.getEntry(i) + trialStepPoint.getEntry(i);
              if(s.getEntry(i) > ZERO) {
                temp = (upperDifference.getEntry(i) - xsum) / s.getEntry(i);
              }
              else {
                temp = (lowerDifference.getEntry(i) - xsum) / s.getEntry(i);
              }
              if(temp < stplen) {
                stplen = temp;
                iact = i;
              }
            }
          }
          sdec = ZERO;
          if(stplen > ZERO) {
            ++iterc;
            temp = shs / stepsq;
            if(iact == -1 && temp > ZERO) {
              crvmin = Math.min(crvmin, temp);
              if(crvmin == MINUS_ONE) {
                crvmin = temp;
              }
            }
            ggsav = gredsq;
            gredsq = ZERO;
            for(int i = 0; i < n; i++) {
              gnew.setEntry(i, gnew.getEntry(i) + stplen * hs.getEntry(i));
              if(xbdi.getEntry(i) == ZERO) {
                final double d1 = gnew.getEntry(i);
                gredsq += d1 * d1;
              }
              trialStepPoint.setEntry(i, trialStepPoint.getEntry(i) + stplen * s.getEntry(i));
            }
            final double d1 = stplen * (ggsav - HALF * stplen * shs);
            sdec = Math.max(d1, ZERO);
            qred += sdec;
          }
          if(iact >= 0) {
            ++nact;
            xbdi.setEntry(iact, ONE);
            if(s.getEntry(iact) < ZERO) {
              xbdi.setEntry(iact, MINUS_ONE);
            }
            final double d1 = trialStepPoint.getEntry(iact);
            delsq -= d1 * d1;
            if(delsq <= ZERO) {
              state = 190;
              break ;
            }
            state = 20;
            break ;
          }
          if(stplen < blen) {
            if(iterc == itermax) {
              state = 190;
              break ;
            }
            if(sdec <= qred * .01D) {
              state = 190;
              break ;
            }
            beta = gredsq / ggsav;
            state = 30;
            break ;
          }
        }
        case 90:
        {
          printState(90);
          crvmin = ZERO;
        }
        case 100:
        {
          printState(100);
          if(nact >= n - 1) {
            state = 190;
            break ;
          }
          dredsq = ZERO;
          dredg = ZERO;
          gredsq = ZERO;
          for(int i = 0; i < n; i++) {
            if(xbdi.getEntry(i) == ZERO) {
              double d1 = trialStepPoint.getEntry(i);
              dredsq += d1 * d1;
              dredg += trialStepPoint.getEntry(i) * gnew.getEntry(i);
              d1 = gnew.getEntry(i);
              gredsq += d1 * d1;
              s.setEntry(i, trialStepPoint.getEntry(i));
            }
            else {
              s.setEntry(i, ZERO);
            }
          }
          itcsav = iterc;
          state = 210;
          break ;
        }
        case 120:
        {
          printState(120);
          ++iterc;
          temp = gredsq * dredsq - dredg * dredg;
          if(temp <= qred * 1e-4D * qred) {
            state = 190;
            break ;
          }
          temp = Math.sqrt(temp);
          for(int i = 0; i < n; i++) {
            if(xbdi.getEntry(i) == ZERO) {
              s.setEntry(i, (dredg * trialStepPoint.getEntry(i) - dredsq * gnew.getEntry(i)) / temp);
            }
            else {
              s.setEntry(i, ZERO);
            }
          }
          sredg = -temp;
          angbd = ONE;
          iact = -1;
          for(int i = 0; i < n; i++) {
            if(xbdi.getEntry(i) == ZERO) {
              tempa = trustRegionCenterOffset.getEntry(i) + trialStepPoint.getEntry(i) - lowerDifference.getEntry(i);
              tempb = upperDifference.getEntry(i) - trustRegionCenterOffset.getEntry(i) - trialStepPoint.getEntry(i);
              if(tempa <= ZERO) {
                ++nact;
                xbdi.setEntry(i, MINUS_ONE);
                state = 100;
                break ;
              }
              else 
                if(tempb <= ZERO) {
                  ++nact;
                  xbdi.setEntry(i, ONE);
                  state = 100;
                  break ;
                }
              double d1 = trialStepPoint.getEntry(i);
              double d2 = s.getEntry(i);
              ssq = d1 * d1 + d2 * d2;
              d1 = trustRegionCenterOffset.getEntry(i) - lowerDifference.getEntry(i);
              temp = ssq - d1 * d1;
              if(temp > ZERO) {
                temp = Math.sqrt(temp) - s.getEntry(i);
                if(angbd * temp > tempa) {
                  angbd = tempa / temp;
                  iact = i;
                  xsav = MINUS_ONE;
                }
              }
              d1 = upperDifference.getEntry(i) - trustRegionCenterOffset.getEntry(i);
              temp = ssq - d1 * d1;
              if(temp > ZERO) {
                temp = Math.sqrt(temp) + s.getEntry(i);
                if(angbd * temp > tempb) {
                  angbd = tempb / temp;
                  iact = i;
                  xsav = ONE;
                }
              }
            }
          }
          state = 210;
          break ;
        }
        case 150:
        {
          printState(150);
          shs = ZERO;
          dhs = ZERO;
          dhd = ZERO;
          for(int i = 0; i < n; i++) {
            if(xbdi.getEntry(i) == ZERO) {
              shs += s.getEntry(i) * hs.getEntry(i);
              dhs += trialStepPoint.getEntry(i) * hs.getEntry(i);
              dhd += trialStepPoint.getEntry(i) * hred.getEntry(i);
            }
          }
          redmax = ZERO;
          isav = -1;
          redsav = ZERO;
          iu = (int)(angbd * 17.D + 3.1D);
          for(int i = 0; i < iu; i++) {
            angt = angbd * i / iu;
            sth = (angt + angt) / (ONE + angt * angt);
            temp = shs + angt * (angt * dhd - dhs - dhs);
            rednew = sth * (angt * dredg - sredg - HALF * sth * temp);
            if(rednew > redmax) {
              redmax = rednew;
              isav = i;
              rdprev = redsav;
            }
            else 
              if(i == isav + 1) {
                rdnext = rednew;
              }
            redsav = rednew;
          }
          if(isav < 0) {
            state = 190;
            break ;
          }
          if(isav < iu) {
            temp = (rdnext - rdprev) / (redmax + redmax - rdprev - rdnext);
            angt = angbd * (isav + HALF * temp) / iu;
          }
          cth = (ONE - angt * angt) / (ONE + angt * angt);
          sth = (angt + angt) / (ONE + angt * angt);
          temp = shs + angt * (angt * dhd - dhs - dhs);
          sdec = sth * (angt * dredg - sredg - HALF * sth * temp);
          if(sdec <= ZERO) {
            state = 190;
            break ;
          }
          dredg = ZERO;
          gredsq = ZERO;
          for(int i = 0; i < n; i++) {
            gnew.setEntry(i, gnew.getEntry(i) + (cth - ONE) * hred.getEntry(i) + sth * hs.getEntry(i));
            if(xbdi.getEntry(i) == ZERO) {
              trialStepPoint.setEntry(i, cth * trialStepPoint.getEntry(i) + sth * s.getEntry(i));
              dredg += trialStepPoint.getEntry(i) * gnew.getEntry(i);
              final double d1 = gnew.getEntry(i);
              gredsq += d1 * d1;
            }
            hred.setEntry(i, cth * hred.getEntry(i) + sth * hs.getEntry(i));
          }
          qred += sdec;
          if(iact >= 0 && isav == iu) {
            ++nact;
            xbdi.setEntry(iact, xsav);
            state = 100;
            break ;
          }
          if(sdec > qred * .01D) {
            state = 120;
            break ;
          }
        }
        case 190:
        {
          printState(190);
          dsq = ZERO;
          for(int i = 0; i < n; i++) {
            final double min = Math.min(trustRegionCenterOffset.getEntry(i) + trialStepPoint.getEntry(i), upperDifference.getEntry(i));
            newPoint.setEntry(i, Math.max(min, lowerDifference.getEntry(i)));
            if(xbdi.getEntry(i) == MINUS_ONE) {
              newPoint.setEntry(i, lowerDifference.getEntry(i));
            }
            if(xbdi.getEntry(i) == ONE) {
              newPoint.setEntry(i, upperDifference.getEntry(i));
            }
            trialStepPoint.setEntry(i, newPoint.getEntry(i) - trustRegionCenterOffset.getEntry(i));
            final double d1 = trialStepPoint.getEntry(i);
            dsq += d1 * d1;
          }
          return new double[]{ dsq, crvmin } ;
        }
        case 210:
        {
          printState(210);
          int ih = 0;
          for(int j = 0; j < n; j++) {
            hs.setEntry(j, ZERO);
            for(int i = 0; i <= j; i++) {
              if(i < j) {
                hs.setEntry(j, hs.getEntry(j) + modelSecondDerivativesValues.getEntry(ih) * s.getEntry(i));
              }
              hs.setEntry(i, hs.getEntry(i) + modelSecondDerivativesValues.getEntry(ih) * s.getEntry(j));
              ih++;
            }
          }
          final RealVector tmp = interpolationPoints.operate(s).ebeMultiply(modelSecondDerivativesParameters);
          for(int k = 0; k < npt; k++) {
            if(modelSecondDerivativesParameters.getEntry(k) != ZERO) {
              for(int i = 0; i < n; i++) {
                hs.setEntry(i, hs.getEntry(i) + tmp.getEntry(k) * interpolationPoints.getEntry(k, i));
              }
            }
          }
          if(crvmin != ZERO) {
            state = 50;
            break ;
          }
          if(iterc > itcsav) {
            state = 150;
            break ;
          }
          for(int i = 0; i < n; i++) {
            hred.setEntry(i, hs.getEntry(i));
          }
          state = 120;
          break ;
        }
        default:
        {
          throw new MathIllegalStateException(LocalizedFormats.SIMPLE_MESSAGE, "trsbox");
        }
      }
    }
  }
  private void prelim(double[] lowerBound, double[] upperBound) {
    printMethod();
    final int n = currentBest.getDimension();
    final int npt = numberOfInterpolationPoints;
    final int ndim = bMatrix.getRowDimension();
    final double rhosq = initialTrustRegionRadius * initialTrustRegionRadius;
    final double recip = 1D / rhosq;
    final int np = n + 1;
    for(int j = 0; j < n; j++) {
      originShift.setEntry(j, currentBest.getEntry(j));
      for(int k = 0; k < npt; k++) {
        interpolationPoints.setEntry(k, j, ZERO);
      }
      for(int i = 0; i < ndim; i++) {
        bMatrix.setEntry(i, j, ZERO);
      }
    }
    for(int i = 0, max = n * np / 2; i < max; i++) {
      modelSecondDerivativesValues.setEntry(i, ZERO);
    }
    for(int k = 0; k < npt; k++) {
      modelSecondDerivativesParameters.setEntry(k, ZERO);
      for(int j = 0, max = npt - np; j < max; j++) {
        zMatrix.setEntry(k, j, ZERO);
      }
    }
    int ipt = 0;
    int jpt = 0;
    double fbeg = Double.NaN;
    do {
      final int nfm = getEvaluations();
      final int nfx = nfm - n;
      final int nfmm = nfm - 1;
      final int nfxm = nfx - 1;
      double stepa = 0;
      double stepb = 0;
      if(nfm <= 2 * n) {
        if(nfm >= 1 && nfm <= n) {
          stepa = initialTrustRegionRadius;
          if(upperDifference.getEntry(nfmm) == ZERO) {
            stepa = -stepa;
          }
          interpolationPoints.setEntry(nfm, nfmm, stepa);
        }
        else 
          if(nfm > n) {
            stepa = interpolationPoints.getEntry(nfx, nfxm);
            stepb = -initialTrustRegionRadius;
            if(lowerDifference.getEntry(nfxm) == ZERO) {
              stepb = Math.min(TWO * initialTrustRegionRadius, upperDifference.getEntry(nfxm));
            }
            if(upperDifference.getEntry(nfxm) == ZERO) {
              stepb = Math.max(-TWO * initialTrustRegionRadius, lowerDifference.getEntry(nfxm));
            }
            interpolationPoints.setEntry(nfm, nfxm, stepb);
          }
      }
      else {
        final int tmp1 = (nfm - np) / n;
        jpt = nfm - tmp1 * n - n;
        ipt = jpt + tmp1;
        if(ipt > n) {
          final int tmp2 = jpt;
          jpt = ipt - n;
          ipt = tmp2;
        }
        final int iptMinus1 = ipt - 1;
        final int jptMinus1 = jpt - 1;
        interpolationPoints.setEntry(nfm, iptMinus1, interpolationPoints.getEntry(ipt, iptMinus1));
        interpolationPoints.setEntry(nfm, jptMinus1, interpolationPoints.getEntry(jpt, jptMinus1));
      }
      for(int j = 0; j < n; j++) {
        currentBest.setEntry(j, Math.min(Math.max(lowerBound[j], originShift.getEntry(j) + interpolationPoints.getEntry(nfm, j)), upperBound[j]));
        if(interpolationPoints.getEntry(nfm, j) == lowerDifference.getEntry(j)) {
          currentBest.setEntry(j, lowerBound[j]);
        }
        if(interpolationPoints.getEntry(nfm, j) == upperDifference.getEntry(j)) {
          currentBest.setEntry(j, upperBound[j]);
        }
      }
      final double objectiveValue = computeObjectiveValue(currentBest.toArray());
      final double f = isMinimize ? objectiveValue : -objectiveValue;
      final int numEval = getEvaluations();
      fAtInterpolationPoints.setEntry(nfm, f);
      if(numEval == 1) {
        fbeg = f;
        trustRegionCenterInterpolationPointIndex = 0;
      }
      else 
        if(f < fAtInterpolationPoints.getEntry(trustRegionCenterInterpolationPointIndex)) {
          trustRegionCenterInterpolationPointIndex = nfm;
        }
      if(numEval <= 2 * n + 1) {
        if(numEval >= 2 && numEval <= n + 1) {
          gradientAtTrustRegionCenter.setEntry(nfmm, (f - fbeg) / stepa);
          if(npt < numEval + n) {
            final double oneOverStepA = ONE / stepa;
            bMatrix.setEntry(0, nfmm, -oneOverStepA);
            bMatrix.setEntry(nfm, nfmm, oneOverStepA);
            bMatrix.setEntry(npt + nfmm, nfmm, -HALF * rhosq);
          }
        }
        else 
          if(numEval >= n + 2) {
            final int ih = nfx * (nfx + 1) / 2 - 1;
            final double tmp = (f - fbeg) / stepb;
            final double diff = stepb - stepa;
            modelSecondDerivativesValues.setEntry(ih, TWO * (tmp - gradientAtTrustRegionCenter.getEntry(nfxm)) / diff);
            gradientAtTrustRegionCenter.setEntry(nfxm, (gradientAtTrustRegionCenter.getEntry(nfxm) * stepb - tmp * stepa) / diff);
            if(stepa * stepb < ZERO && f < fAtInterpolationPoints.getEntry(nfm - n)) {
              fAtInterpolationPoints.setEntry(nfm, fAtInterpolationPoints.getEntry(nfm - n));
              fAtInterpolationPoints.setEntry(nfm - n, f);
              if(trustRegionCenterInterpolationPointIndex == nfm) {
                trustRegionCenterInterpolationPointIndex = nfm - n;
              }
              interpolationPoints.setEntry(nfm - n, nfxm, stepb);
              interpolationPoints.setEntry(nfm, nfxm, stepa);
            }
            bMatrix.setEntry(0, nfxm, -(stepa + stepb) / (stepa * stepb));
            bMatrix.setEntry(nfm, nfxm, -HALF / interpolationPoints.getEntry(nfm - n, nfxm));
            bMatrix.setEntry(nfm - n, nfxm, -bMatrix.getEntry(0, nfxm) - bMatrix.getEntry(nfm, nfxm));
            zMatrix.setEntry(0, nfxm, Math.sqrt(TWO) / (stepa * stepb));
            zMatrix.setEntry(nfm, nfxm, Math.sqrt(HALF) / rhosq);
            zMatrix.setEntry(nfm - n, nfxm, -zMatrix.getEntry(0, nfxm) - zMatrix.getEntry(nfm, nfxm));
          }
      }
      else {
        zMatrix.setEntry(0, nfxm, recip);
        zMatrix.setEntry(nfm, nfxm, recip);
        zMatrix.setEntry(ipt, nfxm, -recip);
        zMatrix.setEntry(jpt, nfxm, -recip);
        final int ih = ipt * (ipt - 1) / 2 + jpt - 1;
        final double tmp = interpolationPoints.getEntry(nfm, ipt - 1) * interpolationPoints.getEntry(nfm, jpt - 1);
        modelSecondDerivativesValues.setEntry(ih, (fbeg - fAtInterpolationPoints.getEntry(ipt) - fAtInterpolationPoints.getEntry(jpt) + f) / tmp);
      }
    }while(getEvaluations() < npt);
  }
  private static void printMethod() {
  }
  private static void printState(int s) {
  }
  private void setup(double[] lowerBound, double[] upperBound) {
    printMethod();
    double[] init = getStartPoint();
    final int dimension = init.length;
    if(dimension < MINIMUM_PROBLEM_DIMENSION) {
      throw new NumberIsTooSmallException(dimension, MINIMUM_PROBLEM_DIMENSION, true);
    }
    final int[] nPointsInterval = { dimension + 2, (dimension + 2) * (dimension + 1) / 2 } ;
    if(numberOfInterpolationPoints < nPointsInterval[0] || numberOfInterpolationPoints > nPointsInterval[1]) {
      throw new OutOfRangeException(LocalizedFormats.NUMBER_OF_INTERPOLATION_POINTS, numberOfInterpolationPoints, nPointsInterval[0], nPointsInterval[1]);
    }
    boundDifference = new double[dimension];
    double requiredMinDiff = 2 * initialTrustRegionRadius;
    double minDiff = Double.POSITIVE_INFINITY;
    for(int i = 0; i < dimension; i++) {
      boundDifference[i] = upperBound[i] - lowerBound[i];
      minDiff = Math.min(minDiff, boundDifference[i]);
    }
    if(minDiff < requiredMinDiff) {
      initialTrustRegionRadius = minDiff / 3.0D;
    }
    bMatrix = new Array2DRowRealMatrix(dimension + numberOfInterpolationPoints, dimension);
    zMatrix = new Array2DRowRealMatrix(numberOfInterpolationPoints, numberOfInterpolationPoints - dimension - 1);
    interpolationPoints = new Array2DRowRealMatrix(numberOfInterpolationPoints, dimension);
    originShift = new ArrayRealVector(dimension);
    fAtInterpolationPoints = new ArrayRealVector(numberOfInterpolationPoints);
    trustRegionCenterOffset = new ArrayRealVector(dimension);
    gradientAtTrustRegionCenter = new ArrayRealVector(dimension);
    lowerDifference = new ArrayRealVector(dimension);
    upperDifference = new ArrayRealVector(dimension);
    modelSecondDerivativesParameters = new ArrayRealVector(numberOfInterpolationPoints);
    newPoint = new ArrayRealVector(dimension);
    alternativeNewPoint = new ArrayRealVector(dimension);
    trialStepPoint = new ArrayRealVector(dimension);
    lagrangeValuesAtNewPoint = new ArrayRealVector(dimension + numberOfInterpolationPoints);
    modelSecondDerivativesValues = new ArrayRealVector(dimension * (dimension + 1) / 2);
  }
  private void update(double beta, double denom, int knew) {
    printMethod();
    final int n = currentBest.getDimension();
    final int npt = numberOfInterpolationPoints;
    final int nptm = npt - n - 1;
    final ArrayRealVector work = new ArrayRealVector(npt + n);
    double ztest = ZERO;
    for(int k = 0; k < npt; k++) {
      for(int j = 0; j < nptm; j++) {
        ztest = Math.max(ztest, Math.abs(zMatrix.getEntry(k, j)));
      }
    }
    ztest *= 1e-20D;
    for(int j = 1; j < nptm; j++) {
      final double d1 = zMatrix.getEntry(knew, j);
      if(Math.abs(d1) > ztest) {
        final double d2 = zMatrix.getEntry(knew, 0);
        final double d3 = zMatrix.getEntry(knew, j);
        final double d4 = Math.sqrt(d2 * d2 + d3 * d3);
        final double d5 = zMatrix.getEntry(knew, 0) / d4;
        final double d6 = zMatrix.getEntry(knew, j) / d4;
        for(int i = 0; i < npt; i++) {
          final double d7 = d5 * zMatrix.getEntry(i, 0) + d6 * zMatrix.getEntry(i, j);
          zMatrix.setEntry(i, j, d5 * zMatrix.getEntry(i, j) - d6 * zMatrix.getEntry(i, 0));
          zMatrix.setEntry(i, 0, d7);
        }
      }
      zMatrix.setEntry(knew, j, ZERO);
    }
    for(int i = 0; i < npt; i++) {
      work.setEntry(i, zMatrix.getEntry(knew, 0) * zMatrix.getEntry(i, 0));
    }
    final double alpha = work.getEntry(knew);
    final double tau = lagrangeValuesAtNewPoint.getEntry(knew);
    lagrangeValuesAtNewPoint.setEntry(knew, lagrangeValuesAtNewPoint.getEntry(knew) - ONE);
    final double sqrtDenom = Math.sqrt(denom);
    final double d1 = tau / sqrtDenom;
    final double d2 = zMatrix.getEntry(knew, 0) / sqrtDenom;
    for(int i = 0; i < npt; i++) {
      zMatrix.setEntry(i, 0, d1 * zMatrix.getEntry(i, 0) - d2 * lagrangeValuesAtNewPoint.getEntry(i));
    }
    for(int j = 0; j < n; j++) {
      final int jp = npt + j;
      work.setEntry(jp, bMatrix.getEntry(knew, j));
      final double d3 = (alpha * lagrangeValuesAtNewPoint.getEntry(jp) - tau * work.getEntry(jp)) / denom;
      final double d4 = (-beta * work.getEntry(jp) - tau * lagrangeValuesAtNewPoint.getEntry(jp)) / denom;
      for(int i = 0; i <= jp; i++) {
        bMatrix.setEntry(i, j, bMatrix.getEntry(i, j) + d3 * lagrangeValuesAtNewPoint.getEntry(i) + d4 * work.getEntry(i));
        if(i >= npt) {
          bMatrix.setEntry(jp, (i - npt), bMatrix.getEntry(i, j));
        }
      }
    }
  }
  
  private static class PathIsExploredException extends RuntimeException  {
    final private static long serialVersionUID = 745350979634801853L;
    final private static String PATH_IS_EXPLORED = "If this exception is thrown, just remove it from the code";
    PathIsExploredException() {
      super(PATH_IS_EXPLORED + " " + BOBYQAOptimizer.caller(3));
    }
  }
}