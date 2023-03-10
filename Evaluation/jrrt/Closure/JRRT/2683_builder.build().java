package com.google.javascript.rhino.jstype;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.RecordTypeBuilder.RecordProperty;
import java.util.Map;
import java.util.Set;

class RecordType extends PrototypeObjectType  {
  final private static long serialVersionUID = 1L;
  final private boolean declared;
  private boolean isFrozen = false;
  RecordType(JSTypeRegistry registry, Map<String, RecordProperty> properties) {
    this(registry, properties, true);
  }
  RecordType(JSTypeRegistry registry, Map<String, RecordProperty> properties, boolean declared) {
    super(registry, null, null);
    setPrettyPrint(true);
    this.declared = declared;
    for (String property : properties.keySet()) {
      RecordProperty prop = properties.get(property);
      if(prop == null) {
        throw new IllegalStateException("RecordProperty associated with a property should not be null!");
      }
      if(declared) {
        defineDeclaredProperty(property, prop.getType(), prop.getPropertyNode());
      }
      else {
        defineSynthesizedProperty(property, prop.getType(), prop.getPropertyNode());
      }
    }
    isFrozen = true;
  }
  JSType getGreatestSubtypeHelper(JSType that) {
    if(that.isRecordType()) {
      RecordType thatRecord = that.toMaybeRecordType();
      RecordTypeBuilder builder = new RecordTypeBuilder(registry);
      builder.setSynthesized(true);
      for (String property : getOwnPropertyNames()) {
        if(thatRecord.hasProperty(property) && !thatRecord.getPropertyType(property).isInvariant(getPropertyType(property))) {
          return registry.getNativeObjectType(JSTypeNative.NO_TYPE);
        }
        builder.addProperty(property, getPropertyType(property), getPropertyNode(property));
      }
      for (String property : thatRecord.getOwnPropertyNames()) {
        if(!hasProperty(property)) {
          builder.addProperty(property, thatRecord.getPropertyType(property), thatRecord.getPropertyNode(property));
        }
      }
      JSType var_2683 = builder.build();
      return var_2683;
    }
    JSType greatestSubtype = registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE);
    JSType thatRestrictedToObj = registry.getNativeType(JSTypeNative.OBJECT_TYPE).getGreatestSubtype(that);
    if(!thatRestrictedToObj.isEmptyType()) {
      for (String propName : getOwnPropertyNames()) {
        JSType propType = getPropertyType(propName);
        UnionTypeBuilder builder = new UnionTypeBuilder(registry);
        for (ObjectType alt : registry.getEachReferenceTypeWithProperty(propName)) {
          JSType altPropType = alt.getPropertyType(propName);
          if(altPropType != null && !alt.isEquivalentTo(this) && alt.isSubtype(that) && propType.isInvariant(altPropType)) {
            builder.addAlternate(alt);
          }
        }
        greatestSubtype = greatestSubtype.getLeastSupertype(builder.build());
      }
    }
    return greatestSubtype;
  }
  @Override() public ObjectType getImplicitPrototype() {
    return registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
  }
  @Override() RecordType toMaybeRecordType() {
    return this;
  }
  boolean checkRecordEquivalenceHelper(RecordType otherRecord, EquivalenceMethod eqMethod) {
    Set<String> keySet = getOwnPropertyNames();
    Set<String> otherKeySet = otherRecord.getOwnPropertyNames();
    if(!otherKeySet.equals(keySet)) {
      return false;
    }
    for (String key : keySet) {
      if(!otherRecord.getPropertyType(key).checkEquivalenceHelper(getPropertyType(key), eqMethod)) {
        return false;
      }
    }
    return true;
  }
  @Override() boolean defineProperty(String propertyName, JSType type, boolean inferred, Node propertyNode) {
    if(isFrozen) {
      return false;
    }
    return super.defineProperty(propertyName, type, inferred, propertyNode);
  }
  @Override() public boolean isSubtype(JSType that) {
    if(JSType.isSubtypeHelper(this, that)) {
      return true;
    }
    if(registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE).isSubtype(that)) {
      return true;
    }
    if(!that.isRecordType()) {
      return false;
    }
    return RecordType.isSubtype(this, that.toMaybeRecordType());
  }
  static boolean isSubtype(ObjectType typeA, RecordType typeB) {
    for (String property : typeB.getOwnPropertyNames()) {
      if(!typeA.hasProperty(property)) {
        return false;
      }
      JSType propA = typeA.getPropertyType(property);
      JSType propB = typeB.getPropertyType(property);
      if(typeA.isPropertyTypeDeclared(property)) {
        if(!propA.isInvariant(propB)) {
          return false;
        }
      }
      else {
        if(!propA.isSubtype(propB)) {
          return false;
        }
      }
    }
    return true;
  }
  boolean isSynthetic() {
    return !declared;
  }
}