package org.joda.time.field;
import org.joda.time.DateTimeField;
import org.joda.time.DateTimeFieldType;
import org.joda.time.DurationField;

public class DividedDateTimeField extends DecoratedDateTimeField  {
  @SuppressWarnings(value = {"unused", }) final private static long serialVersionUID = 8318475124230605365L;
  final int iDivisor;
  final DurationField iDurationField;
  final DurationField iRangeDurationField;
  final private int iMin;
  final private int iMax;
  public DividedDateTimeField(DateTimeField field, DateTimeFieldType type, int divisor) {
    this(field, field.getRangeDurationField(), type, divisor);
  }
  public DividedDateTimeField(DateTimeField field, DurationField rangeField, DateTimeFieldType type, int divisor) {
    super(field, type);
    if(divisor < 2) {
      throw new IllegalArgumentException("The divisor must be at least 2");
    }
    DurationField unitField = field.getDurationField();
    if(unitField == null) {
      iDurationField = null;
    }
    else {
      iDurationField = new ScaledDurationField(unitField, type.getDurationType(), divisor);
    }
    iRangeDurationField = rangeField;
    iDivisor = divisor;
    int i = field.getMinimumValue();
    int min = (i >= 0) ? i / divisor : ((i + 1) / divisor - 1);
    int j = field.getMaximumValue();
    int max = (j >= 0) ? j / divisor : ((j + 1) / divisor - 1);
    iMin = min;
    iMax = max;
  }
  public DividedDateTimeField(RemainderDateTimeField remainderField, DateTimeFieldType type) {
    this(remainderField, null, type);
  }
  public DividedDateTimeField(RemainderDateTimeField remainderField, DurationField rangeField, DateTimeFieldType type) {
    super(remainderField.getWrappedField(), type);
    int divisor = iDivisor = remainderField.iDivisor;
    iDurationField = remainderField.iRangeField;
    iRangeDurationField = rangeField;
    DateTimeField field = getWrappedField();
    int i = field.getMinimumValue();
    int min = (i >= 0) ? i / divisor : ((i + 1) / divisor - 1);
    int j = field.getMaximumValue();
    int max = (j >= 0) ? j / divisor : ((j + 1) / divisor - 1);
    iMin = min;
    iMax = max;
  }
  public DurationField getDurationField() {
    return iDurationField;
  }
  @Override() public DurationField getRangeDurationField() {
    if(iRangeDurationField != null) {
      return iRangeDurationField;
    }
    return super.getRangeDurationField();
  }
  public int get(long instant) {
    int value = getWrappedField().get(instant);
    if(value >= 0) {
      return value / iDivisor;
    }
    else {
      return ((value + 1) / iDivisor) - 1;
    }
  }
  public int getDifference(long minuendInstant, long subtrahendInstant) {
    return getWrappedField().getDifference(minuendInstant, subtrahendInstant) / iDivisor;
  }
  public int getDivisor() {
    return iDivisor;
  }
  public int getMaximumValue() {
    return iMax;
  }
  public int getMinimumValue() {
    return iMin;
  }
  private int getRemainder(int value) {
    if(value >= 0) {
      return value % iDivisor;
    }
    else {
      return (iDivisor - 1) + ((value + 1) % iDivisor);
    }
  }
  public long add(long instant, int amount) {
    return getWrappedField().add(instant, amount * iDivisor);
  }
  public long add(long instant, long amount) {
    return getWrappedField().add(instant, amount * iDivisor);
  }
  public long addWrapField(long instant, int amount) {
    return set(instant, FieldUtils.getWrappedValue(get(instant), amount, iMin, iMax));
  }
  public long getDifferenceAsLong(long minuendInstant, long subtrahendInstant) {
    return getWrappedField().getDifferenceAsLong(minuendInstant, subtrahendInstant) / iDivisor;
  }
  public long remainder(long instant) {
    return set(instant, get(getWrappedField().remainder(instant)));
  }
  public long roundFloor(long instant) {
    DateTimeField field = getWrappedField();
    return field.roundFloor(field.set(instant, get(instant) * iDivisor));
  }
  public long set(long instant, int value) {
    FieldUtils.verifyValueBounds(this, value, iMin, iMax);
    DateTimeField var_449 = getWrappedField();
    int remainder = getRemainder(var_449.get(instant));
    return getWrappedField().set(instant, value * iDivisor + remainder);
  }
}