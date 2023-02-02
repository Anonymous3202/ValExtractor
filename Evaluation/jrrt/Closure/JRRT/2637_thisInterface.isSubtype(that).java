package com.google.javascript.rhino.jstype;
import static com.google.common.base.Preconditions.checkState;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import java.util.Set;

class PrototypeObjectType extends ObjectType  {
  final private static long serialVersionUID = 1L;
  final private String className;
  final private PropertyMap properties;
  final private boolean nativeType;
  private ObjectType implicitPrototypeFallback;
  private FunctionType ownerFunction = null;
  private boolean prettyPrint = false;
  final private static int MAX_PRETTY_PRINTED_PROPERTIES = 4;
  PrototypeObjectType(JSTypeRegistry registry, String className, ObjectType implicitPrototype) {
    this(registry, className, implicitPrototype, false, null, null);
  }
  PrototypeObjectType(JSTypeRegistry registry, String className, ObjectType implicitPrototype, boolean nativeType, ImmutableList<String> templateKeys, ImmutableList<JSType> templatizedTypes) {
    super(registry, templateKeys, templatizedTypes);
    this.properties = new PropertyMap();
    this.properties.setParentSource(this);
    this.className = className;
    this.nativeType = nativeType;
    if(nativeType || implicitPrototype != null) {
      setImplicitPrototype(implicitPrototype);
    }
    else {
      setImplicitPrototype(registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE));
    }
  }
  @Override() public FunctionType getConstructor() {
    return null;
  }
  @Override() public FunctionType getOwnerFunction() {
    return ownerFunction;
  }
  @Override() public Iterable<ObjectType> getCtorExtendedInterfaces() {
    return isFunctionPrototypeType() ? getOwnerFunction().getExtendedInterfaces() : ImmutableList.<ObjectType>of();
  }
  @Override() public Iterable<ObjectType> getCtorImplementedInterfaces() {
    return isFunctionPrototypeType() ? getOwnerFunction().getImplementedInterfaces() : ImmutableList.<ObjectType>of();
  }
  @Override() JSType resolveInternal(ErrorReporter t, StaticScope<JSType> scope) {
    setResolvedTypeInternal(this);
    ObjectType implicitPrototype = getImplicitPrototype();
    if(implicitPrototype != null) {
      implicitPrototypeFallback = (ObjectType)implicitPrototype.resolve(t, scope);
    }
    for (Property prop : properties.values()) {
      prop.setType(safeResolve(prop.getType(), t, scope));
    }
    return this;
  }
  @Override() public JSType unboxesTo() {
    if(isStringObjectType()) {
      return getNativeType(JSTypeNative.STRING_TYPE);
    }
    else 
      if(isBooleanObjectType()) {
        return getNativeType(JSTypeNative.BOOLEAN_TYPE);
      }
      else 
        if(isNumberObjectType()) {
          return getNativeType(JSTypeNative.NUMBER_TYPE);
        }
        else {
          return super.unboxesTo();
        }
  }
  @Override() public ObjectType getImplicitPrototype() {
    return implicitPrototypeFallback;
  }
  @Override() PropertyMap getPropertyMap() {
    return properties;
  }
  @Override() public String getReferenceName() {
    if(className != null) {
      return className;
    }
    else 
      if(ownerFunction != null) {
        return ownerFunction.getReferenceName() + ".prototype";
      }
      else {
        return null;
      }
  }
  @Override() String toStringHelper(boolean forAnnotations) {
    if(hasReferenceName()) {
      return getReferenceName();
    }
    else 
      if(prettyPrint) {
        prettyPrint = false;
        Set<String> propertyNames = Sets.newTreeSet();
        for(com.google.javascript.rhino.jstype.ObjectType current = this; current != null && !current.isNativeObjectType() && propertyNames.size() <= MAX_PRETTY_PRINTED_PROPERTIES; current = current.getImplicitPrototype()) {
          propertyNames.addAll(current.getOwnPropertyNames());
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        int i = 0;
        for (String property : propertyNames) {
          if(i > 0) {
            sb.append(", ");
          }
          sb.append(property);
          sb.append(": ");
          sb.append(getPropertyType(property).toStringHelper(forAnnotations));
          ++i;
          if(!forAnnotations && i == MAX_PRETTY_PRINTED_PROPERTIES) {
            sb.append(", ...");
            break ;
          }
        }
        sb.append("}");
        prettyPrint = true;
        return sb.toString();
      }
      else {
        return forAnnotations ? "?" : "{...}";
      }
  }
  @Override() public boolean canBeCalled() {
    return isRegexpType();
  }
  @Override() boolean defineProperty(String name, JSType type, boolean inferred, Node propertyNode) {
    if(hasOwnDeclaredProperty(name)) {
      return false;
    }
    Property newProp = new Property(name, type, inferred, propertyNode);
    properties.putProperty(name, newProp);
    return true;
  }
  @Override() public boolean hasCachedValues() {
    return super.hasCachedValues();
  }
  private boolean hasOverridenNativeProperty(String propertyName) {
    if(isNativeObjectType()) {
      return false;
    }
    JSType propertyType = getPropertyType(propertyName);
    ObjectType nativeType = isFunctionType() ? registry.getNativeObjectType(JSTypeNative.FUNCTION_PROTOTYPE) : registry.getNativeObjectType(JSTypeNative.OBJECT_PROTOTYPE);
    JSType nativePropertyType = nativeType.getPropertyType(propertyName);
    return propertyType != nativePropertyType;
  }
  @Override() public boolean hasReferenceName() {
    return className != null || ownerFunction != null;
  }
  private boolean implicitPrototypeChainIsUnknown() {
    ObjectType p = getImplicitPrototype();
    while(p != null){
      if(p.isUnknownType()) {
        return true;
      }
      p = p.getImplicitPrototype();
    }
    return false;
  }
  @Override() public boolean isNativeObjectType() {
    return nativeType;
  }
  boolean isPrettyPrint() {
    return prettyPrint;
  }
  @Override() public boolean isSubtype(JSType that) {
    if(JSType.isSubtypeHelper(this, that)) {
      return true;
    }
    if(that.isUnionType()) {
      return false;
    }
    if(that.isRecordType()) {
      return RecordType.isSubtype(this, that.toMaybeRecordType());
    }
    ObjectType thatObj = that.toObjectType();
    FunctionType thatCtor = thatObj == null ? null : thatObj.getConstructor();
    if(getConstructor() != null && getConstructor().isInterface()) {
      for (ObjectType thisInterface : getCtorExtendedInterfaces()) {
        boolean var_2637 = thisInterface.isSubtype(that);
        if(var_2637) {
          return true;
        }
      }
    }
    else 
      if(thatCtor != null && thatCtor.isInterface()) {
        Iterable<ObjectType> thisInterfaces = getCtorImplementedInterfaces();
        for (ObjectType thisInterface : thisInterfaces) {
          if(thisInterface.isSubtype(that)) {
            return true;
          }
        }
      }
    if(isUnknownType() || implicitPrototypeChainIsUnknown()) {
      return true;
    }
    return thatObj != null && isImplicitPrototype(thatObj);
  }
  @Override() public boolean matchesNumberContext() {
    return isNumberObjectType() || isDateType() || isBooleanObjectType() || isStringObjectType() || hasOverridenNativeProperty("valueOf");
  }
  @Override() public boolean matchesObjectContext() {
    return true;
  }
  @Override() public boolean matchesStringContext() {
    return isTheObjectType() || isStringObjectType() || isDateType() || isRegexpType() || isArrayType() || isNumberObjectType() || isBooleanObjectType() || hasOverridenNativeProperty("toString");
  }
  @Override() public boolean removeProperty(String name) {
    return properties.removeProperty(name);
  }
  @Override() public void matchConstraint(JSType constraint) {
    if(hasReferenceName()) {
      return ;
    }
    if(constraint.isRecordType()) {
      matchRecordTypeConstraint(constraint.toObjectType());
    }
    else 
      if(constraint.isUnionType()) {
        for (JSType alt : constraint.toMaybeUnionType().getAlternates()) {
          if(alt.isRecordType()) {
            matchRecordTypeConstraint(alt.toObjectType());
          }
        }
      }
  }
  public void matchRecordTypeConstraint(ObjectType constraintObj) {
    for (String prop : constraintObj.getOwnPropertyNames()) {
      JSType propType = constraintObj.getPropertyType(prop);
      if(!isPropertyTypeDeclared(prop)) {
        JSType typeToInfer = propType;
        if(!hasProperty(prop)) {
          typeToInfer = getNativeType(JSTypeNative.VOID_TYPE).getLeastSupertype(propType);
        }
        defineInferredProperty(prop, typeToInfer, null);
      }
    }
  }
  final void setImplicitPrototype(ObjectType implicitPrototype) {
    checkState(!hasCachedValues());
    this.implicitPrototypeFallback = implicitPrototype;
  }
  @Override() void setOwnerFunction(FunctionType type) {
    Preconditions.checkState(ownerFunction == null || type == null);
    ownerFunction = type;
  }
  void setPrettyPrint(boolean prettyPrint) {
    this.prettyPrint = prettyPrint;
  }
  @Override() public void setPropertyJSDocInfo(String propertyName, JSDocInfo info) {
    if(info != null) {
      if(properties.getOwnProperty(propertyName) == null) {
        defineInferredProperty(propertyName, getPropertyType(propertyName), null);
      }
      Property property = properties.getOwnProperty(propertyName);
      if(property != null) {
        property.setJSDocInfo(info);
      }
    }
  }
}