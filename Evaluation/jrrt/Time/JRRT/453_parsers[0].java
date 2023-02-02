package org.joda.time.format;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.joda.time.Chronology;
import org.joda.time.DateTimeConstants;
import org.joda.time.DateTimeField;
import org.joda.time.DateTimeFieldType;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.MutableDateTime;
import org.joda.time.ReadablePartial;
import org.joda.time.MutableDateTime.Property;
import org.joda.time.field.MillisDurationField;
import org.joda.time.field.PreciseDateTimeField;

public class DateTimeFormatterBuilder  {
  private ArrayList<Object> iElementPairs;
  private Object iFormatter;
  public DateTimeFormatterBuilder() {
    super();
    iElementPairs = new ArrayList<Object>();
  }
  public DateTimeFormatter toFormatter() {
    Object f = getFormatter();
    DateTimePrinter printer = null;
    if(isPrinter(f)) {
      printer = (DateTimePrinter)f;
    }
    DateTimeParser parser = null;
    if(isParser(f)) {
      parser = (DateTimeParser)f;
    }
    if(printer != null || parser != null) {
      return new DateTimeFormatter(printer, parser);
    }
    throw new UnsupportedOperationException("Both printing and parsing not supported");
  }
  public DateTimeFormatterBuilder append(DateTimeFormatter formatter) {
    if(formatter == null) {
      throw new IllegalArgumentException("No formatter supplied");
    }
    return append0(formatter.getPrinter(), formatter.getParser());
  }
  public DateTimeFormatterBuilder append(DateTimeParser parser) {
    checkParser(parser);
    return append0(null, parser);
  }
  public DateTimeFormatterBuilder append(DateTimePrinter printer) {
    checkPrinter(printer);
    return append0(printer, null);
  }
  public DateTimeFormatterBuilder append(DateTimePrinter printer, DateTimeParser parser) {
    checkPrinter(printer);
    checkParser(parser);
    return append0(printer, parser);
  }
  public DateTimeFormatterBuilder append(DateTimePrinter printer, DateTimeParser[] parsers) {
    if(printer != null) {
      checkPrinter(printer);
    }
    if(parsers == null) {
      throw new IllegalArgumentException("No parsers supplied");
    }
    int length = parsers.length;
    if(length == 1) {
      DateTimeParser var_453 = parsers[0];
      if(var_453 == null) {
        throw new IllegalArgumentException("No parser supplied");
      }
      return append0(printer, parsers[0]);
    }
    DateTimeParser[] copyOfParsers = new DateTimeParser[length];
    int i;
    for(i = 0; i < length - 1; i++) {
      if((copyOfParsers[i] = parsers[i]) == null) {
        throw new IllegalArgumentException("Incomplete parser array");
      }
    }
    copyOfParsers[i] = parsers[i];
    return append0(printer, new MatchingParser(copyOfParsers));
  }
  private DateTimeFormatterBuilder append0(Object element) {
    iFormatter = null;
    iElementPairs.add(element);
    iElementPairs.add(element);
    return this;
  }
  private DateTimeFormatterBuilder append0(DateTimePrinter printer, DateTimeParser parser) {
    iFormatter = null;
    iElementPairs.add(printer);
    iElementPairs.add(parser);
    return this;
  }
  public DateTimeFormatterBuilder appendCenturyOfEra(int minDigits, int maxDigits) {
    return appendSignedDecimal(DateTimeFieldType.centuryOfEra(), minDigits, maxDigits);
  }
  public DateTimeFormatterBuilder appendClockhourOfDay(int minDigits) {
    return appendDecimal(DateTimeFieldType.clockhourOfDay(), minDigits, 2);
  }
  public DateTimeFormatterBuilder appendClockhourOfHalfday(int minDigits) {
    return appendDecimal(DateTimeFieldType.clockhourOfHalfday(), minDigits, 2);
  }
  public DateTimeFormatterBuilder appendDayOfMonth(int minDigits) {
    return appendDecimal(DateTimeFieldType.dayOfMonth(), minDigits, 2);
  }
  public DateTimeFormatterBuilder appendDayOfWeek(int minDigits) {
    return appendDecimal(DateTimeFieldType.dayOfWeek(), minDigits, 1);
  }
  public DateTimeFormatterBuilder appendDayOfWeekShortText() {
    return appendShortText(DateTimeFieldType.dayOfWeek());
  }
  public DateTimeFormatterBuilder appendDayOfWeekText() {
    return appendText(DateTimeFieldType.dayOfWeek());
  }
  public DateTimeFormatterBuilder appendDayOfYear(int minDigits) {
    return appendDecimal(DateTimeFieldType.dayOfYear(), minDigits, 3);
  }
  public DateTimeFormatterBuilder appendDecimal(DateTimeFieldType fieldType, int minDigits, int maxDigits) {
    if(fieldType == null) {
      throw new IllegalArgumentException("Field type must not be null");
    }
    if(maxDigits < minDigits) {
      maxDigits = minDigits;
    }
    if(minDigits < 0 || maxDigits <= 0) {
      throw new IllegalArgumentException();
    }
    if(minDigits <= 1) {
      return append0(new UnpaddedNumber(fieldType, maxDigits, false));
    }
    else {
      return append0(new PaddedNumber(fieldType, maxDigits, false, minDigits));
    }
  }
  public DateTimeFormatterBuilder appendEraText() {
    return appendText(DateTimeFieldType.era());
  }
  public DateTimeFormatterBuilder appendFixedDecimal(DateTimeFieldType fieldType, int numDigits) {
    if(fieldType == null) {
      throw new IllegalArgumentException("Field type must not be null");
    }
    if(numDigits <= 0) {
      throw new IllegalArgumentException("Illegal number of digits: " + numDigits);
    }
    return append0(new FixedNumber(fieldType, numDigits, false));
  }
  public DateTimeFormatterBuilder appendFixedSignedDecimal(DateTimeFieldType fieldType, int numDigits) {
    if(fieldType == null) {
      throw new IllegalArgumentException("Field type must not be null");
    }
    if(numDigits <= 0) {
      throw new IllegalArgumentException("Illegal number of digits: " + numDigits);
    }
    return append0(new FixedNumber(fieldType, numDigits, true));
  }
  public DateTimeFormatterBuilder appendFraction(DateTimeFieldType fieldType, int minDigits, int maxDigits) {
    if(fieldType == null) {
      throw new IllegalArgumentException("Field type must not be null");
    }
    if(maxDigits < minDigits) {
      maxDigits = minDigits;
    }
    if(minDigits < 0 || maxDigits <= 0) {
      throw new IllegalArgumentException();
    }
    return append0(new Fraction(fieldType, minDigits, maxDigits));
  }
  public DateTimeFormatterBuilder appendFractionOfDay(int minDigits, int maxDigits) {
    return appendFraction(DateTimeFieldType.dayOfYear(), minDigits, maxDigits);
  }
  public DateTimeFormatterBuilder appendFractionOfHour(int minDigits, int maxDigits) {
    return appendFraction(DateTimeFieldType.hourOfDay(), minDigits, maxDigits);
  }
  public DateTimeFormatterBuilder appendFractionOfMinute(int minDigits, int maxDigits) {
    return appendFraction(DateTimeFieldType.minuteOfDay(), minDigits, maxDigits);
  }
  public DateTimeFormatterBuilder appendFractionOfSecond(int minDigits, int maxDigits) {
    return appendFraction(DateTimeFieldType.secondOfDay(), minDigits, maxDigits);
  }
  public DateTimeFormatterBuilder appendHalfdayOfDayText() {
    return appendText(DateTimeFieldType.halfdayOfDay());
  }
  public DateTimeFormatterBuilder appendHourOfDay(int minDigits) {
    return appendDecimal(DateTimeFieldType.hourOfDay(), minDigits, 2);
  }
  public DateTimeFormatterBuilder appendHourOfHalfday(int minDigits) {
    return appendDecimal(DateTimeFieldType.hourOfHalfday(), minDigits, 2);
  }
  public DateTimeFormatterBuilder appendLiteral(char c) {
    return append0(new CharacterLiteral(c));
  }
  public DateTimeFormatterBuilder appendLiteral(String text) {
    if(text == null) {
      throw new IllegalArgumentException("Literal must not be null");
    }
    switch (text.length()){
      case 0:
      return this;
      case 1:
      return append0(new CharacterLiteral(text.charAt(0)));
      default:
      return append0(new StringLiteral(text));
    }
  }
  public DateTimeFormatterBuilder appendMillisOfDay(int minDigits) {
    return appendDecimal(DateTimeFieldType.millisOfDay(), minDigits, 8);
  }
  public DateTimeFormatterBuilder appendMillisOfSecond(int minDigits) {
    return appendDecimal(DateTimeFieldType.millisOfSecond(), minDigits, 3);
  }
  public DateTimeFormatterBuilder appendMinuteOfDay(int minDigits) {
    return appendDecimal(DateTimeFieldType.minuteOfDay(), minDigits, 4);
  }
  public DateTimeFormatterBuilder appendMinuteOfHour(int minDigits) {
    return appendDecimal(DateTimeFieldType.minuteOfHour(), minDigits, 2);
  }
  public DateTimeFormatterBuilder appendMonthOfYear(int minDigits) {
    return appendDecimal(DateTimeFieldType.monthOfYear(), minDigits, 2);
  }
  public DateTimeFormatterBuilder appendMonthOfYearShortText() {
    return appendShortText(DateTimeFieldType.monthOfYear());
  }
  public DateTimeFormatterBuilder appendMonthOfYearText() {
    return appendText(DateTimeFieldType.monthOfYear());
  }
  public DateTimeFormatterBuilder appendOptional(DateTimeParser parser) {
    checkParser(parser);
    DateTimeParser[] parsers = new DateTimeParser[]{ parser, null } ;
    return append0(null, new MatchingParser(parsers));
  }
  public DateTimeFormatterBuilder appendPattern(String pattern) {
    DateTimeFormat.appendPatternTo(this, pattern);
    return this;
  }
  public DateTimeFormatterBuilder appendSecondOfDay(int minDigits) {
    return appendDecimal(DateTimeFieldType.secondOfDay(), minDigits, 5);
  }
  public DateTimeFormatterBuilder appendSecondOfMinute(int minDigits) {
    return appendDecimal(DateTimeFieldType.secondOfMinute(), minDigits, 2);
  }
  public DateTimeFormatterBuilder appendShortText(DateTimeFieldType fieldType) {
    if(fieldType == null) {
      throw new IllegalArgumentException("Field type must not be null");
    }
    return append0(new TextField(fieldType, true));
  }
  public DateTimeFormatterBuilder appendSignedDecimal(DateTimeFieldType fieldType, int minDigits, int maxDigits) {
    if(fieldType == null) {
      throw new IllegalArgumentException("Field type must not be null");
    }
    if(maxDigits < minDigits) {
      maxDigits = minDigits;
    }
    if(minDigits < 0 || maxDigits <= 0) {
      throw new IllegalArgumentException();
    }
    if(minDigits <= 1) {
      return append0(new UnpaddedNumber(fieldType, maxDigits, true));
    }
    else {
      return append0(new PaddedNumber(fieldType, maxDigits, true, minDigits));
    }
  }
  public DateTimeFormatterBuilder appendText(DateTimeFieldType fieldType) {
    if(fieldType == null) {
      throw new IllegalArgumentException("Field type must not be null");
    }
    return append0(new TextField(fieldType, false));
  }
  public DateTimeFormatterBuilder appendTimeZoneId() {
    return append0(TimeZoneId.INSTANCE, TimeZoneId.INSTANCE);
  }
  public DateTimeFormatterBuilder appendTimeZoneName() {
    return append0(new TimeZoneName(TimeZoneName.LONG_NAME, null), null);
  }
  public DateTimeFormatterBuilder appendTimeZoneName(Map<String, DateTimeZone> parseLookup) {
    TimeZoneName pp = new TimeZoneName(TimeZoneName.LONG_NAME, parseLookup);
    return append0(pp, pp);
  }
  public DateTimeFormatterBuilder appendTimeZoneOffset(String zeroOffsetText, boolean showSeparators, int minFields, int maxFields) {
    return append0(new TimeZoneOffset(zeroOffsetText, zeroOffsetText, showSeparators, minFields, maxFields));
  }
  public DateTimeFormatterBuilder appendTimeZoneOffset(String zeroOffsetPrintText, String zeroOffsetParseText, boolean showSeparators, int minFields, int maxFields) {
    return append0(new TimeZoneOffset(zeroOffsetPrintText, zeroOffsetParseText, showSeparators, minFields, maxFields));
  }
  public DateTimeFormatterBuilder appendTimeZoneShortName() {
    return append0(new TimeZoneName(TimeZoneName.SHORT_NAME, null), null);
  }
  public DateTimeFormatterBuilder appendTimeZoneShortName(Map<String, DateTimeZone> parseLookup) {
    TimeZoneName pp = new TimeZoneName(TimeZoneName.SHORT_NAME, parseLookup);
    return append0(pp, pp);
  }
  public DateTimeFormatterBuilder appendTwoDigitWeekyear(int pivot) {
    return appendTwoDigitWeekyear(pivot, false);
  }
  public DateTimeFormatterBuilder appendTwoDigitWeekyear(int pivot, boolean lenientParse) {
    return append0(new TwoDigitYear(DateTimeFieldType.weekyear(), pivot, lenientParse));
  }
  public DateTimeFormatterBuilder appendTwoDigitYear(int pivot) {
    return appendTwoDigitYear(pivot, false);
  }
  public DateTimeFormatterBuilder appendTwoDigitYear(int pivot, boolean lenientParse) {
    return append0(new TwoDigitYear(DateTimeFieldType.year(), pivot, lenientParse));
  }
  public DateTimeFormatterBuilder appendWeekOfWeekyear(int minDigits) {
    return appendDecimal(DateTimeFieldType.weekOfWeekyear(), minDigits, 2);
  }
  public DateTimeFormatterBuilder appendWeekyear(int minDigits, int maxDigits) {
    return appendSignedDecimal(DateTimeFieldType.weekyear(), minDigits, maxDigits);
  }
  public DateTimeFormatterBuilder appendYear(int minDigits, int maxDigits) {
    return appendSignedDecimal(DateTimeFieldType.year(), minDigits, maxDigits);
  }
  public DateTimeFormatterBuilder appendYearOfCentury(int minDigits, int maxDigits) {
    return appendDecimal(DateTimeFieldType.yearOfCentury(), minDigits, maxDigits);
  }
  public DateTimeFormatterBuilder appendYearOfEra(int minDigits, int maxDigits) {
    return appendDecimal(DateTimeFieldType.yearOfEra(), minDigits, maxDigits);
  }
  public DateTimeParser toParser() {
    Object f = getFormatter();
    if(isParser(f)) {
      return (DateTimeParser)f;
    }
    throw new UnsupportedOperationException("Parsing is not supported");
  }
  public DateTimePrinter toPrinter() {
    Object f = getFormatter();
    if(isPrinter(f)) {
      return (DateTimePrinter)f;
    }
    throw new UnsupportedOperationException("Printing is not supported");
  }
  private Object getFormatter() {
    Object f = iFormatter;
    if(f == null) {
      if(iElementPairs.size() == 2) {
        Object printer = iElementPairs.get(0);
        Object parser = iElementPairs.get(1);
        if(printer != null) {
          if(printer == parser || parser == null) {
            f = printer;
          }
        }
        else {
          f = parser;
        }
      }
      if(f == null) {
        f = new Composite(iElementPairs);
      }
      iFormatter = f;
    }
    return f;
  }
  public boolean canBuildFormatter() {
    return isFormatter(getFormatter());
  }
  public boolean canBuildParser() {
    return isParser(getFormatter());
  }
  public boolean canBuildPrinter() {
    return isPrinter(getFormatter());
  }
  private boolean isFormatter(Object f) {
    return (isPrinter(f) || isParser(f));
  }
  private boolean isParser(Object f) {
    if(f instanceof DateTimeParser) {
      if(f instanceof Composite) {
        return ((Composite)f).isParser();
      }
      return true;
    }
    return false;
  }
  private boolean isPrinter(Object f) {
    if(f instanceof DateTimePrinter) {
      if(f instanceof Composite) {
        return ((Composite)f).isPrinter();
      }
      return true;
    }
    return false;
  }
  static void appendUnknownString(StringBuffer buf, int len) {
    for(int i = len; --i >= 0; ) {
      buf.append('\ufffd');
    }
  }
  private void checkParser(DateTimeParser parser) {
    if(parser == null) {
      throw new IllegalArgumentException("No parser supplied");
    }
  }
  private void checkPrinter(DateTimePrinter printer) {
    if(printer == null) {
      throw new IllegalArgumentException("No printer supplied");
    }
  }
  public void clear() {
    iFormatter = null;
    iElementPairs.clear();
  }
  static void printUnknownString(Writer out, int len) throws IOException {
    for(int i = len; --i >= 0; ) {
      out.write('\ufffd');
    }
  }
  
