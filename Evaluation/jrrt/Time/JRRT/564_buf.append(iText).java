package org.joda.time.format;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import org.joda.time.DateTimeConstants;
import org.joda.time.DurationFieldType;
import org.joda.time.PeriodType;
import org.joda.time.ReadWritablePeriod;
import org.joda.time.ReadablePeriod;

public class PeriodFormatterBuilder  {
  final private static int PRINT_ZERO_RARELY_FIRST = 1;
  final private static int PRINT_ZERO_RARELY_LAST = 2;
  final private static int PRINT_ZERO_IF_SUPPORTED = 3;
  final private static int PRINT_ZERO_ALWAYS = 4;
  final private static int PRINT_ZERO_NEVER = 5;
  final private static int YEARS = 0;
  final private static int MONTHS = 1;
  final private static int WEEKS = 2;
  final private static int DAYS = 3;
  final private static int HOURS = 4;
  final private static int MINUTES = 5;
  final private static int SECONDS = 6;
  final private static int MILLIS = 7;
  final private static int SECONDS_MILLIS = 8;
  final private static int SECONDS_OPTIONAL_MILLIS = 9;
  final private static int MAX_FIELD = SECONDS_OPTIONAL_MILLIS;
  private int iMinPrintedDigits;
  private int iPrintZeroSetting;
  private int iMaxParsedDigits;
  private boolean iRejectSignedValues;
  private PeriodFieldAffix iPrefix;
  private List<Object> iElementPairs;
  private boolean iNotPrinter;
  private boolean iNotParser;
  private FieldFormatter[] iFieldFormatters;
  public PeriodFormatterBuilder() {
    super();
    clear();
  }
  private static Object[] createComposite(List<Object> elementPairs) {
    switch (elementPairs.size()){
      case 0:
      return new Object[]{ Literal.EMPTY, Literal.EMPTY } ;
      case 1:
      return new Object[]{ elementPairs.get(0), elementPairs.get(1) } ;
      default:
      Composite comp = new Composite(elementPairs);
      return new Object[]{ comp, comp } ;
    }
  }
  public PeriodFormatter toFormatter() {
    PeriodFormatter formatter = toFormatter(iElementPairs, iNotPrinter, iNotParser);
    iFieldFormatters = (FieldFormatter[])iFieldFormatters.clone();
    return formatter;
  }
  private static PeriodFormatter toFormatter(List<Object> elementPairs, boolean notPrinter, boolean notParser) {
    if(notPrinter && notParser) {
      throw new IllegalStateException("Builder has created neither a printer nor a parser");
    }
    int size = elementPairs.size();
    if(size >= 2 && elementPairs.get(0) instanceof Separator) {
      Separator sep = (Separator)elementPairs.get(0);
      if(sep.iAfterParser == null && sep.iAfterPrinter == null) {
        PeriodFormatter f = toFormatter(elementPairs.subList(2, size), notPrinter, notParser);
        sep = sep.finish(f.getPrinter(), f.getParser());
        return new PeriodFormatter(sep, sep);
      }
    }
    Object[] comp = createComposite(elementPairs);
    if(notPrinter) {
      return new PeriodFormatter(null, (PeriodParser)comp[1]);
    }
    else 
      if(notParser) {
        return new PeriodFormatter((PeriodPrinter)comp[0], null);
      }
      else {
        return new PeriodFormatter((PeriodPrinter)comp[0], (PeriodParser)comp[1]);
      }
  }
  public PeriodFormatterBuilder append(PeriodFormatter formatter) {
    if(formatter == null) {
      throw new IllegalArgumentException("No formatter supplied");
    }
    clearPrefix();
    append0(formatter.getPrinter(), formatter.getParser());
    return this;
  }
  public PeriodFormatterBuilder append(PeriodPrinter printer, PeriodParser parser) {
    if(printer == null && parser == null) {
      throw new IllegalArgumentException("No printer or parser supplied");
    }
    clearPrefix();
    append0(printer, parser);
    return this;
  }
  private PeriodFormatterBuilder append0(PeriodPrinter printer, PeriodParser parser) {
    iElementPairs.add(printer);
    iElementPairs.add(parser);
    iNotPrinter |= (printer == null);
    iNotParser |= (parser == null);
    return this;
  }
  public PeriodFormatterBuilder appendDays() {
    appendField(DAYS);
    return this;
  }
  public PeriodFormatterBuilder appendHours() {
    appendField(HOURS);
    return this;
  }
  public PeriodFormatterBuilder appendLiteral(String text) {
    if(text == null) {
      throw new IllegalArgumentException("Literal must not be null");
    }
    clearPrefix();
    Literal literal = new Literal(text);
    append0(literal, literal);
    return this;
  }
  public PeriodFormatterBuilder appendMillis() {
    appendField(MILLIS);
    return this;
  }
  public PeriodFormatterBuilder appendMillis3Digit() {
    appendField(7, 3);
    return this;
  }
  public PeriodFormatterBuilder appendMinutes() {
    appendField(MINUTES);
    return this;
  }
  public PeriodFormatterBuilder appendMonths() {
    appendField(MONTHS);
    return this;
  }
  public PeriodFormatterBuilder appendPrefix(String text) {
    if(text == null) {
      throw new IllegalArgumentException();
    }
    return appendPrefix(new SimpleAffix(text));
  }
  public PeriodFormatterBuilder appendPrefix(String singularText, String pluralText) {
    if(singularText == null || pluralText == null) {
      throw new IllegalArgumentException();
    }
    return appendPrefix(new PluralAffix(singularText, pluralText));
  }
  private PeriodFormatterBuilder appendPrefix(PeriodFieldAffix prefix) {
    if(prefix == null) {
      throw new IllegalArgumentException();
    }
    if(iPrefix != null) {
      prefix = new CompositeAffix(iPrefix, prefix);
    }
    iPrefix = prefix;
    return this;
  }
  public PeriodFormatterBuilder appendSeconds() {
    appendField(SECONDS);
    return this;
  }
  public PeriodFormatterBuilder appendSecondsWithMillis() {
    appendField(SECONDS_MILLIS);
    return this;
  }
  public PeriodFormatterBuilder appendSecondsWithOptionalMillis() {
    appendField(SECONDS_OPTIONAL_MILLIS);
    return this;
  }
  public PeriodFormatterBuilder appendSeparator(String text) {
    return appendSeparator(text, text, null, true, true);
  }
  public PeriodFormatterBuilder appendSeparator(String text, String finalText) {
    return appendSeparator(text, finalText, null, true, true);
  }
  public PeriodFormatterBuilder appendSeparator(String text, String finalText, String[] variants) {
    return appendSeparator(text, finalText, variants, true, true);
  }
  private PeriodFormatterBuilder appendSeparator(String text, String finalText, String[] variants, boolean useBefore, boolean useAfter) {
    if(text == null || finalText == null) {
      throw new IllegalArgumentException();
    }
    clearPrefix();
    List<Object> pairs = iElementPairs;
    if(pairs.size() == 0) {
      if(useAfter && useBefore == false) {
        Separator separator = new Separator(text, finalText, variants, Literal.EMPTY, Literal.EMPTY, useBefore, useAfter);
        append0(separator, separator);
      }
      return this;
    }
    int i;
    Separator lastSeparator = null;
    for(i = pairs.size(); --i >= 0; ) {
      if(pairs.get(i) instanceof Separator) {
        lastSeparator = (Separator)pairs.get(i);
        pairs = pairs.subList(i + 1, pairs.size());
        break ;
      }
      i--;
    }
    if(lastSeparator != null && pairs.size() == 0) {
      throw new IllegalStateException("Cannot have two adjacent separators");
    }
    else {
      Object[] comp = createComposite(pairs);
      pairs.clear();
      Separator separator = new Separator(text, finalText, variants, (PeriodPrinter)comp[0], (PeriodParser)comp[1], useBefore, useAfter);
      pairs.add(separator);
      pairs.add(separator);
    }
    return this;
  }
  public PeriodFormatterBuilder appendSeparatorIfFieldsAfter(String text) {
    return appendSeparator(text, text, null, false, true);
  }
  public PeriodFormatterBuilder appendSeparatorIfFieldsBefore(String text) {
    return appendSeparator(text, text, null, true, false);
  }
  public PeriodFormatterBuilder appendSuffix(String text) {
    if(text == null) {
      throw new IllegalArgumentException();
    }
    return appendSuffix(new SimpleAffix(text));
  }
  public PeriodFormatterBuilder appendSuffix(String singularText, String pluralText) {
    if(singularText == null || pluralText == null) {
      throw new IllegalArgumentException();
    }
    return appendSuffix(new PluralAffix(singularText, pluralText));
  }
  private PeriodFormatterBuilder appendSuffix(PeriodFieldAffix suffix) {
    final Object originalPrinter;
    final Object originalParser;
    if(iElementPairs.size() > 0) {
      originalPrinter = iElementPairs.get(iElementPairs.size() - 2);
      originalParser = iElementPairs.get(iElementPairs.size() - 1);
    }
    else {
      originalPrinter = null;
      originalParser = null;
    }
    if(originalPrinter == null || originalParser == null || originalPrinter != originalParser || !(originalPrinter instanceof FieldFormatter)) {
      throw new IllegalStateException("No field to apply suffix to");
    }
    clearPrefix();
    FieldFormatter newField = new FieldFormatter((FieldFormatter)originalPrinter, suffix);
    iElementPairs.set(iElementPairs.size() - 2, newField);
    iElementPairs.set(iElementPairs.size() - 1, newField);
    iFieldFormatters[newField.getFieldType()] = newField;
    return this;
  }
  public PeriodFormatterBuilder appendWeeks() {
    appendField(WEEKS);
    return this;
  }
  public PeriodFormatterBuilder appendYears() {
    appendField(YEARS);
    return this;
  }
  public PeriodFormatterBuilder maximumParsedDigits(int maxDigits) {
    iMaxParsedDigits = maxDigits;
    return this;
  }
  public PeriodFormatterBuilder minimumPrintedDigits(int minDigits) {
    iMinPrintedDigits = minDigits;
    return this;
  }
  public PeriodFormatterBuilder printZeroAlways() {
    iPrintZeroSetting = PRINT_ZERO_ALWAYS;
    return this;
  }
  public PeriodFormatterBuilder printZeroIfSupported() {
    iPrintZeroSetting = PRINT_ZERO_IF_SUPPORTED;
    return this;
  }
  public PeriodFormatterBuilder printZeroNever() {
    iPrintZeroSetting = PRINT_ZERO_NEVER;
    return this;
  }
  public PeriodFormatterBuilder printZeroRarelyFirst() {
    iPrintZeroSetting = PRINT_ZERO_RARELY_FIRST;
    return this;
  }
  public PeriodFormatterBuilder printZeroRarelyLast() {
    iPrintZeroSetting = PRINT_ZERO_RARELY_LAST;
    return this;
  }
  public PeriodFormatterBuilder rejectSignedValues(boolean v) {
    iRejectSignedValues = v;
    return this;
  }
  public PeriodParser toParser() {
    if(iNotParser) {
      return null;
    }
    return toFormatter().getParser();
  }
  public PeriodPrinter toPrinter() {
    if(iNotPrinter) {
      return null;
    }
    return toFormatter().getPrinter();
  }
  private void appendField(int type) {
    appendField(type, iMinPrintedDigits);
  }
  private void appendField(int type, int minPrinted) {
    FieldFormatter field = new FieldFormatter(minPrinted, iPrintZeroSetting, iMaxParsedDigits, iRejectSignedValues, type, iFieldFormatters, iPrefix, null);
    append0(field, field);
    iFieldFormatters[type] = field;
    iPrefix = null;
  }
  public void clear() {
    iMinPrintedDigits = 1;
    iPrintZeroSetting = PRINT_ZERO_RARELY_LAST;
    iMaxParsedDigits = 10;
    iRejectSignedValues = false;
    iPrefix = null;
    if(iElementPairs == null) {
      iElementPairs = new ArrayList<Object>();
    }
    else {
      iElementPairs.clear();
    }
    iNotPrinter = false;
    iNotParser = false;
    iFieldFormatters = new FieldFormatter[10];
  }
  private void clearPrefix() throws IllegalStateException {
    if(iPrefix != null) {
      throw new IllegalStateException("Prefix not followed by field");
    }
    iPrefix = null;
  }
  
