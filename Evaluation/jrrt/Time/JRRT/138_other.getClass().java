package org.joda.time.base;
import java.io.Serializable;
import org.joda.time.Chronology;
import org.joda.time.DateTimeUtils;
import org.joda.time.DurationField;
import org.joda.time.DurationFieldType;
import org.joda.time.MutablePeriod;
import org.joda.time.Period;
import org.joda.time.PeriodType;
import org.joda.time.ReadableInstant;
import org.joda.time.ReadablePartial;
import org.joda.time.ReadablePeriod;
import org.joda.time.chrono.ISOChronology;
import org.joda.time.field.FieldUtils;

abstract public class BaseSingleFieldPeriod implements ReadablePeriod, Comparable<BaseSingleFieldPeriod>, Serializable  {
  final private static long serialVersionUID = 9386874258972L;
  final private static long START_1972 = 2L * 365L * 86400L * 1000L;
  private volatile int iPeriod;
  protected BaseSingleFieldPeriod(int period) {
    super();
    iPeriod = period;
  }
  abstract public DurationFieldType getFieldType();
  public DurationFieldType getFieldType(int index) {
    if(index != 0) {
      throw new IndexOutOfBoundsException(String.valueOf(index));
    }
    return getFieldType();
  }
  public MutablePeriod toMutablePeriod() {
    MutablePeriod period = new MutablePeriod();
    period.add(this);
    return period;
  }
  public Period toPeriod() {
    return Period.ZERO.withFields(this);
  }
  abstract public PeriodType getPeriodType();
  public boolean equals(Object period) {
    if(this == period) {
      return true;
    }
    if(period instanceof ReadablePeriod == false) {
      return false;
    }
    ReadablePeriod other = (ReadablePeriod)period;
    return (other.getPeriodType() == getPeriodType() && other.getValue(0) == getValue());
  }
  public boolean isSupported(DurationFieldType type) {
    return (type == getFieldType());
  }
  protected static int between(ReadableInstant start, ReadableInstant end, DurationFieldType field) {
    if(start == null || end == null) {
      throw new IllegalArgumentException("ReadableInstant objects must not be null");
    }
    Chronology chrono = DateTimeUtils.getInstantChronology(start);
    int amount = field.getField(chrono).getDifference(end.getMillis(), start.getMillis());
    return amount;
  }
  protected static int between(ReadablePartial start, ReadablePartial end, ReadablePeriod zeroInstance) {
    if(start == null || end == null) {
      throw new IllegalArgumentException("ReadablePartial objects must not be null");
    }
    if(start.size() != end.size()) {
      throw new IllegalArgumentException("ReadablePartial objects must have the same set of fields");
    }
    for(int i = 0, isize = start.size(); i < isize; i++) {
      if(start.getFieldType(i) != end.getFieldType(i)) {
        throw new IllegalArgumentException("ReadablePartial objects must have the same set of fields");
      }
    }
    if(DateTimeUtils.isContiguous(start) == false) {
      throw new IllegalArgumentException("ReadablePartial objects must be contiguous");
    }
    Chronology chrono = DateTimeUtils.getChronology(start.getChronology()).withUTC();
    int[] values = chrono.get(zeroInstance, chrono.set(start, START_1972), chrono.set(end, START_1972));
    return values[0];
  }
  public int compareTo(BaseSingleFieldPeriod other) {
    Class<? extends BaseSingleFieldPeriod> var_138 = other.getClass();
    if(var_138 != getClass()) {
      throw new ClassCastException(getClass() + " cannot be compared to " + other.getClass());
    }
    int otherValue = other.getValue();
    int thisValue = getValue();
    if(thisValue > otherValue) {
      return 1;
    }
    if(thisValue < otherValue) {
      return -1;
    }
    return 0;
  }
  public int get(DurationFieldType type) {
    if(type == getFieldType()) {
      return getValue();
    }
    return 0;
  }
  protected int getValue() {
    return iPeriod;
  }
  public int getValue(int index) {
    if(index != 0) {
      throw new IndexOutOfBoundsException(String.valueOf(index));
    }
    return getValue();
  }
  public int hashCode() {
    int total = 17;
    total = 27 * total + getValue();
    total = 27 * total + getFieldType().hashCode();
    return total;
  }
  public int size() {
    return 1;
  }
  protected static int standardPeriodIn(ReadablePeriod period, long millisPerUnit) {
    if(period == null) {
      return 0;
    }
    Chronology iso = ISOChronology.getInstanceUTC();
    long duration = 0L;
    for(int i = 0; i < period.size(); i++) {
      int value = period.getValue(i);
      if(value != 0) {
        DurationField field = period.getFieldType(i).getField(iso);
        if(field.isPrecise() == false) {
          throw new IllegalArgumentException("Cannot convert period to duration as " + field.getName() + " is not precise in the period " + period);
        }
        duration = FieldUtils.safeAdd(duration, FieldUtils.safeMultiply(field.getUnitMillis(), value));
      }
    }
    return FieldUtils.safeToInt(duration / millisPerUnit);
  }
  protected void setValue(int value) {
    iPeriod = value;
  }
}