  static class CharacterLiteral implements DateTimePrinter, DateTimeParser  {
    final private char iValue;
    CharacterLiteral(char value) {
      super();
      iValue = value;
    }
    public int estimateParsedLength() {
      return 1;
    }
    public int estimatePrintedLength() {
      return 1;
    }
    public int parseInto(DateTimeParserBucket bucket, String text, int position) {
      if(position >= text.length()) {
        return ~position;
      }
      char a = text.charAt(position);
      char b = iValue;
      if(a != b) {
        a = Character.toUpperCase(a);
        b = Character.toUpperCase(b);
        if(a != b) {
          a = Character.toLowerCase(a);
          b = Character.toLowerCase(b);
          if(a != b) {
            return ~position;
          }
        }
      }
      return position + 1;
    }
    public void printTo(Writer out, long instant, Chronology chrono, int displayOffset, DateTimeZone displayZone, Locale locale) throws IOException {
      out.write(iValue);
    }
    public void printTo(Writer out, ReadablePartial partial, Locale locale) throws IOException {
      out.write(iValue);
    }
    public void printTo(StringBuffer buf, long instant, Chronology chrono, int displayOffset, DateTimeZone displayZone, Locale locale) {
      buf.append(iValue);
    }
    public void printTo(StringBuffer buf, ReadablePartial partial, Locale locale) {
      buf.append(iValue);
    }
  }
  
