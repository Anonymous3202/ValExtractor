package org.apache.commons.math3.stat.descriptive;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.exception.NotPositiveException;
import org.apache.commons.math3.exception.NullArgumentException;
import org.apache.commons.math3.exception.NumberIsTooLargeException;
import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.apache.commons.math3.exception.util.LocalizedFormats;

abstract public class AbstractUnivariateStatistic implements UnivariateStatistic  {
  private double[] storedData;
  abstract public UnivariateStatistic copy();
  protected boolean test(final double[] values, final double[] weights, final int begin, final int length) throws MathIllegalArgumentException {
    return test(values, weights, begin, length, false);
  }
  protected boolean test(final double[] values, final double[] weights, final int begin, final int length, final boolean allowEmpty) throws MathIllegalArgumentException {
    if(weights == null || values == null) {
      throw new NullArgumentException(LocalizedFormats.INPUT_ARRAY);
    }
    if(weights.length != values.length) {
      throw new DimensionMismatchException(weights.length, values.length);
    }
    boolean containsPositiveWeight = false;
    for(int i = begin; i < begin + length; i++) {
      if(Double.isNaN(weights[i])) {
        throw new MathIllegalArgumentException(LocalizedFormats.NAN_ELEMENT_AT_INDEX, i);
      }
      if(Double.isInfinite(weights[i])) {
        throw new MathIllegalArgumentException(LocalizedFormats.INFINITE_ARRAY_ELEMENT, weights[i], i);
      }
      if(weights[i] < 0) {
        throw new MathIllegalArgumentException(LocalizedFormats.NEGATIVE_ELEMENT_AT_INDEX, i, weights[i]);
      }
      if(!containsPositiveWeight && weights[i] > 0.0D) {
        containsPositiveWeight = true;
      }
    }
    if(!containsPositiveWeight) {
      throw new MathIllegalArgumentException(LocalizedFormats.WEIGHT_AT_LEAST_ONE_NON_ZERO);
    }
    return test(values, begin, length, allowEmpty);
  }
  protected boolean test(final double[] values, final int begin, final int length) throws MathIllegalArgumentException {
    return test(values, begin, length, false);
  }
  protected boolean test(final double[] values, final int begin, final int length, final boolean allowEmpty) throws MathIllegalArgumentException {
    if(values == null) {
      throw new NullArgumentException(LocalizedFormats.INPUT_ARRAY);
    }
    if(begin < 0) {
      throw new NotPositiveException(LocalizedFormats.START_POSITION, begin);
    }
    if(length < 0) {
      throw new NotPositiveException(LocalizedFormats.LENGTH, length);
    }
    if(begin + length > values.length) {
      throw new NumberIsTooLargeException(LocalizedFormats.SUBARRAY_ENDS_AFTER_ARRAY_END, begin + length, values.length, true);
    }
    if(length == 0 && !allowEmpty) {
      return false;
    }
    return true;
  }
  public double evaluate() throws MathIllegalArgumentException {
    return evaluate(storedData);
  }
  public double evaluate(final double[] values) throws MathIllegalArgumentException {
    test(values, 0, 0);
    return evaluate(values, 0, values.length);
  }
  abstract public double evaluate(final double[] values, final int begin, final int length) throws MathIllegalArgumentException;
  public double[] getData() {
    return (storedData == null) ? null : storedData.clone();
  }
  protected double[] getDataRef() {
    return storedData;
  }
  public void setData(final double[] values) {
    storedData = (values == null) ? null : values.clone();
  }
  public void setData(final double[] values, final int begin, final int length) throws MathIllegalArgumentException {
    if(values == null) {
      throw new NullArgumentException(LocalizedFormats.INPUT_ARRAY);
    }
    if(begin < 0) {
      throw new NotPositiveException(LocalizedFormats.START_POSITION, begin);
    }
    if(length < 0) {
      throw new NotPositiveException(LocalizedFormats.LENGTH, length);
    }
    int var_3782 = values.length;
    if(begin + length > var_3782) {
      throw new NumberIsTooLargeException(LocalizedFormats.SUBARRAY_ENDS_AFTER_ARRAY_END, begin + length, values.length, true);
    }
    storedData = new double[length];
    System.arraycopy(values, begin, storedData, 0, length);
  }
}