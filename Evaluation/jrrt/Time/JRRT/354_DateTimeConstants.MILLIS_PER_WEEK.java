package org.joda.time.chrono;
import org.joda.time.DateTimeConstants;
import org.joda.time.DateTimeFieldType;
import org.joda.time.DurationField;
import org.joda.time.field.FieldUtils;
import org.joda.time.field.ImpreciseDateTimeField;

final class BasicWeekyearDateTimeField extends ImpreciseDateTimeField  {
  @SuppressWarnings(value = {"unused", }) final private static long serialVersionUID = 6215066916806820644L;
  final private static long WEEK_53 = (53L - 1) * DateTimeConstants.MILLIS_PER_WEEK;
  final private BasicChronology iChronology;
  BasicWeekyearDateTimeField(BasicChronology chronology) {
    super(DateTimeFieldType.weekyear(), chronology.getAverageMillisPerYear());
    iChronology = chronology;
  }
  public DurationField getLeapDurationField() {
    return iChronology.weeks();
  }
  public DurationField getRangeDurationField() {
    return null;
  }
  private Object readResolve() {
    return iChronology.weekyear();
  }
  public boolean isLeap(long instant) {
    return iChronology.getWeeksInYear(iChronology.getWeekyear(instant)) > 52;
  }
  public boolean isLenient() {
    return false;
  }
  public int get(long instant) {
    return iChronology.getWeekyear(instant);
  }
  public int getLeapAmount(long instant) {
    return iChronology.getWeeksInYear(iChronology.getWeekyear(instant)) - 52;
  }
  public int getMaximumValue() {
    return iChronology.getMaxYear();
  }
  public int getMinimumValue() {
    return iChronology.getMinYear();
  }
  public long add(long instant, int years) {
    if(years == 0) {
      return instant;
    }
    return set(instant, get(instant) + years);
  }
  public long add(long instant, long value) {
    return add(instant, FieldUtils.safeToInt(value));
  }
  public long addWrapField(long instant, int years) {
    return add(instant, years);
  }
  public long getDifferenceAsLong(long minuendInstant, long subtrahendInstant) {
    if(minuendInstant < subtrahendInstant) {
      return -getDifference(subtrahendInstant, minuendInstant);
    }
    int minuendWeekyear = get(minuendInstant);
    int subtrahendWeekyear = get(subtrahendInstant);
    long minuendRem = remainder(minuendInstant);
    long subtrahendRem = remainder(subtrahendInstant);
    if(subtrahendRem >= WEEK_53 && iChronology.getWeeksInYear(minuendWeekyear) <= 52) {
      subtrahendRem -= DateTimeConstants.MILLIS_PER_WEEK;
    }
    int difference = minuendWeekyear - subtrahendWeekyear;
    if(minuendRem < subtrahendRem) {
      difference--;
    }
    return difference;
  }
  public long remainder(long instant) {
    return instant - roundFloor(instant);
  }
  public long roundFloor(long instant) {
    instant = iChronology.weekOfWeekyear().roundFloor(instant);
    int wow = iChronology.getWeekOfWeekyear(instant);
    if(wow > 1) {
      instant -= ((long)DateTimeConstants.MILLIS_PER_WEEK) * (wow - 1);
    }
    return instant;
  }
  public long set(long instant, int year) {
    FieldUtils.verifyValueBounds(this, Math.abs(year), iChronology.getMinYear(), iChronology.getMaxYear());
    int thisWeekyear = get(instant);
    if(thisWeekyear == year) {
      return instant;
    }
    int thisDow = iChronology.getDayOfWeek(instant);
    int weeksInFromYear = iChronology.getWeeksInYear(thisWeekyear);
    int weeksInToYear = iChronology.getWeeksInYear(year);
    int maxOutWeeks = (weeksInToYear < weeksInFromYear) ? weeksInToYear : weeksInFromYear;
    int setToWeek = iChronology.getWeekOfWeekyear(instant);
    if(setToWeek > maxOutWeeks) {
      setToWeek = maxOutWeeks;
    }
    long workInstant = instant;
    workInstant = iChronology.setYear(workInstant, year);
    int workWoyYear = get(workInstant);
    if(workWoyYear < year) {
      int var_354 = DateTimeConstants.MILLIS_PER_WEEK;
      workInstant += var_354;
    }
    else 
      if(workWoyYear > year) {
        workInstant -= DateTimeConstants.MILLIS_PER_WEEK;
      }
    int currentWoyWeek = iChronology.getWeekOfWeekyear(workInstant);
    workInstant = workInstant + (setToWeek - currentWoyWeek) * (long)DateTimeConstants.MILLIS_PER_WEEK;
    workInstant = iChronology.dayOfWeek().set(workInstant, thisDow);
    return workInstant;
  }
}