  static class Composite implements PeriodPrinter, PeriodParser  {
    final private PeriodPrinter[] iPrinters;
    final private PeriodParser[] iParsers;
    Composite(List<Object> elementPairs) {
      super();
      List<Object> printerList = new ArrayList<Object>();
      List<Object> parserList = new ArrayList<Object>();
      decompose(elementPairs, printerList, parserList);
      if(printerList.size() <= 0) {
        iPrinters = null;
      }
      else {
        iPrinters = printerList.toArray(new PeriodPrinter[printerList.size()]);
      }
      if(parserList.size() <= 0) {
        iParsers = null;
      }
      else {
        iParsers = parserList.toArray(new PeriodParser[parserList.size()]);
      }
    }
    public int calculatePrintedLength(ReadablePeriod period, Locale locale) {
      int sum = 0;
      PeriodPrinter[] printers = iPrinters;
      for(int i = printers.length; --i >= 0; ) {
        sum += printers[i].calculatePrintedLength(period, locale);
      }
      return sum;
    }
    public int countFieldsToPrint(ReadablePeriod period, int stopAt, Locale locale) {
      int sum = 0;
      PeriodPrinter[] printers = iPrinters;
      for(int i = printers.length; sum < stopAt && --i >= 0; ) {
        sum += printers[i].countFieldsToPrint(period, Integer.MAX_VALUE, locale);
      }
      return sum;
    }
    public int parseInto(ReadWritablePeriod period, String periodStr, int position, Locale locale) {
      PeriodParser[] parsers = iParsers;
      if(parsers == null) {
        throw new UnsupportedOperationException();
      }
      int len = parsers.length;
      for(int i = 0; i < len && position >= 0; i++) {
        position = parsers[i].parseInto(period, periodStr, position, locale);
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
        if(element instanceof PeriodPrinter) {
          if(element instanceof Composite) {
            addArrayToList(printerList, ((Composite)element).iPrinters);
          }
          else {
            printerList.add(element);
          }
        }
        element = elementPairs.get(i + 1);
        if(element instanceof PeriodParser) {
          if(element instanceof Composite) {
            addArrayToList(parserList, ((Composite)element).iParsers);
          }
          else {
            parserList.add(element);
          }
        }
      }
    }
    public void printTo(Writer out, ReadablePeriod period, Locale locale) throws IOException {
      PeriodPrinter[] printers = iPrinters;
      int len = printers.length;
      for(int i = 0; i < len; i++) {
        printers[i].printTo(out, period, locale);
      }
    }
    public void printTo(StringBuffer buf, ReadablePeriod period, Locale locale) {
      PeriodPrinter[] printers = iPrinters;
      int len = printers.length;
      for(int i = 0; i < len; i++) {
        printers[i].printTo(buf, period, locale);
      }
    }
  }
  
