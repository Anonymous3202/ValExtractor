package org.joda.time.format;
import java.io.IOException;
import java.io.Writer;
import java.util.Locale;
import org.joda.time.Chronology;
import org.joda.time.DateTime;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;
import org.joda.time.LocalTime;
import org.joda.time.MutableDateTime;
import org.joda.time.ReadWritableInstant;
import org.joda.time.ReadableInstant;
import org.joda.time.ReadablePartial;

public class DateTimeFormatter  {
  final private DateTimePrinter iPrinter;
  final private DateTimeParser iParser;
  final private Locale iLocale;
  final private boolean iOffsetParsed;
  final private Chronology iChrono;
  final private DateTimeZone iZone;
  final private Integer iPivotYear;
  final private int iDefaultYear;
  public DateTimeFormatter(DateTimePrinter printer, DateTimeParser parser) {
    super();
    iPrinter = printer;
    iParser = parser;
    iLocale = null;
    iOffsetParsed = false;
    iChrono = null;
    iZone = null;
    iPivotYear = null;
    iDefaultYear = 2000;
  }
  private DateTimeFormatter(DateTimePrinter printer, DateTimeParser parser, Locale locale, boolean offsetParsed, Chronology chrono, DateTimeZone zone, Integer pivotYear, int defaultYear) {
    super();
    iPrinter = printer;
    iParser = parser;
    iLocale = locale;
    iOffsetParsed = offsetParsed;
    iChrono = chrono;
    iZone = zone;
    iPivotYear = pivotYear;
    iDefaultYear = defaultYear;
  }
  @Deprecated() public Chronology getChronolgy() {
    return iChrono;
  }
  public Chronology getChronology() {
    return iChrono;
  }
  private Chronology selectChronology(Chronology chrono) {
    chrono = DateTimeUtils.getChronology(chrono);
    if(iChrono != null) {
      chrono = iChrono;
    }
    if(iZone != null) {
      chrono = chrono.withZone(iZone);
    }
    return chrono;
  }
  public DateTime parseDateTime(String text) {
    DateTimeParser parser = requireParser();
    Chronology chrono = selectChronology(null);
    DateTimeParserBucket bucket = new DateTimeParserBucket(0, chrono, iLocale, iPivotYear, iDefaultYear);
    int newPos = parser.parseInto(bucket, text, 0);
    if(newPos >= 0) {
      if(newPos >= text.length()) {
        long millis = bucket.computeMillis(true, text);
        if(iOffsetParsed && bucket.getOffsetInteger() != null) {
          int parsedOffset = bucket.getOffsetInteger();
          DateTimeZone parsedZone = DateTimeZone.forOffsetMillis(parsedOffset);
          chrono = chrono.withZone(parsedZone);
        }
        else {
          DateTimeZone var_523 = bucket.getZone();
          if(var_523 != null) {
            chrono = chrono.withZone(bucket.getZone());
          }
        }
        DateTime dt = new DateTime(millis, chrono);
        if(iZone != null) {
          dt = dt.withZone(iZone);
        }
        return dt;
      }
    }
    else {
      newPos = ~newPos;
    }
    throw new IllegalArgumentException(FormatUtils.createErrorMessage(text, newPos));
  }
  public DateTimeFormatter withChronology(Chronology chrono) {
    if(iChrono == chrono) {
      return this;
    }
    return new DateTimeFormatter(iPrinter, iParser, iLocale, iOffsetParsed, chrono, iZone, iPivotYear, iDefaultYear);
  }
  public DateTimeFormatter withDefaultYear(int defaultYear) {
    return new DateTimeFormatter(iPrinter, iParser, iLocale, iOffsetParsed, iChrono, iZone, iPivotYear, defaultYear);
  }
  public DateTimeFormatter withLocale(Locale locale) {
    if(locale == getLocale() || (locale != null && locale.equals(getLocale()))) {
      return this;
    }
    return new DateTimeFormatter(iPrinter, iParser, locale, iOffsetParsed, iChrono, iZone, iPivotYear, iDefaultYear);
  }
  public DateTimeFormatter withOffsetParsed() {
    if(iOffsetParsed == true) {
      return this;
    }
    return new DateTimeFormatter(iPrinter, iParser, iLocale, true, iChrono, null, iPivotYear, iDefaultYear);
  }
  public DateTimeFormatter withPivotYear(int pivotYear) {
    return withPivotYear(Integer.valueOf(pivotYear));
  }
  public DateTimeFormatter withPivotYear(Integer pivotYear) {
    if(iPivotYear == pivotYear || (iPivotYear != null && iPivotYear.equals(pivotYear))) {
      return this;
    }
    return new DateTimeFormatter(iPrinter, iParser, iLocale, iOffsetParsed, iChrono, iZone, pivotYear, iDefaultYear);
  }
  public DateTimeFormatter withZone(DateTimeZone zone) {
    if(iZone == zone) {
      return this;
    }
    return new DateTimeFormatter(iPrinter, iParser, iLocale, false, iChrono, zone, iPivotYear, iDefaultYear);
  }
  public DateTimeFormatter withZoneUTC() {
    return withZone(DateTimeZone.UTC);
  }
  public DateTimeParser getParser() {
    return iParser;
  }
  private DateTimeParser requireParser() {
    DateTimeParser parser = iParser;
    if(parser == null) {
      throw new UnsupportedOperationException("Parsing not supported");
    }
    return parser;
  }
  public DateTimePrinter getPrinter() {
    return iPrinter;
  }
  private DateTimePrinter requirePrinter() {
    DateTimePrinter printer = iPrinter;
    if(printer == null) {
      throw new UnsupportedOperationException("Printing not supported");
    }
    return printer;
  }
  public DateTimeZone getZone() {
    return iZone;
  }
  public Integer getPivotYear() {
    return iPivotYear;
  }
  public LocalDate parseLocalDate(String text) {
    return parseLocalDateTime(text).toLocalDate();
  }
  public LocalDateTime parseLocalDateTime(String text) {
    DateTimeParser parser = requireParser();
    Chronology chrono = selectChronology(null).withUTC();
    DateTimeParserBucket bucket = new DateTimeParserBucket(0, chrono, iLocale, iPivotYear, iDefaultYear);
    int newPos = parser.parseInto(bucket, text, 0);
    if(newPos >= 0) {
      if(newPos >= text.length()) {
        long millis = bucket.computeMillis(true, text);
        if(bucket.getOffsetInteger() != null) {
          int parsedOffset = bucket.getOffsetInteger();
          DateTimeZone parsedZone = DateTimeZone.forOffsetMillis(parsedOffset);
          chrono = chrono.withZone(parsedZone);
        }
        else 
          if(bucket.getZone() != null) {
            chrono = chrono.withZone(bucket.getZone());
          }
        return new LocalDateTime(millis, chrono);
      }
    }
    else {
      newPos = ~newPos;
    }
    throw new IllegalArgumentException(FormatUtils.createErrorMessage(text, newPos));
  }
  public LocalTime parseLocalTime(String text) {
    return parseLocalDateTime(text).toLocalTime();
  }
  public Locale getLocale() {
    return iLocale;
  }
  public MutableDateTime parseMutableDateTime(String text) {
    DateTimeParser parser = requireParser();
    Chronology chrono = selectChronology(null);
    DateTimeParserBucket bucket = new DateTimeParserBucket(0, chrono, iLocale, iPivotYear, iDefaultYear);
    int newPos = parser.parseInto(bucket, text, 0);
    if(newPos >= 0) {
      if(newPos >= text.length()) {
        long millis = bucket.computeMillis(true, text);
        if(iOffsetParsed && bucket.getOffsetInteger() != null) {
          int parsedOffset = bucket.getOffsetInteger();
          DateTimeZone parsedZone = DateTimeZone.forOffsetMillis(parsedOffset);
          chrono = chrono.withZone(parsedZone);
        }
        else 
          if(bucket.getZone() != null) {
            chrono = chrono.withZone(bucket.getZone());
          }
        MutableDateTime dt = new MutableDateTime(millis, chrono);
        if(iZone != null) {
          dt.setZone(iZone);
        }
        return dt;
      }
    }
    else {
      newPos = ~newPos;
    }
    throw new IllegalArgumentException(FormatUtils.createErrorMessage(text, newPos));
  }
  public String print(long instant) {
    StringBuffer buf = new StringBuffer(requirePrinter().estimatePrintedLength());
    printTo(buf, instant);
    return buf.toString();
  }
  public String print(ReadableInstant instant) {
    StringBuffer buf = new StringBuffer(requirePrinter().estimatePrintedLength());
    printTo(buf, instant);
    return buf.toString();
  }
  public String print(ReadablePartial partial) {
    StringBuffer buf = new StringBuffer(requirePrinter().estimatePrintedLength());
    printTo(buf, partial);
    return buf.toString();
  }
  public boolean isOffsetParsed() {
    return iOffsetParsed;
  }
  public boolean isParser() {
    return (iParser != null);
  }
  public boolean isPrinter() {
    return (iPrinter != null);
  }
  public int getDefaultYear() {
    return iDefaultYear;
  }
  public int parseInto(ReadWritableInstant instant, String text, int position) {
    DateTimeParser parser = requireParser();
    if(instant == null) {
      throw new IllegalArgumentException("Instant must not be null");
    }
    long instantMillis = instant.getMillis();
    Chronology chrono = instant.getChronology();
    int defaultYear = DateTimeUtils.getChronology(chrono).year().get(instantMillis);
    long instantLocal = instantMillis + chrono.getZone().getOffset(instantMillis);
    chrono = selectChronology(chrono);
    DateTimeParserBucket bucket = new DateTimeParserBucket(instantLocal, chrono, iLocale, iPivotYear, defaultYear);
    int newPos = parser.parseInto(bucket, text, position);
    instant.setMillis(bucket.computeMillis(false, text));
    if(iOffsetParsed && bucket.getOffsetInteger() != null) {
      int parsedOffset = bucket.getOffsetInteger();
      DateTimeZone parsedZone = DateTimeZone.forOffsetMillis(parsedOffset);
      chrono = chrono.withZone(parsedZone);
    }
    else 
      if(bucket.getZone() != null) {
        chrono = chrono.withZone(bucket.getZone());
      }
    instant.setChronology(chrono);
    if(iZone != null) {
      instant.setZone(iZone);
    }
    return newPos;
  }
  public long parseMillis(String text) {
    DateTimeParser parser = requireParser();
    Chronology chrono = selectChronology(iChrono);
    DateTimeParserBucket bucket = new DateTimeParserBucket(0, chrono, iLocale, iPivotYear, iDefaultYear);
    int newPos = parser.parseInto(bucket, text, 0);
    if(newPos >= 0) {
      if(newPos >= text.length()) {
        return bucket.computeMillis(true, text);
      }
    }
    else {
      newPos = ~newPos;
    }
    throw new IllegalArgumentException(FormatUtils.createErrorMessage(text, newPos));
  }
  public void printTo(Writer out, long instant) throws IOException {
    printTo(out, instant, null);
  }
  private void printTo(Writer buf, long instant, Chronology chrono) throws IOException {
    DateTimePrinter printer = requirePrinter();
    chrono = selectChronology(chrono);
    DateTimeZone zone = chrono.getZone();
    int offset = zone.getOffset(instant);
    long adjustedInstant = instant + offset;
    if((instant ^ adjustedInstant) < 0 && (instant ^ offset) >= 0) {
      zone = DateTimeZone.UTC;
      offset = 0;
      adjustedInstant = instant;
    }
    printer.printTo(buf, adjustedInstant, chrono.withUTC(), offset, zone, iLocale);
  }
  public void printTo(Writer out, ReadableInstant instant) throws IOException {
    long millis = DateTimeUtils.getInstantMillis(instant);
    Chronology chrono = DateTimeUtils.getInstantChronology(instant);
    printTo(out, millis, chrono);
  }
  public void printTo(Writer out, ReadablePartial partial) throws IOException {
    DateTimePrinter printer = requirePrinter();
    if(partial == null) {
      throw new IllegalArgumentException("The partial must not be null");
    }
    printer.printTo(out, partial, iLocale);
  }
  public void printTo(Appendable appendable, long instant) throws IOException {
    appendable.append(print(instant));
  }
  public void printTo(Appendable appendable, ReadableInstant instant) throws IOException {
    appendable.append(print(instant));
  }
  public void printTo(Appendable appendable, ReadablePartial partial) throws IOException {
    appendable.append(print(partial));
  }
  public void printTo(StringBuffer buf, long instant) {
    printTo(buf, instant, null);
  }
  private void printTo(StringBuffer buf, long instant, Chronology chrono) {
    DateTimePrinter printer = requirePrinter();
    chrono = selectChronology(chrono);
    DateTimeZone zone = chrono.getZone();
    int offset = zone.getOffset(instant);
    long adjustedInstant = instant + offset;
    if((instant ^ adjustedInstant) < 0 && (instant ^ offset) >= 0) {
      zone = DateTimeZone.UTC;
      offset = 0;
      adjustedInstant = instant;
    }
    printer.printTo(buf, adjustedInstant, chrono.withUTC(), offset, zone, iLocale);
  }
  public void printTo(StringBuffer buf, ReadableInstant instant) {
    long millis = DateTimeUtils.getInstantMillis(instant);
    Chronology chrono = DateTimeUtils.getInstantChronology(instant);
    printTo(buf, millis, chrono);
  }
  public void printTo(StringBuffer buf, ReadablePartial partial) {
    DateTimePrinter printer = requirePrinter();
    if(partial == null) {
      throw new IllegalArgumentException("The partial must not be null");
    }
    printer.printTo(buf, partial, iLocale);
  }
}