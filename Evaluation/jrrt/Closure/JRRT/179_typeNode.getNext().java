package com.google.javascript.jscomp;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class ClosureCodingConvention extends CodingConventions.Proxy  {
  final private static long serialVersionUID = 1L;
  final static DiagnosticType OBJECTLIT_EXPECTED = DiagnosticType.warning("JSC_REFLECT_OBJECTLIT_EXPECTED", "Object literal expected as second argument");
  final private Set<String> indirectlyDeclaredProperties;
  final private Set<String> propertyTestFunctions = ImmutableSet.of("goog.isDef", "goog.isNull", "goog.isDefAndNotNull", "goog.isString", "goog.isNumber", "goog.isBoolean", "goog.isFunction", "goog.isArray", "goog.isObject");
  public ClosureCodingConvention() {
    this(CodingConventions.getDefault());
  }
  public ClosureCodingConvention(CodingConvention wrapped) {
    super(wrapped);
    Set<String> props = Sets.newHashSet("superClass_", "instance_", "getInstance");
    props.addAll(wrapped.getIndirectlyDeclaredProperties());
    indirectlyDeclaredProperties = ImmutableSet.copyOf(props);
  }
  @Override() public Bind describeFunctionBind(Node n, boolean useTypeInfo) {
    Bind result = super.describeFunctionBind(n, useTypeInfo);
    if(result != null) {
      return result;
    }
    if(!n.isCall()) {
      return null;
    }
    Node callTarget = n.getFirstChild();
    String name = callTarget.getQualifiedName();
    if(name != null) {
      if(name.equals("goog.bind") || name.equals("goog$bind")) {
        Node fn = callTarget.getNext();
        if(fn == null) {
          return null;
        }
        Node thisValue = safeNext(fn);
        Node parameters = safeNext(thisValue);
        return new Bind(fn, thisValue, parameters);
      }
      if(name.equals("goog.partial") || name.equals("goog$partial")) {
        Node fn = callTarget.getNext();
        if(fn == null) {
          return null;
        }
        Node thisValue = null;
        Node parameters = safeNext(fn);
        return new Bind(fn, thisValue, parameters);
      }
    }
    return null;
  }
  @Override() public Collection<AssertionFunctionSpec> getAssertionFunctions() {
    return ImmutableList.<AssertionFunctionSpec>of(new AssertionFunctionSpec("goog.asserts.assert"), new AssertionFunctionSpec("goog.asserts.assertNumber", JSTypeNative.NUMBER_TYPE), new AssertionFunctionSpec("goog.asserts.assertString", JSTypeNative.STRING_TYPE), new AssertionFunctionSpec("goog.asserts.assertFunction", JSTypeNative.FUNCTION_INSTANCE_TYPE), new AssertionFunctionSpec("goog.asserts.assertObject", JSTypeNative.OBJECT_TYPE), new AssertionFunctionSpec("goog.asserts.assertArray", JSTypeNative.ARRAY_TYPE), new AssertInstanceofSpec("goog.asserts.assertInstanceof"));
  }
  @Override() public Collection<String> getIndirectlyDeclaredProperties() {
    return indirectlyDeclaredProperties;
  }
  @Override() public List<String> identifyTypeDeclarationCall(Node n) {
    Node callName = n.getFirstChild();
    if("goog.addDependency".equals(callName.getQualifiedName()) && n.getChildCount() >= 3) {
      Node typeArray = callName.getNext().getNext();
      if(typeArray.isArrayLit()) {
        List<String> typeNames = Lists.newArrayList();
        for(com.google.javascript.rhino.Node name = typeArray.getFirstChild(); name != null; name = name.getNext()) {
          if(name.isString()) {
            typeNames.add(name.getString());
          }
        }
        return typeNames;
      }
    }
    return super.identifyTypeDeclarationCall(n);
  }
  private Node safeNext(Node n) {
    if(n != null) {
      return n.getNext();
    }
    return null;
  }
  @Override() public ObjectLiteralCast getObjectLiteralCast(Node callNode) {
    Preconditions.checkArgument(callNode.isCall());
    ObjectLiteralCast proxyCast = super.getObjectLiteralCast(callNode);
    if(proxyCast != null) {
      return proxyCast;
    }
    Node callName = callNode.getFirstChild();
    if(!"goog.reflect.object".equals(callName.getQualifiedName()) || callNode.getChildCount() != 3) {
      return null;
    }
    Node typeNode = callName.getNext();
    if(!typeNode.isQualifiedName()) {
      return null;
    }
    Node var_179 = typeNode.getNext();
    Node objectNode = var_179;
    if(!objectNode.isObjectLit()) {
      return new ObjectLiteralCast(null, null, OBJECTLIT_EXPECTED);
    }
    return new ObjectLiteralCast(typeNode.getQualifiedName(), typeNode.getNext(), null);
  }
  private static String extractClassNameIfGoog(Node node, Node parent, String functionName) {
    String className = null;
    if(NodeUtil.isExprCall(parent)) {
      Node callee = node.getFirstChild();
      if(callee != null && callee.isGetProp()) {
        String qualifiedName = callee.getQualifiedName();
        if(functionName.equals(qualifiedName)) {
          Node target = callee.getNext();
          if(target != null && target.isString()) {
            className = target.getString();
          }
        }
      }
    }
    return className;
  }
  @Override() public String extractClassNameIfProvide(Node node, Node parent) {
    return extractClassNameIfGoog(node, parent, "goog.provide");
  }
  @Override() public String extractClassNameIfRequire(Node node, Node parent) {
    return extractClassNameIfGoog(node, parent, "goog.require");
  }
  @Override() public String getAbstractMethodName() {
    return "goog.abstractMethod";
  }
  @Override() public String getExportPropertyFunction() {
    return "goog.exportProperty";
  }
  @Override() public String getExportSymbolFunction() {
    return "goog.exportSymbol";
  }
  @Override() public String getGlobalObject() {
    return "goog.global";
  }
  @Override() public String getSingletonGetterClassName(Node callNode) {
    Node callArg = callNode.getFirstChild();
    String callName = callArg.getQualifiedName();
    if(!("goog.addSingletonGetter".equals(callName) || "goog$addSingletonGetter".equals(callName)) || callNode.getChildCount() != 2) {
      return super.getSingletonGetterClassName(callNode);
    }
    return callArg.getNext().getQualifiedName();
  }
  @Override() public SubclassRelationship getClassesDefinedByCall(Node callNode) {
    SubclassRelationship relationship = super.getClassesDefinedByCall(callNode);
    if(relationship != null) 
      return relationship;
    Node callName = callNode.getFirstChild();
    SubclassType type = typeofClassDefiningName(callName);
    if(type != null) {
      Node subclass = null;
      Node superclass = callNode.getLastChild();
      boolean isDeprecatedCall = callNode.getChildCount() == 2 && callName.isGetProp();
      if(isDeprecatedCall) {
        subclass = callName.getFirstChild();
      }
      else 
        if(callNode.getChildCount() == 3) {
          subclass = callName.getNext();
        }
        else {
          return null;
        }
      if(type == SubclassType.MIXIN) {
        if(!endsWithPrototype(superclass)) {
          return null;
        }
        if(!isDeprecatedCall) {
          if(!endsWithPrototype(subclass)) {
            return null;
          }
          subclass = subclass.getFirstChild();
        }
        superclass = superclass.getFirstChild();
      }
      if(subclass != null && subclass.isUnscopedQualifiedName() && superclass.isUnscopedQualifiedName()) {
        return new SubclassRelationship(type, subclass, superclass);
      }
    }
    return null;
  }
  private SubclassType typeofClassDefiningName(Node callName) {
    String methodName = null;
    if(callName.isGetProp()) {
      methodName = callName.getLastChild().getString();
    }
    else 
      if(callName.isName()) {
        String name = callName.getString();
        int dollarIndex = name.lastIndexOf('$');
        if(dollarIndex != -1) {
          methodName = name.substring(dollarIndex + 1);
        }
      }
    if(methodName != null) {
      if(methodName.equals("inherits")) {
        return SubclassType.INHERITS;
      }
      else 
        if(methodName.equals("mixin")) {
          return SubclassType.MIXIN;
        }
    }
    return null;
  }
  private boolean endsWithPrototype(Node qualifiedName) {
    return qualifiedName.isGetProp() && qualifiedName.getLastChild().getString().equals("prototype");
  }
  @Override() public boolean isOptionalParameter(Node parameter) {
    return false;
  }
  @Override() public boolean isPrivate(String name) {
    return false;
  }
  @Override() public boolean isPropertyTestFunction(Node call) {
    Preconditions.checkArgument(call.isCall());
    return propertyTestFunctions.contains(call.getFirstChild().getQualifiedName()) || super.isPropertyTestFunction(call);
  }
  @Override() public boolean isSuperClassReference(String propertyName) {
    return "superClass_".equals(propertyName) || super.isSuperClassReference(propertyName);
  }
  @Override() public boolean isVarArgsParameter(Node parameter) {
    return false;
  }
  @Override() public void applySingletonGetter(FunctionType functionType, FunctionType getterType, ObjectType objectType) {
    super.applySingletonGetter(functionType, getterType, objectType);
    functionType.defineDeclaredProperty("getInstance", getterType, functionType.getSource());
    functionType.defineDeclaredProperty("instance_", objectType, functionType.getSource());
  }
  @Override() public void applySubclassRelationship(FunctionType parentCtor, FunctionType childCtor, SubclassType type) {
    super.applySubclassRelationship(parentCtor, childCtor, type);
    if(type == SubclassType.INHERITS) {
      childCtor.defineDeclaredProperty("superClass_", parentCtor.getPrototype(), childCtor.getSource());
      childCtor.getPrototype().defineDeclaredProperty("constructor", childCtor.cloneWithoutArrowType(), childCtor.getSource());
    }
  }
  
  public static class AssertInstanceofSpec extends AssertionFunctionSpec  {
    public AssertInstanceofSpec(String functionName) {
      super(functionName, JSTypeNative.OBJECT_TYPE);
    }
    @Override() public JSType getAssertedType(Node call, JSTypeRegistry registry) {
      if(call.getChildCount() > 2) {
        Node constructor = call.getFirstChild().getNext().getNext();
        if(constructor != null) {
          JSType ownerType = constructor.getJSType();
          if(ownerType != null && ownerType.isFunctionType() && ownerType.isConstructor()) {
            FunctionType functionType = ((FunctionType)ownerType);
            return functionType.getInstanceType();
          }
        }
      }
      return super.getAssertedType(call, registry);
    }
  }
}