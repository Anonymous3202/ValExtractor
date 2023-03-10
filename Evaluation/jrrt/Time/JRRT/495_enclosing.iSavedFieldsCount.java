package org.joda.time.format;
import java.util.Arrays;
import java.util.Locale;
import org.joda.time.Chronology;
import org.joda.time.DateTimeField;
import org.joda.time.DateTimeFieldType;
import org.joda.time.DateTimeUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.DurationField;
import org.joda.time.DurationFieldType;
import org.joda.time.IllegalFieldValueException;
import org.joda.time.IllegalInstantException;

public class DateTimeParserBucket  {
  final private Chronology iChrono;
  final private long iMillis;
  private DateTimeZone iZone;
  private Integer iOffset;
  private Locale iLocale;
  private Integer iPivotYear;
  private int iDefaultYear;
  private SavedField[] iSavedFields = new SavedField[8];
  private int iSavedFieldsCount;
  private boolean iSavedFieldsShared;
  private Object iSavedState;
  @Deprecated() public DateTimeParserBucket(long instantLocal, Chronology chrono, Locale locale) {
    this(instantLocal, chrono, locale, null, 2000);
  }
  @Deprecated() public DateTimeParserBucket(long instantLocal, Chronology chrono, Locale locale, Integer pivotYear) {
    this(instantLocal, chrono, locale, pivotYear, 2000);
  }
  public DateTimeParserBucket(long instantLocal, Chronology chrono, Locale locale, Integer pivotYear, int defaultYear) {
    super();
    chrono = DateTimeUtils.getChronology(chrono);
    iMillis = instantLocal;
    iZone = chrono.getZone();
    iChrono = chrono.withUTC();
    iLocale = (locale == null ? Locale.getDefault() : locale);
    iPivotYear = pivotYear;
    iDefaultYear = defaultYear;
  }
  public Chronology getChronology() {
    return iChrono;
  }
  public DateTimeZone getZone() {
    return iZone;
  }
  public Integer getOffsetInteger() {
    return iOffset;
  }
  public Integer getPivotYear() {
    return iPivotYear;
  }
  public Locale getLocale() {
    return iLocale;
  }
  public Object saveState() {
    if(iSavedState == null) {
      iSavedState = new SavedState();
    }
    return iSavedState;
  }
  public boolean restoreState(Object savedState) {
    if(savedState instanceof SavedState) {
      if(((SavedState)savedState).restoreState(this)) {
        iSavedState = savedState;
        return true;
      }
    }
    return false;
  }
  static int compareReverse(DurationField a, DurationField b) {
    if(a == null || !a.isSupported()) {
      if(b == null || !b.isSupported()) {
        return 0;
      }
      return -1;
    }
    if(b == null || !b.isSupported()) {
      return 1;
    }
    return -a.compareTo(b);
  }
  @Deprecated() public int getOffset() {
    return (iOffset != null ? iOffset : 0);
  }
  public long computeMillis() {
    return computeMillis(false, null);
  }
  public long computeMillis(boolean resetFields) {
    return computeMillis(resetFields, null);
  }
  public long computeMillis(boolean resetFields, String text) {
    SavedField[] savedFields = iSavedFields;
    int count = iSavedFieldsCount;
    if(iSavedFieldsShared) {
      iSavedFields = savedFields = (SavedField[])iSavedFields.clone();
      iSavedFieldsShared = false;
    }
    sort(savedFields, count);
    if(count > 0) {
      DurationField months = DurationFieldType.months().getField(iChrono);
      DurationField days = DurationFieldType.days().getField(iChrono);
      DurationField first = savedFields[0].iField.getDurationField();
      if(compareReverse(first, months) >= 0 && compareReverse(first, days) <= 0) {
        saveField(DateTimeFieldType.year(), iDefaultYear);
        return computeMillis(resetFields, text);
      }
    }
    long millis = iMillis;
    try {
      for(int i = 0; i < count; i++) {
        millis = savedFields[i].set(millis, resetFields);
      }
      if(resetFields) {
        for(int i = 0; i < count; i++) {
          millis = savedFields[i].set(millis, i == (count - 1));
        }
      }
    }
    catch (IllegalFieldValueException e) {
      if(text != null) {
        e.prependMessage("Cannot parse \"" + text + '\"');
      }
      throw e;
    }
    if(iOffset != null) {
      millis -= iOffset;
    }
    else 
      if(iZone != null) {
        int offset = iZone.getOffsetFromLocal(millis);
        millis -= offset;
        if(offset != iZone.getOffset(millis)) {
          String message = "Illegal instant due to time zone offset transition (" + iZone + ')';
          if(text != null) {
            message = "Cannot parse \"" + text + "\": " + message;
          }
          throw new IllegalInstantException(message);
        }
      }
    return millis;
  }
  public void saveField(DateTimeField field, int value) {
    saveField(new SavedField(field, value));
  }
  public void saveField(DateTimeFieldType fieldType, int value) {
    saveField(new SavedField(fieldType.getField(iChrono), value));
  }
  public void saveField(DateTimeFieldType fieldType, String text, Locale locale) {
    saveField(new SavedField(fieldType.getField(iChrono), text, locale));
  }
  private void saveField(SavedField field) {
    SavedField[] savedFields = iSavedFields;
    int savedFieldsCount = iSavedFieldsCount;
    if(savedFieldsCount == savedFields.length || iSavedFieldsShared) {
      SavedField[] newArray = new SavedField[savedFieldsCount == savedFields.length ? savedFieldsCount * 2 : savedFields.length];
      System.arraycopy(savedFields, 0, newArray, 0, savedFieldsCount);
      iSavedFields = savedFields = newArray;
      iSavedFieldsShared = false;
    }
    iSavedState = null;
    savedFields[savedFieldsCount] = field;
    iSavedFieldsCount = savedFieldsCount + 1;
  }
  @Deprecated() public void setOffset(int offset) {
    iSavedState = null;
    iOffset = offset;
  }
  public void setOffset(Integer offset) {
    iSavedState = null;
    iOffset = offset;
  }
  public void setPivotYear(Integer pivotYear) {
    iPivotYear = pivotYear;
  }
  public void setZone(DateTimeZone zone) {
    iSavedState = null;
    iZone = zone;
  }
  private static void sort(SavedField[] array, int high) {
    if(high > 10) {
      Arrays.sort(array, 0, high);
    }
    else {
      for(int i = 0; i < high; i++) {
        for(int j = i; j > 0 && (array[j - 1]).compareTo(array[j]) > 0; j--) {
          SavedField t = array[j];
          array[j] = array[j - 1];
          array[j - 1] = t;
        }
      }
    }
  }
  
