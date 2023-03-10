package org.joda.time.field;
import java.io.Serializable;
import java.util.Locale;
import org.joda.time.Chronology;
import org.joda.time.DateTimeField;
import org.joda.time.DateTimeFieldType;
import org.joda.time.DateTimeUtils;
import org.joda.time.DurationField;
import org.joda.time.Interval;
import org.joda.time.ReadableInstant;
import org.joda.time.ReadablePartial;

abstract public class AbstractReadableInstantFieldProperty implements Serializable  {
  final private static long serialVersionUID = 1971226328211649661L;
  public AbstractReadableInstantFieldProperty() {
    super();
  }
  protected Chronology getChronology() {
    throw new UnsupportedOperationException("The method getChronology() was added in v1.4 and needs " + "to be implemented by subclasses of AbstractReadableInstantFieldProperty");
  }
  abstract public DateTimeField getField();
  public DateTimeFieldType getFieldType() {
    return getField().getType();
  }
  public DurationField getDurationField() {
    return getField().getDurationField();
  }
  public DurationField getLeapDurationField() {
    return getField().getLeapDurationField();
  }
  public DurationField getRangeDurationField() {
    return getField().getRangeDurationField();
  }
  public Interval toInterval() {
    DateTimeField field = getField();
    long start = field.roundFloor(getMillis());
    long end = field.add(start, 1);
    Interval interval = new Interval(start, end);
    return interval;
  }
  public String getAsShortText() {
    return getAsShortText(null);
  }
  public String getAsShortText(Locale locale) {
    return getField().getAsShortText(getMillis(), locale);
  }
  public String getAsString() {
    return Integer.toString(get());
  }
  public String getAsText() {
    return getAsText(null);
  }
  public String getAsText(Locale locale) {
    return getField().getAsText(getMillis(), locale);
  }
  public String getName() {
    return getField().getName();
  }
  public String toString() {
    return "Property[" + getName() + "]";
  }
  public boolean equals(Object object) {
    if(this == object) {
      return true;
    }
    if(object instanceof AbstractReadableInstantFieldProperty == false) {
      return false;
    }
    AbstractReadableInstantFieldProperty other = (AbstractReadableInstantFieldProperty)object;
    return get() == other.get() && getFieldType().equals(other.getFieldType()) && FieldUtils.equals(getChronology(), other.getChronology());
  }
  public boolean isLeap() {
    return getField().isLeap(getMillis());
  }
  public int compareTo(ReadableInstant instant) {
    if(instant == null) {
      throw new IllegalArgumentException("The instant must not be null");
    }
    int thisValue = get();
    int otherValue = instant.get(getFieldType());
    if(thisValue < otherValue) {
      return -1;
    }
    else 
      if(thisValue > otherValue) {
        return 1;
      }
      else {
        return 0;
      }
  }
  public int compareTo(ReadablePartial partial) {
    if(partial == null) {
      throw new IllegalArgumentException("The partial must not be null");
    }
    int thisValue = get();
    int otherValue = partial.get(getFieldType());
    if(thisValue < otherValue) {
      return -1;
    }
    else 
      if(thisValue > otherValue) {
        return 1;
      }
      else {
        return 0;
      }
  }
  public int get() {
    return getField().get(getMillis());
  }
  public int getDifference(ReadableInstant instant) {
    if(instant == null) {
      return getField().getDifference(getMillis(), DateTimeUtils.currentTimeMillis());
    }
    return getField().getDifference(getMillis(), instant.getMillis());
  }
  public int getLeapAmount() {
    return getField().getLeapAmount(getMillis());
  }
  public int getMaximumShortTextLength(Locale locale) {
    return getField().getMaximumShortTextLength(locale);
  }
  public int getMaximumTextLength(Locale locale) {
    return getField().getMaximumTextLength(locale);
  }
  public int getMaximumValue() {
    return getField().getMaximumValue(getMillis());
  }
  public int getMaximumValueOverall() {
    return getField().getMaximumValue();
  }
  public int getMinimumValue() {
    return getField().getMinimumValue(getMillis());
  }
  public int getMinimumValueOverall() {
    return getField().getMinimumValue();
  }
  public int hashCode() {
    return get() * 17 + getFieldType().hashCode() + getChronology().hashCode();
  }
  public long getDifferenceAsLong(ReadableInstant instant) {
    if(instant == null) {
      DateTimeField var_421 = getField();
      return var_421.getDifferenceAsLong(getMillis(), DateTimeUtils.currentTimeMillis());
    }
    return getField().getDifferenceAsLong(getMillis(), instant.getMillis());
  }
  abstract protected long getMillis();
  public long remainder() {
    return getField().remainder(getMillis());
  }
}