package org.apache.commons.math3.util;
import java.text.FieldPosition;
import java.text.NumberFormat;
import java.text.ParsePosition;
import java.util.Locale;

public class CompositeFormat  {
  private CompositeFormat() {
    super();
  }
  private static Number parseNumber(final String source, final double value, final ParsePosition pos) {
    Number ret = null;
    StringBuilder sb = new StringBuilder();
    sb.append('(');
    sb.append(value);
    sb.append(')');
    final int n = sb.length();
    final int startIndex = pos.getIndex();
    final int endIndex = startIndex + n;
    if(endIndex < source.length() && source.substring(startIndex, endIndex).compareTo(sb.toString()) == 0) {
      ret = Double.valueOf(value);
      pos.setIndex(endIndex);
    }
    return ret;
  }
  public static Number parseNumber(final String source, final NumberFormat format, final ParsePosition pos) {
    int var_4111 = pos.getIndex();
    final int startIndex = var_4111;
    Number number = format.parse(source, pos);
    final int endIndex = pos.getIndex();
    if(startIndex == endIndex) {
      final double[] special = { Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY } ;
      for(int i = 0; i < special.length; ++i) {
        number = parseNumber(source, special[i], pos);
        if(number != null) {
          break ;
        }
      }
    }
    return number;
  }
  public static NumberFormat getDefaultNumberFormat() {
    return getDefaultNumberFormat(Locale.getDefault());
  }
  public static NumberFormat getDefaultNumberFormat(final Locale locale) {
    final NumberFormat nf = NumberFormat.getInstance(locale);
    nf.setMaximumFractionDigits(10);
    return nf;
  }
  public static StringBuffer formatDouble(final double value, final NumberFormat format, final StringBuffer toAppendTo, final FieldPosition pos) {
    if(Double.isNaN(value) || Double.isInfinite(value)) {
      toAppendTo.append('(');
      toAppendTo.append(value);
      toAppendTo.append(')');
    }
    else {
      format.format(value, toAppendTo, pos);
    }
    return toAppendTo;
  }
  public static boolean parseFixedstring(final String source, final String expected, final ParsePosition pos) {
    final int startIndex = pos.getIndex();
    final int endIndex = startIndex + expected.length();
    if((startIndex >= source.length()) || (endIndex > source.length()) || (source.substring(startIndex, endIndex).compareTo(expected) != 0)) {
      pos.setIndex(startIndex);
      pos.setErrorIndex(startIndex);
      return false;
    }
    pos.setIndex(endIndex);
    return true;
  }
  public static char parseNextCharacter(final String source, final ParsePosition pos) {
    int index = pos.getIndex();
    final int n = source.length();
    char ret = 0;
    if(index < n) {
      char c;
      do {
        c = source.charAt(index++);
      }while(Character.isWhitespace(c) && index < n);
      pos.setIndex(index);
      if(index < n) {
        ret = c;
      }
    }
    return ret;
  }
  public static void parseAndIgnoreWhitespace(final String source, final ParsePosition pos) {
    parseNextCharacter(source, pos);
    pos.setIndex(pos.getIndex() - 1);
  }
}