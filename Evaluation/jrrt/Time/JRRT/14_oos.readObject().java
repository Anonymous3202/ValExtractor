package org.joda.time;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Locale;
import org.joda.convert.FromString;
import org.joda.time.base.BaseDateTime;
import org.joda.time.field.AbstractReadableInstantFieldProperty;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

@Deprecated() final public class DateMidnight extends BaseDateTime implements ReadableDateTime, Serializable  {
  final private static long serialVersionUID = 156371964018738L;
  public DateMidnight() {
    super();
  }
  public DateMidnight(Chronology chronology) {
    super(chronology);
  }
  public DateMidnight(DateTimeZone zone) {
    super(zone);
  }
  public DateMidnight(Object instant) {
    super(instant, (Chronology)null);
  }
  public DateMidnight(Object instant, Chronology chronology) {
    super(instant, DateTimeUtils.getChronology(chronology));
  }
  public DateMidnight(Object instant, DateTimeZone zone) {
    super(instant, zone);
  }
  public DateMidnight(int year, int monthOfYear, int dayOfMonth) {
    super(year, monthOfYear, dayOfMonth, 0, 0, 0, 0);
  }
  public DateMidnight(int year, int monthOfYear, int dayOfMonth, Chronology chronology) {
    super(year, monthOfYear, dayOfMonth, 0, 0, 0, 0, chronology);
  }
  public DateMidnight(int year, int monthOfYear, int dayOfMonth, DateTimeZone zone) {
    super(year, monthOfYear, dayOfMonth, 0, 0, 0, 0, zone);
  }
  public DateMidnight(long instant) {
    super(instant);
  }
  public DateMidnight(long instant, Chronology chronology) {
    super(instant, chronology);
  }
  public DateMidnight(long instant, DateTimeZone zone) {
    super(instant, zone);
  }
  public DateMidnight minus(long duration) {
    return withDurationAdded(duration, -1);
  }
  public DateMidnight minus(ReadableDuration duration) {
    return withDurationAdded(duration, -1);
  }
  public DateMidnight minus(ReadablePeriod period) {
    return withPeriodAdded(period, -1);
  }
  public DateMidnight minusDays(int days) {
    if(days == 0) {
      return this;
    }
    long instant = getChronology().days().subtract(getMillis(), days);
    return withMillis(instant);
  }
  public DateMidnight minusMonths(int months) {
    if(months == 0) {
      return this;
    }
    long instant = getChronology().months().subtract(getMillis(), months);
    return withMillis(instant);
  }
  public DateMidnight minusWeeks(int weeks) {
    if(weeks == 0) {
      return this;
    }
    long instant = getChronology().weeks().subtract(getMillis(), weeks);
    return withMillis(instant);
  }
  public DateMidnight minusYears(int years) {
    if(years == 0) {
      return this;
    }
    long instant = getChronology().years().subtract(getMillis(), years);
    return withMillis(instant);
  }
  public static DateMidnight now() {
    return new DateMidnight();
  }
  public static DateMidnight now(Chronology chronology) {
    if(chronology == null) {
      throw new NullPointerException("Chronology must not be null");
    }
    return new DateMidnight(chronology);
  }
  public static DateMidnight now(DateTimeZone zone) {
    if(zone == null) {
      throw new NullPointerException("Zone must not be null");
    }
    return new DateMidnight(zone);
  }
  @FromString() public static DateMidnight parse(String str) {
    return parse(str, ISODateTimeFormat.dateTimeParser().withOffsetParsed());
  }
  public static DateMidnight parse(String str, DateTimeFormatter formatter) {
    return formatter.parseDateTime(str).toDateMidnight();
  }
  public DateMidnight plus(long duration) {
    return withDurationAdded(duration, 1);
  }
  public DateMidnight plus(ReadableDuration duration) {
    return withDurationAdded(duration, 1);
  }
  public DateMidnight plus(ReadablePeriod period) {
    return withPeriodAdded(period, 1);
  }
  public DateMidnight plusDays(int days) {
    if(days == 0) {
      return this;
    }
    long instant = getChronology().days().add(getMillis(), days);
    return withMillis(instant);
  }
  public DateMidnight plusMonths(int months) {
    if(months == 0) {
      return this;
    }
    long instant = getChronology().months().add(getMillis(), months);
    return withMillis(instant);
  }
  public DateMidnight plusWeeks(int weeks) {
    if(weeks == 0) {
      return this;
    }
    long instant = getChronology().weeks().add(getMillis(), weeks);
    return withMillis(instant);
  }
  public DateMidnight plusYears(int years) {
    if(years == 0) {
      return this;
    }
    long instant = getChronology().years().add(getMillis(), years);
    return withMillis(instant);
  }
  public DateMidnight withCenturyOfEra(int centuryOfEra) {
    return withMillis(getChronology().centuryOfEra().set(getMillis(), centuryOfEra));
  }
  public DateMidnight withChronology(Chronology newChronology) {
    return (newChronology == getChronology() ? this : new DateMidnight(getMillis(), newChronology));
  }
  public DateMidnight withDayOfMonth(int dayOfMonth) {
    return withMillis(getChronology().dayOfMonth().set(getMillis(), dayOfMonth));
  }
  public DateMidnight withDayOfWeek(int dayOfWeek) {
    return withMillis(getChronology().dayOfWeek().set(getMillis(), dayOfWeek));
  }
  public DateMidnight withDayOfYear(int dayOfYear) {
    return withMillis(getChronology().dayOfYear().set(getMillis(), dayOfYear));
  }
  public DateMidnight withDurationAdded(long durationToAdd, int scalar) {
    if(durationToAdd == 0 || scalar == 0) {
      return this;
    }
    long instant = getChronology().add(getMillis(), durationToAdd, scalar);
    return withMillis(instant);
  }
  public DateMidnight withDurationAdded(ReadableDuration durationToAdd, int scalar) {
    if(durationToAdd == null || scalar == 0) {
      return this;
    }
    return withDurationAdded(durationToAdd.getMillis(), scalar);
  }
  public DateMidnight withEra(int era) {
    return withMillis(getChronology().era().set(getMillis(), era));
  }
  public DateMidnight withField(DateTimeFieldType fieldType, int value) {
    if(fieldType == null) {
      throw new IllegalArgumentException("Field must not be null");
    }
    long instant = fieldType.getField(getChronology()).set(getMillis(), value);
    return withMillis(instant);
  }
  public DateMidnight withFieldAdded(DurationFieldType fieldType, int amount) {
    if(fieldType == null) {
      throw new IllegalArgumentException("Field must not be null");
    }
    if(amount == 0) {
      return this;
    }
    long instant = fieldType.getField(getChronology()).add(getMillis(), amount);
    return withMillis(instant);
  }
  public DateMidnight withFields(ReadablePartial partial) {
    if(partial == null) {
      return this;
    }
    return withMillis(getChronology().set(partial, getMillis()));
  }
  public DateMidnight withMillis(long newMillis) {
    Chronology chrono = getChronology();
    newMillis = checkInstant(newMillis, chrono);
    return (newMillis == getMillis() ? this : new DateMidnight(newMillis, chrono));
  }
  public DateMidnight withMonthOfYear(int monthOfYear) {
    return withMillis(getChronology().monthOfYear().set(getMillis(), monthOfYear));
  }
  public DateMidnight withPeriodAdded(ReadablePeriod period, int scalar) {
    if(period == null || scalar == 0) {
      return this;
    }
    long instant = getChronology().add(period, getMillis(), scalar);
    return withMillis(instant);
  }
  public DateMidnight withWeekOfWeekyear(int weekOfWeekyear) {
    return withMillis(getChronology().weekOfWeekyear().set(getMillis(), weekOfWeekyear));
  }
  public DateMidnight withWeekyear(int weekyear) {
    return withMillis(getChronology().weekyear().set(getMillis(), weekyear));
  }
  public DateMidnight withYear(int year) {
    return withMillis(getChronology().year().set(getMillis(), year));
  }
  public DateMidnight withYearOfCentury(int yearOfCentury) {
    return withMillis(getChronology().yearOfCentury().set(getMillis(), yearOfCentury));
  }
  public DateMidnight withYearOfEra(int yearOfEra) {
    return withMillis(getChronology().yearOfEra().set(getMillis(), yearOfEra));
  }
  public DateMidnight withZoneRetainFields(DateTimeZone newZone) {
    newZone = DateTimeUtils.getZone(newZone);
    DateTimeZone originalZone = DateTimeUtils.getZone(getZone());
    if(newZone == originalZone) {
      return this;
    }
    long millis = originalZone.getMillisKeepLocal(newZone, getMillis());
    return new DateMidnight(millis, getChronology().withZone(newZone));
  }
  public Interval toInterval() {
    Chronology chrono = getChronology();
    long start = getMillis();
    long end = DurationFieldType.days().getField(chrono).add(start, 1);
    return new Interval(start, end, chrono);
  }
  public LocalDate toLocalDate() {
    return new LocalDate(getMillis(), getChronology());
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
  public Property monthOfYear() {
    return new Property(this, getChronology().monthOfYear());
  }
  public Property property(DateTimeFieldType type) {
    if(type == null) {
      throw new IllegalArgumentException("The DateTimeFieldType must not be null");
    }
    DateTimeField field = type.getField(getChronology());
    if(field.isSupported() == false) {
      throw new IllegalArgumentException("Field \'" + type + "\' is not supported");
    }
    return new Property(this, field);
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
  @Deprecated() public YearMonthDay toYearMonthDay() {
    return new YearMonthDay(getMillis(), getChronology());
  }
  protected long checkInstant(long instant, Chronology chronology) {
    return chronology.dayOfMonth().roundFloor(instant);
  }
  
  final public static class Property extends AbstractReadableInstantFieldProperty  {
    final private static long serialVersionUID = 257629620L;
    private DateMidnight iInstant;
    private DateTimeField iField;
    Property(DateMidnight instant, DateTimeField field) {
      super();
      iInstant = instant;
      iField = field;
    }
    protected Chronology getChronology() {
      return iInstant.getChronology();
    }
    public DateMidnight addToCopy(int value) {
      return iInstant.withMillis(iField.add(iInstant.getMillis(), value));
    }
    public DateMidnight addToCopy(long value) {
      return iInstant.withMillis(iField.add(iInstant.getMillis(), value));
    }
    public DateMidnight addWrapFieldToCopy(int value) {
      return iInstant.withMillis(iField.addWrapField(iInstant.getMillis(), value));
    }
    public DateMidnight getDateMidnight() {
      return iInstant;
    }
    public DateMidnight roundCeilingCopy() {
      return iInstant.withMillis(iField.roundCeiling(iInstant.getMillis()));
    }
    public DateMidnight roundFloorCopy() {
      return iInstant.withMillis(iField.roundFloor(iInstant.getMillis()));
    }
    public DateMidnight roundHalfCeilingCopy() {
      return iInstant.withMillis(iField.roundHalfCeiling(iInstant.getMillis()));
    }
    public DateMidnight roundHalfEvenCopy() {
      return iInstant.withMillis(iField.roundHalfEven(iInstant.getMillis()));
    }
    public DateMidnight roundHalfFloorCopy() {
      return iInstant.withMillis(iField.roundHalfFloor(iInstant.getMillis()));
    }
    public DateMidnight setCopy(int value) {
      return iInstant.withMillis(iField.set(iInstant.getMillis(), value));
    }
    public DateMidnight setCopy(String text) {
      return setCopy(text, null);
    }
    public DateMidnight setCopy(String text, Locale locale) {
      return iInstant.withMillis(iField.set(iInstant.getMillis(), text, locale));
    }
    public DateMidnight withMaximumValue() {
      return setCopy(getMaximumValue());
    }
    public DateMidnight withMinimumValue() {
      return setCopy(getMinimumValue());
    }
    public DateTimeField getField() {
      return iField;
    }
    protected long getMillis() {
      return iInstant.getMillis();
    }
    private void readObject(ObjectInputStream oos) throws IOException, ClassNotFoundException {
      Object var_14 = oos.readObject();
      iInstant = (DateMidnight)var_14;
      DateTimeFieldType type = (DateTimeFieldType)oos.readObject();
      iField = type.getField(iInstant.getChronology());
    }
    private void writeObject(ObjectOutputStream oos) throws IOException {
      oos.writeObject(iInstant);
      oos.writeObject(iField.getType());
    }
  }
}