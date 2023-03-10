package org.joda.time.format;
import java.io.IOException;
import java.io.Writer;

public class FormatUtils  {
  final private static double LOG_10 = Math.log(10);
  private FormatUtils() {
    super();
  }
  static String createErrorMessage(final String text, final int errorPos) {
    int sampleLen = errorPos + 32;
    String sampleText;
    if(text.length() <= sampleLen + 3) {
      sampleText = text;
    }
    else {
      sampleText = text.substring(0, sampleLen).concat("...");
    }
    if(errorPos <= 0) {
      return "Invalid format: \"" + sampleText + '\"';
    }
    if(errorPos >= text.length()) {
      return "Invalid format: \"" + sampleText + "\" is too short";
    }
    return "Invalid format: \"" + sampleText + "\" is malformed at \"" + sampleText.substring(errorPos) + '\"';
  }
  public static int calculateDigitCount(long value) {
    if(value < 0) {
      if(value != Long.MIN_VALUE) {
        return calculateDigitCount(-value) + 1;
      }
      else {
        return 20;
      }
    }
    return (value < 10 ? 1 : (value < 100 ? 2 : (value < 1000 ? 3 : (value < 10000 ? 4 : ((int)(Math.log(value) / LOG_10) + 1)))));
  }
  static int parseTwoDigits(String text, int position) {
    int value = text.charAt(position) - '0';
    return ((value << 3) + (value << 1)) + text.charAt(position + 1) - '0';
  }
  public static void appendPaddedInteger(StringBuffer buf, int value, int size) {
    if(value < 0) {
      buf.append('-');
      if(value != Integer.MIN_VALUE) {
        value = -value;
      }
      else {
        for(; size > 10; size--) {
          buf.append('0');
        }
        buf.append("" + -(long)Integer.MIN_VALUE);
        return ;
      }
    }
    if(value < 10) {
      for(; size > 1; size--) {
        buf.append('0');
      }
      buf.append((char)(value + '0'));
    }
    else 
      if(value < 100) {
        for(; size > 2; size--) {
          buf.append('0');
        }
        int d = ((value + 1) * 13421772) >> 27;
        buf.append((char)(d + '0'));
        buf.append((char)(value - (d << 3) - (d << 1) + '0'));
      }
      else {
        int digits;
        if(value < 1000) {
          digits = 3;
        }
        else 
          if(value < 10000) {
            digits = 4;
          }
          else {
            digits = (int)(Math.log(value) / LOG_10) + 1;
          }
        for(; size > digits; size--) {
          buf.append('0');
        }
        buf.append(Integer.toString(value));
      }
  }
  public static void appendPaddedInteger(StringBuffer buf, long value, int size) {
    int intValue = (int)value;
    if(intValue == value) {
      appendPaddedInteger(buf, intValue, size);
    }
    else 
      if(size <= 19) {
        String var_501 = Long.toString(value);
        buf.append(var_501);
      }
      else {
        if(value < 0) {
          buf.append('-');
          if(value != Long.MIN_VALUE) {
            value = -value;
          }
          else {
            for(; size > 19; size--) {
              buf.append('0');
            }
            buf.append("9223372036854775808");
            return ;
          }
        }
        int digits = (int)(Math.log(value) / LOG_10) + 1;
        for(; size > digits; size--) {
          buf.append('0');
        }
        buf.append(Long.toString(value));
      }
  }
  public static void appendUnpaddedInteger(StringBuffer buf, int value) {
    if(value < 0) {
      buf.append('-');
      if(value != Integer.MIN_VALUE) {
        value = -value;
      }
      else {
        buf.append("" + -(long)Integer.MIN_VALUE);
        return ;
      }
    }
    if(value < 10) {
      buf.append((char)(value + '0'));
    }
    else 
      if(value < 100) {
        int d = ((value + 1) * 13421772) >> 27;
        buf.append((char)(d + '0'));
        buf.append((char)(value - (d << 3) - (d << 1) + '0'));
      }
      else {
        buf.append(Integer.toString(value));
      }
  }
  public static void appendUnpaddedInteger(StringBuffer buf, long value) {
    int intValue = (int)value;
    if(intValue == value) {
      appendUnpaddedInteger(buf, intValue);
    }
    else {
      buf.append(Long.toString(value));
    }
  }
  public static void writePaddedInteger(Writer out, int value, int size) throws IOException {
    if(value < 0) {
      out.write('-');
      if(value != Integer.MIN_VALUE) {
        value = -value;
      }
      else {
        for(; size > 10; size--) {
          out.write('0');
        }
        out.write("" + -(long)Integer.MIN_VALUE);
        return ;
      }
    }
    if(value < 10) {
      for(; size > 1; size--) {
        out.write('0');
      }
      out.write(value + '0');
    }
    else 
      if(value < 100) {
        for(; size > 2; size--) {
          out.write('0');
        }
        int d = ((value + 1) * 13421772) >> 27;
        out.write(d + '0');
        out.write(value - (d << 3) - (d << 1) + '0');
      }
      else {
        int digits;
        if(value < 1000) {
          digits = 3;
        }
        else 
          if(value < 10000) {
            digits = 4;
          }
          else {
            digits = (int)(Math.log(value) / LOG_10) + 1;
          }
        for(; size > digits; size--) {
          out.write('0');
        }
        out.write(Integer.toString(value));
      }
  }
  public static void writePaddedInteger(Writer out, long value, int size) throws IOException {
    int intValue = (int)value;
    if(intValue == value) {
      writePaddedInteger(out, intValue, size);
    }
    else 
      if(size <= 19) {
        out.write(Long.toString(value));
      }
      else {
        if(value < 0) {
          out.write('-');
          if(value != Long.MIN_VALUE) {
            value = -value;
          }
          else {
            for(; size > 19; size--) {
              out.write('0');
            }
            out.write("9223372036854775808");
            return ;
          }
        }
        int digits = (int)(Math.log(value) / LOG_10) + 1;
        for(; size > digits; size--) {
          out.write('0');
        }
        out.write(Long.toString(value));
      }
  }
  public static void writeUnpaddedInteger(Writer out, int value) throws IOException {
    if(value < 0) {
      out.write('-');
      if(value != Integer.MIN_VALUE) {
        value = -value;
      }
      else {
        out.write("" + -(long)Integer.MIN_VALUE);
        return ;
      }
    }
    if(value < 10) {
      out.write(value + '0');
    }
    else 
      if(value < 100) {
        int d = ((value + 1) * 13421772) >> 27;
        out.write(d + '0');
        out.write(value - (d << 3) - (d << 1) + '0');
      }
      else {
        out.write(Integer.toString(value));
      }
  }
  public static void writeUnpaddedInteger(Writer out, long value) throws IOException {
    int intValue = (int)value;
    if(intValue == value) {
      writeUnpaddedInteger(out, intValue);
    }
    else {
      out.write(Long.toString(value));
    }
  }
}