  static class Composite implements DateTimePrinter, DateTimeParser  {
    final private DateTimePrinter[] iPrinters;
    final private DateTimeParser[] iParsers;
    final private int iPrintedLengthEstimate;
    final private int iParsedLengthEstimate;
    Composite(List<Object> elementPairs) {
      super();
      List<Object> printerList = new ArrayList<Object>();
      List<Object> parserList = new ArrayList<Object>();
      decompose(elementPairs, printerList, parserList);
      if(printerList.contains(null) || printerList.isEmpty()) {
        iPrinters = null;
        iPrintedLengthEstimate = 0;
      }
      else {
        int size = printerList.size();
        iPrinters = new DateTimePrinter[size];
        int printEst = 0;
        for(int i = 0; i < size; i++) {
          DateTimePrinter printer = (DateTimePrinter)printerList.get(i);
          printEst += printer.estimatePrintedLength();
          iPrinters[i] = printer;
        }
        iPrintedLengthEstimate = printEst;
      }
      if(parserList.contains(null) || parserList.isEmpty()) {
        iParsers = null;
        iParsedLengthEstimate = 0;
      }
      else {
        int size = parserList.size();
        iParsers = new DateTimeParser[size];
        int parseEst = 0;
        for(int i = 0; i < size; i++) {
          DateTimeParser parser = (DateTimeParser)parserList.get(i);
          parseEst += parser.estimateParsedLength();
          iParsers[i] = parser;
        }
        iParsedLengthEstimate = parseEst;
      }
    }
    boolean isParser() {
      return iParsers != null;
    }
    boolean isPrinter() {
      return iPrinters != null;
    }
    public int estimateParsedLength() {
      return iParsedLengthEstimate;
    }
    public int estimatePrintedLength() {
      return iPrintedLengthEstimate;
    }
    public int parseInto(DateTimeParserBucket bucket, String text, int position) {
      DateTimeParser[] elements = iParsers;
      if(elements == null) {
        throw new UnsupportedOperationException();
      }
      int len = elements.length;
      for(int i = 0; i < len && position >= 0; i++) {
        position = elements[i].parseInto(bucket, text, position);
      }
      return position;
    }
    private void addArrayToList(List<Object> list, Object[] array) {
      if(array != null) {
        for(int i = 0; i < array.length; i++) {
          list.add(array[i]);
        }
      }
    }
    private void decompose(List<Object> elementPairs, List<Object> printerList, List<Object> parserList) {
      int size = elementPairs.size();
      for(int i = 0; i < size; i += 2) {
        Object element = elementPairs.get(i);
        if(element instanceof Composite) {
          addArrayToList(printerList, ((Composite)element).iPrinters);
        }
        else {
          printerList.add(element);
        }
        element = elementPairs.get(i + 1);
        if(element instanceof Composite) {
          addArrayToList(parserList, ((Composite)element).iParsers);
        }
        else {
          parserList.add(element);
        }
      }
    }
    public void printTo(Writer out, long instant, Chronology chrono, int displayOffset, DateTimeZone displayZone, Locale locale) throws IOException {
      DateTimePrinter[] elements = iPrinters;
      if(elements == null) {
        throw new UnsupportedOperationException();
      }
      if(locale == null) {
        locale = Locale.getDefault();
      }
      int len = elements.length;
      for(int i = 0; i < len; i++) {
        elements[i].printTo(out, instant, chrono, displayOffset, displayZone, locale);
      }
    }
    public void printTo(Writer out, ReadablePartial partial, Locale locale) throws IOException {
      DateTimePrinter[] elements = iPrinters;
      if(elements == null) {
        throw new UnsupportedOperationException();
      }
      if(locale == null) {
        locale = Locale.getDefault();
      }
      int len = elements.length;
      for(int i = 0; i < len; i++) {
        elements[i].printTo(out, partial, locale);
      }
    }
    public void printTo(StringBuffer buf, long instant, Chronology chrono, int displayOffset, DateTimeZone displayZone, Locale locale) {
      DateTimePrinter[] elements = iPrinters;
      if(elements == null) {
        throw new UnsupportedOperationException();
      }
      if(locale == null) {
        locale = Locale.getDefault();
      }
      int len = elements.length;
      for(int i = 0; i < len; i++) {
        elements[i].printTo(buf, instant, chrono, displayOffset, displayZone, locale);
      }
    }
    public void printTo(StringBuffer buf, ReadablePartial partial, Locale locale) {
      DateTimePrinter[] elements = iPrinters;
      if(elements == null) {
        throw new UnsupportedOperationException();
      }
      if(locale == null) {
        locale = Locale.getDefault();
      }
      int len = elements.length;
      for(int i = 0; i < len; i++) {
        elements[i].printTo(buf, partial, locale);
      }
    }
  }
  
