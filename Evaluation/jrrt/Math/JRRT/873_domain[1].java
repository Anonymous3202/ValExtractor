package org.apache.commons.math3.distribution;
import org.apache.commons.math3.exception.NotPositiveException;
import org.apache.commons.math3.exception.NotStrictlyPositiveException;
import org.apache.commons.math3.exception.NumberIsTooLargeException;
import org.apache.commons.math3.exception.util.LocalizedFormats;
import org.apache.commons.math3.util.FastMath;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.random.Well19937c;

public class HypergeometricDistribution extends AbstractIntegerDistribution  {
  final private static long serialVersionUID = -436928820673516179L;
  final private int numberOfSuccesses;
  final private int populationSize;
  final private int sampleSize;
  private double numericalVariance = Double.NaN;
  private boolean numericalVarianceIsCalculated = false;
  public HypergeometricDistribution(RandomGenerator rng, int populationSize, int numberOfSuccesses, int sampleSize) throws NotPositiveException, NotStrictlyPositiveException, NumberIsTooLargeException {
    super(rng);
    if(populationSize <= 0) {
      throw new NotStrictlyPositiveException(LocalizedFormats.POPULATION_SIZE, populationSize);
    }
    if(numberOfSuccesses < 0) {
      throw new NotPositiveException(LocalizedFormats.NUMBER_OF_SUCCESSES, numberOfSuccesses);
    }
    if(sampleSize < 0) {
      throw new NotPositiveException(LocalizedFormats.NUMBER_OF_SAMPLES, sampleSize);
    }
    if(numberOfSuccesses > populationSize) {
      throw new NumberIsTooLargeException(LocalizedFormats.NUMBER_OF_SUCCESS_LARGER_THAN_POPULATION_SIZE, numberOfSuccesses, populationSize, true);
    }
    if(sampleSize > populationSize) {
      throw new NumberIsTooLargeException(LocalizedFormats.SAMPLE_SIZE_LARGER_THAN_POPULATION_SIZE, sampleSize, populationSize, true);
    }
    this.numberOfSuccesses = numberOfSuccesses;
    this.populationSize = populationSize;
    this.sampleSize = sampleSize;
  }
  public HypergeometricDistribution(int populationSize, int numberOfSuccesses, int sampleSize) throws NotPositiveException, NotStrictlyPositiveException, NumberIsTooLargeException {
    this(new Well19937c(), populationSize, numberOfSuccesses, sampleSize);
  }
  public boolean isSupportConnected() {
    return true;
  }
  protected double calculateNumericalVariance() {
    final double N = getPopulationSize();
    final double m = getNumberOfSuccesses();
    final double n = getSampleSize();
    return (n * m * (N - n) * (N - m)) / (N * N * (N - 1));
  }
  public double cumulativeProbability(int x) {
    double ret;
    int[] domain = getDomain(populationSize, numberOfSuccesses, sampleSize);
    if(x < domain[0]) {
      ret = 0.0D;
    }
    else 
      if(x >= domain[1]) {
        ret = 1.0D;
      }
      else {
        ret = innerCumulativeProbability(domain[0], x, 1);
      }
    return ret;
  }
  public double getNumericalMean() {
    return getSampleSize() * (getNumberOfSuccesses() / (double)getPopulationSize());
  }
  public double getNumericalVariance() {
    if(!numericalVarianceIsCalculated) {
      numericalVariance = calculateNumericalVariance();
      numericalVarianceIsCalculated = true;
    }
    return numericalVariance;
  }
  private double innerCumulativeProbability(int x0, int x1, int dx) {
    double ret = probability(x0);
    while(x0 != x1){
      x0 += dx;
      ret += probability(x0);
    }
    return ret;
  }
  public double probability(int x) {
    double ret;
    int[] domain = getDomain(populationSize, numberOfSuccesses, sampleSize);
    if(x < domain[0] || x > domain[1]) {
      ret = 0.0D;
    }
    else {
      double p = (double)sampleSize / (double)populationSize;
      double q = (double)(populationSize - sampleSize) / (double)populationSize;
      double p1 = SaddlePointExpansion.logBinomialProbability(x, numberOfSuccesses, p, q);
      double p2 = SaddlePointExpansion.logBinomialProbability(sampleSize - x, populationSize - numberOfSuccesses, p, q);
      double p3 = SaddlePointExpansion.logBinomialProbability(sampleSize, populationSize, p, q);
      ret = FastMath.exp(p1 + p2 - p3);
    }
    return ret;
  }
  public double upperCumulativeProbability(int x) {
    double ret;
    final int[] domain = getDomain(populationSize, numberOfSuccesses, sampleSize);
    if(x <= domain[0]) {
      ret = 1.0D;
    }
    else {
      int var_873 = domain[1];
      if(x > var_873) {
        ret = 0.0D;
      }
      else {
        ret = innerCumulativeProbability(domain[1], x, -1);
      }
    }
    return ret;
  }
  private int getLowerDomain(int n, int m, int k) {
    return FastMath.max(0, m - (n - k));
  }
  public int getNumberOfSuccesses() {
    return numberOfSuccesses;
  }
  public int getPopulationSize() {
    return populationSize;
  }
  public int getSampleSize() {
    return sampleSize;
  }
  public int getSupportLowerBound() {
    return FastMath.max(0, getSampleSize() + getNumberOfSuccesses() - getPopulationSize());
  }
  public int getSupportUpperBound() {
    return FastMath.min(getNumberOfSuccesses(), getSampleSize());
  }
  private int getUpperDomain(int m, int k) {
    return FastMath.min(k, m);
  }
  private int[] getDomain(int n, int m, int k) {
    return new int[]{ getLowerDomain(n, m, k), getUpperDomain(m, k) } ;
  }
}