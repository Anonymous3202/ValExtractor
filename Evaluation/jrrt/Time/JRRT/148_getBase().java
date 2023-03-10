package org.joda.time.chrono;
import java.util.HashMap;
import java.util.Locale;
import org.joda.time.Chronology;
import org.joda.time.DateTimeConstants;
import org.joda.time.DateTimeField;
import org.joda.time.DateTimeZone;
import org.joda.time.DurationField;
import org.joda.time.IllegalFieldValueException;
import org.joda.time.IllegalInstantException;
import org.joda.time.ReadablePartial;
import org.joda.time.field.BaseDateTimeField;
import org.joda.time.field.BaseDurationField;

final public class ZonedChronology extends AssembledChronology  {
  final private static long serialVersionUID = -1079258847191166848L;
  private ZonedChronology(Chronology base, DateTimeZone zone) {
    super(base, zone);
  }
  public Chronology withUTC() {
    return getBase();
  }
  public Chronology withZone(DateTimeZone zone) {
    if(zone == null) {
      zone = DateTimeZone.getDefault();
    }
    if(zone == getParam()) {
      return this;
    }
    if(zone == DateTimeZone.UTC) {
      Chronology var_148 = getBase();
      return var_148;
    }
    return new ZonedChronology(getBase(), zone);
  }
  private DateTimeField convertField(DateTimeField field, HashMap<Object, Object> converted) {
    if(field == null || !field.isSupported()) {
      return field;
    }
    if(converted.containsKey(field)) {
      return (DateTimeField)converted.get(field);
    }
    ZonedDateTimeField zonedField = new ZonedDateTimeField(field, getZone(), convertField(field.getDurationField(), converted), convertField(field.getRangeDurationField(), converted), convertField(field.getLeapDurationField(), converted));
    converted.put(field, zonedField);
    return zonedField;
  }
  public DateTimeZone getZone() {
    return (DateTimeZone)getParam();
  }
  private DurationField convertField(DurationField field, HashMap<Object, Object> converted) {
    if(field == null || !field.isSupported()) {
      return field;
    }
    if(converted.containsKey(field)) {
      return (DurationField)converted.get(field);
    }
    ZonedDurationField zonedField = new ZonedDurationField(field, getZone());
    converted.put(field, zonedField);
    return zonedField;
  }
  public String toString() {
    return "ZonedChronology[" + getBase() + ", " + getZone().getID() + ']';
  }
  public static ZonedChronology getInstance(Chronology base, DateTimeZone zone) {
    if(base == null) {
      throw new IllegalArgumentException("Must supply a chronology");
    }
    base = base.withUTC();
    if(base == null) {
      throw new IllegalArgumentException("UTC chronology must not be null");
    }
    if(zone == null) {
      throw new IllegalArgumentException("DateTimeZone must not be null");
    }
    return new ZonedChronology(base, zone);
  }
  public boolean equals(Object obj) {
    if(this == obj) {
      return true;
    }
    if(obj instanceof ZonedChronology == false) {
      return false;
    }
    ZonedChronology chrono = (ZonedChronology)obj;
    return getBase().equals(chrono.getBase()) && getZone().equals(chrono.getZone());
  }
  static boolean useTimeArithmetic(DurationField field) {
    return field != null && field.getUnitMillis() < DateTimeConstants.MILLIS_PER_HOUR * 12;
  }
  public int hashCode() {
    return 326565 + getZone().hashCode() * 11 + getBase().hashCode() * 7;
  }
  public long getDateTimeMillis(int year, int monthOfYear, int dayOfMonth, int millisOfDay) throws IllegalArgumentException {
    return localToUTC(getBase().getDateTimeMillis(year, monthOfYear, dayOfMonth, millisOfDay));
  }
  public long getDateTimeMillis(int year, int monthOfYear, int dayOfMonth, int hourOfDay, int minuteOfHour, int secondOfMinute, int millisOfSecond) throws IllegalArgumentException {
    return localToUTC(getBase().getDateTimeMillis(year, monthOfYear, dayOfMonth, hourOfDay, minuteOfHour, secondOfMinute, millisOfSecond));
  }
  public long getDateTimeMillis(long instant, int hourOfDay, int minuteOfHour, int secondOfMinute, int millisOfSecond) throws IllegalArgumentException {
    return localToUTC(getBase().getDateTimeMillis(instant + getZone().getOffset(instant), hourOfDay, minuteOfHour, secondOfMinute, millisOfSecond));
  }
  private long localToUTC(long localInstant) {
    DateTimeZone zone = getZone();
    int offset = zone.getOffsetFromLocal(localInstant);
    localInstant -= offset;
    if(offset != zone.getOffset(localInstant)) {
      throw new IllegalInstantException(localInstant, zone.getID());
    }
    return localInstant;
  }
  protected void assemble(Fields fields) {
    HashMap<Object, Object> converted = new HashMap<Object, Object>();
    fields.eras = convertField(fields.eras, converted);
    fields.centuries = convertField(fields.centuries, converted);
    fields.years = convertField(fields.years, converted);
    fields.months = convertField(fields.months, converted);
    fields.weekyears = convertField(fields.weekyears, converted);
    fields.weeks = convertField(fields.weeks, converted);
    fields.days = convertField(fields.days, converted);
    fields.halfdays = convertField(fields.halfdays, converted);
    fields.hours = convertField(fields.hours, converted);
    fields.minutes = convertField(fields.minutes, converted);
    fields.seconds = convertField(fields.seconds, converted);
    fields.millis = convertField(fields.millis, converted);
    fields.year = convertField(fields.year, converted);
    fields.yearOfEra = convertField(fields.yearOfEra, converted);
    fields.yearOfCentury = convertField(fields.yearOfCentury, converted);
    fields.centuryOfEra = convertField(fields.centuryOfEra, converted);
    fields.era = convertField(fields.era, converted);
    fields.dayOfWeek = convertField(fields.dayOfWeek, converted);
    fields.dayOfMonth = convertField(fields.dayOfMonth, converted);
    fields.dayOfYear = convertField(fields.dayOfYear, converted);
    fields.monthOfYear = convertField(fields.monthOfYear, converted);
    fields.weekOfWeekyear = convertField(fields.weekOfWeekyear, converted);
    fields.weekyear = convertField(fields.weekyear, converted);
    fields.weekyearOfCentury = convertField(fields.weekyearOfCentury, converted);
    fields.millisOfSecond = convertField(fields.millisOfSecond, converted);
    fields.millisOfDay = convertField(fields.millisOfDay, converted);
    fields.secondOfMinute = convertField(fields.secondOfMinute, converted);
    fields.secondOfDay = convertField(fields.secondOfDay, converted);
    fields.minuteOfHour = convertField(fields.minuteOfHour, converted);
    fields.minuteOfDay = convertField(fields.minuteOfDay, converted);
    fields.hourOfDay = convertField(fields.hourOfDay, converted);
    fields.hourOfHalfday = convertField(fields.hourOfHalfday, converted);
    fields.clockhourOfDay = convertField(fields.clockhourOfDay, converted);
    fields.clockhourOfHalfday = convertField(fields.clockhourOfHalfday, converted);
    fields.halfdayOfDay = convertField(fields.halfdayOfDay, converted);
  }
  