  static class FixedNumber extends PaddedNumber  {
    protected FixedNumber(DateTimeFieldType fieldType, int numDigits, boolean signed) {
      super(fieldType, numDigits, signed, numDigits);
    }
    public int parseInto(DateTimeParserBucket bucket, String text, int position) {
      int newPos = super.parseInto(bucket, text, position);
      if(newPos < 0) {
        return newPos;
      }
      int expectedPos = position + iMaxParsedDigits;
      if(newPos != expectedPos) {
        if(iSigned) {
          char c = text.charAt(position);
          if(c == '-' || c == '+') {
            expectedPos++;
          }
        }
        if(newPos > expectedPos) {
          return ~(expectedPos + 1);
        }
        else 
          if(newPos < expectedPos) {
            return ~newPos;
          }
      }
      return newPos;
    }
  }
  
  static class Fraction implements DateTimePrinter, DateTimeParser  {
    final private DateTimeFieldType iFieldType;
    protected int iMinDigits;
    protected int iMaxDigits;
    protected Fraction(DateTimeFieldType fieldType, int minDigits, int maxDigits) {
      super();
      iFieldType = fieldType;
      if(maxDigits > 18) {
        maxDigits = 18;
      }
      iMinDigits = minDigits;
      iMaxDigits = maxDigits;
    }
    public int estimateParsedLength() {
      return iMaxDigits;
    }
    public int estimatePrintedLength() {
      return iMaxDigits;
    }
    public int parseInto(DateTimeParserBucket bucket, String text, int position) {
      DateTimeField field = iFieldType.getField(bucket.getChronology());
      int limit = Math.min(iMaxDigits, text.length() - position);
      long value = 0;
      long n = field.getDurationField().getUnitMillis() * 10;
      int length = 0;
      while(length < limit){
        char c = text.charAt(position + length);
        if(c < '0' || c > '9') {
          break ;
        }
        length++;
        long nn = n / 10;
        value += (c - '0') * nn;
        n = nn;
      }
      value /= 10;
      if(length == 0) {
        return ~position;
      }
      if(value > Integer.MAX_VALUE) {
        return ~position;
      }
      DateTimeField parseField = new PreciseDateTimeField(DateTimeFieldType.millisOfSecond(), MillisDurationField.INSTANCE, field.getDurationField());
      bucket.saveField(parseField, (int)value);
      return position + length;
    }
    private long[] getFractionData(long fraction, DateTimeField field) {
      long rangeMillis = field.getDurationField().getUnitMillis();
      long scalar;
      int maxDigits = iMaxDigits;
      while(true){
        switch (maxDigits){
          default:
          scalar = 1L;
          break ;
          case 1:
          scalar = 10L;
          break ;
          case 2:
          scalar = 100L;
          break ;
          case 3:
          scalar = 1000L;
          break ;
          case 4:
          scalar = 10000L;
          break ;
          case 5:
          scalar = 100000L;
          break ;
          case 6:
          scalar = 1000000L;
          break ;
          case 7:
          scalar = 10000000L;
          break ;
          case 8:
          scalar = 100000000L;
          break ;
          case 9:
          scalar = 1000000000L;
          break ;
          case 10:
          scalar = 10000000000L;
          break ;
          case 11:
          scalar = 100000000000L;
          break ;
          case 12:
          scalar = 1000000000000L;
          break ;
          case 13:
          scalar = 10000000000000L;
          break ;
          case 14:
          scalar = 100000000000000L;
          break ;
          case 15:
          scalar = 1000000000000000L;
          break ;
          case 16:
          scalar = 10000000000000000L;
          break ;
          case 17:
          scalar = 100000000000000000L;
          break ;
          case 18:
          scalar = 1000000000000000000L;
          break ;
        }
        if(((rangeMillis * scalar) / scalar) == rangeMillis) {
          break ;
        }
        maxDigits--;
      }
      return new long[]{ fraction * scalar / rangeMillis, maxDigits } ;
    }
    public void printTo(Writer out, long instant, Chronology chrono, int displayOffset, DateTimeZone displayZone, Locale locale) throws IOException {
      printTo(null, out, instant, chrono);
    }
    public void printTo(Writer out, ReadablePartial partial, Locale locale) throws IOException {
      long millis = partial.getChronology().set(partial, 0L);
      printTo(null, out, millis, partial.getChronology());
    }
    protected void printTo(StringBuffer buf, Writer out, long instant, Chronology chrono) throws IOException {
      DateTimeField field = iFieldType.getField(chrono);
      int minDigits = iMinDigits;
      long fraction;
      try {
        fraction = field.remainder(instant);
      }
      catch (RuntimeException e) {
        if(buf != null) {
          appendUnknownString(buf, minDigits);
        }
        else {
          printUnknownString(out, minDigits);
        }
        return ;
      }
      if(fraction == 0) {
        if(buf != null) {
          while(--minDigits >= 0){
            buf.append('0');
          }
        }
        else {
          while(--minDigits >= 0){
            out.write('0');
          }
        }
        return ;
      }
      String str;
      long[] fractionData = getFractionData(fraction, field);
      long scaled = fractionData[0];
      int maxDigits = (int)fractionData[1];
      if((scaled & 0x7fffffff) == scaled) {
        str = Integer.toString((int)scaled);
      }
      else {
        str = Long.toString(scaled);
      }
      int length = str.length();
      int digits = maxDigits;
      while(length < digits){
        if(buf != null) {
          buf.append('0');
        }
        else {
          out.write('0');
        }
        minDigits--;
        digits--;
      }
      if(minDigits < digits) {
        while(minDigits < digits){
          if(length <= 1 || str.charAt(length - 1) != '0') {
            break ;
          }
          digits--;
          length--;
        }
        if(length < str.length()) {
          if(buf != null) {
            for(int i = 0; i < length; i++) {
              buf.append(str.charAt(i));
            }
          }
          else {
            for(int i = 0; i < length; i++) {
              out.write(str.charAt(i));
            }
          }
          return ;
        }
      }
      if(buf != null) {
        buf.append(str);
      }
      else {
        out.write(str);
      }
    }
    public void printTo(StringBuffer buf, long instant, Chronology chrono, int displayOffset, DateTimeZone displayZone, Locale locale) {
      try {
        printTo(buf, null, instant, chrono);
      }
      catch (IOException e) {
      }
    }
    public void printTo(StringBuffer buf, ReadablePartial partial, Locale locale) {
      long millis = partial.getChronology().set(partial, 0L);
      try {
        printTo(buf, null, millis, partial.getChronology());
      }
      catch (IOException e) {
      }
    }
  }
  
