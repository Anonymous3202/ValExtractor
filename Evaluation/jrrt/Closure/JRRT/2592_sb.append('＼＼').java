package com.google.javascript.rhino;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

public class ScriptRuntime  {
  final public static double NaN = Double.longBitsToDouble(0x7ff8000000000000L);
  final public static double negativeZero = Double.longBitsToDouble(0x8000000000000000L);
  protected ScriptRuntime() {
    super();
  }
  public static String escapeString(String s) {
    return escapeString(s, '\"');
  }
  public static String escapeString(String s, char escapeQuote) {
    if(!(escapeQuote == '\"' || escapeQuote == '\'')) {
      throw new IllegalStateException("unexpected quote char:" + escapeQuote);
    }
    StringBuffer sb = null;
    for(int i = 0, L = s.length(); i != L; ++i) {
      int c = s.charAt(i);
      if(' ' <= c && c <= '~' && c != escapeQuote && c != '\\') {
        if(sb != null) {
          sb.append((char)c);
        }
        continue ;
      }
      if(sb == null) {
        sb = new StringBuffer(L + 3);
        sb.append(s);
        sb.setLength(i);
      }
      int escape = -1;
      switch (c){
        case '\b':
        escape = 'b';
        break ;
        case '\f':
        escape = 'f';
        break ;
        case '\n':
        escape = 'n';
        break ;
        case '\r':
        escape = 'r';
        break ;
        case '\t':
        escape = 't';
        break ;
        case 0xb:
        escape = 'v';
        break ;
        case ' ':
        escape = ' ';
        break ;
        case '\\':
        escape = '\\';
        break ;
      }
      if(escape >= 0) {
        StringBuffer var_2592 = sb.append('\\');
        sb.append((char)escape);
      }
      else 
        if(c == escapeQuote) {
          sb.append('\\');
          sb.append(escapeQuote);
        }
        else {
          int hexSize;
          if(c < 256) {
            sb.append("\\x");
            hexSize = 2;
          }
          else {
            sb.append("\\u");
            hexSize = 4;
          }
          for(int shift = (hexSize - 1) * 4; shift >= 0; shift -= 4) {
            int digit = 0xf & (c >> shift);
            int hc = (digit < 10) ? '0' + digit : 'a' - 10 + digit;
            sb.append((char)hc);
          }
        }
    }
    return (sb == null) ? s : sb.toString();
  }
  public static String getMessage(String messageId, Object[] arguments) {
    final String defaultResource = "rhino_ast.java.com.google.javascript.rhino.Messages";
    Locale locale = Locale.getDefault();
    ResourceBundle rb = ResourceBundle.getBundle(defaultResource, locale);
    String formatString;
    try {
      formatString = rb.getString(messageId);
    }
    catch (java.util.MissingResourceException mre) {
      throw new RuntimeException("no message resource found for message property " + messageId);
    }
    MessageFormat formatter = new MessageFormat(formatString);
    return formatter.format(arguments);
  }
  public static String getMessage0(String messageId) {
    return getMessage(messageId, null);
  }
  public static String getMessage1(String messageId, Object arg1) {
    Object[] arguments = { arg1 } ;
    return getMessage(messageId, arguments);
  }
  public static boolean isJSLineTerminator(int c) {
    if((c & 0xDFD0) != 0) {
      return false;
    }
    return c == '\n' || c == '\r' || c == 0x2028 || c == 0x2029;
  }
  static boolean isSpecialProperty(String s) {
    return s.equals("__proto__") || s.equals("__parent__");
  }
  static boolean isValidIdentifierName(String s) {
    int L = s.length();
    if(L == 0) 
      return false;
    if(!Character.isJavaIdentifierStart(s.charAt(0))) 
      return false;
    for(int i = 1; i != L; ++i) {
      if(!Character.isJavaIdentifierPart(s.charAt(i))) 
        return false;
    }
    return !TokenStream.isKeyword(s);
  }
  @SuppressWarnings(value = {"fallthrough", }) static double stringToNumber(String s, int start, int radix) {
    char digitMax = '9';
    char lowerCaseBound = 'a';
    char upperCaseBound = 'A';
    int len = s.length();
    if(radix < 10) {
      digitMax = (char)('0' + radix - 1);
    }
    if(radix > 10) {
      lowerCaseBound = (char)('a' + radix - 10);
      upperCaseBound = (char)('A' + radix - 10);
    }
    int end;
    double sum = 0.0D;
    for(end = start; end < len; end++) {
      char c = s.charAt(end);
      int newDigit;
      if('0' <= c && c <= digitMax) 
        newDigit = c - '0';
      else 
        if('a' <= c && c < lowerCaseBound) 
          newDigit = c - 'a' + 10;
        else 
          if('A' <= c && c < upperCaseBound) 
            newDigit = c - 'A' + 10;
          else 
            break ;
      sum = sum * radix + newDigit;
    }
    if(start == end) {
      return NaN;
    }
    if(sum >= 9007199254740992.0D) {
      if(radix == 10) {
        try {
          return Double.valueOf(s.substring(start, end)).doubleValue();
        }
        catch (NumberFormatException nfe) {
          return NaN;
        }
      }
      else 
        if(radix == 2 || radix == 4 || radix == 8 || radix == 16 || radix == 32) {
          int bitShiftInChar = 1;
          int digit = 0;
          final int SKIP_LEADING_ZEROS = 0;
          final int FIRST_EXACT_53_BITS = 1;
          final int AFTER_BIT_53 = 2;
          final int ZEROS_AFTER_54 = 3;
          final int MIXED_AFTER_54 = 4;
          int state = SKIP_LEADING_ZEROS;
          int exactBitsLimit = 53;
          double factor = 0.0D;
          boolean bit53 = false;
          boolean bit54 = false;
          for(; true; ) {
            if(bitShiftInChar == 1) {
              if(start == end) 
                break ;
              digit = s.charAt(start++);
              if('0' <= digit && digit <= '9') 
                digit -= '0';
              else 
                if('a' <= digit && digit <= 'z') 
                  digit -= 'a' - 10;
                else 
                  digit -= 'A' - 10;
              bitShiftInChar = radix;
            }
            bitShiftInChar >>= 1;
            boolean bit = (digit & bitShiftInChar) != 0;
            switch (state){
              case SKIP_LEADING_ZEROS:
              if(bit) {
                --exactBitsLimit;
                sum = 1.0D;
                state = FIRST_EXACT_53_BITS;
              }
              break ;
              case FIRST_EXACT_53_BITS:
              sum *= 2.0D;
              if(bit) 
                sum += 1.0D;
              --exactBitsLimit;
              if(exactBitsLimit == 0) {
                bit53 = bit;
                state = AFTER_BIT_53;
              }
              break ;
              case AFTER_BIT_53:
              bit54 = bit;
              factor = 2.0D;
              state = ZEROS_AFTER_54;
              break ;
              case ZEROS_AFTER_54:
              if(bit) {
                state = MIXED_AFTER_54;
              }
              case MIXED_AFTER_54:
              factor *= 2;
              break ;
            }
          }
          switch (state){
            case SKIP_LEADING_ZEROS:
            sum = 0.0D;
            break ;
            case FIRST_EXACT_53_BITS:
            case AFTER_BIT_53:
            break ;
            case ZEROS_AFTER_54:
            if(bit54 & bit53) 
              sum += 1.0D;
            sum *= factor;
            break ;
            case MIXED_AFTER_54:
            if(bit54) 
              sum += 1.0D;
            sum *= factor;
            break ;
          }
        }
    }
    return sum;
  }
  public static long testUint32String(String str) {
    final int MAX_VALUE_LENGTH = 10;
    int len = str.length();
    if(1 <= len && len <= MAX_VALUE_LENGTH) {
      int c = str.charAt(0);
      c -= '0';
      if(c == 0) {
        return (len == 1) ? 0L : -1L;
      }
      if(1 <= c && c <= 9) {
        long v = c;
        for(int i = 1; i != len; ++i) {
          c = str.charAt(i) - '0';
          if(!(0 <= c && c <= 9)) {
            return -1;
          }
          v = 10 * v + c;
        }
        if((v >>> 32) == 0) {
          return v;
        }
      }
    }
    return -1;
  }
}