  static class CompositeAffix implements PeriodFieldAffix  {
    final private PeriodFieldAffix iLeft;
    final private PeriodFieldAffix iRight;
    CompositeAffix(PeriodFieldAffix left, PeriodFieldAffix right) {
      super();
      iLeft = left;
      iRight = right;
    }
    public int calculatePrintedLength(int value) {
      return iLeft.calculatePrintedLength(value) + iRight.calculatePrintedLength(value);
    }
    public int parse(String periodStr, int position) {
      position = iLeft.parse(periodStr, position);
      if(position >= 0) {
        position = iRight.parse(periodStr, position);
      }
      return position;
    }
    public int scan(String periodStr, final int position) {
      int pos = iLeft.scan(periodStr, position);
      if(pos >= 0) {
        return iRight.scan(periodStr, pos);
      }
      return ~position;
    }
    public void printTo(Writer out, int value) throws IOException {
      iLeft.printTo(out, value);
      iRight.printTo(out, value);
    }
    public void printTo(StringBuffer buf, int value) {
      iLeft.printTo(buf, value);
      iRight.printTo(buf, value);
    }
  }
  
  static class FieldFormatter implements PeriodPrinter, PeriodParser  {
    final private int iMinPrintedDigits;
    final private int iPrintZeroSetting;
    final private int iMaxParsedDigits;
    final private boolean iRejectSignedValues;
    final private int iFieldType;
    final private FieldFormatter[] iFieldFormatters;
    final private PeriodFieldAffix iPrefix;
    final private PeriodFieldAffix iSuffix;
    FieldFormatter(FieldFormatter field, PeriodFieldAffix suffix) {
      super();
      iMinPrintedDigits = field.iMinPrintedDigits;
      iPrintZeroSetting = field.iPrintZeroSetting;
      iMaxParsedDigits = field.iMaxParsedDigits;
      iRejectSignedValues = field.iRejectSignedValues;
      iFieldType = field.iFieldType;
      iFieldFormatters = field.iFieldFormatters;
      iPrefix = field.iPrefix;
      if(field.iSuffix != null) {
        suffix = new CompositeAffix(field.iSuffix, suffix);
      }
      iSuffix = suffix;
    }
    FieldFormatter(int minPrintedDigits, int printZeroSetting, int maxParsedDigits, boolean rejectSignedValues, int fieldType, FieldFormatter[] fieldFormatters, PeriodFieldAffix prefix, PeriodFieldAffix suffix) {
      super();
      iMinPrintedDigits = minPrintedDigits;
      iPrintZeroSetting = printZeroSetting;
      iMaxParsedDigits = maxParsedDigits;
      iRejectSignedValues = rejectSignedValues;
      iFieldType = fieldType;
      iFieldFormatters = fieldFormatters;
      iPrefix = prefix;
      iSuffix = suffix;
    }
    boolean isSupported(PeriodType type, int field) {
      switch (field){
        default:
        return false;
        case YEARS:
        return type.isSupported(DurationFieldType.years());
        case MONTHS:
        return type.isSupported(DurationFieldType.months());
        case WEEKS:
        return type.isSupported(DurationFieldType.weeks());
        case DAYS:
        return type.isSupported(DurationFieldType.days());
        case HOURS:
        return type.isSupported(DurationFieldType.hours());
        case MINUTES:
        return type.isSupported(DurationFieldType.minutes());
        case SECONDS:
        return type.isSupported(DurationFieldType.seconds());
        case MILLIS:
        return type.isSupported(DurationFieldType.millis());
        case SECONDS_MILLIS:
        case SECONDS_OPTIONAL_MILLIS:
        return type.isSupported(DurationFieldType.seconds()) || type.isSupported(DurationFieldType.millis());
      }
    }
    boolean isZero(ReadablePeriod period) {
      for(int i = 0, isize = period.size(); i < isize; i++) {
        if(period.getValue(i) != 0) {
          return false;
        }
      }
      return true;
    }
    public int calculatePrintedLength(ReadablePeriod period, Locale locale) {
      long valueLong = getFieldValue(period);
      if(valueLong == Long.MAX_VALUE) {
        return 0;
      }
      int sum = Math.max(FormatUtils.calculateDigitCount(valueLong), iMinPrintedDigits);
      if(iFieldType >= SECONDS_MILLIS) {
        sum = (valueLong < 0 ? Math.max(sum, 5) : Math.max(sum, 4));
        sum++;
        if(iFieldType == SECONDS_OPTIONAL_MILLIS && (Math.abs(valueLong) % DateTimeConstants.MILLIS_PER_SECOND) == 0) {
          sum -= 4;
        }
        valueLong = valueLong / DateTimeConstants.MILLIS_PER_SECOND;
      }
      int value = (int)valueLong;
      if(iPrefix != null) {
        sum += iPrefix.calculatePrintedLength(value);
      }
      if(iSuffix != null) {
        sum += iSuffix.calculatePrintedLength(value);
      }
      return sum;
    }
    public int countFieldsToPrint(ReadablePeriod period, int stopAt, Locale locale) {
      if(stopAt <= 0) {
        return 0;
      }
      if(iPrintZeroSetting == PRINT_ZERO_ALWAYS || getFieldValue(period) != Long.MAX_VALUE) {
        return 1;
      }
      return 0;
    }
    int getFieldType() {
      return iFieldType;
    }
    private int parseInt(String text, int position, int length) {
      if(length >= 10) {
        return Integer.parseInt(text.substring(position, position + length));
      }
      if(length <= 0) {
        return 0;
      }
      int value = text.charAt(position++);
      length--;
      boolean negative;
      if(value == '-') {
        if(--length < 0) {
          return 0;
        }
        negative = true;
        value = text.charAt(position++);
      }
      else {
        negative = false;
      }
      value -= '0';
      while(length-- > 0){
        value = ((value << 3) + (value << 1)) + text.charAt(position++) - '0';
      }
      return negative ? -value : value;
    }
    public int parseInto(ReadWritablePeriod period, String text, int position, Locale locale) {
      boolean mustParse = (iPrintZeroSetting == PRINT_ZERO_ALWAYS);
      if(position >= text.length()) {
        return mustParse ? ~position : position;
      }
      if(iPrefix != null) {
        position = iPrefix.parse(text, position);
        if(position >= 0) {
          mustParse = true;
        }
        else {
          if(!mustParse) {
            return ~position;
          }
          return position;
        }
      }
      int suffixPos = -1;
      if(iSuffix != null && !mustParse) {
        suffixPos = iSuffix.scan(text, position);
        if(suffixPos >= 0) {
          mustParse = true;
        }
        else {
          if(!mustParse) {
            return ~suffixPos;
          }
          return suffixPos;
        }
      }
      if(!mustParse && !isSupported(period.getPeriodType(), iFieldType)) {
        return position;
      }
      int limit;
      if(suffixPos > 0) {
        limit = Math.min(iMaxParsedDigits, suffixPos - position);
      }
      else {
        limit = Math.min(iMaxParsedDigits, text.length() - position);
      }
      int length = 0;
      int fractPos = -1;
      boolean hasDigits = false;
      while(length < limit){
        char c = text.charAt(position + length);
        if(length == 0 && (c == '-' || c == '+') && !iRejectSignedValues) {
          boolean negative = c == '-';
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
        if(c >= '0' && c <= '9') {
          hasDigits = true;
        }
        else {
          if((c == '.' || c == ',') && (iFieldType == SECONDS_MILLIS || iFieldType == SECONDS_OPTIONAL_MILLIS)) {
            if(fractPos >= 0) {
              break ;
            }
            fractPos = position + length + 1;
            limit = Math.min(limit + 1, text.length() - position);
          }
          else {
            break ;
          }
        }
        length++;
      }
      if(!hasDigits) {
        return ~position;
      }
      if(suffixPos >= 0 && position + length != suffixPos) {
        return position;
      }
      if(iFieldType != SECONDS_MILLIS && iFieldType != SECONDS_OPTIONAL_MILLIS) {
        setFieldValue(period, iFieldType, parseInt(text, position, length));
      }
      else 
        if(fractPos < 0) {
          setFieldValue(period, SECONDS, parseInt(text, position, length));
          setFieldValue(period, MILLIS, 0);
        }
        else {
          int wholeValue = parseInt(text, position, fractPos - position - 1);
          setFieldValue(period, SECONDS, wholeValue);
          int fractLen = position + length - fractPos;
          int fractValue;
          if(fractLen <= 0) {
            fractValue = 0;
          }
          else {
            if(fractLen >= 3) {
              fractValue = parseInt(text, fractPos, 3);
            }
            else {
              fractValue = parseInt(text, fractPos, fractLen);
              if(fractLen == 1) {
                fractValue *= 100;
              }
              else {
                fractValue *= 10;
              }
            }
            if(wholeValue < 0) {
              fractValue = -fractValue;
            }
          }
          setFieldValue(period, MILLIS, fractValue);
        }
      position += length;
      if(position >= 0 && iSuffix != null) {
        position = iSuffix.parse(text, position);
      }
      return position;
    }
    long getFieldValue(ReadablePeriod period) {
      PeriodType type;
      if(iPrintZeroSetting == PRINT_ZERO_ALWAYS) {
        type = null;
      }
      else {
        type = period.getPeriodType();
      }
      if(type != null && isSupported(type, iFieldType) == false) {
        return Long.MAX_VALUE;
      }
      long value;
      switch (iFieldType){
        default:
        return Long.MAX_VALUE;
        case YEARS:
        value = period.get(DurationFieldType.years());
        break ;
        case MONTHS:
        value = period.get(DurationFieldType.months());
        break ;
        case WEEKS:
        value = period.get(DurationFieldType.weeks());
        break ;
        case DAYS:
        value = period.get(DurationFieldType.days());
        break ;
        case HOURS:
        value = period.get(DurationFieldType.hours());
        break ;
        case MINUTES:
        value = period.get(DurationFieldType.minutes());
        break ;
        case SECONDS:
        value = period.get(DurationFieldType.seconds());
        break ;
        case MILLIS:
        value = period.get(DurationFieldType.millis());
        break ;
        case SECONDS_MILLIS:
        case SECONDS_OPTIONAL_MILLIS:
        int seconds = period.get(DurationFieldType.seconds());
        int millis = period.get(DurationFieldType.millis());
        value = (seconds * (long)DateTimeConstants.MILLIS_PER_SECOND) + millis;
        break ;
      }
      if(value == 0) {
        switch (iPrintZeroSetting){
          case PRINT_ZERO_NEVER:
          return Long.MAX_VALUE;
          case PRINT_ZERO_RARELY_LAST:
          if(isZero(period) && iFieldFormatters[iFieldType] == this) {
            for(int i = iFieldType + 1; i <= MAX_FIELD; i++) {
              if(isSupported(type, i) && iFieldFormatters[i] != null) {
                return Long.MAX_VALUE;
              }
            }
          }
          else {
            return Long.MAX_VALUE;
          }
          break ;
          case PRINT_ZERO_RARELY_FIRST:
          if(isZero(period) && iFieldFormatters[iFieldType] == this) {
            int i = Math.min(iFieldType, 8);
            i--;
            for(; i >= 0 && i <= MAX_FIELD; i--) {
              if(isSupported(type, i) && iFieldFormatters[i] != null) {
                return Long.MAX_VALUE;
              }
            }
          }
          else {
            return Long.MAX_VALUE;
          }
          break ;
        }
      }
      return value;
    }
    public void printTo(Writer out, ReadablePeriod period, Locale locale) throws IOException {
      long valueLong = getFieldValue(period);
      if(valueLong == Long.MAX_VALUE) {
        return ;
      }
      int value = (int)valueLong;
      if(iFieldType >= SECONDS_MILLIS) {
        value = (int)(valueLong / DateTimeConstants.MILLIS_PER_SECOND);
      }
      if(iPrefix != null) {
        iPrefix.printTo(out, value);
      }
      int minDigits = iMinPrintedDigits;
      if(minDigits <= 1) {
        FormatUtils.writeUnpaddedInteger(out, value);
      }
      else {
        FormatUtils.writePaddedInteger(out, value, minDigits);
      }
      if(iFieldType >= SECONDS_MILLIS) {
        int dp = (int)(Math.abs(valueLong) % DateTimeConstants.MILLIS_PER_SECOND);
        if(iFieldType == SECONDS_MILLIS || dp > 0) {
          out.write('.');
          FormatUtils.writePaddedInteger(out, dp, 3);
        }
      }
      if(iSuffix != null) {
        iSuffix.printTo(out, value);
      }
    }
    public void printTo(StringBuffer buf, ReadablePeriod period, Locale locale) {
      long valueLong = getFieldValue(period);
      if(valueLong == Long.MAX_VALUE) {
        return ;
      }
      int value = (int)valueLong;
      if(iFieldType >= SECONDS_MILLIS) {
        value = (int)(valueLong / DateTimeConstants.MILLIS_PER_SECOND);
      }
      if(iPrefix != null) {
        iPrefix.printTo(buf, value);
      }
      int bufLen = buf.length();
      int minDigits = iMinPrintedDigits;
      if(minDigits <= 1) {
        FormatUtils.appendUnpaddedInteger(buf, value);
      }
      else {
        FormatUtils.appendPaddedInteger(buf, value, minDigits);
      }
      if(iFieldType >= SECONDS_MILLIS) {
        int dp = (int)(Math.abs(valueLong) % DateTimeConstants.MILLIS_PER_SECOND);
        if(iFieldType == SECONDS_MILLIS || dp > 0) {
          if(valueLong < 0 && valueLong > -DateTimeConstants.MILLIS_PER_SECOND) {
            buf.insert(bufLen, '-');
          }
          buf.append('.');
          FormatUtils.appendPaddedInteger(buf, dp, 3);
        }
      }
      if(iSuffix != null) {
        iSuffix.printTo(buf, value);
      }
    }
    void setFieldValue(ReadWritablePeriod period, int field, int value) {
      switch (field){
        default:
        break ;
        case YEARS:
        period.setYears(value);
        break ;
        case MONTHS:
        period.setMonths(value);
        break ;
        case WEEKS:
        period.setWeeks(value);
        break ;
        case DAYS:
        period.setDays(value);
        break ;
        case HOURS:
        period.setHours(value);
        break ;
        case MINUTES:
        period.setMinutes(value);
        break ;
        case SECONDS:
        period.setSeconds(value);
        break ;
        case MILLIS:
        period.setMillis(value);
        break ;
      }
    }
  }
  