  static class SavedField implements Comparable<SavedField>  {
    final DateTimeField iField;
    final int iValue;
    final String iText;
    final Locale iLocale;
    SavedField(DateTimeField field, String text, Locale locale) {
      super();
      iField = field;
      iValue = 0;
      iText = text;
      iLocale = locale;
    }
    SavedField(DateTimeField field, int value) {
      super();
      iField = field;
      iValue = value;
      iText = null;
      iLocale = null;
    }
    public int compareTo(SavedField obj) {
      DateTimeField other = obj.iField;
      int result = compareReverse(iField.getRangeDurationField(), other.getRangeDurationField());
      if(result != 0) {
        return result;
      }
      return compareReverse(iField.getDurationField(), other.getDurationField());
    }
    long set(long millis, boolean reset) {
      if(iText == null) {
        millis = iField.set(millis, iValue);
      }
      else {
        millis = iField.set(millis, iText, iLocale);
      }
      if(reset) {
        millis = iField.roundFloor(millis);
      }
      return millis;
    }
  }
  
  class SavedState  {
    final DateTimeZone iZone;
    final Integer iOffset;
    final SavedField[] iSavedFields;
    final int iSavedFieldsCount;
    SavedState() {
      super();
      this.iZone = DateTimeParserBucket.this.iZone;
      this.iOffset = DateTimeParserBucket.this.iOffset;
      this.iSavedFields = DateTimeParserBucket.this.iSavedFields;
      this.iSavedFieldsCount = DateTimeParserBucket.this.iSavedFieldsCount;
    }
    boolean restoreState(DateTimeParserBucket enclosing) {
      if(enclosing != DateTimeParserBucket.this) {
        return false;
      }
      enclosing.iZone = this.iZone;
      enclosing.iOffset = this.iOffset;
      enclosing.iSavedFields = this.iSavedFields;
      int var_495 = enclosing.iSavedFieldsCount;
      if(this.iSavedFieldsCount < var_495) {
        enclosing.iSavedFieldsShared = true;
      }
      enclosing.iSavedFieldsCount = this.iSavedFieldsCount;
      return true;
    }
  }
}