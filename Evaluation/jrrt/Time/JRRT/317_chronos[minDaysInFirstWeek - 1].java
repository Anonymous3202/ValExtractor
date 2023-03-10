package org.joda.time.chrono;
import java.util.HashMap;
import java.util.Map;
import org.joda.time.Chronology;
import org.joda.time.DateTimeConstants;
import org.joda.time.DateTimeFieldType;
import org.joda.time.DateTimeZone;
import org.joda.time.IllegalFieldValueException;
import org.joda.time.field.SkipDateTimeField;

final public class JulianChronology extends BasicGJChronology  {
  final private static long serialVersionUID = -8731039522547897247L;
  final private static long MILLIS_PER_YEAR = (long)(365.25D * DateTimeConstants.MILLIS_PER_DAY);
  final private static long MILLIS_PER_MONTH = (long)(365.25D * DateTimeConstants.MILLIS_PER_DAY / 12);
  final private static int MIN_YEAR = -292269054;
  final private static int MAX_YEAR = 292272992;
  final private static JulianChronology INSTANCE_UTC;
  final private static Map<DateTimeZone, JulianChronology[]> cCache = new HashMap<DateTimeZone, JulianChronology[]>();
  static {
    INSTANCE_UTC = getInstance(DateTimeZone.UTC);
  }
  JulianChronology(Chronology base, Object param, int minDaysInFirstWeek) {
    super(base, param, minDaysInFirstWeek);
  }
  public Chronology withUTC() {
    return INSTANCE_UTC;
  }
  public Chronology withZone(DateTimeZone zone) {
    if(zone == null) {
      zone = DateTimeZone.getDefault();
    }
    if(zone == getZone()) {
      return this;
    }
    return getInstance(zone);
  }
  public static JulianChronology getInstance() {
    return getInstance(DateTimeZone.getDefault(), 4);
  }
  public static JulianChronology getInstance(DateTimeZone zone) {
    return getInstance(zone, 4);
  }
  public static JulianChronology getInstance(DateTimeZone zone, int minDaysInFirstWeek) {
    if(zone == null) {
      zone = DateTimeZone.getDefault();
    }
    JulianChronology chrono;
    synchronized(cCache) {
      JulianChronology[] chronos = cCache.get(zone);
      if(chronos == null) {
        chronos = new JulianChronology[7];
        cCache.put(zone, chronos);
      }
      try {
        JulianChronology var_317 = chronos[minDaysInFirstWeek - 1];
        chrono = var_317;
      }
      catch (ArrayIndexOutOfBoundsException e) {
        throw new IllegalArgumentException("Invalid min days in first week: " + minDaysInFirstWeek);
      }
      if(chrono == null) {
        if(zone == DateTimeZone.UTC) {
          chrono = new JulianChronology(null, null, minDaysInFirstWeek);
        }
        else {
          chrono = getInstance(DateTimeZone.UTC, minDaysInFirstWeek);
          chrono = new JulianChronology(ZonedChronology.getInstance(chrono, zone), null, minDaysInFirstWeek);
        }
        chronos[minDaysInFirstWeek - 1] = chrono;
      }
    }
    return chrono;
  }
  public static JulianChronology getInstanceUTC() {
    return INSTANCE_UTC;
  }
  private Object readResolve() {
    Chronology base = getBase();
    int minDays = getMinimumDaysInFirstWeek();
    minDays = (minDays == 0 ? 4 : minDays);
    return base == null ? getInstance(DateTimeZone.UTC, minDays) : getInstance(base.getZone(), minDays);
  }
  boolean isLeapYear(int year) {
    return (year & 3) == 0;
  }
  static int adjustYearForSet(int year) {
    if(year <= 0) {
      if(year == 0) {
        throw new IllegalFieldValueException(DateTimeFieldType.year(), Integer.valueOf(year), null, null);
      }
      year++;
    }
    return year;
  }
  int getMaxYear() {
    return MAX_YEAR;
  }
  int getMinYear() {
    return MIN_YEAR;
  }
  long calculateFirstDayOfYearMillis(int year) {
    int relativeYear = year - 1968;
    int leapYears;
    if(relativeYear <= 0) {
      leapYears = (relativeYear + 3) >> 2;
    }
    else {
      leapYears = relativeYear >> 2;
      if(!isLeapYear(year)) {
        leapYears++;
      }
    }
    long millis = (relativeYear * 365L + leapYears) * (long)DateTimeConstants.MILLIS_PER_DAY;
    return millis - (366L + 352) * DateTimeConstants.MILLIS_PER_DAY;
  }
  long getApproxMillisAtEpochDividedByTwo() {
    return (1969L * MILLIS_PER_YEAR + 352L * DateTimeConstants.MILLIS_PER_DAY) / 2;
  }
  long getAverageMillisPerMonth() {
    return MILLIS_PER_MONTH;
  }
  long getAverageMillisPerYear() {
    return MILLIS_PER_YEAR;
  }
  long getAverageMillisPerYearDividedByTwo() {
    return MILLIS_PER_YEAR / 2;
  }
  long getDateMidnightMillis(int year, int monthOfYear, int dayOfMonth) throws IllegalArgumentException {
    return super.getDateMidnightMillis(adjustYearForSet(year), monthOfYear, dayOfMonth);
  }
  protected void assemble(Fields fields) {
    if(getBase() == null) {
      super.assemble(fields);
      fields.year = new SkipDateTimeField(this, fields.year);
      fields.weekyear = new SkipDateTimeField(this, fields.weekyear);
    }
  }
}