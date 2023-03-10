package org.apache.commons.math3.stat.ranking;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.math3.exception.MathInternalError;
import org.apache.commons.math3.exception.NotANumberException;
import org.apache.commons.math3.random.RandomDataGenerator;
import org.apache.commons.math3.random.RandomGenerator;
import org.apache.commons.math3.util.FastMath;

public class NaturalRanking implements RankingAlgorithm  {
  final public static NaNStrategy DEFAULT_NAN_STRATEGY = NaNStrategy.FAILED;
  final public static TiesStrategy DEFAULT_TIES_STRATEGY = TiesStrategy.AVERAGE;
  final private NaNStrategy nanStrategy;
  final private TiesStrategy tiesStrategy;
  final private RandomDataGenerator randomData;
  public NaturalRanking() {
    super();
    tiesStrategy = DEFAULT_TIES_STRATEGY;
    nanStrategy = DEFAULT_NAN_STRATEGY;
    randomData = null;
  }
  public NaturalRanking(NaNStrategy nanStrategy) {
    super();
    this.nanStrategy = nanStrategy;
    tiesStrategy = DEFAULT_TIES_STRATEGY;
    randomData = null;
  }
  public NaturalRanking(NaNStrategy nanStrategy, RandomGenerator randomGenerator) {
    super();
    this.nanStrategy = nanStrategy;
    this.tiesStrategy = TiesStrategy.RANDOM;
    randomData = new RandomDataGenerator(randomGenerator);
  }
  public NaturalRanking(NaNStrategy nanStrategy, TiesStrategy tiesStrategy) {
    super();
    this.nanStrategy = nanStrategy;
    this.tiesStrategy = tiesStrategy;
    randomData = new RandomDataGenerator();
  }
  public NaturalRanking(RandomGenerator randomGenerator) {
    super();
    this.tiesStrategy = TiesStrategy.RANDOM;
    nanStrategy = DEFAULT_NAN_STRATEGY;
    randomData = new RandomDataGenerator(randomGenerator);
  }
  public NaturalRanking(TiesStrategy tiesStrategy) {
    super();
    this.tiesStrategy = tiesStrategy;
    nanStrategy = DEFAULT_NAN_STRATEGY;
    randomData = new RandomDataGenerator();
  }
  private IntDoublePair[] removeNaNs(IntDoublePair[] ranks) {
    if(!containsNaNs(ranks)) {
      return ranks;
    }
    IntDoublePair[] outRanks = new IntDoublePair[ranks.length];
    int j = 0;
    for(int i = 0; i < ranks.length; i++) {
      if(Double.isNaN(ranks[i].getValue())) {
        for(int k = i + 1; k < ranks.length; k++) {
          ranks[k] = new IntDoublePair(ranks[k].getValue(), ranks[k].getPosition() - 1);
        }
      }
      else {
        outRanks[j] = new IntDoublePair(ranks[i].getValue(), ranks[i].getPosition());
        j++;
      }
    }
    IntDoublePair[] returnRanks = new IntDoublePair[j];
    System.arraycopy(outRanks, 0, returnRanks, 0, j);
    return returnRanks;
  }
  private List<Integer> getNanPositions(IntDoublePair[] ranks) {
    ArrayList<Integer> out = new ArrayList<Integer>();
    for(int i = 0; i < ranks.length; i++) {
      if(Double.isNaN(ranks[i].getValue())) {
        out.add(Integer.valueOf(i));
      }
    }
    return out;
  }
  public NaNStrategy getNanStrategy() {
    return nanStrategy;
  }
  public TiesStrategy getTiesStrategy() {
    return tiesStrategy;
  }
  private boolean containsNaNs(IntDoublePair[] ranks) {
    for(int i = 0; i < ranks.length; i++) {
      if(Double.isNaN(ranks[i].getValue())) {
        return true;
      }
    }
    return false;
  }
  public double[] rank(double[] data) {
    IntDoublePair[] ranks = new IntDoublePair[data.length];
    for(int i = 0; i < data.length; i++) {
      ranks[i] = new IntDoublePair(data[i], i);
    }
    List<Integer> nanPositions = null;
    switch (nanStrategy){
      case MAXIMAL:
      recodeNaNs(ranks, Double.POSITIVE_INFINITY);
      break ;
      case MINIMAL:
      recodeNaNs(ranks, Double.NEGATIVE_INFINITY);
      break ;
      case REMOVED:
      ranks = removeNaNs(ranks);
      break ;
      case FIXED:
      nanPositions = getNanPositions(ranks);
      break ;
      case FAILED:
      nanPositions = getNanPositions(ranks);
      if(nanPositions.size() > 0) {
        throw new NotANumberException();
      }
      break ;
      default:
      throw new MathInternalError();
    }
    Arrays.sort(ranks);
    double[] out = new double[ranks.length];
    int pos = 1;
    IntDoublePair var_3867 = ranks[0];
    out[var_3867.getPosition()] = pos;
    List<Integer> tiesTrace = new ArrayList<Integer>();
    tiesTrace.add(ranks[0].getPosition());
    for(int i = 1; i < ranks.length; i++) {
      if(Double.compare(ranks[i].getValue(), ranks[i - 1].getValue()) > 0) {
        pos = i + 1;
        if(tiesTrace.size() > 1) {
          resolveTie(out, tiesTrace);
        }
        tiesTrace = new ArrayList<Integer>();
        tiesTrace.add(ranks[i].getPosition());
      }
      else {
        tiesTrace.add(ranks[i].getPosition());
      }
      out[ranks[i].getPosition()] = pos;
    }
    if(tiesTrace.size() > 1) {
      resolveTie(out, tiesTrace);
    }
    if(nanStrategy == NaNStrategy.FIXED) {
      restoreNaNs(out, nanPositions);
    }
    return out;
  }
  private void fill(double[] data, List<Integer> tiesTrace, double value) {
    Iterator<Integer> iterator = tiesTrace.iterator();
    while(iterator.hasNext()){
      data[iterator.next()] = value;
    }
  }
  private void recodeNaNs(IntDoublePair[] ranks, double value) {
    for(int i = 0; i < ranks.length; i++) {
      if(Double.isNaN(ranks[i].getValue())) {
        ranks[i] = new IntDoublePair(value, ranks[i].getPosition());
      }
    }
  }
  private void resolveTie(double[] ranks, List<Integer> tiesTrace) {
    final double c = ranks[tiesTrace.get(0)];
    final int length = tiesTrace.size();
    switch (tiesStrategy){
      case AVERAGE:
      fill(ranks, tiesTrace, (2 * c + length - 1) / 2D);
      break ;
      case MAXIMUM:
      fill(ranks, tiesTrace, c + length - 1);
      break ;
      case MINIMUM:
      fill(ranks, tiesTrace, c);
      break ;
      case RANDOM:
      Iterator<Integer> iterator = tiesTrace.iterator();
      long f = FastMath.round(c);
      while(iterator.hasNext()){
        ranks[iterator.next()] = randomData.nextLong(f, f + length - 1);
      }
      break ;
      case SEQUENTIAL:
      iterator = tiesTrace.iterator();
      f = FastMath.round(c);
      int i = 0;
      while(iterator.hasNext()){
        ranks[iterator.next()] = f + i++;
      }
      break ;
      default:
      throw new MathInternalError();
    }
  }
  private void restoreNaNs(double[] ranks, List<Integer> nanPositions) {
    if(nanPositions.size() == 0) {
      return ;
    }
    Iterator<Integer> iterator = nanPositions.iterator();
    while(iterator.hasNext()){
      ranks[iterator.next().intValue()] = Double.NaN;
    }
  }
  
  private static class IntDoublePair implements Comparable<IntDoublePair>  {
    final private double value;
    final private int position;
    public IntDoublePair(double value, int position) {
      super();
      this.value = value;
      this.position = position;
    }
    public double getValue() {
      return value;
    }
    public int compareTo(IntDoublePair other) {
      return Double.compare(value, other.value);
    }
    public int getPosition() {
      return position;
    }
  }
}