  final static class ZonedDateTimeField extends BaseDateTimeField  {
    @SuppressWarnings(value = {"unused", }) final private static long serialVersionUID = -3968986277775529794L;
    final DateTimeField iField;
    final DateTimeZone iZone;
    final DurationField iDurationField;
    final boolean iTimeField;
    final DurationField iRangeDurationField;
    final DurationField iLeapDurationField;
    ZonedDateTimeField(DateTimeField field, DateTimeZone zone, DurationField durationField, DurationField rangeDurationField, DurationField leapDurationField) {
      super(field.getType());
      if(!field.isSupported()) {
        throw new IllegalArgumentException();
      }
      iField = field;
      iZone = zone;
      iDurationField = durationField;
      iTimeField = useTimeArithmetic(durationField);
      iRangeDurationField = rangeDurationField;
      iLeapDurationField = leapDurationField;
    }
    final public DurationField getDurationField() {
      return iDurationField;
    }
    final public DurationField getLeapDurationField() {
      return iLeapDurationField;
    }
    final public DurationField getRangeDurationField() {
      return iRangeDurationField;
    }
    public String getAsShortText(int fieldValue, Locale locale) {
      return iField.getAsShortText(fieldValue, locale);
    }
    public String getAsShortText(long instant, Locale locale) {
      long localInstant = iZone.convertUTCToLocal(instant);
      return iField.getAsShortText(localInstant, locale);
    }
    public String getAsText(int fieldValue, Locale locale) {
      return iField.getAsText(fieldValue, locale);
    }
    public String getAsText(long instant, Locale locale) {
      long localInstant = iZone.convertUTCToLocal(instant);
      return iField.getAsText(localInstant, locale);
    }
    @Override() public boolean equals(Object obj) {
      if(this == obj) {
        return true;
      }
      else 
        if(obj instanceof ZonedDateTimeField) {
          ZonedDateTimeField other = (ZonedDateTimeField)obj;
          return iField.equals(other.iField) && iZone.equals(other.iZone) && iDurationField.equals(other.iDurationField) && iRangeDurationField.equals(other.iRangeDurationField);
        }
      return false;
    }
    public boolean isLeap(long instant) {
      long localInstant = iZone.convertUTCToLocal(instant);
      return iField.isLeap(localInstant);
    }
    public boolean isLenient() {
      return iField.isLenient();
    }
    public int get(long instant) {
      long localInstant = iZone.convertUTCToLocal(instant);
      return iField.get(localInstant);
    }
    public int getDifference(long minuendInstant, long subtrahendInstant) {
      int offset = getOffsetToAdd(subtrahendInstant);
      return iField.getDifference(minuendInstant + (iTimeField ? offset : getOffsetToAdd(minuendInstant)), subtrahendInstant + offset);
    }
    public int getLeapAmount(long instant) {
      long localInstant = iZone.convertUTCToLocal(instant);
      return iField.getLeapAmount(localInstant);
    }
    public int getMaximumShortTextLength(Locale locale) {
      return iField.getMaximumShortTextLength(locale);
    }
    public int getMaximumTextLength(Locale locale) {
      return iField.getMaximumTextLength(locale);
    }
    public int getMaximumValue() {
      return iField.getMaximumValue();
    }
    public int getMaximumValue(long instant) {
      long localInstant = iZone.convertUTCToLocal(instant);
      return iField.getMaximumValue(localInstant);
    }
    public int getMaximumValue(ReadablePartial instant) {
      return iField.getMaximumValue(instant);
    }
    public int getMaximumValue(ReadablePartial instant, int[] values) {
      return iField.getMaximumValue(instant, values);
    }
    public int getMinimumValue() {
      return iField.getMinimumValue();
    }
    public int getMinimumValue(long instant) {
      long localInstant = iZone.convertUTCToLocal(instant);
      return iField.getMinimumValue(localInstant);
    }
    public int getMinimumValue(ReadablePartial instant) {
      return iField.getMinimumValue(instant);
    }
    public int getMinimumValue(ReadablePartial instant, int[] values) {
      return iField.getMinimumValue(instant, values);
    }
    private int getOffsetToAdd(long instant) {
      int offset = this.iZone.getOffset(instant);
      long sum = instant + offset;
      if((instant ^ sum) < 0 && (instant ^ offset) >= 0) {
        throw new ArithmeticException("Adding time zone offset caused overflow");
      }
      return offset;
    }
    @Override() public int hashCode() {
      return iField.hashCode() ^ iZone.hashCode();
    }
    public long add(long instant, int value) {
      if(iTimeField) {
        int offset = getOffsetToAdd(instant);
        long localInstant = iField.add(instant + offset, value);
        return localInstant - offset;
      }
      else {
        long localInstant = iZone.convertUTCToLocal(instant);
        localInstant = iField.add(localInstant, value);
        return iZone.convertLocalToUTC(localInstant, false, instant);
      }
    }
    public long add(long instant, long value) {
      if(iTimeField) {
        int offset = getOffsetToAdd(instant);
        long localInstant = iField.add(instant + offset, value);
        return localInstant - offset;
      }
      else {
        long localInstant = iZone.convertUTCToLocal(instant);
        localInstant = iField.add(localInstant, value);
        return iZone.convertLocalToUTC(localInstant, false, instant);
      }
    }
    public long addWrapField(long instant, int value) {
      if(iTimeField) {
        int offset = getOffsetToAdd(instant);
        long localInstant = iField.addWrapField(instant + offset, value);
        return localInstant - offset;
      }
      else {
        long localInstant = iZone.convertUTCToLocal(instant);
        localInstant = iField.addWrapField(localInstant, value);
        return iZone.convertLocalToUTC(localInstant, false, instant);
      }
    }
    public long getDifferenceAsLong(long minuendInstant, long subtrahendInstant) {
      int offset = getOffsetToAdd(subtrahendInstant);
      return iField.getDifferenceAsLong(minuendInstant + (iTimeField ? offset : getOffsetToAdd(minuendInstant)), subtrahendInstant + offset);
    }
    public long remainder(long instant) {
      long localInstant = iZone.convertUTCToLocal(instant);
      return iField.remainder(localInstant);
    }
    public long roundCeiling(long instant) {
      if(iTimeField) {
        int offset = getOffsetToAdd(instant);
        instant = iField.roundCeiling(instant + offset);
        return instant - offset;
      }
      else {
        long localInstant = iZone.convertUTCToLocal(instant);
        localInstant = iField.roundCeiling(localInstant);
        return iZone.convertLocalToUTC(localInstant, false, instant);
      }
    }
    public long roundFloor(long instant) {
      if(iTimeField) {
        int offset = getOffsetToAdd(instant);
        instant = iField.roundFloor(instant + offset);
        return instant - offset;
      }
      else {
        long localInstant = iZone.convertUTCToLocal(instant);
        localInstant = iField.roundFloor(localInstant);
        return iZone.convertLocalToUTC(localInstant, false, instant);
      }
    }
    public long set(long instant, int value) {
      long localInstant = iZone.convertUTCToLocal(instant);
      localInstant = iField.set(localInstant, value);
      long result = iZone.convertLocalToUTC(localInstant, false, instant);
      if(get(result) != value) {
        IllegalInstantException cause = new IllegalInstantException(localInstant, iZone.getID());
        IllegalFieldValueException ex = new IllegalFieldValueException(iField.getType(), Integer.valueOf(value), cause.getMessage());
        ex.initCause(cause);
        throw ex;
      }
      return result;
    }
    public long set(long instant, String text, Locale locale) {
      long localInstant = iZone.convertUTCToLocal(instant);
      localInstant = iField.set(localInstant, text, locale);
      return iZone.convertLocalToUTC(localInstant, false, instant);
    }
  }
  
