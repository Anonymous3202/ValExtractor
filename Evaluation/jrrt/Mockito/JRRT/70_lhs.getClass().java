package org.mockito.internal.matchers.apachecommons;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@SuppressWarnings(value = {"unchecked", }) class EqualsBuilder  {
  private boolean isEquals = true;
  public EqualsBuilder() {
    super();
  }
  public EqualsBuilder append(boolean lhs, boolean rhs) {
    isEquals &= (lhs == rhs);
    return this;
  }
  public EqualsBuilder append(boolean[] lhs, boolean[] rhs) {
    if(!isEquals) {
      return this;
    }
    if(lhs == rhs) {
      return this;
    }
    if(lhs == null || rhs == null) {
      this.setEquals(false);
      return this;
    }
    if(lhs.length != rhs.length) {
      this.setEquals(false);
      return this;
    }
    for(int i = 0; i < lhs.length && isEquals; ++i) {
      append(lhs[i], rhs[i]);
    }
    return this;
  }
  public EqualsBuilder append(byte lhs, byte rhs) {
    isEquals &= (lhs == rhs);
    return this;
  }
  public EqualsBuilder append(byte[] lhs, byte[] rhs) {
    if(!isEquals) {
      return this;
    }
    if(lhs == rhs) {
      return this;
    }
    if(lhs == null || rhs == null) {
      this.setEquals(false);
      return this;
    }
    if(lhs.length != rhs.length) {
      this.setEquals(false);
      return this;
    }
    for(int i = 0; i < lhs.length && isEquals; ++i) {
      append(lhs[i], rhs[i]);
    }
    return this;
  }
  public EqualsBuilder append(char lhs, char rhs) {
    isEquals &= (lhs == rhs);
    return this;
  }
  public EqualsBuilder append(char[] lhs, char[] rhs) {
    if(!isEquals) {
      return this;
    }
    if(lhs == rhs) {
      return this;
    }
    if(lhs == null || rhs == null) {
      this.setEquals(false);
      return this;
    }
    if(lhs.length != rhs.length) {
      this.setEquals(false);
      return this;
    }
    for(int i = 0; i < lhs.length && isEquals; ++i) {
      append(lhs[i], rhs[i]);
    }
    return this;
  }
  public EqualsBuilder append(double lhs, double rhs) {
    if(!isEquals) {
      return this;
    }
    return append(Double.doubleToLongBits(lhs), Double.doubleToLongBits(rhs));
  }
  public EqualsBuilder append(double[] lhs, double[] rhs) {
    if(!isEquals) {
      return this;
    }
    if(lhs == rhs) {
      return this;
    }
    if(lhs == null || rhs == null) {
      this.setEquals(false);
      return this;
    }
    if(lhs.length != rhs.length) {
      this.setEquals(false);
      return this;
    }
    for(int i = 0; i < lhs.length && isEquals; ++i) {
      append(lhs[i], rhs[i]);
    }
    return this;
  }
  public EqualsBuilder append(float lhs, float rhs) {
    if(!isEquals) {
      return this;
    }
    return append(Float.floatToIntBits(lhs), Float.floatToIntBits(rhs));
  }
  public EqualsBuilder append(float[] lhs, float[] rhs) {
    if(!isEquals) {
      return this;
    }
    if(lhs == rhs) {
      return this;
    }
    if(lhs == null || rhs == null) {
      this.setEquals(false);
      return this;
    }
    if(lhs.length != rhs.length) {
      this.setEquals(false);
      return this;
    }
    for(int i = 0; i < lhs.length && isEquals; ++i) {
      append(lhs[i], rhs[i]);
    }
    return this;
  }
  public EqualsBuilder append(int lhs, int rhs) {
    isEquals &= (lhs == rhs);
    return this;
  }
  public EqualsBuilder append(int[] lhs, int[] rhs) {
    if(!isEquals) {
      return this;
    }
    if(lhs == rhs) {
      return this;
    }
    if(lhs == null || rhs == null) {
      this.setEquals(false);
      return this;
    }
    if(lhs.length != rhs.length) {
      this.setEquals(false);
      return this;
    }
    for(int i = 0; i < lhs.length && isEquals; ++i) {
      append(lhs[i], rhs[i]);
    }
    return this;
  }
  public EqualsBuilder append(Object lhs, Object rhs) {
    if(!isEquals) {
      return this;
    }
    if(lhs == rhs) {
      return this;
    }
    if(lhs == null || rhs == null) {
      this.setEquals(false);
      return this;
    }
    Class<? extends Object> var_70 = lhs.getClass();
    Class lhsClass = var_70;
    if(!lhsClass.isArray()) {
      if(lhs instanceof java.math.BigDecimal && rhs instanceof java.math.BigDecimal) {
        isEquals = (((java.math.BigDecimal)lhs).compareTo((java.math.BigDecimal)rhs) == 0);
      }
      else {
        isEquals = lhs.equals(rhs);
      }
    }
    else 
      if(lhs.getClass() != rhs.getClass()) {
        this.setEquals(false);
      }
      else 
        if(lhs instanceof long[]) {
          append((long[])lhs, (long[])rhs);
        }
        else 
          if(lhs instanceof int[]) {
            append((int[])lhs, (int[])rhs);
          }
          else 
            if(lhs instanceof short[]) {
              append((short[])lhs, (short[])rhs);
            }
            else 
              if(lhs instanceof char[]) {
                append((char[])lhs, (char[])rhs);
              }
              else 
                if(lhs instanceof byte[]) {
                  append((byte[])lhs, (byte[])rhs);
                }
                else 
                  if(lhs instanceof double[]) {
                    append((double[])lhs, (double[])rhs);
                  }
                  else 
                    if(lhs instanceof float[]) {
                      append((float[])lhs, (float[])rhs);
                    }
                    else 
                      if(lhs instanceof boolean[]) {
                        append((boolean[])lhs, (boolean[])rhs);
                      }
                      else {
                        append((Object[])lhs, (Object[])rhs);
                      }
    return this;
  }
  public EqualsBuilder append(Object[] lhs, Object[] rhs) {
    if(!isEquals) {
      return this;
    }
    if(lhs == rhs) {
      return this;
    }
    if(lhs == null || rhs == null) {
      this.setEquals(false);
      return this;
    }
    if(lhs.length != rhs.length) {
      this.setEquals(false);
      return this;
    }
    for(int i = 0; i < lhs.length && isEquals; ++i) {
      append(lhs[i], rhs[i]);
    }
    return this;
  }
  public EqualsBuilder append(long lhs, long rhs) {
    isEquals &= (lhs == rhs);
    return this;
  }
  public EqualsBuilder append(long[] lhs, long[] rhs) {
    if(!isEquals) {
      return this;
    }
    if(lhs == rhs) {
      return this;
    }
    if(lhs == null || rhs == null) {
      this.setEquals(false);
      return this;
    }
    if(lhs.length != rhs.length) {
      this.setEquals(false);
      return this;
    }
    for(int i = 0; i < lhs.length && isEquals; ++i) {
      append(lhs[i], rhs[i]);
    }
    return this;
  }
  public EqualsBuilder append(short lhs, short rhs) {
    isEquals &= (lhs == rhs);
    return this;
  }
  public EqualsBuilder append(short[] lhs, short[] rhs) {
    if(!isEquals) {
      return this;
    }
    if(lhs == rhs) {
      return this;
    }
    if(lhs == null || rhs == null) {
      this.setEquals(false);
      return this;
    }
    if(lhs.length != rhs.length) {
      this.setEquals(false);
      return this;
    }
    for(int i = 0; i < lhs.length && isEquals; ++i) {
      append(lhs[i], rhs[i]);
    }
    return this;
  }
  public EqualsBuilder appendSuper(boolean superEquals) {
    isEquals &= superEquals;
    return this;
  }
  public boolean isEquals() {
    return this.isEquals;
  }
  public static boolean reflectionEquals(Object lhs, Object rhs) {
    return reflectionEquals(lhs, rhs, false, null, null);
  }
  public static boolean reflectionEquals(Object lhs, Object rhs, boolean testTransients) {
    return reflectionEquals(lhs, rhs, testTransients, null, null);
  }
  public static boolean reflectionEquals(Object lhs, Object rhs, boolean testTransients, Class reflectUpToClass) {
    return reflectionEquals(lhs, rhs, testTransients, reflectUpToClass, null);
  }
  public static boolean reflectionEquals(Object lhs, Object rhs, boolean testTransients, Class reflectUpToClass, String[] excludeFields) {
    if(lhs == rhs) {
      return true;
    }
    if(lhs == null || rhs == null) {
      return false;
    }
    Class lhsClass = lhs.getClass();
    Class rhsClass = rhs.getClass();
    Class testClass;
    if(lhsClass.isInstance(rhs)) {
      testClass = lhsClass;
      if(!rhsClass.isInstance(lhs)) {
        testClass = rhsClass;
      }
    }
    else 
      if(rhsClass.isInstance(lhs)) {
        testClass = rhsClass;
        if(!lhsClass.isInstance(rhs)) {
          testClass = lhsClass;
        }
      }
      else {
        return false;
      }
    EqualsBuilder equalsBuilder = new EqualsBuilder();
    try {
      reflectionAppend(lhs, rhs, testClass, equalsBuilder, testTransients, excludeFields);
      while(testClass.getSuperclass() != null && testClass != reflectUpToClass){
        testClass = testClass.getSuperclass();
        reflectionAppend(lhs, rhs, testClass, equalsBuilder, testTransients, excludeFields);
      }
    }
    catch (IllegalArgumentException e) {
      return false;
    }
    return equalsBuilder.isEquals();
  }
  public static boolean reflectionEquals(Object lhs, Object rhs, String[] excludeFields) {
    return reflectionEquals(lhs, rhs, false, null, excludeFields);
  }
  private static void reflectionAppend(Object lhs, Object rhs, Class clazz, EqualsBuilder builder, boolean useTransients, String[] excludeFields) {
    Field[] fields = clazz.getDeclaredFields();
    List excludedFieldList = excludeFields != null ? Arrays.asList(excludeFields) : Collections.EMPTY_LIST;
    AccessibleObject.setAccessible(fields, true);
    for(int i = 0; i < fields.length && builder.isEquals; i++) {
      Field f = fields[i];
      if(!excludedFieldList.contains(f.getName()) && (f.getName().indexOf('$') == -1) && (useTransients || !Modifier.isTransient(f.getModifiers())) && (!Modifier.isStatic(f.getModifiers()))) {
        try {
          builder.append(f.get(lhs), f.get(rhs));
        }
        catch (IllegalAccessException e) {
          throw new InternalError("Unexpected IllegalAccessException");
        }
      }
    }
  }
  protected void setEquals(boolean isEquals) {
    this.isEquals = isEquals;
  }
}