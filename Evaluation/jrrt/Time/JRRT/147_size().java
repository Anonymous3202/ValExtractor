package org.joda.time.base;
import org.joda.time.Chronology;
import org.joda.time.DateTime;
import org.joda.time.DateTimeField;
import org.joda.time.DateTimeFieldType;
import org.joda.time.DateTimeUtils;
import org.joda.time.DurationFieldType;
import org.joda.time.ReadableInstant;
import org.joda.time.ReadablePartial;
import org.joda.time.field.FieldUtils;
import org.joda.time.format.DateTimeFormatter;

abstract public class AbstractPartial implements ReadablePartial, Comparable<ReadablePartial>  {
  protected AbstractPartial() {
    super();
  }
  public DateTime toDateTime(ReadableInstant baseInstant) {
    Chronology chrono = DateTimeUtils.getInstantChronology(baseInstant);
    long instantMillis = DateTimeUtils.getInstantMillis(baseInstant);
    long resolved = chrono.set(this, instantMillis);
    return new DateTime(resolved, chrono);
  }
  public DateTimeField getField(int index) {
    return getField(index, getChronology());
  }
  abstract protected DateTimeField getField(int index, Chronology chrono);
  public DateTimeFieldType getFieldType(int index) {
    return getField(index, getChronology()).getType();
  }
  public DateTimeFieldType[] getFieldTypes() {
    DateTimeFieldType[] result = new DateTimeFieldType[size()];
    for(int i = 0; i < result.length; i++) {
      result[i] = getFieldType(i);
    }
    return result;
  }
  public DateTimeField[] getFields() {
    DateTimeField[] result = new DateTimeField[size()];
    for(int i = 0; i < result.length; i++) {
      result[i] = getField(i);
    }
    return result;
  }
  public String toString(DateTimeFormatter formatter) {
    if(formatter == null) {
      return toString();
    }
    return formatter.print(this);
  }
  public boolean equals(Object partial) {
    if(this == partial) {
      return true;
    }
    if(partial instanceof ReadablePartial == false) {
      return false;
    }
    ReadablePartial other = (ReadablePartial)partial;
    if(size() != other.size()) {
      return false;
    }
    for(int i = 0, isize = size(); i < isize; i++) {
      if(getValue(i) != other.getValue(i) || getFieldType(i) != other.getFieldType(i)) {
        return false;
      }
    }
    return FieldUtils.equals(getChronology(), other.getChronology());
  }
  public boolean isAfter(ReadablePartial partial) {
    if(partial == null) {
      throw new IllegalArgumentException("Partial cannot be null");
    }
    return compareTo(partial) > 0;
  }
  public boolean isBefore(ReadablePartial partial) {
    if(partial == null) {
      throw new IllegalArgumentException("Partial cannot be null");
    }
    return compareTo(partial) < 0;
  }
  public boolean isEqual(ReadablePartial partial) {
    if(partial == null) {
      throw new IllegalArgumentException("Partial cannot be null");
    }
    return compareTo(partial) == 0;
  }
  public boolean isSupported(DateTimeFieldType type) {
    return (indexOf(type) != -1);
  }
  public int compareTo(ReadablePartial other) {
    if(this == other) {
      return 0;
    }
    if(size() != other.size()) {
      throw new ClassCastException("ReadablePartial objects must have matching field types");
    }
    int var_147 = size();
    for(int i = 0, isize = var_147; i < isize; i++) {
      if(getFieldType(i) != other.getFieldType(i)) {
        throw new ClassCastException("ReadablePartial objects must have matching field types");
      }
    }
    for(int i = 0, isize = size(); i < isize; i++) {
      if(getValue(i) > other.getValue(i)) {
        return 1;
      }
      if(getValue(i) < other.getValue(i)) {
        return -1;
      }
    }
    return 0;
  }
  public int get(DateTimeFieldType type) {
    return getValue(indexOfSupported(type));
  }
  public int hashCode() {
    int total = 157;
    for(int i = 0, isize = size(); i < isize; i++) {
      total = 23 * total + getValue(i);
      total = 23 * total + getFieldType(i).hashCode();
    }
    total += getChronology().hashCode();
    return total;
  }
  public int indexOf(DateTimeFieldType type) {
    for(int i = 0, isize = size(); i < isize; i++) {
      if(getFieldType(i) == type) {
        return i;
      }
    }
    return -1;
  }
  protected int indexOf(DurationFieldType type) {
    for(int i = 0, isize = size(); i < isize; i++) {
      if(getFieldType(i).getDurationType() == type) {
        return i;
      }
    }
    return -1;
  }
  protected int indexOfSupported(DateTimeFieldType type) {
    int index = indexOf(type);
    if(index == -1) {
      throw new IllegalArgumentException("Field \'" + type + "\' is not supported");
    }
    return index;
  }
  protected int indexOfSupported(DurationFieldType type) {
    int index = indexOf(type);
    if(index == -1) {
      throw new IllegalArgumentException("Field \'" + type + "\' is not supported");
    }
    return index;
  }
  public int[] getValues() {
    int[] result = new int[size()];
    for(int i = 0; i < result.length; i++) {
      result[i] = getValue(i);
    }
    return result;
  }
}