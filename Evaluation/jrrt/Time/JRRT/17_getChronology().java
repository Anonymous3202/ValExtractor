package org.joda.time;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import org.joda.convert.FromString;
import org.joda.convert.ToString;
import org.joda.time.base.BaseLocal;
import org.joda.time.chrono.ISOChronology;
import org.joda.time.convert.ConverterManager;
import org.joda.time.convert.PartialConverter;
import org.joda.time.field.AbstractReadableInstantFieldProperty;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

final public class LocalDateTime extends BaseLocal implements ReadablePartial, Serializable  {
  final private static long serialVersionUID = -268716875315837168L;
  final private static int YEAR = 0;
  final private static int MONTH_OF_YEAR = 1;
  final private static int DAY_OF_MONTH = 2;
  final private static int MILLIS_OF_DAY = 3;
  final private long iLocalMillis;
  final private Chronology iChronology;
  public LocalDateTime() {
    this(DateTimeUtils.currentTimeMillis(), ISOChronology.getInstance());
  }
  public LocalDateTime(Chronology chronology) {
    this(DateTimeUtils.currentTimeMillis(), chronology);
  }
  public LocalDateTime(DateTimeZone zone) {
    this(DateTimeUtils.currentTimeMillis(), ISOChronology.getInstance(zone));
  }
  public LocalDateTime(Object instant) {
    this(instant, (Chronology)null);
  }
  public LocalDateTime(Object instant, Chronology chronology) {
    super();
    PartialConverter converter = ConverterManager.getInstance().getPartialConverter(instant);
    chronology = converter.getChronology(instant, chronology);
    chronology = DateTimeUtils.getChronology(chronology);
    iChronology = chronology.withUTC();
    int[] values = converter.getPartialValues(this, instant, chronology, ISODateTimeFormat.localDateOptionalTimeParser());
    iLocalMillis = iChronology.getDateTimeMillis(values[0], values[1], values[2], values[3]);
  }
  public LocalDateTime(Object instant, DateTimeZone zone) {
    super();
    PartialConverter converter = ConverterManager.getInstance().getPartialConverter(instant);
    Chronology chronology = converter.getChronology(instant, zone);
    chronology = DateTimeUtils.getChronology(chronology);
    iChronology = chronology.withUTC();
    int[] values = converter.getPartialValues(this, instant, chronology, ISODateTimeFormat.localDateOptionalTimeParser());
    iLocalMillis = iChronology.getDateTimeMillis(values[0], values[1], values[2], values[3]);
  }
  public LocalDateTime(int year, int monthOfYear, int dayOfMonth, int hourOfDay, int minuteOfHour) {
    this(year, monthOfYear, dayOfMonth, hourOfDay, minuteOfHour, 0, 0, ISOChronology.getInstanceUTC());
  }
  public LocalDateTime(int year, int monthOfYear, int dayOfMonth, int hourOfDay, int minuteOfHour, int secondOfMinute) {
    this(year, monthOfYear, dayOfMonth, hourOfDay, minuteOfHour, secondOfMinute, 0, ISOChronology.getInstanceUTC());
  }
  public LocalDateTime(int year, int monthOfYear, int dayOfMonth, int hourOfDay, int minuteOfHour, int secondOfMinute, int millisOfSecond) {
    this(year, monthOfYear, dayOfMonth, hourOfDay, minuteOfHour, secondOfMinute, millisOfSecond, ISOChronology.getInstanceUTC());
  }
  public LocalDateTime(int year, int monthOfYear, int dayOfMonth, int hourOfDay, int minuteOfHour, int secondOfMinute, int millisOfSecond, Chronology chronology) {
    super();
    chronology = DateTimeUtils.getChronology(chronology).withUTC();
    long instant = chronology.getDateTimeMillis(year, monthOfYear, dayOfMonth, hourOfDay, minuteOfHour, secondOfMinute, millisOfSecond);
    iChronology = chronology;
    iLocalMillis = instant;
  }
  public LocalDateTime(long instant) {
    this(instant, ISOChronology.getInstance());
  }
  public LocalDateTime(long instant, Chronology chronology) {
    super();
    chronology = DateTimeUtils.getChronology(chronology);
    long localMillis = chronology.getZone().getMillisKeepLocal(DateTimeZone.UTC, instant);
    iLocalMillis = localMillis;
    iChronology = chronology.withUTC();
  }
  public LocalDateTime(long instant, DateTimeZone zone) {
    this(instant, ISOChronology.getInstance(zone));
  }
  public Chronology getChronology() {
    return iChronology;
  }
  private Date correctDstTransition(Date date, final TimeZone timeZone) {
    Calendar calendar = Calendar.getInstance(timeZone);
    calendar.setTime(date);
    LocalDateTime check = LocalDateTime.fromCalendarFields(calendar);
    if(check.isBefore(this)) {
      while(check.isBefore(this)){
        calendar.setTimeInMillis(calendar.getTimeInMillis() + 60000);
        check = LocalDateTime.fromCalendarFields(calendar);
      }
      while(check.isBefore(this) == false){
        calendar.setTimeInMillis(calendar.getTimeInMillis() - 1000);
        check = LocalDateTime.fromCalendarFields(calendar);
      }
      calendar.setTimeInMillis(calendar.getTimeInMillis() + 1000);
    }
    else 
      if(check.equals(this)) {
        final Calendar earlier = Calendar.getInstance(timeZone);
        earlier.setTimeInMillis(calendar.getTimeInMillis() - timeZone.getDSTSavings());
        check = LocalDateTime.fromCalendarFields(earlier);
        if(check.equals(this)) {
          calendar = earlier;
        }
      }
    return calendar.getTime();
  }
  @SuppressWarnings(value = {"deprecation", }) public Date toDate() {
    int dom = getDayOfMonth();
    Date date = new Date(getYear() - 1900, getMonthOfYear() - 1, dom, getHourOfDay(), getMinuteOfHour(), getSecondOfMinute());
    date.setTime(date.getTime() + getMillisOfSecond());
    return correctDstTransition(date, TimeZone.getDefault());
  }
  public Date toDate(final TimeZone timeZone) {
    final Calendar calendar = Calendar.getInstance(timeZone);
    calendar.clear();
    calendar.set(getYear(), getMonthOfYear() - 1, getDayOfMonth(), getHourOfDay(), getMinuteOfHour(), getSecondOfMinute());
    Date date = calendar.getTime();
    date.setTime(date.getTime() + getMillisOfSecond());
    return correctDstTransition(date, timeZone);
  }
  public DateTime toDateTime() {
    return toDateTime((DateTimeZone)null);
  }
  public DateTime toDateTime(DateTimeZone zone) {
    zone = DateTimeUtils.getZone(zone);
    Chronology chrono = iChronology.withZone(zone);
    return new DateTime(getYear(), getMonthOfYear(), getDayOfMonth(), getHourOfDay(), getMinuteOfHour(), getSecondOfMinute(), getMillisOfSecond(), chrono);
  }
  protected DateTimeField getField(int index, Chronology chrono) {
    switch (index){
      case YEAR:
      return chrono.year();
      case MONTH_OF_YEAR:
      return chrono.monthOfYear();
      case DAY_OF_MONTH:
      return chrono.dayOfMonth();
      case MILLIS_OF_DAY:
      return chrono.millisOfDay();
      default:
      throw new IndexOutOfBoundsException("Invalid index: " + index);
    }
  }
  public LocalDate toLocalDate() {
    return new LocalDate(getLocalMillis(), getChronology());
  }
  public static LocalDateTime fromCalendarFields(Calendar calendar) {
    if(calendar == null) {
      throw new IllegalArgumentException("The calendar must not be null");
    }
    int era = calendar.get(Calendar.ERA);
    int yearOfEra = calendar.get(Calendar.YEAR);
    return new LocalDateTime((era == GregorianCalendar.AD ? yearOfEra : 1 - yearOfEra), calendar.get(Calendar.MONTH) + 1, calendar.get(Calendar.DAY_OF_MONTH), calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), calendar.get(Calendar.SECOND), calendar.get(Calendar.MILLISECOND));
  }
  @SuppressWarnings(value = {"deprecation", }) public static LocalDateTime fromDateFields(Date date) {
    if(date == null) {
      throw new IllegalArgumentException("The date must not be null");
    }
    if(date.getTime() < 0) {
      GregorianCalendar cal = new GregorianCalendar();
      cal.setTime(date);
      return fromCalendarFields(cal);
    }
    return new LocalDateTime(date.getYear() + 1900, date.getMonth() + 1, date.getDate(), date.getHours(), date.getMinutes(), date.getSeconds(), (((int)(date.getTime() % 1000)) + 1000) % 1000);
  }
  public LocalDateTime minus(ReadableDuration duration) {
    return withDurationAdded(duration, -1);
  }
  public LocalDateTime minus(ReadablePeriod period) {
    return withPeriodAdded(period, -1);
  }
  public LocalDateTime minusDays(int days) {
    if(days == 0) {
      return this;
    }
    long instant = getChronology().days().subtract(getLocalMillis(), days);
    return withLocalMillis(instant);
  }
  public LocalDateTime minusHours(int hours) {
    if(hours == 0) {
      return this;
    }
    long instant = getChronology().hours().subtract(getLocalMillis(), hours);
    return withLocalMillis(instant);
  }
  public LocalDateTime minusMillis(int millis) {
    if(millis == 0) {
      return this;
    }
    long instant = getChronology().millis().subtract(getLocalMillis(), millis);
    return withLocalMillis(instant);
  }
  public LocalDateTime minusMinutes(int minutes) {
    if(minutes == 0) {
      return this;
    }
    long instant = getChronology().minutes().subtract(getLocalMillis(), minutes);
    return withLocalMillis(instant);
  }
  public LocalDateTime minusMonths(int months) {
    if(months == 0) {
      return this;
    }
    long instant = getChronology().months().subtract(getLocalMillis(), months);
    return withLocalMillis(instant);
  }
  public LocalDateTime minusSeconds(int seconds) {
    if(seconds == 0) {
      return this;
    }
    long instant = getChronology().seconds().subtract(getLocalMillis(), seconds);
    return withLocalMillis(instant);
  }
  public LocalDateTime minusWeeks(int weeks) {
    if(weeks == 0) {
      return this;
    }
    long instant = getChronology().weeks().subtract(getLocalMillis(), weeks);
    return withLocalMillis(instant);
  }
  public LocalDateTime minusYears(int years) {
    if(years == 0) {
      return this;
    }
    long instant = getChronology().years().subtract(getLocalMillis(), years);
    return withLocalMillis(instant);
  }
  public static LocalDateTime now() {
    return new LocalDateTime();
  }
  public static LocalDateTime now(Chronology chronology) {
    if(chronology == null) {
      throw new NullPointerException("Chronology must not be null");
    }
    return new LocalDateTime(chronology);
  }
  public static LocalDateTime now(DateTimeZone zone) {
    if(zone == null) {
      throw new NullPointerException("Zone must not be null");
    }
    return new LocalDateTime(zone);
  }
  @FromString() public static LocalDateTime parse(String str) {
    return parse(str, ISODateTimeFormat.localDateOptionalTimeParser());
  }
  public static LocalDateTime parse(String str, DateTimeFormatter formatter) {
    return formatter.parseLocalDateTime(str);
  }
  public LocalDateTime plus(ReadableDuration duration) {
    return withDurationAdded(duration, 1);
  }
  public LocalDateTime plus(ReadablePeriod period) {
    return withPeriodAdded(period, 1);
  }
  public LocalDateTime plusDays(int days) {
    if(days == 0) {
      return this;
    }
    long instant = getChronology().days().add(getLocalMillis(), days);
    return withLocalMillis(instant);
  }
  public LocalDateTime plusHours(int hours) {
    if(hours == 0) {
      return this;
    }
    long instant = getChronology().hours().add(getLocalMillis(), hours);
    return withLocalMillis(instant);
  }
  public LocalDateTime plusMillis(int millis) {
    if(millis == 0) {
      return this;
    }
    long instant = getChronology().millis().add(getLocalMillis(), millis);
    return withLocalMillis(instant);
  }
  public LocalDateTime plusMinutes(int minutes) {
    if(minutes == 0) {
      return this;
    }
    long instant = getChronology().minutes().add(getLocalMillis(), minutes);
    return withLocalMillis(instant);
  }
  public LocalDateTime plusMonths(int months) {
    if(months == 0) {
      return this;
    }
    long instant = getChronology().months().add(getLocalMillis(), months);
    return withLocalMillis(instant);
  }
  public LocalDateTime plusSeconds(int seconds) {
    if(seconds == 0) {
      return this;
    }
    long instant = getChronology().seconds().add(getLocalMillis(), seconds);
    return withLocalMillis(instant);
  }
  public LocalDateTime plusWeeks(int weeks) {
    if(weeks == 0) {
      return this;
    }
    long instant = getChronology().weeks().add(getLocalMillis(), weeks);
    return withLocalMillis(instant);
  }
  public LocalDateTime plusYears(int years) {
    if(years == 0) {
      return this;
    }
    long instant = getChronology().years().add(getLocalMillis(), years);
    return withLocalMillis(instant);
  }
  public LocalDateTime withCenturyOfEra(int centuryOfEra) {
    return withLocalMillis(getChronology().centuryOfEra().set(getLocalMillis(), centuryOfEra));
  }
  public LocalDateTime withDate(int year, int monthOfYear, int dayOfMonth) {
    Chronology chrono = getChronology();
    long instant = getLocalMillis();
    instant = chrono.year().set(instant, year);
    instant = chrono.monthOfYear().set(instant, monthOfYear);
    instant = chrono.dayOfMonth().set(instant, dayOfMonth);
    return withLocalMillis(instant);
  }
  public LocalDateTime withDayOfMonth(int dayOfMonth) {
    return withLocalMillis(getChronology().dayOfMonth().set(getLocalMillis(), dayOfMonth));
  }
  public LocalDateTime withDayOfWeek(int dayOfWeek) {
    return withLocalMillis(getChronology().dayOfWeek().set(getLocalMillis(), dayOfWeek));
  }
  public LocalDateTime withDayOfYear(int dayOfYear) {
    return withLocalMillis(getChronology().dayOfYear().set(getLocalMillis(), dayOfYear));
  }
  public LocalDateTime withDurationAdded(ReadableDuration durationToAdd, int scalar) {
    if(durationToAdd == null || scalar == 0) {
      return this;
    }
    long instant = getChronology().add(getLocalMillis(), durationToAdd.getMillis(), scalar);
    return withLocalMillis(instant);
  }
  public LocalDateTime withEra(int era) {
    return withLocalMillis(getChronology().era().set(getLocalMillis(), era));
  }
  public LocalDateTime withField(DateTimeFieldType fieldType, int value) {
    if(fieldType == null) {
      throw new IllegalArgumentException("Field must not be null");
    }
    long instant = fieldType.getField(getChronology()).set(getLocalMillis(), value);
    return withLocalMillis(instant);
  }
  public LocalDateTime withFieldAdded(DurationFieldType fieldType, int amount) {
    if(fieldType == null) {
      throw new IllegalArgumentException("Field must not be null");
    }
    if(amount == 0) {
      return this;
    }
    long instant = fieldType.getField(getChronology()).add(getLocalMillis(), amount);
    return withLocalMillis(instant);
  }
  public LocalDateTime withFields(ReadablePartial partial) {
    if(partial == null) {
      return this;
    }
    return withLocalMillis(getChronology().set(partial, getLocalMillis()));
  }
  public LocalDateTime withHourOfDay(int hour) {
    return withLocalMillis(getChronology().hourOfDay().set(getLocalMillis(), hour));
  }
  LocalDateTime withLocalMillis(long newMillis) {
    return (newMillis == getLocalMillis() ? this : new LocalDateTime(newMillis, getChronology()));
  }
  public LocalDateTime withMillisOfDay(int millis) {
    return withLocalMillis(getChronology().millisOfDay().set(getLocalMillis(), millis));
  }
  public LocalDateTime withMillisOfSecond(int millis) {
    return withLocalMillis(getChronology().millisOfSecond().set(getLocalMillis(), millis));
  }
  public LocalDateTime withMinuteOfHour(int minute) {
    return withLocalMillis(getChronology().minuteOfHour().set(getLocalMillis(), minute));
  }
  public LocalDateTime withMonthOfYear(int monthOfYear) {
    return withLocalMillis(getChronology().monthOfYear().set(getLocalMillis(), monthOfYear));
  }
  public LocalDateTime withPeriodAdded(ReadablePeriod period, int scalar) {
    if(period == null || scalar == 0) {
      return this;
    }
    long instant = getChronology().add(period, getLocalMillis(), scalar);
    return withLocalMillis(instant);
  }
  public LocalDateTime withSecondOfMinute(int second) {
    return withLocalMillis(getChronology().secondOfMinute().set(getLocalMillis(), second));
  }
  public LocalDateTime withTime(int hourOfDay, int minuteOfHour, int secondOfMinute, int millisOfSecond) {
    Chronology chrono = getChronology();
    long instant = getLocalMillis();
    instant = chrono.hourOfDay().set(instant, hourOfDay);
    instant = chrono.minuteOfHour().set(instant, minuteOfHour);
    instant = chrono.secondOfMinute().set(instant, secondOfMinute);
    instant = chrono.millisOfSecond().set(instant, millisOfSecond);
    return withLocalMillis(instant);
  }
  public LocalDateTime withWeekOfWeekyear(int weekOfWeekyear) {
    return withLocalMillis(getChronology().weekOfWeekyear().set(getLocalMillis(), weekOfWeekyear));
  }
  public LocalDateTime withWeekyear(int weekyear) {
    return withLocalMillis(getChronology().weekyear().set(getLocalMillis(), weekyear));
  }
  public LocalDateTime withYear(int year) {
    return withLocalMillis(getChronology().year().set(getLocalMillis(), year));
  }
  public LocalDateTime withYearOfCentury(int yearOfCentury) {
    return withLocalMillis(getChronology().yearOfCentury().set(getLocalMillis(), yearOfCentury));
  }
  public LocalDateTime withYearOfEra(int yearOfEra) {
    return withLocalMillis(getChronology().yearOfEra().set(getLocalMillis(), yearOfEra));
  }
  public LocalTime toLocalTime() {
    return new LocalTime(getLocalMillis(), getChronology());
  }
  private Object readResolve() {
    if(iChronology == null) {
      return new LocalDateTime(iLocalMillis, ISOChronology.getInstanceUTC());
    }
    if(DateTimeZone.UTC.equals(iChronology.getZone()) == false) {
      return new LocalDateTime(iLocalMillis, iChronology.withUTC());
    }
    return this;
  }
  public Property centuryOfEra() {
    return new Property(this, getChronology().centuryOfEra());
  }
  public Property dayOfMonth() {
    return new Property(this, getChronology().dayOfMonth());
  }
  public Property dayOfWeek() {
    return new Property(this, getChronology().dayOfWeek());
  }
  public Property dayOfYear() {
    return new Property(this, getChronology().dayOfYear());
  }
  public Property era() {
    return new Property(this, getChronology().era());
  }
  public Property hourOfDay() {
    return new Property(this, getChronology().hourOfDay());
  }
  public Property millisOfDay() {
    return new Property(this, getChronology().millisOfDay());
  }
  public Property millisOfSecond() {
    return new Property(this, getChronology().millisOfSecond());
  }
  public Property minuteOfHour() {
    return new Property(this, getChronology().minuteOfHour());
  }
  public Property monthOfYear() {
    return new Property(this, getChronology().monthOfYear());
  }
  public Property property(DateTimeFieldType fieldType) {
    if(fieldType == null) {
      throw new IllegalArgumentException("The DateTimeFieldType must not be null");
    }
    if(isSupported(fieldType) == false) {
      throw new IllegalArgumentException("Field \'" + fieldType + "\' is not supported");
    }
    return new Property(this, fieldType.getField(getChronology()));
  }
  public Property secondOfMinute() {
    return new Property(this, getChronology().secondOfMinute());
  }
  public Property weekOfWeekyear() {
    return new Property(this, getChronology().weekOfWeekyear());
  }
  public Property weekyear() {
    return new Property(this, getChronology().weekyear());
  }
  public Property year() {
    return new Property(this, getChronology().year());
  }
  public Property yearOfCentury() {
    return new Property(this, getChronology().yearOfCentury());
  }
  public Property yearOfEra() {
    return new Property(this, getChronology().yearOfEra());
  }
  @ToString() public String toString() {
    return ISODateTimeFormat.dateTime().print(this);
  }
  public String toString(String pattern) {
    if(pattern == null) {
      return toString();
    }
    return DateTimeFormat.forPattern(pattern).print(this);
  }
  public String toString(String pattern, Locale locale) throws IllegalArgumentException {
    if(pattern == null) {
      return toString();
    }
    return DateTimeFormat.forPattern(pattern).withLocale(locale).print(this);
  }
  public boolean equals(Object partial) {
    if(this == partial) {
      return true;
    }
    if(partial instanceof LocalDateTime) {
      LocalDateTime other = (LocalDateTime)partial;
      if(iChronology.equals(other.iChronology)) {
        return iLocalMillis == other.iLocalMillis;
      }
    }
    return super.equals(partial);
  }
  public boolean isSupported(DateTimeFieldType type) {
    if(type == null) {
      return false;
    }
    return type.getField(getChronology()).isSupported();
  }
  public boolean isSupported(DurationFieldType type) {
    if(type == null) {
      return false;
    }
    return type.getField(getChronology()).isSupported();
  }
  public int compareTo(ReadablePartial partial) {
    if(this == partial) {
      return 0;
    }
    if(partial instanceof LocalDateTime) {
      LocalDateTime other = (LocalDateTime)partial;
      if(iChronology.equals(other.iChronology)) {
        return (iLocalMillis < other.iLocalMillis ? -1 : (iLocalMillis == other.iLocalMillis ? 0 : 1));
      }
    }
    return super.compareTo(partial);
  }
  public int get(DateTimeFieldType type) {
    if(type == null) {
      throw new IllegalArgumentException("The DateTimeFieldType must not be null");
    }
    return type.getField(getChronology()).get(getLocalMillis());
  }
  public int getCenturyOfEra() {
    return getChronology().centuryOfEra().get(getLocalMillis());
  }
  public int getDayOfMonth() {
    return getChronology().dayOfMonth().get(getLocalMillis());
  }
  public int getDayOfWeek() {
    return getChronology().dayOfWeek().get(getLocalMillis());
  }
  public int getDayOfYear() {
    return getChronology().dayOfYear().get(getLocalMillis());
  }
  public int getEra() {
    return getChronology().era().get(getLocalMillis());
  }
  public int getHourOfDay() {
    return getChronology().hourOfDay().get(getLocalMillis());
  }
  public int getMillisOfDay() {
    return getChronology().millisOfDay().get(getLocalMillis());
  }
  public int getMillisOfSecond() {
    return getChronology().millisOfSecond().get(getLocalMillis());
  }
  public int getMinuteOfHour() {
    return getChronology().minuteOfHour().get(getLocalMillis());
  }
  public int getMonthOfYear() {
    return getChronology().monthOfYear().get(getLocalMillis());
  }
  public int getSecondOfMinute() {
    return getChronology().secondOfMinute().get(getLocalMillis());
  }
  public int getValue(int index) {
    switch (index){
      case YEAR:
      return getChronology().year().get(getLocalMillis());
      case MONTH_OF_YEAR:
      return getChronology().monthOfYear().get(getLocalMillis());
      case DAY_OF_MONTH:
      Chronology var_17 = getChronology();
      return var_17.dayOfMonth().get(getLocalMillis());
      case MILLIS_OF_DAY:
      return getChronology().millisOfDay().get(getLocalMillis());
      default:
      throw new IndexOutOfBoundsException("Invalid index: " + index);
    }
  }
  public int getWeekOfWeekyear() {
    return getChronology().weekOfWeekyear().get(getLocalMillis());
  }
  public int getWeekyear() {
    return getChronology().weekyear().get(getLocalMillis());
  }
  public int getYear() {
    return getChronology().year().get(getLocalMillis());
  }
  public int getYearOfCentury() {
    return getChronology().yearOfCentury().get(getLocalMillis());
  }
  public int getYearOfEra() {
    return getChronology().yearOfEra().get(getLocalMillis());
  }
  public int size() {
    return 4;
  }
  protected long getLocalMillis() {
    return iLocalMillis;
  }
  
  final public static class Property extends AbstractReadableInstantFieldProperty  {
    final private static long serialVersionUID = -358138762846288L;
    private transient LocalDateTime iInstant;
    private transient DateTimeField iField;
    Property(LocalDateTime instant, DateTimeField field) {
      super();
      iInstant = instant;
      iField = field;
    }
    protected Chronology getChronology() {
      return iInstant.getChronology();
    }
    public DateTimeField getField() {
      return iField;
    }
    public LocalDateTime addToCopy(int value) {
      return iInstant.withLocalMillis(iField.add(iInstant.getLocalMillis(), value));
    }
    public LocalDateTime addToCopy(long value) {
      return iInstant.withLocalMillis(iField.add(iInstant.getLocalMillis(), value));
    }
    public LocalDateTime addWrapFieldToCopy(int value) {
      return iInstant.withLocalMillis(iField.addWrapField(iInstant.getLocalMillis(), value));
    }
    public LocalDateTime getLocalDateTime() {
      return iInstant;
    }
    public LocalDateTime roundCeilingCopy() {
      return iInstant.withLocalMillis(iField.roundCeiling(iInstant.getLocalMillis()));
    }
    public LocalDateTime roundFloorCopy() {
      return iInstant.withLocalMillis(iField.roundFloor(iInstant.getLocalMillis()));
    }
    public LocalDateTime roundHalfCeilingCopy() {
      return iInstant.withLocalMillis(iField.roundHalfCeiling(iInstant.getLocalMillis()));
    }
    public LocalDateTime roundHalfEvenCopy() {
      return iInstant.withLocalMillis(iField.roundHalfEven(iInstant.getLocalMillis()));
    }
    public LocalDateTime roundHalfFloorCopy() {
      return iInstant.withLocalMillis(iField.roundHalfFloor(iInstant.getLocalMillis()));
    }
    public LocalDateTime setCopy(int value) {
      return iInstant.withLocalMillis(iField.set(iInstant.getLocalMillis(), value));
    }
    public LocalDateTime setCopy(String text) {
      return setCopy(text, null);
    }
    public LocalDateTime setCopy(String text, Locale locale) {
      return iInstant.withLocalMillis(iField.set(iInstant.getLocalMillis(), text, locale));
    }
    public LocalDateTime withMaximumValue() {
      return setCopy(getMaximumValue());
    }
    public LocalDateTime withMinimumValue() {
      return setCopy(getMinimumValue());
    }
    protected long getMillis() {
      return iInstant.getLocalMillis();
    }
    private void readObject(ObjectInputStream oos) throws IOException, ClassNotFoundException {
      iInstant = (LocalDateTime)oos.readObject();
      DateTimeFieldType type = (DateTimeFieldType)oos.readObject();
      iField = type.getField(iInstant.getChronology());
    }
    private void writeObject(ObjectOutputStream oos) throws IOException {
      oos.writeObject(iInstant);
      oos.writeObject(iField.getType());
    }
  }
}