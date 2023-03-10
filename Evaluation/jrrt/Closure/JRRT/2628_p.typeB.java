package com.google.javascript.rhino.jstype;
import static com.google.javascript.rhino.jstype.TernaryValue.UNKNOWN;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.javascript.rhino.ErrorReporter;
import java.util.Collection;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

public class UnionType extends JSType  {
  final private static long serialVersionUID = 1L;
  Collection<JSType> alternates;
  final private int hashcode;
  UnionType(JSTypeRegistry registry, Collection<JSType> alternates) {
    super(registry);
    this.alternates = alternates;
    this.hashcode = this.alternates.hashCode();
  }
  @Override() public BooleanLiteralSet getPossibleToBooleanOutcomes() {
    BooleanLiteralSet literals = BooleanLiteralSet.EMPTY;
    for (JSType element : alternates) {
      literals = literals.union(element.getPossibleToBooleanOutcomes());
      if(literals == BooleanLiteralSet.BOTH) {
        break ;
      }
    }
    return literals;
  }
  public Iterable<JSType> getAlternates() {
    return alternates;
  }
  @Override() public JSType autobox() {
    UnionTypeBuilder restricted = new UnionTypeBuilder(registry);
    for (JSType t : alternates) {
      restricted.addAlternate(t.autobox());
    }
    return restricted.build();
  }
  @Override() public JSType collapseUnion() {
    JSType currentValue = null;
    ObjectType currentCommonSuper = null;
    for (JSType a : alternates) {
      if(a.isUnknownType()) {
        return getNativeType(JSTypeNative.UNKNOWN_TYPE);
      }
      ObjectType obj = a.toObjectType();
      if(obj == null) {
        if(currentValue == null && currentCommonSuper == null) {
          currentValue = a;
        }
        else {
          return getNativeType(JSTypeNative.ALL_TYPE);
        }
      }
      else 
        if(currentValue != null) {
          return getNativeType(JSTypeNative.ALL_TYPE);
        }
        else 
          if(currentCommonSuper == null) {
            currentCommonSuper = obj;
          }
          else {
            currentCommonSuper = registry.findCommonSuperObject(currentCommonSuper, obj);
          }
    }
    return currentCommonSuper;
  }
  @Override() public JSType findPropertyType(String propertyName) {
    JSType propertyType = null;
    for (JSType alternate : getAlternates()) {
      if(alternate.isNullType() || alternate.isVoidType()) {
        continue ;
      }
      JSType altPropertyType = alternate.findPropertyType(propertyName);
      if(altPropertyType == null) {
        continue ;
      }
      if(propertyType == null) {
        propertyType = altPropertyType;
      }
      else {
        propertyType = propertyType.getLeastSupertype(altPropertyType);
      }
    }
    return propertyType;
  }
  @Override() public JSType getLeastSupertype(JSType that) {
    if(!that.isUnknownType() && !that.isUnionType()) {
      for (JSType alternate : alternates) {
        if(!alternate.isUnknownType() && that.isSubtype(alternate)) {
          return this;
        }
      }
    }
    return getLeastSupertype(this, that);
  }
  @Override() public JSType getRestrictedTypeGivenToBooleanOutcome(boolean outcome) {
    UnionTypeBuilder restricted = new UnionTypeBuilder(registry);
    for (JSType element : alternates) {
      restricted.addAlternate(element.getRestrictedTypeGivenToBooleanOutcome(outcome));
    }
    return restricted.build();
  }
  public JSType getRestrictedUnion(JSType type) {
    UnionTypeBuilder restricted = new UnionTypeBuilder(registry);
    for (JSType t : alternates) {
      if(t.isUnknownType() || t.isNoResolvedType() || !t.isSubtype(type)) {
        restricted.addAlternate(t);
      }
    }
    return restricted.build();
  }
  JSType meet(JSType that) {
    UnionTypeBuilder builder = new UnionTypeBuilder(registry);
    for (JSType alternate : alternates) {
      if(alternate.isSubtype(that)) {
        builder.addAlternate(alternate);
      }
    }
    if(that.isUnionType()) {
      for (JSType otherAlternate : that.toMaybeUnionType().alternates) {
        if(otherAlternate.isSubtype(this)) {
          builder.addAlternate(otherAlternate);
        }
      }
    }
    else 
      if(that.isSubtype(this)) {
        builder.addAlternate(that);
      }
    JSType result = builder.build();
    if(!result.isNoType()) {
      return result;
    }
    else 
      if(this.isObject() && (that.isObject() && !that.isNoType())) {
        return getNativeType(JSTypeNative.NO_OBJECT_TYPE);
      }
      else {
        return getNativeType(JSTypeNative.NO_TYPE);
      }
  }
  @Override() JSType resolveInternal(ErrorReporter t, StaticScope<JSType> scope) {
    setResolvedTypeInternal(this);
    boolean changed = false;
    ImmutableList.Builder<JSType> resolvedTypes = ImmutableList.builder();
    for (JSType alternate : alternates) {
      JSType newAlternate = alternate.resolve(t, scope);
      changed |= (alternate != newAlternate);
      resolvedTypes.add(alternate);
    }
    if(changed) {
      Collection<JSType> newAlternates = resolvedTypes.build();
      Preconditions.checkState(newAlternates.hashCode() == this.hashcode);
      alternates = newAlternates;
    }
    return this;
  }
  @Override() public JSType restrictByNotNullOrUndefined() {
    UnionTypeBuilder restricted = new UnionTypeBuilder(registry);
    for (JSType t : alternates) {
      restricted.addAlternate(t.restrictByNotNullOrUndefined());
    }
    return restricted.build();
  }
  @Override() public String toDebugHashCodeString() {
    List<String> hashCodes = Lists.newArrayList();
    for (JSType a : alternates) {
      hashCodes.add(a.toDebugHashCodeString());
    }
    return "{(" + Joiner.on(",").join(hashCodes) + ")}";
  }
  @Override() String toStringHelper(boolean forAnnotations) {
    StringBuilder result = new StringBuilder();
    boolean firstAlternate = true;
    result.append("(");
    SortedSet<JSType> sorted = new TreeSet<JSType>(ALPHA);
    sorted.addAll(alternates);
    for (JSType t : sorted) {
      if(!firstAlternate) {
        result.append("|");
      }
      result.append(t.toStringHelper(forAnnotations));
      firstAlternate = false;
    }
    result.append(")");
    return result.toString();
  }
  @Override<T>() T visit(RelationshipVisitor<T> visitor, JSType that) {
    return visitor.caseUnionType(this, that);
  }
  @Override() public  <T extends java.lang.Object> T visit(Visitor<T> visitor) {
    return visitor.caseUnionType(this);
  }
  @Override() public TernaryValue testForEquality(JSType that) {
    TernaryValue result = null;
    for (JSType t : alternates) {
      TernaryValue test = t.testForEquality(that);
      if(result == null) {
        result = test;
      }
      else 
        if(!result.equals(test)) {
          return UNKNOWN;
        }
    }
    return result;
  }
  @Override() public TypePair getTypesUnderEquality(JSType that) {
    UnionTypeBuilder thisRestricted = new UnionTypeBuilder(registry);
    UnionTypeBuilder thatRestricted = new UnionTypeBuilder(registry);
    for (JSType element : alternates) {
      TypePair p = element.getTypesUnderEquality(that);
      if(p.typeA != null) {
        thisRestricted.addAlternate(p.typeA);
      }
      JSType var_2628 = p.typeB;
      if(var_2628 != null) {
        thatRestricted.addAlternate(p.typeB);
      }
    }
    return new TypePair(thisRestricted.build(), thatRestricted.build());
  }
  @Override() public TypePair getTypesUnderInequality(JSType that) {
    UnionTypeBuilder thisRestricted = new UnionTypeBuilder(registry);
    UnionTypeBuilder thatRestricted = new UnionTypeBuilder(registry);
    for (JSType element : alternates) {
      TypePair p = element.getTypesUnderInequality(that);
      if(p.typeA != null) {
        thisRestricted.addAlternate(p.typeA);
      }
      if(p.typeB != null) {
        thatRestricted.addAlternate(p.typeB);
      }
    }
    return new TypePair(thisRestricted.build(), thatRestricted.build());
  }
  @Override() public TypePair getTypesUnderShallowInequality(JSType that) {
    UnionTypeBuilder thisRestricted = new UnionTypeBuilder(registry);
    UnionTypeBuilder thatRestricted = new UnionTypeBuilder(registry);
    for (JSType element : alternates) {
      TypePair p = element.getTypesUnderShallowInequality(that);
      if(p.typeA != null) {
        thisRestricted.addAlternate(p.typeA);
      }
      if(p.typeB != null) {
        thatRestricted.addAlternate(p.typeB);
      }
    }
    return new TypePair(thisRestricted.build(), thatRestricted.build());
  }
  @Override() public UnionType toMaybeUnionType() {
    return this;
  }
  @Override() public boolean canBeCalled() {
    for (JSType t : alternates) {
      if(!t.canBeCalled()) {
        return false;
      }
    }
    return true;
  }
  boolean checkUnionEquivalenceHelper(UnionType that, EquivalenceMethod eqMethod) {
    if(eqMethod == EquivalenceMethod.IDENTITY && alternates.size() != that.alternates.size()) {
      return false;
    }
    for (JSType alternate : that.alternates) {
      if(!hasAlternate(alternate, eqMethod)) {
        return false;
      }
    }
    return true;
  }
  public boolean contains(JSType type) {
    for (JSType alt : alternates) {
      if(alt.isEquivalentTo(type)) {
        return true;
      }
    }
    return false;
  }
  private boolean hasAlternate(JSType type, EquivalenceMethod eqMethod) {
    for (JSType alternate : alternates) {
      if(alternate.checkEquivalenceHelper(type, eqMethod)) {
        return true;
      }
    }
    return false;
  }
  @Override() public boolean hasAnyTemplateTypesInternal() {
    for (JSType alternate : alternates) {
      if(alternate.hasAnyTemplateTypes()) {
        return true;
      }
    }
    return false;
  }
  @Override() public boolean hasProperty(String pname) {
    for (JSType alternate : alternates) {
      if(alternate.hasProperty(pname)) {
        return true;
      }
    }
    return false;
  }
  @Override() public boolean isDict() {
    for (JSType typ : getAlternates()) {
      if(typ.isDict()) {
        return true;
      }
    }
    return false;
  }
  @Override() public boolean isNullable() {
    for (JSType t : alternates) {
      if(t.isNullable()) {
        return true;
      }
    }
    return false;
  }
  @Override() public boolean isObject() {
    for (JSType alternate : alternates) {
      if(!alternate.isObject()) {
        return false;
      }
    }
    return true;
  }
  @Override() public boolean isStruct() {
    for (JSType typ : getAlternates()) {
      if(typ.isStruct()) {
        return true;
      }
    }
    return false;
  }
  @Override() public boolean isSubtype(JSType that) {
    if(that.isUnknownType()) {
      return true;
    }
    if(that.isAllType()) {
      return true;
    }
    for (JSType element : alternates) {
      if(!element.isSubtype(that)) {
        return false;
      }
    }
    return true;
  }
  @Override() public boolean isUnknownType() {
    for (JSType t : alternates) {
      if(t.isUnknownType()) {
        return true;
      }
    }
    return false;
  }
  @Override() public boolean matchesNumberContext() {
    for (JSType t : alternates) {
      if(t.matchesNumberContext()) {
        return true;
      }
    }
    return false;
  }
  @Override() public boolean matchesObjectContext() {
    for (JSType t : alternates) {
      if(t.matchesObjectContext()) {
        return true;
      }
    }
    return false;
  }
  @Override() public boolean matchesStringContext() {
    for (JSType t : alternates) {
      if(t.matchesStringContext()) {
        return true;
      }
    }
    return false;
  }
  @Override() public boolean setValidator(Predicate<JSType> validator) {
    for (JSType a : alternates) {
      a.setValidator(validator);
    }
    return true;
  }
  @Override() public int hashCode() {
    return this.hashcode;
  }
  @Override() public void matchConstraint(JSType constraint) {
    for (JSType alternate : alternates) {
      alternate.matchConstraint(constraint);
    }
  }
}