  static class Literal implements PeriodPrinter, PeriodParser  {
    final static Literal EMPTY = new Literal("");
    final private String iText;
    Literal(String text) {
      super();
      iText = text;
    }
    public int calculatePrintedLength(ReadablePeriod period, Locale locale) {
      return iText.length();
    }
    public int countFieldsToPrint(ReadablePeriod period, int stopAt, Locale locale) {
      return 0;
    }
    public int parseInto(ReadWritablePeriod period, String periodStr, int position, Locale locale) {
      if(periodStr.regionMatches(true, position, iText, 0, iText.length())) {
        return position + iText.length();
      }
      return ~position;
    }
    public void printTo(Writer out, ReadablePeriod period, Locale locale) throws IOException {
      out.write(iText);
    }
    public void printTo(StringBuffer buf, ReadablePeriod period, Locale locale) {
      buf.append(iText);
    }
  }
  
  static interface PeriodFieldAffix  {
    int calculatePrintedLength(int value);
    int parse(String periodStr, int position);
    int scan(String periodStr, int position);
    void printTo(Writer out, int value) throws IOException;
    void printTo(StringBuffer buf, int value);
  }
  
  static class PluralAffix implements PeriodFieldAffix  {
    final private String iSingularText;
    final private String iPluralText;
    PluralAffix(String singularText, String pluralText) {
      super();
      iSingularText = singularText;
      iPluralText = pluralText;
    }
    public int calculatePrintedLength(int value) {
      return (value == 1 ? iSingularText : iPluralText).length();
    }
    public int parse(String periodStr, int position) {
      String text1 = iPluralText;
      String text2 = iSingularText;
      if(text1.length() < text2.length()) {
        String temp = text1;
        text1 = text2;
        text2 = temp;
      }
      if(periodStr.regionMatches(true, position, text1, 0, text1.length())) {
        return position + text1.length();
      }
      if(periodStr.regionMatches(true, position, text2, 0, text2.length())) {
        return position + text2.length();
      }
      return ~position;
    }
    public int scan(String periodStr, final int position) {
      String text1 = iPluralText;
      String text2 = iSingularText;
      if(text1.length() < text2.length()) {
        String temp = text1;
        text1 = text2;
        text2 = temp;
      }
      int textLength1 = text1.length();
      int textLength2 = text2.length();
      int sourceLength = periodStr.length();
      for(int pos = position; pos < sourceLength; pos++) {
        if(periodStr.regionMatches(true, pos, text1, 0, textLength1)) {
          return pos;
        }
        if(periodStr.regionMatches(true, pos, text2, 0, textLength2)) {
          return pos;
        }
      }
      return ~position;
    }
    public void printTo(Writer out, int value) throws IOException {
      out.write(value == 1 ? iSingularText : iPluralText);
    }
    public void printTo(StringBuffer buf, int value) {
      buf.append(value == 1 ? iSingularText : iPluralText);
    }
  }
  