  static class MatchingParser implements DateTimeParser  {
    final private DateTimeParser[] iParsers;
    final private int iParsedLengthEstimate;
    MatchingParser(DateTimeParser[] parsers) {
      super();
      iParsers = parsers;
      int est = 0;
      for(int i = parsers.length; --i >= 0; ) {
        DateTimeParser parser = parsers[i];
        if(parser != null) {
          int len = parser.estimateParsedLength();
          if(len > est) {
            est = len;
          }
        }
      }
      iParsedLengthEstimate = est;
    }
    public int estimateParsedLength() {
      return iParsedLengthEstimate;
    }
    public int parseInto(DateTimeParserBucket bucket, String text, int position) {
      DateTimeParser[] parsers = iParsers;
      int length = parsers.length;
      final Object originalState = bucket.saveState();
      boolean isOptional = false;
      int bestValidPos = position;
      Object bestValidState = null;
      int bestInvalidPos = position;
      for(int i = 0; i < length; i++) {
        DateTimeParser parser = parsers[i];
        if(parser == null) {
          if(bestValidPos <= position) {
            return position;
          }
          isOptional = true;
          break ;
        }
        int parsePos = parser.parseInto(bucket, text, position);
        if(parsePos >= position) {
          if(parsePos > bestValidPos) {
            if(parsePos >= text.length() || (i + 1) >= length || parsers[i + 1] == null) {
              return parsePos;
            }
            bestValidPos = parsePos;
            bestValidState = bucket.saveState();
          }
        }
        else {
          if(parsePos < 0) {
            parsePos = ~parsePos;
            if(parsePos > bestInvalidPos) {
              bestInvalidPos = parsePos;
            }
          }
        }
        bucket.restoreState(originalState);
      }
      if(bestValidPos > position || (bestValidPos == position && isOptional)) {
        if(bestValidState != null) {
          bucket.restoreState(bestValidState);
        }
        return bestValidPos;
      }
      return ~bestInvalidPos;
    }
  }
  
  abstract static class NumberFormatter implements DateTimePrinter, DateTimeParser  {
    final protected DateTimeFieldType iFieldType;
    final protected int iMaxParsedDigits;
    final protected boolean iSigned;
    NumberFormatter(DateTimeFieldType fieldType, int maxParsedDigits, boolean signed) {
      super();
      iFieldType = fieldType;
      iMaxParsedDigits = maxParsedDigits;
      iSigned = signed;
    }
    public int estimateParsedLength() {
      return iMaxParsedDigits;
    }
    public int parseInto(DateTimeParserBucket bucket, String text, int position) {
      int limit = Math.min(iMaxParsedDigits, text.length() - position);
      boolean negative = false;
      int length = 0;
      while(length < limit){
        char c = text.charAt(position + length);
        if(length == 0 && (c == '-' || c == '+') && iSigned) {
          negative = c == '-';
          if(length + 1 >= limit || (c = text.charAt(position + length + 1)) < '0' || c > '9') {
            break ;
          }
          if(negative) {
            length++;
          }
          else {
            position++;
          }
          limit = Math.min(limit + 1, text.length() - position);
          continue ;
        }
        if(c < '0' || c > '9') {
          break ;
        }
        length++;
      }
      if(length == 0) {
        return ~position;
      }
      int value;
      if(length >= 9) {
        value = Integer.parseInt(text.substring(position, position += length));
      }
      else {
        int i = position;
        if(negative) {
          i++;
        }
        try {
          value = text.charAt(i++) - '0';
        }
        catch (StringIndexOutOfBoundsException e) {
          return ~position;
        }
        position += length;
        while(i < position){
          value = ((value << 3) + (value << 1)) + text.charAt(i++) - '0';
        }
        if(negative) {
          value = -value;
        }
      }
      bucket.saveField(iFieldType, value);
      return position;
    }
  }
  
  static class PaddedNumber extends NumberFormatter  {
    final protected int iMinPrintedDigits;
    protected PaddedNumber(DateTimeFieldType fieldType, int maxParsedDigits, boolean signed, int minPrintedDigits) {
      super(fieldType, maxParsedDigits, signed);
      iMinPrintedDigits = minPrintedDigits;
    }
    public int estimatePrintedLength() {
      return iMaxParsedDigits;
    }
    public void printTo(Writer out, long instant, Chronology chrono, int displayOffset, DateTimeZone displayZone, Locale locale) throws IOException {
      try {
        DateTimeField field = iFieldType.getField(chrono);
        FormatUtils.writePaddedInteger(out, field.get(instant), iMinPrintedDigits);
      }
      catch (RuntimeException e) {
        printUnknownString(out, iMinPrintedDigits);
      }
    }
    public void printTo(Writer out, ReadablePartial partial, Locale locale) throws IOException {
      if(partial.isSupported(iFieldType)) {
        try {
          FormatUtils.writePaddedInteger(out, partial.get(iFieldType), iMinPrintedDigits);
        }
        catch (RuntimeException e) {
          printUnknownString(out, iMinPrintedDigits);
        }
      }
      else {
        printUnknownString(out, iMinPrintedDigits);
      }
    }
    public void printTo(StringBuffer buf, long instant, Chronology chrono, int displayOffset, DateTimeZone displayZone, Locale locale) {
      try {
        DateTimeField field = iFieldType.getField(chrono);
        FormatUtils.appendPaddedInteger(buf, field.get(instant), iMinPrintedDigits);
      }
      catch (RuntimeException e) {
        appendUnknownString(buf, iMinPrintedDigits);
      }
    }
    public void printTo(StringBuffer buf, ReadablePartial partial, Locale locale) {
      if(partial.isSupported(iFieldType)) {
        try {
          FormatUtils.appendPaddedInteger(buf, partial.get(iFieldType), iMinPrintedDigits);
        }
        catch (RuntimeException e) {
          appendUnknownString(buf, iMinPrintedDigits);
        }
      }
      else {
        appendUnknownString(buf, iMinPrintedDigits);
      }
    }
  }
  
  static class StringLiteral implements DateTimePrinter, DateTimeParser  {
    final private String iValue;
    StringLiteral(String value) {
      super();
      iValue = value;
    }
    public int estimateParsedLength() {
      return iValue.length();
    }
    public int estimatePrintedLength() {
      return iValue.length();
    }
    public int parseInto(DateTimeParserBucket bucket, String text, int position) {
      if(text.regionMatches(true, position, iValue, 0, iValue.length())) {
        return position + iValue.length();
      }
      return ~position;
    }
    public void printTo(Writer out, long instant, Chronology chrono, int displayOffset, DateTimeZone displayZone, Locale locale) throws IOException {
      out.write(iValue);
    }
    public void printTo(Writer out, ReadablePartial partial, Locale locale) throws IOException {
      out.write(iValue);
    }
    public void printTo(StringBuffer buf, long instant, Chronology chrono, int displayOffset, DateTimeZone displayZone, Locale locale) {
      buf.append(iValue);
    }
    public void printTo(StringBuffer buf, ReadablePartial partial, Locale locale) {
      buf.append(iValue);
    }
  }
  