  static class ZonedDurationField extends BaseDurationField  {
    final private static long serialVersionUID = -485345310999208286L;
    final DurationField iField;
    final boolean iTimeField;
    final DateTimeZone iZone;
    ZonedDurationField(DurationField field, DateTimeZone zone) {
      super(field.getType());
      if(!field.isSupported()) {
        throw new IllegalArgumentException();
      }
      iField = field;
      iTimeField = useTimeArithmetic(field);
      iZone = zone;
    }
    @Override() public boolean equals(Object obj) {
      if(this == obj) {
        return true;
      }
      else 
        if(obj instanceof ZonedDurationField) {
          ZonedDurationField other = (ZonedDurationField)obj;
          return iField.equals(other.iField) && iZone.equals(other.iZone);
        }
      return false;
    }
    public boolean isPrecise() {
      return iTimeField ? iField.isPrecise() : iField.isPrecise() && this.iZone.isFixed();
    }
    public int getDifference(long minuendInstant, long subtrahendInstant) {
      int offset = getOffsetToAdd(subtrahendInstant);
      return iField.getDifference(minuendInstant + (iTimeField ? offset : getOffsetToAdd(minuendInstant)), subtrahendInstant + offset);
    }
    private int getOffsetFromLocalToSubtract(long instant) {
      int offset = this.iZone.getOffsetFromLocal(instant);
      long diff = instant - offset;
      if((instant ^ diff) < 0 && (instant ^ offset) < 0) {
        throw new ArithmeticException("Subtracting time zone offset caused overflow");
      }
      return offset;
    }
    private int getOffsetToAdd(long instant) {
      int offset = this.iZone.getOffset(instant);
      long sum = instant + offset;
      if((instant ^ sum) < 0 && (instant ^ offset) >= 0) {
        throw new ArithmeticException("Adding time zone offset caused overflow");
      }
      return offset;
    }
    public int getValue(long duration, long instant) {
      return iField.getValue(duration, addOffset(instant));
    }
    @Override() public int hashCode() {
      return iField.hashCode() ^ iZone.hashCode();
    }
    public long add(long instant, int value) {
      int offset = getOffsetToAdd(instant);
      instant = iField.add(instant + offset, value);
      return instant - (iTimeField ? offset : getOffsetFromLocalToSubtract(instant));
    }
    public long add(long instant, long value) {
      int offset = getOffsetToAdd(instant);
      instant = iField.add(instant + offset, value);
      return instant - (iTimeField ? offset : getOffsetFromLocalToSubtract(instant));
    }
    private long addOffset(long instant) {
      return iZone.convertUTCToLocal(instant);
    }
    public long getDifferenceAsLong(long minuendInstant, long subtrahendInstant) {
      int offset = getOffsetToAdd(subtrahendInstant);
      return iField.getDifferenceAsLong(minuendInstant + (iTimeField ? offset : getOffsetToAdd(minuendInstant)), subtrahendInstant + offset);
    }
    public long getMillis(int value, long instant) {
      return iField.getMillis(value, addOffset(instant));
    }
    public long getMillis(long value, long instant) {
      return iField.getMillis(value, addOffset(instant));
    }
    public long getUnitMillis() {
      return iField.getUnitMillis();
    }
    public long getValueAsLong(long duration, long instant) {
      return iField.getValueAsLong(duration, addOffset(instant));
    }
  }
}