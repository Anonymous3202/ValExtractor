package org.joda.time.chrono;
import org.joda.time.Chronology;
import org.joda.time.DateTimeConstants;

abstract class BasicGJChronology extends BasicChronology  {
  final private static long serialVersionUID = 538276888268L;
  final private static int[] MIN_DAYS_PER_MONTH_ARRAY = { 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31 } ;
  final private static int[] MAX_DAYS_PER_MONTH_ARRAY = { 31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31 } ;
  final private static long[] MIN_TOTAL_MILLIS_BY_MONTH_ARRAY;
  final private static long[] MAX_TOTAL_MILLIS_BY_MONTH_ARRAY;
  final private static long FEB_29 = (31L + 29 - 1) * DateTimeConstants.MILLIS_PER_DAY;
  static {
    MIN_TOTAL_MILLIS_BY_MONTH_ARRAY = new long[12];
    MAX_TOTAL_MILLIS_BY_MONTH_ARRAY = new long[12];
    long minSum = 0;
    long maxSum = 0;
    for(int i = 0; i < 11; i++) {
      long millis = MIN_DAYS_PER_MONTH_ARRAY[i] * (long)DateTimeConstants.MILLIS_PER_DAY;
      minSum += millis;
      MIN_TOTAL_MILLIS_BY_MONTH_ARRAY[i + 1] = minSum;
      millis = MAX_DAYS_PER_MONTH_ARRAY[i] * (long)DateTimeConstants.MILLIS_PER_DAY;
      maxSum += millis;
      MAX_TOTAL_MILLIS_BY_MONTH_ARRAY[i + 1] = maxSum;
    }
  }
  BasicGJChronology(Chronology base, Object param, int minDaysInFirstWeek) {
    super(base, param, minDaysInFirstWeek);
  }
  int getDaysInMonthMax(int month) {
    return MAX_DAYS_PER_MONTH_ARRAY[month - 1];
  }
  int getDaysInMonthMaxForSet(long instant, int value) {
    return ((value > 28 || value < 1) ? getDaysInMonthMax(instant) : 28);
  }
  int getDaysInYearMonth(int year, int month) {
    if(isLeapYear(year)) {
      return MAX_DAYS_PER_MONTH_ARRAY[month - 1];
    }
    else {
      return MIN_DAYS_PER_MONTH_ARRAY[month - 1];
    }
  }
  int getMonthOfYear(long millis, int year) {
    int i = (int)((millis - getYearMillis(year)) >> 10);
    return (isLeapYear(year)) ? ((i < 182 * 84375) ? ((i < 91 * 84375) ? ((i < 31 * 84375) ? 1 : (i < 60 * 84375) ? 2 : 3) : ((i < 121 * 84375) ? 4 : (i < 152 * 84375) ? 5 : 6)) : ((i < 274 * 84375) ? ((i < 213 * 84375) ? 7 : (i < 244 * 84375) ? 8 : 9) : ((i < 305 * 84375) ? 10 : (i < 335 * 84375) ? 11 : 12))) : ((i < 181 * 84375) ? ((i < 90 * 84375) ? ((i < 31 * 84375) ? 1 : (i < 59 * 84375) ? 2 : 3) : ((i < 120 * 84375) ? 4 : (i < 151 * 84375) ? 5 : 6)) : ((i < 273 * 84375) ? ((i < 212 * 84375) ? 7 : (i < 243 * 84375) ? 8 : 9) : ((i < 304 * 84375) ? 10 : (i < 334 * 84375) ? 11 : 12)));
  }
  long getTotalMillisByYearMonth(int year, int month) {
    if(isLeapYear(year)) {
      return MAX_TOTAL_MILLIS_BY_MONTH_ARRAY[month - 1];
    }
    else {
      return MIN_TOTAL_MILLIS_BY_MONTH_ARRAY[month - 1];
    }
  }
  long getYearDifference(long minuendInstant, long subtrahendInstant) {
    int minuendYear = getYear(minuendInstant);
    int subtrahendYear = getYear(subtrahendInstant);
    long minuendRem = minuendInstant - getYearMillis(minuendYear);
    long subtrahendRem = subtrahendInstant - getYearMillis(subtrahendYear);
    if(subtrahendRem >= FEB_29) {
      if(isLeapYear(subtrahendYear)) {
        if(!isLeapYear(minuendYear)) {
          int var_315 = DateTimeConstants.MILLIS_PER_DAY;
          subtrahendRem -= var_315;
        }
      }
      else 
        if(minuendRem >= FEB_29 && isLeapYear(minuendYear)) {
          minuendRem -= DateTimeConstants.MILLIS_PER_DAY;
        }
    }
    int difference = minuendYear - subtrahendYear;
    if(minuendRem < subtrahendRem) {
      difference--;
    }
    return difference;
  }
  long setYear(long instant, int year) {
    int thisYear = getYear(instant);
    int dayOfYear = getDayOfYear(instant, thisYear);
    int millisOfDay = getMillisOfDay(instant);
    if(dayOfYear > (31 + 28)) {
      if(isLeapYear(thisYear)) {
        if(!isLeapYear(year)) {
          dayOfYear--;
        }
      }
      else {
        if(isLeapYear(year)) {
          dayOfYear++;
        }
      }
    }
    instant = getYearMonthDayMillis(year, 1, dayOfYear);
    instant += millisOfDay;
    return instant;
  }
}