  static class TextField implements DateTimePrinter, DateTimeParser  {
    private static Map<Locale, Map<DateTimeFieldType, Object[]>> cParseCache = new HashMap<Locale, Map<DateTimeFieldType, Object[]>>();
    final private DateTimeFieldType iFieldType;
    final private boolean iShort;
    TextField(DateTimeFieldType fieldType, boolean isShort) {
      super();
      iFieldType = fieldType;
      iShort = isShort;
    }
    private String print(long instant, Chronology chrono, Locale locale) {
      DateTimeField field = iFieldType.getField(chrono);
      if(iShort) {
        return field.getAsShortText(instant, locale);
      }
      else {
        return field.getAsText(instant, locale);
      }
    }
    private String print(ReadablePartial partial, Locale locale) {
      if(partial.isSupported(iFieldType)) {
        DateTimeField field = iFieldType.getField(partial.getChronology());
        if(iShort) {
          return field.getAsShortText(partial, locale);
        }
        else {
          return field.getAsText(partial, locale);
        }
      }
      else {
        return "\ufffd";
      }
    }
    public int estimateParsedLength() {
      return estimatePrintedLength();
    }
    public int estimatePrintedLength() {
      return iShort ? 6 : 20;
    }
    @SuppressWarnings(value = {"unchecked", }) public int parseInto(DateTimeParserBucket bucket, String text, int position) {
      Locale locale = bucket.getLocale();
      Set<String> validValues = null;
      int maxLength = 0;
      synchronized(cParseCache) {
        Map<DateTimeFieldType, Object[]> innerMap = cParseCache.get(locale);
        if(innerMap == null) {
          innerMap = new HashMap<DateTimeFieldType, Object[]>();
          cParseCache.put(locale, innerMap);
        }
        Object[] array = innerMap.get(iFieldType);
        if(array == null) {
          validValues = new HashSet<String>(32);
          MutableDateTime dt = new MutableDateTime(0L, DateTimeZone.UTC);
          Property property = dt.property(iFieldType);
          int min = property.getMinimumValueOverall();
          int max = property.getMaximumValueOverall();
          if(max - min > 32) {
            return ~position;
          }
          maxLength = property.getMaximumTextLength(locale);
          for(int i = min; i <= max; i++) {
            property.set(i);
            validValues.add(property.getAsShortText(locale));
            validValues.add(property.getAsShortText(locale).toLowerCase(locale));
            validValues.add(property.getAsShortText(locale).toUpperCase(locale));
            validValues.add(property.getAsText(locale));
            validValues.add(property.getAsText(locale).toLowerCase(locale));
            validValues.add(property.getAsText(locale).toUpperCase(locale));
          }
          if("en".equals(locale.getLanguage()) && iFieldType == DateTimeFieldType.era()) {
            validValues.add("BCE");
            validValues.add("bce");
            validValues.add("CE");
            validValues.add("ce");
            maxLength = 3;
          }
          array = new Object[]{ validValues, Integer.valueOf(maxLength) } ;
          innerMap.put(iFieldType, array);
        }
        else {
          validValues = (Set<String>)array[0];
          maxLength = ((Integer)array[1]).intValue();
        }
      }
      int limit = Math.min(text.length(), position + maxLength);
      for(int i = limit; i > position; i--) {
        String match = text.substring(position, i);
        if(validValues.contains(match)) {
          bucket.saveField(iFieldType, match, locale);
          return i;
        }
      }
      return ~position;
    }
    public void printTo(Writer out, long instant, Chronology chrono, int displayOffset, DateTimeZone displayZone, Locale locale) throws IOException {
      try {
        out.write(print(instant, chrono, locale));
      }
      catch (RuntimeException e) {
        out.write('\ufffd');
      }
    }
    public void printTo(Writer out, ReadablePartial partial, Locale locale) throws IOException {
      try {
        out.write(print(partial, locale));
      }
      catch (RuntimeException e) {
        out.write('\ufffd');
      }
    }
    public void printTo(StringBuffer buf, long instant, Chronology chrono, int displayOffset, DateTimeZone displayZone, Locale locale) {
      try {
        buf.append(print(instant, chrono, locale));
      }
      catch (RuntimeException e) {
        buf.append('\ufffd');
      }
    }
    public void printTo(StringBuffer buf, ReadablePartial partial, Locale locale) {
      try {
        buf.append(print(partial, locale));
      }
      catch (RuntimeException e) {
        buf.append('\ufffd');
      }
    }
  }
  static enum TimeZoneId implements DateTimePrinter, DateTimeParser {
    INSTANCE(),

  ;
    final static Set<String> ALL_IDS = DateTimeZone.getAvailableIDs();
    final static int MAX_LENGTH;
    static {
      int max = 0;
      for (String id : ALL_IDS) {
        max = Math.max(max, id.length());
      }
      MAX_LENGTH = max;
    }
    public int estimatePrintedLength() {
      return MAX_LENGTH;
    }
    public void printTo(StringBuffer buf, long instant, Chronology chrono, int displayOffset, DateTimeZone displayZone, Locale locale) {
      buf.append(displayZone != null ? displayZone.getID() : "");
    }
    public void printTo(Writer out, long instant, Chronology chrono, int displayOffset, DateTimeZone displayZone, Locale locale) throws IOException {
      out.write(displayZone != null ? displayZone.getID() : "");
    }
    public void printTo(StringBuffer buf, ReadablePartial partial, Locale locale) {
    }
    public void printTo(Writer out, ReadablePartial partial, Locale locale) throws IOException {
    }
    public int estimateParsedLength() {
      return MAX_LENGTH;
    }
    public int parseInto(DateTimeParserBucket bucket, String text, int position) {
      String str = text.substring(position);
      String best = null;
      for (String id : ALL_IDS) {
        if(str.startsWith(id)) {
          if(best == null || id.length() > best.length()) {
            best = id;
          }
        }
      }
      if(best != null) {
        bucket.setZone(DateTimeZone.forID(best));
        return position + best.length();
      }
      return ~position;
    }
  private TimeZoneId() {
  }
  }
  
  static class TimeZoneName implements DateTimePrinter, DateTimeParser  {
    final static int LONG_NAME = 0;
    final static int SHORT_NAME = 1;
    final private Map<String, DateTimeZone> iParseLookup;
    final private int iType;
    TimeZoneName(int type, Map<String, DateTimeZone> parseLookup) {
      super();
      iType = type;
      iParseLookup = parseLookup;
    }
    private String print(long instant, DateTimeZone displayZone, Locale locale) {
      if(displayZone == null) {
        return "";
      }
      switch (iType){
        case LONG_NAME:
        return displayZone.getName(instant, locale);
        case SHORT_NAME:
        return displayZone.getShortName(instant, locale);
      }
      return "";
    }
    public int estimateParsedLength() {
      return (iType == SHORT_NAME ? 4 : 20);
    }
    public int estimatePrintedLength() {
      return (iType == SHORT_NAME ? 4 : 20);
    }
    public int parseInto(DateTimeParserBucket bucket, String text, int position) {
      Map<String, DateTimeZone> parseLookup = iParseLookup;
      parseLookup = (parseLookup != null ? parseLookup : DateTimeUtils.getDefaultTimeZoneNames());
      String str = text.substring(position);
      String matched = null;
      for (String name : parseLookup.keySet()) {
        if(str.startsWith(name)) {
          if(matched == null || name.length() > matched.length()) {
            matched = name;
          }
        }
      }
      if(matched != null) {
        bucket.setZone(parseLookup.get(matched));
        return position + matched.length();
      }
      return ~position;
    }
    public void printTo(Writer out, long instant, Chronology chrono, int displayOffset, DateTimeZone displayZone, Locale locale) throws IOException {
      out.write(print(instant - displayOffset, displayZone, locale));
    }
    public void printTo(Writer out, ReadablePartial partial, Locale locale) throws IOException {
    }
    public void printTo(StringBuffer buf, long instant, Chronology chrono, int displayOffset, DateTimeZone displayZone, Locale locale) {
      buf.append(print(instant - displayOffset, displayZone, locale));
    }
    public void printTo(StringBuffer buf, ReadablePartial partial, Locale locale) {
    }
  }
  
