package org.joda.time.base;
import java.io.Serializable;
import org.joda.time.Chronology;
import org.joda.time.DateTimeUtils;
import org.joda.time.Duration;
import org.joda.time.DurationFieldType;
import org.joda.time.MutablePeriod;
import org.joda.time.PeriodType;
import org.joda.time.ReadWritablePeriod;
import org.joda.time.ReadableDuration;
import org.joda.time.ReadableInstant;
import org.joda.time.ReadablePartial;
import org.joda.time.ReadablePeriod;
import org.joda.time.chrono.ISOChronology;
import org.joda.time.convert.ConverterManager;
import org.joda.time.convert.PeriodConverter;
import org.joda.time.field.FieldUtils;

abstract public class BasePeriod extends AbstractPeriod implements ReadablePeriod, Serializable  {
  final private static long serialVersionUID = -2110953284060001145L;
  final private static ReadablePeriod DUMMY_PERIOD = new AbstractPeriod() {
      public int getValue(int index) {
        return 0;
      }
      public PeriodType getPeriodType() {
        return PeriodType.time();
      }
  };
  final private PeriodType iType;
  final private int[] iValues;
  protected BasePeriod(Object period, PeriodType type, Chronology chrono) {
    super();
    PeriodConverter converter = ConverterManager.getInstance().getPeriodConverter(period);
    type = (type == null ? converter.getPeriodType(period) : type);
    type = checkPeriodType(type);
    iType = type;
    if(this instanceof ReadWritablePeriod) {
      iValues = new int[size()];
      chrono = DateTimeUtils.getChronology(chrono);
      converter.setInto((ReadWritablePeriod)this, period, chrono);
    }
    else {
      iValues = new MutablePeriod(period, type, chrono).getValues();
    }
  }
  protected BasePeriod(ReadableDuration duration, ReadableInstant endInstant, PeriodType type) {
    super();
    type = checkPeriodType(type);
    long durationMillis = DateTimeUtils.getDurationMillis(duration);
    long endMillis = DateTimeUtils.getInstantMillis(endInstant);
    long startMillis = FieldUtils.safeSubtract(endMillis, durationMillis);
    Chronology chrono = DateTimeUtils.getInstantChronology(endInstant);
    iType = type;
    iValues = chrono.get(this, startMillis, endMillis);
  }
  protected BasePeriod(ReadableInstant startInstant, ReadableDuration duration, PeriodType type) {
    super();
    type = checkPeriodType(type);
    long startMillis = DateTimeUtils.getInstantMillis(startInstant);
    long durationMillis = DateTimeUtils.getDurationMillis(duration);
    long endMillis = FieldUtils.safeAdd(startMillis, durationMillis);
    Chronology chrono = DateTimeUtils.getInstantChronology(startInstant);
    iType = type;
    iValues = chrono.get(this, startMillis, endMillis);
  }
  protected BasePeriod(ReadableInstant startInstant, ReadableInstant endInstant, PeriodType type) {
    super();
    type = checkPeriodType(type);
    if(startInstant == null && endInstant == null) {
      iType = type;
      iValues = new int[size()];
    }
    else {
      long startMillis = DateTimeUtils.getInstantMillis(startInstant);
      long endMillis = DateTimeUtils.getInstantMillis(endInstant);
      Chronology chrono = DateTimeUtils.getIntervalChronology(startInstant, endInstant);
      iType = type;
      iValues = chrono.get(this, startMillis, endMillis);
    }
  }
  protected BasePeriod(ReadablePartial start, ReadablePartial end, PeriodType type) {
    super();
    if(start == null || end == null) {
      throw new IllegalArgumentException("ReadablePartial objects must not be null");
    }
    if(start instanceof BaseLocal && end instanceof BaseLocal && start.getClass() == end.getClass()) {
      type = checkPeriodType(type);
      long startMillis = ((BaseLocal)start).getLocalMillis();
      long endMillis = ((BaseLocal)end).getLocalMillis();
      Chronology var_141 = start.getChronology();
      Chronology chrono = var_141;
      chrono = DateTimeUtils.getChronology(chrono);
      iType = type;
      iValues = chrono.get(this, startMillis, endMillis);
    }
    else {
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
      iType = checkPeriodType(type);
      Chronology chrono = DateTimeUtils.getChronology(start.getChronology()).withUTC();
      iValues = chrono.get(this, chrono.set(start, 0L), chrono.set(end, 0L));
    }
  }
  protected BasePeriod(int years, int months, int weeks, int days, int hours, int minutes, int seconds, int millis, PeriodType type) {
    super();
    type = checkPeriodType(type);
    iType = type;
    iValues = setPeriodInternal(years, months, weeks, days, hours, minutes, seconds, millis);
  }
  protected BasePeriod(int[] values, PeriodType type) {
    super();
    iType = type;
    iValues = values;
  }
  protected BasePeriod(long duration) {
    super();
    iType = PeriodType.standard();
    int[] values = ISOChronology.getInstanceUTC().get(DUMMY_PERIOD, duration);
    iValues = new int[8];
    System.arraycopy(values, 0, iValues, 4, 4);
  }
  protected BasePeriod(long duration, PeriodType type, Chronology chrono) {
    super();
    type = checkPeriodType(type);
    chrono = DateTimeUtils.getChronology(chrono);
    iType = type;
    iValues = chrono.get(this, duration);
  }
  protected BasePeriod(long startInstant, long endInstant, PeriodType type, Chronology chrono) {
    super();
    type = checkPeriodType(type);
    chrono = DateTimeUtils.getChronology(chrono);
    iType = type;
    iValues = chrono.get(this, startInstant, endInstant);
  }
  public Duration toDurationFrom(ReadableInstant startInstant) {
    long startMillis = DateTimeUtils.getInstantMillis(startInstant);
    Chronology chrono = DateTimeUtils.getInstantChronology(startInstant);
    long endMillis = chrono.add(this, startMillis, 1);
    return new Duration(startMillis, endMillis);
  }
  public Duration toDurationTo(ReadableInstant endInstant) {
    long endMillis = DateTimeUtils.getInstantMillis(endInstant);
    Chronology chrono = DateTimeUtils.getInstantChronology(endInstant);
    long startMillis = chrono.add(this, endMillis, -1);
    return new Duration(startMillis, endMillis);
  }
  protected PeriodType checkPeriodType(PeriodType type) {
    return DateTimeUtils.getPeriodType(type);
  }
  public PeriodType getPeriodType() {
    return iType;
  }
  public int getValue(int index) {
    return iValues[index];
  }
  protected int[] addPeriodInto(int[] values, ReadablePeriod period) {
    for(int i = 0, isize = period.size(); i < isize; i++) {
      DurationFieldType type = period.getFieldType(i);
      int value = period.getValue(i);
      if(value != 0) {
        int index = indexOf(type);
        if(index == -1) {
          throw new IllegalArgumentException("Period does not support field \'" + type.getName() + "\'");
        }
        else {
          values[index] = FieldUtils.safeAdd(getValue(index), value);
        }
      }
    }
    return values;
  }
  protected int[] mergePeriodInto(int[] values, ReadablePeriod period) {
    for(int i = 0, isize = period.size(); i < isize; i++) {
      DurationFieldType type = period.getFieldType(i);
      int value = period.getValue(i);
      checkAndUpdate(type, values, value);
    }
    return values;
  }
  private int[] setPeriodInternal(int years, int months, int weeks, int days, int hours, int minutes, int seconds, int millis) {
    int[] newValues = new int[size()];
    checkAndUpdate(DurationFieldType.years(), newValues, years);
    checkAndUpdate(DurationFieldType.months(), newValues, months);
    checkAndUpdate(DurationFieldType.weeks(), newValues, weeks);
    checkAndUpdate(DurationFieldType.days(), newValues, days);
    checkAndUpdate(DurationFieldType.hours(), newValues, hours);
    checkAndUpdate(DurationFieldType.minutes(), newValues, minutes);
    checkAndUpdate(DurationFieldType.seconds(), newValues, seconds);
    checkAndUpdate(DurationFieldType.millis(), newValues, millis);
    return newValues;
  }
  protected void addField(DurationFieldType field, int value) {
    addFieldInto(iValues, field, value);
  }
  protected void addFieldInto(int[] values, DurationFieldType field, int value) {
    int index = indexOf(field);
    if(index == -1) {
      if(value != 0 || field == null) {
        throw new IllegalArgumentException("Period does not support field \'" + field + "\'");
      }
    }
    else {
      values[index] = FieldUtils.safeAdd(values[index], value);
    }
  }
  protected void addPeriod(ReadablePeriod period) {
    if(period != null) {
      setValues(addPeriodInto(getValues(), period));
    }
  }
  private void checkAndUpdate(DurationFieldType type, int[] values, int newValue) {
    int index = indexOf(type);
    if(index == -1) {
      if(newValue != 0) {
        throw new IllegalArgumentException("Period does not support field \'" + type.getName() + "\'");
      }
    }
    else {
      values[index] = newValue;
    }
  }
  protected void mergePeriod(ReadablePeriod period) {
    if(period != null) {
      setValues(mergePeriodInto(getValues(), period));
    }
  }
  protected void setField(DurationFieldType field, int value) {
    setFieldInto(iValues, field, value);
  }
  protected void setFieldInto(int[] values, DurationFieldType field, int value) {
    int index = indexOf(field);
    if(index == -1) {
      if(value != 0 || field == null) {
        throw new IllegalArgumentException("Period does not support field \'" + field + "\'");
      }
    }
    else {
      values[index] = value;
    }
  }
  protected void setPeriod(int years, int months, int weeks, int days, int hours, int minutes, int seconds, int millis) {
    int[] newValues = setPeriodInternal(years, months, weeks, days, hours, minutes, seconds, millis);
    setValues(newValues);
  }
  protected void setPeriod(ReadablePeriod period) {
    if(period == null) {
      setValues(new int[size()]);
    }
    else {
      setPeriodInternal(period);
    }
  }
  private void setPeriodInternal(ReadablePeriod period) {
    int[] newValues = new int[size()];
    for(int i = 0, isize = period.size(); i < isize; i++) {
      DurationFieldType type = period.getFieldType(i);
      int value = period.getValue(i);
      checkAndUpdate(type, newValues, value);
    }
    setValues(newValues);
  }
  protected void setValue(int index, int value) {
    iValues[index] = value;
  }
  protected void setValues(int[] values) {
    System.arraycopy(values, 0, iValues, 0, iValues.length);
  }
}