package com.google.javascript.rhino.jstype;
import static com.google.javascript.rhino.jstype.JSTypeNative.ALL_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.CHECKED_UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NO_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

class UnionTypeBuilder implements Serializable  {
  final private static long serialVersionUID = 1L;
  final private static int DEFAULT_MAX_UNION_SIZE = 20;
  final private JSTypeRegistry registry;
  final private List<JSType> alternates = Lists.newArrayList();
  private boolean isAllType = false;
  private boolean isNativeUnknownType = false;
  private boolean areAllUnknownsChecked = true;
  final private int maxUnionSize;
  private int functionTypePosition = -1;
  private JSType result = null;
  UnionTypeBuilder(JSTypeRegistry registry) {
    this(registry, DEFAULT_MAX_UNION_SIZE);
  }
  UnionTypeBuilder(JSTypeRegistry registry, int maxUnionSize) {
    super();
    this.registry = registry;
    this.maxUnionSize = maxUnionSize;
  }
  private Collection<JSType> getAlternateListCopy() {
    return ImmutableList.copyOf(alternates);
  }
  Iterable<JSType> getAlternates() {
    JSType specialCaseType = reduceAlternatesWithoutUnion();
    if(specialCaseType != null) {
      return ImmutableList.of(specialCaseType);
    }
    return Collections.unmodifiableList(alternates);
  }
  JSType build() {
    if(result == null) {
      result = reduceAlternatesWithoutUnion();
      if(result == null) {
        result = new UnionType(registry, getAlternateListCopy());
      }
    }
    return result;
  }
  private JSType reduceAlternatesWithoutUnion() {
    if(isAllType) {
      return registry.getNativeType(ALL_TYPE);
    }
    else 
      if(isNativeUnknownType) {
        if(areAllUnknownsChecked) {
          return registry.getNativeType(CHECKED_UNKNOWN_TYPE);
        }
        else {
          return registry.getNativeType(UNKNOWN_TYPE);
        }
      }
      else {
        int size = alternates.size();
        if(size > maxUnionSize) {
          return registry.getNativeType(UNKNOWN_TYPE);
        }
        else 
          if(size > 1) {
            return null;
          }
          else 
            if(size == 1) {
              return alternates.iterator().next();
            }
            else {
              return registry.getNativeType(NO_TYPE);
            }
      }
  }
  UnionTypeBuilder addAlternate(JSType alternate) {
    if(alternate.isNoType()) {
      return this;
    }
    isAllType = isAllType || alternate.isAllType();
    boolean isAlternateUnknown = alternate instanceof UnknownType;
    isNativeUnknownType = isNativeUnknownType || isAlternateUnknown;
    if(isAlternateUnknown) {
      areAllUnknownsChecked = areAllUnknownsChecked && alternate.isCheckedUnknownType();
    }
    if(!isAllType && !isNativeUnknownType) {
      if(alternate.isUnionType()) {
        UnionType union = alternate.toMaybeUnionType();
        for (JSType unionAlt : union.getAlternates()) {
          addAlternate(unionAlt);
        }
      }
      else {
        int var_2694 = alternates.size();
        if(var_2694 > maxUnionSize) {
          return this;
        }
        if(alternate.isFunctionType() && functionTypePosition != -1) {
          FunctionType other = alternates.get(functionTypePosition).toMaybeFunctionType();
          FunctionType supremum = alternate.toMaybeFunctionType().supAndInfHelper(other, true);
          alternates.set(functionTypePosition, supremum);
          result = null;
          return this;
        }
        int currentIndex = 0;
        Iterator<JSType> it = alternates.iterator();
        while(it.hasNext()){
          boolean removeCurrent = false;
          JSType current = it.next();
          if(alternate.isUnknownType() || current.isUnknownType() || alternate.isNoResolvedType() || current.isNoResolvedType() || alternate.hasAnyTemplateTypes() || current.hasAnyTemplateTypes()) {
            if(alternate.isEquivalentTo(current)) {
              return this;
            }
          }
          else {
            if(alternate.isParameterizedType() || current.isParameterizedType()) {
              if(!current.isParameterizedType()) {
                if(alternate.isSubtype(current)) {
                  return this;
                }
              }
              else 
                if(!alternate.isParameterizedType()) {
                  if(current.isSubtype(alternate)) {
                    removeCurrent = true;
                  }
                }
                else {
                  Preconditions.checkState(current.isParameterizedType() && alternate.isParameterizedType());
                  ParameterizedType parameterizedAlternate = alternate.toMaybeParameterizedType();
                  ParameterizedType parameterizedCurrent = current.toMaybeParameterizedType();
                  if(parameterizedCurrent.wrapsSameRawType(parameterizedAlternate)) {
                    JSType alternateTypeParameter = parameterizedAlternate.getParameterType();
                    JSType currentTypeParameter = parameterizedCurrent.getParameterType();
                    if(currentTypeParameter.isEquivalentTo(parameterizedCurrent)) {
                      return this;
                    }
                    else {
                      JSType merged = parameterizedCurrent.getReferencedObjTypeInternal();
                      return addAlternate(merged);
                    }
                  }
                }
            }
            else 
              if(alternate.isSubtype(current)) {
                return this;
              }
              else 
                if(current.isSubtype(alternate)) {
                  removeCurrent = true;
                }
          }
          if(removeCurrent) {
            it.remove();
            if(currentIndex == functionTypePosition) {
              functionTypePosition = -1;
            }
            else 
              if(currentIndex < functionTypePosition) {
                functionTypePosition--;
                currentIndex--;
              }
          }
          currentIndex++;
        }
        if(alternate.isFunctionType()) {
          Preconditions.checkState(functionTypePosition == -1);
          functionTypePosition = alternates.size();
        }
        alternates.add(alternate);
        result = null;
      }
    }
    else {
      result = null;
    }
    return this;
  }
}