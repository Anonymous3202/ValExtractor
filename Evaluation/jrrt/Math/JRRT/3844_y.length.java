package org.apache.commons.math3.stat.inference;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.exception.ConvergenceException;
import org.apache.commons.math3.exception.MaxCountExceededException;
import org.apache.commons.math3.exception.NoDataException;
import org.apache.commons.math3.exception.NullArgumentException;
import org.apache.commons.math3.stat.ranking.NaNStrategy;
import org.apache.commons.math3.stat.ranking.NaturalRanking;
import org.apache.commons.math3.stat.ranking.TiesStrategy;
import org.apache.commons.math3.util.FastMath;

public class MannWhitneyUTest  {
  private NaturalRanking naturalRanking;
  public MannWhitneyUTest() {
    super();
    naturalRanking = new NaturalRanking(NaNStrategy.FIXED, TiesStrategy.AVERAGE);
  }
  public MannWhitneyUTest(final NaNStrategy nanStrategy, final TiesStrategy tiesStrategy) {
    super();
    naturalRanking = new NaturalRanking(nanStrategy, tiesStrategy);
  }
  private double calculateAsymptoticPValue(final double Umin, final int n1, final int n2) throws ConvergenceException, MaxCountExceededException {
    final long n1n2prod = (long)n1 * n2;
    final double EU = n1n2prod / 2.0D;
    final double VarU = n1n2prod * (n1 + n2 + 1) / 12.0D;
    final double z = (Umin - EU) / FastMath.sqrt(VarU);
    final NormalDistribution standardNormal = new NormalDistribution(0, 1);
    return 2 * standardNormal.cumulativeProbability(z);
  }
  public double mannWhitneyU(final double[] x, final double[] y) throws NullArgumentException, NoDataException {
    ensureDataConformance(x, y);
    final double[] z = concatenateSamples(x, y);
    final double[] ranks = naturalRanking.rank(z);
    double sumRankX = 0;
    for(int i = 0; i < x.length; ++i) {
      sumRankX += ranks[i];
    }
    final double U1 = sumRankX - (x.length * (x.length + 1)) / 2;
    final double U2 = x.length * y.length - U1;
    return FastMath.max(U1, U2);
  }
  public double mannWhitneyUTest(final double[] x, final double[] y) throws NullArgumentException, NoDataException, ConvergenceException, MaxCountExceededException {
    ensureDataConformance(x, y);
    final double Umax = mannWhitneyU(x, y);
    final double Umin = x.length * y.length - Umax;
    int var_3844 = y.length;
    return calculateAsymptoticPValue(Umin, x.length, var_3844);
  }
  private double[] concatenateSamples(final double[] x, final double[] y) {
    final double[] z = new double[x.length + y.length];
    System.arraycopy(x, 0, z, 0, x.length);
    System.arraycopy(y, 0, z, x.length, y.length);
    return z;
  }
  private void ensureDataConformance(final double[] x, final double[] y) throws NullArgumentException, NoDataException {
    if(x == null || y == null) {
      throw new NullArgumentException();
    }
    if(x.length == 0 || y.length == 0) {
      throw new NoDataException();
    }
  }
}