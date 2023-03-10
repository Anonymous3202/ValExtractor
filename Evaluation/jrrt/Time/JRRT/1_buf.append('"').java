package org.joda.time;

public class IllegalFieldValueException extends IllegalArgumentException  {
  final private static long serialVersionUID = 6305711765985447737L;
  final private DateTimeFieldType iDateTimeFieldType;
  final private DurationFieldType iDurationFieldType;
  final private String iFieldName;
  final private Number iNumberValue;
  final private String iStringValue;
  final private Number iLowerBound;
  final private Number iUpperBound;
  private String iMessage;
  public IllegalFieldValueException(DateTimeFieldType fieldType, Number value, Number lowerBound, Number upperBound) {
    super(createMessage(fieldType.getName(), value, lowerBound, upperBound, null));
    iDateTimeFieldType = fieldType;
    iDurationFieldType = null;
    iFieldName = fieldType.getName();
    iNumberValue = value;
    iStringValue = null;
    iLowerBound = lowerBound;
    iUpperBound = upperBound;
    iMessage = super.getMessage();
  }
  public IllegalFieldValueException(DateTimeFieldType fieldType, Number value, String explain) {
    super(createMessage(fieldType.getName(), value, null, null, explain));
    iDateTimeFieldType = fieldType;
    iDurationFieldType = null;
    iFieldName = fieldType.getName();
    iNumberValue = value;
    iStringValue = null;
    iLowerBound = null;
    iUpperBound = null;
    iMessage = super.getMessage();
  }
  public IllegalFieldValueException(DateTimeFieldType fieldType, String value) {
    super(createMessage(fieldType.getName(), value));
    iDateTimeFieldType = fieldType;
    iDurationFieldType = null;
    iFieldName = fieldType.getName();
    iStringValue = value;
    iNumberValue = null;
    iLowerBound = null;
    iUpperBound = null;
    iMessage = super.getMessage();
  }
  public IllegalFieldValueException(DurationFieldType fieldType, Number value, Number lowerBound, Number upperBound) {
    super(createMessage(fieldType.getName(), value, lowerBound, upperBound, null));
    iDateTimeFieldType = null;
    iDurationFieldType = fieldType;
    iFieldName = fieldType.getName();
    iNumberValue = value;
    iStringValue = null;
    iLowerBound = lowerBound;
    iUpperBound = upperBound;
    iMessage = super.getMessage();
  }
  public IllegalFieldValueException(DurationFieldType fieldType, String value) {
    super(createMessage(fieldType.getName(), value));
    iDateTimeFieldType = null;
    iDurationFieldType = fieldType;
    iFieldName = fieldType.getName();
    iStringValue = value;
    iNumberValue = null;
    iLowerBound = null;
    iUpperBound = null;
    iMessage = super.getMessage();
  }
  public IllegalFieldValueException(String fieldName, Number value, Number lowerBound, Number upperBound) {
    super(createMessage(fieldName, value, lowerBound, upperBound, null));
    iDateTimeFieldType = null;
    iDurationFieldType = null;
    iFieldName = fieldName;
    iNumberValue = value;
    iStringValue = null;
    iLowerBound = lowerBound;
    iUpperBound = upperBound;
    iMessage = super.getMessage();
  }
  public IllegalFieldValueException(String fieldName, String value) {
    super(createMessage(fieldName, value));
    iDateTimeFieldType = null;
    iDurationFieldType = null;
    iFieldName = fieldName;
    iStringValue = value;
    iNumberValue = null;
    iLowerBound = null;
    iUpperBound = null;
    iMessage = super.getMessage();
  }
  public DateTimeFieldType getDateTimeFieldType() {
    return iDateTimeFieldType;
  }
  public DurationFieldType getDurationFieldType() {
    return iDurationFieldType;
  }
  public Number getIllegalNumberValue() {
    return iNumberValue;
  }
  public Number getLowerBound() {
    return iLowerBound;
  }
  public Number getUpperBound() {
    return iUpperBound;
  }
  private static String createMessage(String fieldName, Number value, Number lowerBound, Number upperBound, String explain) {
    StringBuilder buf = new StringBuilder().append("Value ").append(value).append(" for ").append(fieldName).append(' ');
    if(lowerBound == null) {
      if(upperBound == null) {
        buf.append("is not supported");
      }
      else {
        buf.append("must not be larger than ").append(upperBound);
      }
    }
    else 
      if(upperBound == null) {
        buf.append("must not be smaller than ").append(lowerBound);
      }
      else {
        buf.append("must be in the range [").append(lowerBound).append(',').append(upperBound).append(']');
      }
    if(explain != null) {
      buf.append(": ").append(explain);
    }
    return buf.toString();
  }
  private static String createMessage(String fieldName, String value) {
    StringBuffer buf = new StringBuffer().append("Value ");
    if(value == null) {
      buf.append("null");
    }
    else {
      StringBuffer var_1 = buf.append('\"');
      buf.append(value);
      buf.append('\"');
    }
    buf.append(" for ").append(fieldName).append(' ').append("is not supported");
    return buf.toString();
  }
  public String getFieldName() {
    return iFieldName;
  }
  public String getIllegalStringValue() {
    return iStringValue;
  }
  public String getIllegalValueAsString() {
    String value = iStringValue;
    if(value == null) {
      value = String.valueOf(iNumberValue);
    }
    return value;
  }
  public String getMessage() {
    return iMessage;
  }
  public void prependMessage(String message) {
    if(iMessage == null) {
      iMessage = message;
    }
    else 
      if(message != null) {
        iMessage = message + ": " + iMessage;
      }
  }
}