  static class Separator implements PeriodPrinter, PeriodParser  {
    final private String iText;
    final private String iFinalText;
    final private String[] iParsedForms;
    final private boolean iUseBefore;
    final private boolean iUseAfter;
    final private PeriodPrinter iBeforePrinter;
    private volatile PeriodPrinter iAfterPrinter;
    final private PeriodParser iBeforeParser;
    private volatile PeriodParser iAfterParser;
    Separator(String text, String finalText, String[] variants, PeriodPrinter beforePrinter, PeriodParser beforeParser, boolean useBefore, boolean useAfter) {
      super();
      iText = text;
      iFinalText = finalText;
      if((finalText == null || text.equals(finalText)) && (variants == null || variants.length == 0)) {
        iParsedForms = new String[]{ text } ;
      }
      else {
        TreeSet<String> parsedSet = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        parsedSet.add(text);
        parsedSet.add(finalText);
        if(variants != null) {
          for(int i = variants.length; --i >= 0; ) {
            parsedSet.add(variants[i]);
          }
        }
        ArrayList<String> parsedList = new ArrayList<String>(parsedSet);
        Collections.reverse(parsedList);
        iParsedForms = parsedList.toArray(new String[parsedList.size()]);
      }
      iBeforePrinter = beforePrinter;
      iBeforeParser = beforeParser;
      iUseBefore = useBefore;
      iUseAfter = useAfter;
    }
    Separator finish(PeriodPrinter afterPrinter, PeriodParser afterParser) {
      iAfterPrinter = afterPrinter;
      iAfterParser = afterParser;
      return this;
    }
    public int calculatePrintedLength(ReadablePeriod period, Locale locale) {
      PeriodPrinter before = iBeforePrinter;
      PeriodPrinter after = iAfterPrinter;
      int sum = before.calculatePrintedLength(period, locale) + after.calculatePrintedLength(period, locale);
      if(iUseBefore) {
        if(before.countFieldsToPrint(period, 1, locale) > 0) {
          if(iUseAfter) {
            int afterCount = after.countFieldsToPrint(period, 2, locale);
            if(afterCount > 0) {
              sum += (afterCount > 1 ? iText : iFinalText).length();
            }
          }
          else {
            sum += iText.length();
          }
        }
      }
      else 
        if(iUseAfter && after.countFieldsToPrint(period, 1, locale) > 0) {
          sum += iText.length();
        }
      return sum;
    }
    public int countFieldsToPrint(ReadablePeriod period, int stopAt, Locale locale) {
      int sum = iBeforePrinter.countFieldsToPrint(period, stopAt, locale);
      if(sum < stopAt) {
        sum += iAfterPrinter.countFieldsToPrint(period, stopAt, locale);
      }
      return sum;
    }
    public int parseInto(ReadWritablePeriod period, String periodStr, int position, Locale locale) {
      int oldPos = position;
      position = iBeforeParser.parseInto(period, periodStr, position, locale);
      if(position < 0) {
        return position;
      }
      boolean found = false;
      int parsedFormLength = -1;
      if(position > oldPos) {
        String[] parsedForms = iParsedForms;
        int length = parsedForms.length;
        for(int i = 0; i < length; i++) {
          String parsedForm = parsedForms[i];
          if((parsedForm == null || parsedForm.length() == 0) || periodStr.regionMatches(true, position, parsedForm, 0, parsedForm.length())) {
            parsedFormLength = (parsedForm == null ? 0 : parsedForm.length());
            position += parsedFormLength;
            found = true;
            break ;
          }
        }
      }
      oldPos = position;
      position = iAfterParser.parseInto(period, periodStr, position, locale);
      if(position < 0) {
        return position;
      }
      if(found && position == oldPos && parsedFormLength > 0) {
        return ~oldPos;
      }
      if(position > oldPos && !found && !iUseBefore) {
        return ~oldPos;
      }
      return position;
    }
    public void printTo(Writer out, ReadablePeriod period, Locale locale) throws IOException {
      PeriodPrinter before = iBeforePrinter;
      PeriodPrinter after = iAfterPrinter;
      before.printTo(out, period, locale);
      if(iUseBefore) {
        if(before.countFieldsToPrint(period, 1, locale) > 0) {
          if(iUseAfter) {
            int afterCount = after.countFieldsToPrint(period, 2, locale);
            if(afterCount > 0) {
              out.write(afterCount > 1 ? iText : iFinalText);
            }
          }
          else {
            out.write(iText);
          }
        }
      }
      else 
        if(iUseAfter && after.countFieldsToPrint(period, 1, locale) > 0) {
          out.write(iText);
        }
      after.printTo(out, period, locale);
    }
    public void printTo(StringBuffer buf, ReadablePeriod period, Locale locale) {
      PeriodPrinter before = iBeforePrinter;
      PeriodPrinter after = iAfterPrinter;
      before.printTo(buf, period, locale);
      if(iUseBefore) {
        if(before.countFieldsToPrint(period, 1, locale) > 0) {
          if(iUseAfter) {
            int afterCount = after.countFieldsToPrint(period, 2, locale);
            if(afterCount > 0) {
              buf.append(afterCount > 1 ? iText : iFinalText);
            }
          }
          else {
            StringBuffer var_564 = buf.append(iText);
          }
        }
      }
      else 
        if(iUseAfter && after.countFieldsToPrint(period, 1, locale) > 0) {
          buf.append(iText);
        }
      after.printTo(buf, period, locale);
    }
  }
  
  static class SimpleAffix implements PeriodFieldAffix  {
    final private String iText;
    SimpleAffix(String text) {
      super();
      iText = text;
    }
    public int calculatePrintedLength(int value) {
      return iText.length();
    }
    public int parse(String periodStr, int position) {
      String text = iText;
      int textLength = text.length();
      if(periodStr.regionMatches(true, position, text, 0, textLength)) {
        return position + textLength;
      }
      return ~position;
    }
    public int scan(String periodStr, final int position) {
      String text = iText;
      int textLength = text.length();
      int sourceLength = periodStr.length();
      search:
        for(int pos = position; pos < sourceLength; pos++) {
          if(periodStr.regionMatches(true, pos, text, 0, textLength)) {
            return pos;
          }
          switch (periodStr.charAt(pos)){
            case '0':
            case '1':
            case '2':
            case '3':
            case '4':
            case '5':
            case '6':
            case '7':
            case '8':
            case '9':
            case '.':
            case ',':
            case '+':
            case '-':
            break ;
            default:
            break search;
          }
        }
      return ~position;
    }
    public void printTo(Writer out, int value) throws IOException {
      out.write(iText);
    }
    public void printTo(StringBuffer buf, int value) {
      buf.append(iText);
    }
  }
}