  static class TimeZoneOffset implements DateTimePrinter, DateTimeParser  {
    final private String iZeroOffsetPrintText;
    final private String iZeroOffsetParseText;
    final private boolean iShowSeparators;
    final private int iMinFields;
    final private int iMaxFields;
    TimeZoneOffset(String zeroOffsetPrintText, String zeroOffsetParseText, boolean showSeparators, int minFields, int maxFields) {
      super();
      iZeroOffsetPrintText = zeroOffsetPrintText;
      iZeroOffsetParseText = zeroOffsetParseText;
      iShowSeparators = showSeparators;
      if(minFields <= 0 || maxFields < minFields) {
        throw new IllegalArgumentException();
      }
      if(minFields > 4) {
        minFields = 4;
        maxFields = 4;
      }
      iMinFields = minFields;
      iMaxFields = maxFields;
    }
    private int digitCount(String text, int position, int amount) {
      int limit = Math.min(text.length() - position, amount);
      amount = 0;
      for(; limit > 0; limit--) {
        char c = text.charAt(position + amount);
        if(c < '0' || c > '9') {
          break ;
        }
        amount++;
      }
      return amount;
    }
    public int estimateParsedLength() {
      return estimatePrintedLength();
    }
    public int estimatePrintedLength() {
      int est = 1 + iMinFields << 1;
      if(iShowSeparators) {
        est += iMinFields - 1;
      }
      if(iZeroOffsetPrintText != null && iZeroOffsetPrintText.length() > est) {
        est = iZeroOffsetPrintText.length();
      }
      return est;
    }
    public int parseInto(DateTimeParserBucket bucket, String text, int position) {
      int limit = text.length() - position;
      zeroOffset:
        if(iZeroOffsetParseText != null) {
          if(iZeroOffsetParseText.length() == 0) {
            if(limit > 0) {
              char c = text.charAt(position);
              if(c == '-' || c == '+') {
                break zeroOffset;
              }
            }
            bucket.setOffset(Integer.valueOf(0));
            return position;
          }
          if(text.regionMatches(true, position, iZeroOffsetParseText, 0, iZeroOffsetParseText.length())) {
            bucket.setOffset(Integer.valueOf(0));
            return position + iZeroOffsetParseText.length();
          }
        }
      if(limit <= 1) {
        return ~position;
      }
      boolean negative;
      char c = text.charAt(position);
      if(c == '-') {
        negative = true;
      }
      else 
        if(c == '+') {
          negative = false;
        }
        else {
          return ~position;
        }
      limit--;
      position++;
      if(digitCount(text, position, 2) < 2) {
        return ~position;
      }
      int offset;
      int hours = FormatUtils.parseTwoDigits(text, position);
      if(hours > 23) {
        return ~position;
      }
      offset = hours * DateTimeConstants.MILLIS_PER_HOUR;
      limit -= 2;
      position += 2;
      parse:{
        if(limit <= 0) {
          break parse;
        }
        boolean expectSeparators;
        c = text.charAt(position);
        if(c == ':') {
          expectSeparators = true;
          limit--;
          position++;
        }
        else 
          if(c >= '0' && c <= '9') {
            expectSeparators = false;
          }
          else {
            break parse;
          }
        int count = digitCount(text, position, 2);
        if(count == 0 && !expectSeparators) {
          break parse;
        }
        else 
          if(count < 2) {
            return ~position;
          }
        int minutes = FormatUtils.parseTwoDigits(text, position);
        if(minutes > 59) {
          return ~position;
        }
        offset += minutes * DateTimeConstants.MILLIS_PER_MINUTE;
        limit -= 2;
        position += 2;
        if(limit <= 0) {
          break parse;
        }
        if(expectSeparators) {
          if(text.charAt(position) != ':') {
            break parse;
          }
          limit--;
          position++;
        }
        count = digitCount(text, position, 2);
        if(count == 0 && !expectSeparators) {
          break parse;
        }
        else 
          if(count < 2) {
            return ~position;
          }
        int seconds = FormatUtils.parseTwoDigits(text, position);
        if(seconds > 59) {
          return ~position;
        }
        offset += seconds * DateTimeConstants.MILLIS_PER_SECOND;
        limit -= 2;
        position += 2;
        if(limit <= 0) {
          break parse;
        }
        if(expectSeparators) {
          if(text.charAt(position) != '.' && text.charAt(position) != ',') {
            break parse;
          }
          limit--;
          position++;
        }
        count = digitCount(text, position, 3);
        if(count == 0 && !expectSeparators) {
          break parse;
        }
        else 
          if(count < 1) {
            return ~position;
          }
        offset += (text.charAt(position++) - '0') * 100;
        if(count > 1) {
          offset += (text.charAt(position++) - '0') * 10;
          if(count > 2) {
            offset += text.charAt(position++) - '0';
          }
        }
      }
      bucket.setOffset(Integer.valueOf(negative ? -offset : offset));
      return position;
    }
    public void printTo(Writer out, long instant, Chronology chrono, int displayOffset, DateTimeZone displayZone, Locale locale) throws IOException {
      if(displayZone == null) {
        return ;
      }
      if(displayOffset == 0 && iZeroOffsetPrintText != null) {
        out.write(iZeroOffsetPrintText);
        return ;
      }
      if(displayOffset >= 0) {
        out.write('+');
      }
      else {
        out.write('-');
        displayOffset = -displayOffset;
      }
      int hours = displayOffset / DateTimeConstants.MILLIS_PER_HOUR;
      FormatUtils.writePaddedInteger(out, hours, 2);
      if(iMaxFields == 1) {
        return ;
      }
      displayOffset -= hours * (int)DateTimeConstants.MILLIS_PER_HOUR;
      if(displayOffset == 0 && iMinFields == 1) {
        return ;
      }
      int minutes = displayOffset / DateTimeConstants.MILLIS_PER_MINUTE;
      if(iShowSeparators) {
        out.write(':');
      }
      FormatUtils.writePaddedInteger(out, minutes, 2);
      if(iMaxFields == 2) {
        return ;
      }
      displayOffset -= minutes * DateTimeConstants.MILLIS_PER_MINUTE;
      if(displayOffset == 0 && iMinFields == 2) {
        return ;
      }
      int seconds = displayOffset / DateTimeConstants.MILLIS_PER_SECOND;
      if(iShowSeparators) {
        out.write(':');
      }
      FormatUtils.writePaddedInteger(out, seconds, 2);
      if(iMaxFields == 3) {
        return ;
      }
      displayOffset -= seconds * DateTimeConstants.MILLIS_PER_SECOND;
      if(displayOffset == 0 && iMinFields == 3) {
        return ;
      }
      if(iShowSeparators) {
        out.write('.');
      }
      FormatUtils.writePaddedInteger(out, displayOffset, 3);
    }
    public void printTo(Writer out, ReadablePartial partial, Locale locale) throws IOException {
    }
    public void printTo(StringBuffer buf, long instant, Chronology chrono, int displayOffset, DateTimeZone displayZone, Locale locale) {
      if(displayZone == null) {
        return ;
      }
      if(displayOffset == 0 && iZeroOffsetPrintText != null) {
        buf.append(iZeroOffsetPrintText);
        return ;
      }
      if(displayOffset >= 0) {
        buf.append('+');
      }
      else {
        buf.append('-');
        displayOffset = -displayOffset;
      }
      int hours = displayOffset / DateTimeConstants.MILLIS_PER_HOUR;
      FormatUtils.appendPaddedInteger(buf, hours, 2);
      if(iMaxFields == 1) {
        return ;
      }
      displayOffset -= hours * (int)DateTimeConstants.MILLIS_PER_HOUR;
      if(displayOffset == 0 && iMinFields <= 1) {
        return ;
      }
      int minutes = displayOffset / DateTimeConstants.MILLIS_PER_MINUTE;
      if(iShowSeparators) {
        buf.append(':');
      }
      FormatUtils.appendPaddedInteger(buf, minutes, 2);
      if(iMaxFields == 2) {
        return ;
      }
      displayOffset -= minutes * DateTimeConstants.MILLIS_PER_MINUTE;
      if(displayOffset == 0 && iMinFields <= 2) {
        return ;
      }
      int seconds = displayOffset / DateTimeConstants.MILLIS_PER_SECOND;
      if(iShowSeparators) {
        buf.append(':');
      }
      FormatUtils.appendPaddedInteger(buf, seconds, 2);
      if(iMaxFields == 3) {
        return ;
      }
      displayOffset -= seconds * DateTimeConstants.MILLIS_PER_SECOND;
      if(displayOffset == 0 && iMinFields <= 3) {
        return ;
      }
      if(iShowSeparators) {
        buf.append('.');
      }
      FormatUtils.appendPaddedInteger(buf, displayOffset, 3);
    }
    public void printTo(StringBuffer buf, ReadablePartial partial, Locale locale) {
    }
  }
  
