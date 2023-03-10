package org.joda.time.chrono;
import org.joda.time.DateTimeConstants;
import org.joda.time.DateTimeFieldType;
import org.joda.time.DurationField;
import org.joda.time.ReadablePartial;
import org.joda.time.field.PreciseDurationDateTimeField;

final class BasicWeekOfWeekyearDateTimeField extends PreciseDurationDateTimeField  {
  @SuppressWarnings(value = {"unused", }) final private static long serialVersionUID = -1587436826395135328L;
  final private BasicChronology iChronology;
  BasicWeekOfWeekyearDateTimeField(BasicChronology chronology, DurationField weeks) {
    super(DateTimeFieldType.weekOfWeekyear(), weeks);
    iChronology = chronology;
  }
  public DurationField getRangeDurationField() {
    return iChronology.weekyears();
  }
  private Object readResolve() {
    return iChronology.weekOfWeekyear();
  }
  public int get(long instant) {
    return iChronology.getWeekOfWeekyear(instant);
  }
  public int getMaximumValue() {
    return 53;
  }
  public int getMaximumValue(long instant) {
    int weekyear = iChronology.getWeekyear(instant);
    return iChronology.getWeeksInYear(weekyear);
  }
  public int getMaximumValue(ReadablePartial partial) {
    if(partial.isSupported(DateTimeFieldType.weekyear())) {
      int weekyear = partial.get(DateTimeFieldType.weekyear());
      return iChronology.getWeeksInYear(weekyear);
    }
    return 53;
  }
  public int getMaximumValue(ReadablePartial partial, int[] values) {
    int size = partial.size();
    for(int i = 0; i < size; i++) {
      if(partial.getFieldType(i) == DateTimeFieldType.weekyear()) {
        int weekyear = values[i];
        return iChronology.getWeeksInYear(weekyear);
      }
    }
    return 53;
  }
  protected int getMaximumValueForSet(long instant, int value) {
    return value > 52 ? getMaximumValue(instant) : 52;
  }
  public int getMinimumValue() {
    return 1;
  }
  public long remainder(long instant) {
    return super.remainder(instant + 3 * DateTimeConstants.MILLIS_PER_DAY);
  }
  public long roundCeiling(long instant) {
    return super.roundCeiling(instant + 3 * DateTimeConstants.MILLIS_PER_DAY) - 3 * DateTimeConstants.MILLIS_PER_DAY;
  }
  public long roundFloor(long instant) {
    int var_357 = DateTimeConstants.MILLIS_PER_DAY;
    return super.roundFloor(instant + 3 * var_357) - 3 * DateTimeConstants.MILLIS_PER_DAY;
  }
}