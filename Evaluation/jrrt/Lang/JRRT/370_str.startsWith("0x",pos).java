package org.apache.commons.lang3.math;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.apache.commons.lang3.StringUtils;

public class NumberUtils  {
  final public static Long LONG_ZERO = Long.valueOf(0L);
  final public static Long LONG_ONE = Long.valueOf(1L);
  final public static Long LONG_MINUS_ONE = Long.valueOf(-1L);
  final public static Integer INTEGER_ZERO = Integer.valueOf(0);
  final public static Integer INTEGER_ONE = Integer.valueOf(1);
  final public static Integer INTEGER_MINUS_ONE = Integer.valueOf(-1);
  final public static Short SHORT_ZERO = Short.valueOf((short)0);
  final public static Short SHORT_ONE = Short.valueOf((short)1);
  final public static Short SHORT_MINUS_ONE = Short.valueOf((short)-1);
  final public static Byte BYTE_ZERO = Byte.valueOf((byte)0);
  final public static Byte BYTE_ONE = Byte.valueOf((byte)1);
  final public static Byte BYTE_MINUS_ONE = Byte.valueOf((byte)-1);
  final public static Double DOUBLE_ZERO = Double.valueOf(0.0D);
  final public static Double DOUBLE_ONE = Double.valueOf(1.0D);
  final public static Double DOUBLE_MINUS_ONE = Double.valueOf(-1.0D);
  final public static Float FLOAT_ZERO = Float.valueOf(0.0F);
  final public static Float FLOAT_ONE = Float.valueOf(1.0F);
  final public static Float FLOAT_MINUS_ONE = Float.valueOf(-1.0F);
  public NumberUtils() {
    super();
  }
  public static BigDecimal createBigDecimal(final String str) {
    if(str == null) {
      return null;
    }
    if(StringUtils.isBlank(str)) {
      throw new NumberFormatException("A blank string is not a valid number");
    }
    if(str.trim().startsWith("--")) {
      throw new NumberFormatException(str + " is not a valid number.");
    }
    return new BigDecimal(str);
  }
  public static BigInteger createBigInteger(final String str) {
    if(str == null) {
      return null;
    }
    int pos = 0;
    int radix = 10;
    boolean negate = false;
    if(str.startsWith("-")) {
      negate = true;
      pos = 1;
    }
    boolean var_370 = str.startsWith("0x", pos);
    if(var_370 || str.startsWith("0x", pos)) {
      radix = 16;
      pos += 2;
    }
    else 
      if(str.startsWith("#", pos)) {
        radix = 16;
        pos++;
      }
      else 
        if(str.startsWith("0", pos) && str.length() > pos + 1) {
          radix = 8;
          pos++;
        }
    final BigInteger value = new BigInteger(str.substring(pos), radix);
    return negate ? value.negate() : value;
  }
  public static Double createDouble(final String str) {
    if(str == null) {
      return null;
    }
    return Double.valueOf(str);
  }
  public static Float createFloat(final String str) {
    if(str == null) {
      return null;
    }
    return Float.valueOf(str);
  }
  public static Integer createInteger(final String str) {
    if(str == null) {
      return null;
    }
    return Integer.decode(str);
  }
  public static Long createLong(final String str) {
    if(str == null) {
      return null;
    }
    return Long.decode(str);
  }
  public static Number createNumber(final String str) throws NumberFormatException {
    if(str == null) {
      return null;
    }
    if(StringUtils.isBlank(str)) {
      throw new NumberFormatException("A blank string is not a valid number");
    }
    final String[] hex_prefixes = { "0x", "0X", "-0x", "-0X", "#", "-#" } ;
    int pfxLen = 0;
    for (final String pfx : hex_prefixes) {
      if(str.startsWith(pfx)) {
        pfxLen += pfx.length();
        break ;
      }
    }
    if(pfxLen > 0) {
      char firstSigDigit = 0;
      for(int i = pfxLen; i < str.length(); i++) {
        firstSigDigit = str.charAt(i);
        if(firstSigDigit == '0') {
          pfxLen++;
        }
        else {
          break ;
        }
      }
      final int hexDigits = str.length() - pfxLen;
      if(hexDigits > 16 || (hexDigits == 16 && firstSigDigit > '7')) {
        return createBigInteger(str);
      }
      if(hexDigits > 8 || (hexDigits == 8 && firstSigDigit > '7')) {
        return createLong(str);
      }
      return createInteger(str);
    }
    final char lastChar = str.charAt(str.length() - 1);
    String mant;
    String dec;
    String exp;
    final int decPos = str.indexOf('.');
    final int expPos = str.indexOf('e') + str.indexOf('E') + 1;
    int numDecimals = 0;
    if(decPos > -1) {
      if(expPos > -1) {
        if(expPos < decPos || expPos > str.length()) {
          throw new NumberFormatException(str + " is not a valid number.");
        }
        dec = str.substring(decPos + 1, expPos);
      }
      else {
        dec = str.substring(decPos + 1);
      }
      mant = str.substring(0, decPos);
      numDecimals = dec.length();
    }
    else {
      if(expPos > -1) {
        if(expPos > str.length()) {
          throw new NumberFormatException(str + " is not a valid number.");
        }
        mant = str.substring(0, expPos);
      }
      else {
        mant = str;
      }
      dec = null;
    }
    if(!Character.isDigit(lastChar) && lastChar != '.') {
      if(expPos > -1 && expPos < str.length() - 1) {
        exp = str.substring(expPos + 1, str.length() - 1);
      }
      else {
        exp = null;
      }
      final String numeric = str.substring(0, str.length() - 1);
      final boolean allZeros = isAllZeros(mant) && isAllZeros(exp);
      switch (lastChar){
        case 'l':
        case 'L':
        if(dec == null && exp == null && (numeric.charAt(0) == '-' && isDigits(numeric.substring(1)) || isDigits(numeric))) {
          try {
            return createLong(numeric);
          }
          catch (final NumberFormatException nfe) {
          }
          return createBigInteger(numeric);
        }
        throw new NumberFormatException(str + " is not a valid number.");
        case 'f':
        case 'F':
        try {
          final Float f = NumberUtils.createFloat(numeric);
          if(!(f.isInfinite() || (f.floatValue() == 0.0F && !allZeros))) {
            return f;
          }
        }
        catch (final NumberFormatException nfe) {
        }
        case 'd':
        case 'D':
        try {
          final Double d = NumberUtils.createDouble(numeric);
          if(!(d.isInfinite() || (d.floatValue() == 0.0D && !allZeros))) {
            return d;
          }
        }
        catch (final NumberFormatException nfe) {
        }
        try {
          return createBigDecimal(numeric);
        }
        catch (final NumberFormatException e) {
        }
        default:
        throw new NumberFormatException(str + " is not a valid number.");
      }
    }
    if(expPos > -1 && expPos < str.length() - 1) {
      exp = str.substring(expPos + 1, str.length());
    }
    else {
      exp = null;
    }
    if(dec == null && exp == null) {
      try {
        return createInteger(str);
      }
      catch (final NumberFormatException nfe) {
      }
      try {
        return createLong(str);
      }
      catch (final NumberFormatException nfe) {
      }
      return createBigInteger(str);
    }
    final boolean allZeros = isAllZeros(mant) && isAllZeros(exp);
    try {
      if(numDecimals <= 7) {
        final Float f = createFloat(str);
        if(!(f.isInfinite() || (f.floatValue() == 0.0F && !allZeros))) {
          return f;
        }
      }
    }
    catch (final NumberFormatException nfe) {
    }
    try {
      if(numDecimals <= 16) {
        final Double d = createDouble(str);
        if(!(d.isInfinite() || (d.doubleValue() == 0.0D && !allZeros))) {
          return d;
        }
      }
    }
    catch (final NumberFormatException nfe) {
    }
    return createBigDecimal(str);
  }
  private static boolean isAllZeros(final String str) {
    if(str == null) {
      return true;
    }
    for(int i = str.length() - 1; i >= 0; i--) {
      if(str.charAt(i) != '0') {
        return false;
      }
    }
    return str.length() > 0;
  }
  public static boolean isDigits(final String str) {
    if(StringUtils.isEmpty(str)) {
      return false;
    }
    for(int i = 0; i < str.length(); i++) {
      if(!Character.isDigit(str.charAt(i))) {
        return false;
      }
    }
    return true;
  }
  public static boolean isNumber(final String str) {
    if(StringUtils.isEmpty(str)) {
      return false;
    }
    final char[] chars = str.toCharArray();
    int sz = chars.length;
    boolean hasExp = false;
    boolean hasDecPoint = false;
    boolean allowSigns = false;
    boolean foundDigit = false;
    final int start = (chars[0] == '-') ? 1 : 0;
    if(sz > start + 1 && chars[start] == '0' && chars[start + 1] == 'x') {
      int i = start + 2;
      if(i == sz) {
        return false;
      }
      for(; i < chars.length; i++) {
        if((chars[i] < '0' || chars[i] > '9') && (chars[i] < 'a' || chars[i] > 'f') && (chars[i] < 'A' || chars[i] > 'F')) {
          return false;
        }
      }
      return true;
    }
    sz--;
    int i = start;
    while(i < sz || (i < sz + 1 && allowSigns && !foundDigit)){
      if(chars[i] >= '0' && chars[i] <= '9') {
        foundDigit = true;
        allowSigns = false;
      }
      else 
        if(chars[i] == '.') {
          if(hasDecPoint || hasExp) {
            return false;
          }
          hasDecPoint = true;
        }
        else 
          if(chars[i] == 'e' || chars[i] == 'E') {
            if(hasExp) {
              return false;
            }
            if(!foundDigit) {
              return false;
            }
            hasExp = true;
            allowSigns = true;
          }
          else 
            if(chars[i] == '+' || chars[i] == '-') {
              if(!allowSigns) {
                return false;
              }
              allowSigns = false;
              foundDigit = false;
            }
            else {
              return false;
            }
      i++;
    }
    if(i < chars.length) {
      if(chars[i] >= '0' && chars[i] <= '9') {
        return true;
      }
      if(chars[i] == 'e' || chars[i] == 'E') {
        return false;
      }
      if(chars[i] == '.') {
        if(hasDecPoint || hasExp) {
          return false;
        }
        return foundDigit;
      }
      if(!allowSigns && (chars[i] == 'd' || chars[i] == 'D' || chars[i] == 'f' || chars[i] == 'F')) {
        return foundDigit;
      }
      if(chars[i] == 'l' || chars[i] == 'L') {
        return foundDigit && !hasExp && !hasDecPoint;
      }
      return false;
    }
    return !allowSigns && foundDigit;
  }
  public static byte max(byte a, final byte b, final byte c) {
    if(b > a) {
      a = b;
    }
    if(c > a) {
      a = c;
    }
    return a;
  }
  public static byte max(final byte[] array) {
    validateArray(array);
    byte max = array[0];
    for(int i = 1; i < array.length; i++) {
      if(array[i] > max) {
        max = array[i];
      }
    }
    return max;
  }
  public static byte min(byte a, final byte b, final byte c) {
    if(b < a) {
      a = b;
    }
    if(c < a) {
      a = c;
    }
    return a;
  }
  public static byte min(final byte[] array) {
    validateArray(array);
    byte min = array[0];
    for(int i = 1; i < array.length; i++) {
      if(array[i] < min) {
        min = array[i];
      }
    }
    return min;
  }
  public static byte toByte(final String str) {
    return toByte(str, (byte)0);
  }
  public static byte toByte(final String str, final byte defaultValue) {
    if(str == null) {
      return defaultValue;
    }
    try {
      return Byte.parseByte(str);
    }
    catch (final NumberFormatException nfe) {
      return defaultValue;
    }
  }
  public static double max(final double a, final double b, final double c) {
    return Math.max(Math.max(a, b), c);
  }
  public static double max(final double[] array) {
    validateArray(array);
    double max = array[0];
    for(int j = 1; j < array.length; j++) {
      if(Double.isNaN(array[j])) {
        return Double.NaN;
      }
      if(array[j] > max) {
        max = array[j];
      }
    }
    return max;
  }
  public static double min(final double a, final double b, final double c) {
    return Math.min(Math.min(a, b), c);
  }
  public static double min(final double[] array) {
    validateArray(array);
    double min = array[0];
    for(int i = 1; i < array.length; i++) {
      if(Double.isNaN(array[i])) {
        return Double.NaN;
      }
      if(array[i] < min) {
        min = array[i];
      }
    }
    return min;
  }
  public static double toDouble(final String str) {
    return toDouble(str, 0.0D);
  }
  public static double toDouble(final String str, final double defaultValue) {
    if(str == null) {
      return defaultValue;
    }
    try {
      return Double.parseDouble(str);
    }
    catch (final NumberFormatException nfe) {
      return defaultValue;
    }
  }
  public static float max(final float a, final float b, final float c) {
    return Math.max(Math.max(a, b), c);
  }
  public static float max(final float[] array) {
    validateArray(array);
    float max = array[0];
    for(int j = 1; j < array.length; j++) {
      if(Float.isNaN(array[j])) {
        return Float.NaN;
      }
      if(array[j] > max) {
        max = array[j];
      }
    }
    return max;
  }
  public static float min(final float a, final float b, final float c) {
    return Math.min(Math.min(a, b), c);
  }
  public static float min(final float[] array) {
    validateArray(array);
    float min = array[0];
    for(int i = 1; i < array.length; i++) {
      if(Float.isNaN(array[i])) {
        return Float.NaN;
      }
      if(array[i] < min) {
        min = array[i];
      }
    }
    return min;
  }
  public static float toFloat(final String str) {
    return toFloat(str, 0.0F);
  }
  public static float toFloat(final String str, final float defaultValue) {
    if(str == null) {
      return defaultValue;
    }
    try {
      return Float.parseFloat(str);
    }
    catch (final NumberFormatException nfe) {
      return defaultValue;
    }
  }
  public static int max(int a, final int b, final int c) {
    if(b > a) {
      a = b;
    }
    if(c > a) {
      a = c;
    }
    return a;
  }
  public static int max(final int[] array) {
    validateArray(array);
    int max = array[0];
    for(int j = 1; j < array.length; j++) {
      if(array[j] > max) {
        max = array[j];
      }
    }
    return max;
  }
  public static int min(int a, final int b, final int c) {
    if(b < a) {
      a = b;
    }
    if(c < a) {
      a = c;
    }
    return a;
  }
  public static int min(final int[] array) {
    validateArray(array);
    int min = array[0];
    for(int j = 1; j < array.length; j++) {
      if(array[j] < min) {
        min = array[j];
      }
    }
    return min;
  }
  public static int toInt(final String str) {
    return toInt(str, 0);
  }
  public static int toInt(final String str, final int defaultValue) {
    if(str == null) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(str);
    }
    catch (final NumberFormatException nfe) {
      return defaultValue;
    }
  }
  public static long max(long a, final long b, final long c) {
    if(b > a) {
      a = b;
    }
    if(c > a) {
      a = c;
    }
    return a;
  }
  public static long max(final long[] array) {
    validateArray(array);
    long max = array[0];
    for(int j = 1; j < array.length; j++) {
      if(array[j] > max) {
        max = array[j];
      }
    }
    return max;
  }
  public static long min(long a, final long b, final long c) {
    if(b < a) {
      a = b;
    }
    if(c < a) {
      a = c;
    }
    return a;
  }
  public static long min(final long[] array) {
    validateArray(array);
    long min = array[0];
    for(int i = 1; i < array.length; i++) {
      if(array[i] < min) {
        min = array[i];
      }
    }
    return min;
  }
  public static long toLong(final String str) {
    return toLong(str, 0L);
  }
  public static long toLong(final String str, final long defaultValue) {
    if(str == null) {
      return defaultValue;
    }
    try {
      return Long.parseLong(str);
    }
    catch (final NumberFormatException nfe) {
      return defaultValue;
    }
  }
  public static short max(short a, final short b, final short c) {
    if(b > a) {
      a = b;
    }
    if(c > a) {
      a = c;
    }
    return a;
  }
  public static short max(final short[] array) {
    validateArray(array);
    short max = array[0];
    for(int i = 1; i < array.length; i++) {
      if(array[i] > max) {
        max = array[i];
      }
    }
    return max;
  }
  public static short min(short a, final short b, final short c) {
    if(b < a) {
      a = b;
    }
    if(c < a) {
      a = c;
    }
    return a;
  }
  public static short min(final short[] array) {
    validateArray(array);
    short min = array[0];
    for(int i = 1; i < array.length; i++) {
      if(array[i] < min) {
        min = array[i];
      }
    }
    return min;
  }
  public static short toShort(final String str) {
    return toShort(str, (short)0);
  }
  public static short toShort(final String str, final short defaultValue) {
    if(str == null) {
      return defaultValue;
    }
    try {
      return Short.parseShort(str);
    }
    catch (final NumberFormatException nfe) {
      return defaultValue;
    }
  }
  private static void validateArray(final Object array) {
    if(array == null) {
      throw new IllegalArgumentException("The Array must not be null");
    }
    else 
      if(Array.getLength(array) == 0) {
        throw new IllegalArgumentException("Array cannot be empty.");
      }
  }
}