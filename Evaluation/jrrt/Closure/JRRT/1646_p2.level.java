package com.google.javascript.jscomp;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.CheckLevel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;

abstract public class BasicErrorManager implements ErrorManager  {
  final private SortedSet<ErrorWithLevel> messages = Sets.newTreeSet(new LeveledJSErrorComparator());
  private int errorCount = 0;
  private int warningCount = 0;
  private double typedPercent = 0.0D;
  @Override() public JSError[] getErrors() {
    return toArray(CheckLevel.ERROR);
  }
  @Override() public JSError[] getWarnings() {
    return toArray(CheckLevel.WARNING);
  }
  private JSError[] toArray(CheckLevel level) {
    List<JSError> errors = new ArrayList<JSError>(messages.size());
    for (ErrorWithLevel p : messages) {
      if(p.level == level) {
        errors.add(p.error);
      }
    }
    return errors.toArray(new JSError[errors.size()]);
  }
  @Override() public double getTypedPercent() {
    return typedPercent;
  }
  @Override() public int getErrorCount() {
    return errorCount;
  }
  @Override() public int getWarningCount() {
    return warningCount;
  }
  @Override() public void generateReport() {
    for (ErrorWithLevel message : messages) {
      println(message.level, message.error);
    }
    printSummary();
  }
  abstract protected void printSummary();
  abstract public void println(CheckLevel level, JSError error);
  @Override() public void report(CheckLevel level, JSError error) {
    if(messages.add(new ErrorWithLevel(error, level))) {
      if(level == CheckLevel.ERROR) {
        errorCount++;
      }
      else 
        if(level == CheckLevel.WARNING) {
          warningCount++;
        }
    }
  }
  @Override() public void setTypedPercent(double typedPercent) {
    this.typedPercent = typedPercent;
  }
  
  static class ErrorWithLevel  {
    final JSError error;
    final CheckLevel level;
    ErrorWithLevel(JSError error, CheckLevel level) {
      super();
      this.error = error;
      this.level = level;
    }
  }
  
  final static class LeveledJSErrorComparator implements Comparator<ErrorWithLevel>  {
    final private static int P1_LT_P2 = -1;
    final private static int P1_GT_P2 = 1;
    @Override() public int compare(ErrorWithLevel p1, ErrorWithLevel p2) {
      if(p2 == null) {
        if(p1 == null) {
          return 0;
        }
        else {
          return P1_GT_P2;
        }
      }
      CheckLevel var_1646 = p2.level;
      if(p1.level != var_1646) {
        return p2.level.compareTo(p1.level);
      }
      String source1 = p1.error.sourceName;
      String source2 = p2.error.sourceName;
      if(source1 != null && source2 != null) {
        int sourceCompare = source1.compareTo(source2);
        if(sourceCompare != 0) {
          return sourceCompare;
        }
      }
      else 
        if(source1 == null && source2 != null) {
          return P1_LT_P2;
        }
        else 
          if(source1 != null && source2 == null) {
            return P1_GT_P2;
          }
      int lineno1 = p1.error.lineNumber;
      int lineno2 = p2.error.lineNumber;
      if(lineno1 != lineno2) {
        return lineno1 - lineno2;
      }
      else 
        if(lineno1 < 0 && 0 <= lineno2) {
          return P1_LT_P2;
        }
        else 
          if(0 <= lineno1 && lineno2 < 0) {
            return P1_GT_P2;
          }
      int charno1 = p1.error.getCharno();
      int charno2 = p2.error.getCharno();
      if(charno1 != charno2) {
        return charno1 - charno2;
      }
      else 
        if(charno1 < 0 && 0 <= charno2) {
          return P1_LT_P2;
        }
        else 
          if(0 <= charno1 && charno2 < 0) {
            return P1_GT_P2;
          }
      return p1.error.description.compareTo(p2.error.description);
    }
  }
}