  static class TwoDigitYear implements DateTimePrinter, DateTimeParser  {
    final private DateTimeFieldType iType;
    final private int iPivot;
    final private boolean iLenientParse;
    TwoDigitYear(DateTimeFieldType type, int pivot, boolean lenientParse) {
      super();
      iType = type;
      iPivot = pivot;
      iLenientParse = lenientParse;
    }
    public int estimateParsedLength() {
      return iLenientParse ? 4 : 2;
    }
    public int estimatePrintedLength() {
      return 2;
    }
    private int getTwoDigitYear(long instant, Chronology chrono) {
      try {
        int year = iType.getField(chrono).get(instant);
        if(year < 0) {
          year = -year;
        }
        return year % 100;
      }
      catch (RuntimeException e) {
        return -1;
      }
    }
    private int getTwoDigitYear(ReadablePartial partial) {
      if(partial.isSupported(iType)) {
        try {
          int year = partial.get(iType);
          if(year < 0) {
            year = -year;
          }
          return year % 100;
        }
        catch (RuntimeException e) {
        }
      }
      return -1;
    }
    public int parseInto(DateTimeParserBucket bucket, String text, int position) {
      int limit = text.length() - position;
      if(!iLenientParse) {
        limit = Math.min(2, limit);
        if(limit < 2) {
          return ~position;
        }
      }
      else {
        boolean hasSignChar = false;
        boolean negative = false;
        int length = 0;
        while(length < limit){
          char c = text.charAt(position + length);
          if(length == 0 && (c == '-' || c == '+')) {
            hasSignChar = true;
            negative = c == '-';
            if(negative) {
              length++;
            }
            else {
              position++;
              limit--;
            }
            continue ;
          }
          if(c < '0' || c > '9') {
            break ;
          }
          length++;
        }
        if(length == 0) {
          return ~position;
        }
        if(hasSignChar || length != 2) {
          int value;
          if(length >= 9) {
            value = Integer.parseInt(text.substring(position, position += length));
          }
          else {
            int i = position;
            if(negative) {
              i++;
            }
            try {
              value = text.charAt(i++) - '0';
            }
            catch (StringIndexOutOfBoundsException e) {
              return ~position;
            }
            position += length;
            while(i < position){
              value = ((value << 3) + (value << 1)) + text.charAt(i++) - '0';
            }
            if(negative) {
              value = -value;
            }
          }
          bucket.saveField(iType, value);
          return position;
        }
      }
      int year;
      char c = text.charAt(position);
      if(c < '0' || c > '9') {
        return ~position;
      }
      year = c - '0';
      c = text.charAt(position + 1);
      if(c < '0' || c > '9') {
        return ~position;
      }
      year = ((year << 3) + (year << 1)) + c - '0';
      int pivot = iPivot;
      if(bucket.getPivotYear() != null) {
        pivot = bucket.getPivotYear().intValue();
      }
      int low = pivot - 50;
      int t;
      if(low >= 0) {
        t = low % 100;
      }
      else {
        t = 99 + ((low + 1) % 100);
      }
      year += low + ((year < t) ? 100 : 0) - t;
      bucket.saveField(iType, year);
      return position + 2;
    }
    public void printTo(Writer out, long instant, Chronology chrono, int displayOffset, DateTimeZone displayZone, Locale locale) throws IOException {
      int year = getTwoDigitYear(instant, chrono);
      if(year < 0) {
        out.write('\ufffd');
        out.write('\ufffd');
      }
      else {
        FormatUtils.writePaddedInteger(out, year, 2);
      }
    }
    public void printTo(Writer out, ReadablePartial partial, Locale locale) throws IOException {
      int year = getTwoDigitYear(partial);
      if(year < 0) {
        out.write('\ufffd');
        out.write('\ufffd');
      }
      else {
        FormatUtils.writePaddedInteger(out, year, 2);
      }
    }
    public void printTo(StringBuffer buf, long instant, Chronology chrono, int displayOffset, DateTimeZone displayZone, Locale locale) {
      int year = getTwoDigitYear(instant, chrono);
      if(year < 0) {
        buf.append('\ufffd');
        buf.append('\ufffd');
      }
      else {
        FormatUtils.appendPaddedInteger(buf, year, 2);
      }
    }
    public void printTo(StringBuffer buf, ReadablePartial partial, Locale locale) {
      int year = getTwoDigitYear(partial);
      if(year < 0) {
        buf.append('\ufffd');
        buf.append('\ufffd');
      }
      else {
        FormatUtils.appendPaddedInteger(buf, year, 2);
      }
    }
  }
  
  static class UnpaddedNumber extends NumberFormatter  {
    protected UnpaddedNumber(DateTimeFieldType fieldType, int maxParsedDigits, boolean signed) {
      super(fieldType, maxParsedDigits, signed);
    }
    public int estimatePrintedLength() {
      return iMaxParsedDigits;
    }
    public void printTo(Writer out, long instant, Chronology chrono, int displayOffset, DateTimeZone displayZone, Locale locale) throws IOException {
      try {
        DateTimeField field = iFieldType.getField(chrono);
        FormatUtils.writeUnpaddedInteger(out, field.get(instant));
      }
      catch (RuntimeException e) {
        out.write('\ufffd');
      }
    }
    public void printTo(Writer out, ReadablePartial partial, Locale locale) throws IOException {
      if(partial.isSupported(iFieldType)) {
        try {
          FormatUtils.writeUnpaddedInteger(out, partial.get(iFieldType));
        }
        catch (RuntimeException e) {
          out.write('\ufffd');
        }
      }
      else {
        out.write('\ufffd');
      }
    }
    public void printTo(StringBuffer buf, long instant, Chronology chrono, int displayOffset, DateTimeZone displayZone, Locale locale) {
      try {
        DateTimeField field = iFieldType.getField(chrono);
        FormatUtils.appendUnpaddedInteger(buf, field.get(instant));
      }
      catch (RuntimeException e) {
        buf.append('\ufffd');
      }
    }
    public void printTo(StringBuffer buf, ReadablePartial partial, Locale locale) {
      if(partial.isSupported(iFieldType)) {
        try {
          FormatUtils.appendUnpaddedInteger(buf, partial.get(iFieldType));
        }
        catch (RuntimeException e) {
          buf.append('\ufffd');
        }
      }
      else {
        buf.append('\ufffd');
      }
    }
  }
}