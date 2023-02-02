package com.google.javascript.jscomp;
import static com.google.javascript.jscomp.TypeCheck.ENUM_NOT_CONSTANT;
import static com.google.javascript.jscomp.TypeCheck.MULTIPLE_VAR_DEF;
import static com.google.javascript.rhino.jstype.JSTypeNative.ARRAY_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.BOOLEAN_OBJECT_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.BOOLEAN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.DATE_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.ERROR_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.EVAL_ERROR_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.FUNCTION_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.FUNCTION_INSTANCE_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.GLOBAL_THIS;
import static com.google.javascript.rhino.jstype.JSTypeNative.NO_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NULL_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_OBJECT_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.OBJECT_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.RANGE_ERROR_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.REFERENCE_ERROR_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.REGEXP_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.REGEXP_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.STRING_OBJECT_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.STRING_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.SYNTAX_ERROR_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.TYPE_ERROR_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.U2U_CONSTRUCTOR_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.URI_ERROR_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.VOID_TYPE;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.javascript.jscomp.CodingConvention.DelegateRelationship;
import com.google.javascript.jscomp.CodingConvention.ObjectLiteralCast;
import com.google.javascript.jscomp.CodingConvention.SubclassRelationship;
import com.google.javascript.jscomp.CodingConvention.SubclassType;
import com.google.javascript.jscomp.FunctionTypeBuilder.AstFunctionContents;
import com.google.javascript.jscomp.NodeTraversal.AbstractScopedCallback;
import com.google.javascript.jscomp.NodeTraversal.AbstractShallowStatementCallback;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.EnumType;
import com.google.javascript.rhino.jstype.FunctionParamBuilder;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.Property;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

final class TypedScopeCreator implements ScopeCreator  {
  final static String DELEGATE_PROXY_SUFFIX = ObjectType.createDelegateSuffix("Proxy");
  final static DiagnosticType MALFORMED_TYPEDEF = DiagnosticType.warning("JSC_MALFORMED_TYPEDEF", "Typedef for {0} does not have any type information");
  final static DiagnosticType ENUM_INITIALIZER = DiagnosticType.warning("JSC_ENUM_INITIALIZER_NOT_ENUM", "enum initializer must be an object literal or an enum");
  final static DiagnosticType CTOR_INITIALIZER = DiagnosticType.warning("JSC_CTOR_INITIALIZER_NOT_CTOR", "Constructor {0} must be initialized at declaration");
  final static DiagnosticType IFACE_INITIALIZER = DiagnosticType.warning("JSC_IFACE_INITIALIZER_NOT_IFACE", "Interface {0} must be initialized at declaration");
  final static DiagnosticType CONSTRUCTOR_EXPECTED = DiagnosticType.warning("JSC_REFLECT_CONSTRUCTOR_EXPECTED", "Constructor expected as first argument");
  final static DiagnosticType UNKNOWN_LENDS = DiagnosticType.warning("JSC_UNKNOWN_LENDS", "Variable {0} not declared before @lends annotation.");
  final static DiagnosticType LENDS_ON_NON_OBJECT = DiagnosticType.warning("JSC_LENDS_ON_NON_OBJECT", "May only lend properties to object types. {0} has type {1}.");
  final private AbstractCompiler compiler;
  final private ErrorReporter typeParsingErrorReporter;
  final private TypeValidator validator;
  final private CodingConvention codingConvention;
  final private JSTypeRegistry typeRegistry;
  final private List<ObjectType> delegateProxyPrototypes = Lists.newArrayList();
  final private Map<String, String> delegateCallingConventions = Maps.newHashMap();
  final private Map<Node, AstFunctionContents> functionAnalysisResults = Maps.newHashMap();
  final private ObjectType unknownType;
  TypedScopeCreator(AbstractCompiler compiler) {
    this(compiler, compiler.getCodingConvention());
  }
  TypedScopeCreator(AbstractCompiler compiler, CodingConvention codingConvention) {
    super();
    this.compiler = compiler;
    this.validator = compiler.getTypeValidator();
    this.codingConvention = codingConvention;
    this.typeRegistry = compiler.getTypeRegistry();
    this.typeParsingErrorReporter = typeRegistry.getErrorReporter();
    this.unknownType = typeRegistry.getNativeObjectType(UNKNOWN_TYPE);
  }
  private AstFunctionContents getFunctionAnalysisResults(@Nullable() Node n) {
    if(n == null) {
      return null;
    }
    return functionAnalysisResults.get(n);
  }
  private JSType getNativeType(JSTypeNative nativeType) {
    return typeRegistry.getNativeType(nativeType);
  }
  @VisibleForTesting() Scope createInitialScope(Node root) {
    NodeTraversal.traverse(compiler, root, new DiscoverEnumsAndTypedefs(typeRegistry));
    Scope s = Scope.createGlobalScope(root);
    declareNativeFunctionType(s, ARRAY_FUNCTION_TYPE);
    declareNativeFunctionType(s, BOOLEAN_OBJECT_FUNCTION_TYPE);
    declareNativeFunctionType(s, DATE_FUNCTION_TYPE);
    declareNativeFunctionType(s, ERROR_FUNCTION_TYPE);
    declareNativeFunctionType(s, EVAL_ERROR_FUNCTION_TYPE);
    declareNativeFunctionType(s, FUNCTION_FUNCTION_TYPE);
    declareNativeFunctionType(s, NUMBER_OBJECT_FUNCTION_TYPE);
    declareNativeFunctionType(s, OBJECT_FUNCTION_TYPE);
    declareNativeFunctionType(s, RANGE_ERROR_FUNCTION_TYPE);
    declareNativeFunctionType(s, REFERENCE_ERROR_FUNCTION_TYPE);
    declareNativeFunctionType(s, REGEXP_FUNCTION_TYPE);
    declareNativeFunctionType(s, STRING_OBJECT_FUNCTION_TYPE);
    declareNativeFunctionType(s, SYNTAX_ERROR_FUNCTION_TYPE);
    declareNativeFunctionType(s, TYPE_ERROR_FUNCTION_TYPE);
    declareNativeFunctionType(s, URI_ERROR_FUNCTION_TYPE);
    declareNativeValueType(s, "undefined", VOID_TYPE);
    declareNativeValueType(s, "ActiveXObject", FUNCTION_INSTANCE_TYPE);
    return s;
  }
  @Override() public Scope createScope(Node root, Scope parent) {
    Scope newScope = null;
    AbstractScopeBuilder scopeBuilder = null;
    if(parent == null) {
      JSType globalThis = typeRegistry.getNativeObjectType(JSTypeNative.GLOBAL_THIS);
      root.setJSType(globalThis);
      root.getFirstChild().setJSType(globalThis);
      root.getLastChild().setJSType(globalThis);
      (new FirstOrderFunctionAnalyzer(compiler, functionAnalysisResults)).process(root.getFirstChild(), root.getLastChild());
      newScope = createInitialScope(root);
      GlobalScopeBuilder globalScopeBuilder = new GlobalScopeBuilder(newScope);
      scopeBuilder = globalScopeBuilder;
      NodeTraversal.traverse(compiler, root, scopeBuilder);
    }
    else {
      newScope = new Scope(parent, root);
      LocalScopeBuilder localScopeBuilder = new LocalScopeBuilder(newScope);
      scopeBuilder = localScopeBuilder;
      localScopeBuilder.build();
    }
    scopeBuilder.resolveStubDeclarations();
    scopeBuilder.resolveTypes();
    for (Node functionNode : scopeBuilder.nonExternFunctions) {
      JSType type = functionNode.getJSType();
      if(type != null && type.isFunctionType()) {
        FunctionType fnType = type.toMaybeFunctionType();
        JSType fnThisType = fnType.getTypeOfThis();
        if(!fnThisType.isUnknownType()) {
          NodeTraversal.traverse(compiler, functionNode.getLastChild(), scopeBuilder.new CollectProperties(fnThisType));
        }
      }
    }
    if(parent == null) {
      codingConvention.defineDelegateProxyPrototypeProperties(typeRegistry, newScope, delegateProxyPrototypes, delegateCallingConventions);
    }
    return newScope;
  }
  private void declareNativeFunctionType(Scope scope, JSTypeNative tId) {
    FunctionType t = typeRegistry.getNativeFunctionType(tId);
    declareNativeType(scope, t.getInstanceType().getReferenceName(), t);
    declareNativeType(scope, t.getPrototype().getReferenceName(), t.getPrototype());
  }
  private void declareNativeType(Scope scope, String name, JSType t) {
    scope.declare(name, null, t, null, false);
  }
  private void declareNativeValueType(Scope scope, String name, JSTypeNative tId) {
    declareNativeType(scope, name, typeRegistry.getNativeType(tId));
  }
  void patchGlobalScope(Scope globalScope, Node scriptRoot) {
    Preconditions.checkState(scriptRoot.isScript());
    Preconditions.checkNotNull(globalScope);
    Preconditions.checkState(globalScope.isGlobal());
    String scriptName = NodeUtil.getSourceName(scriptRoot);
    Preconditions.checkNotNull(scriptName);
    for (Node node : ImmutableList.copyOf(functionAnalysisResults.keySet())) {
      if(scriptName.equals(NodeUtil.getSourceName(node))) {
        functionAnalysisResults.remove(node);
      }
    }
    (new FirstOrderFunctionAnalyzer(compiler, functionAnalysisResults)).process(null, scriptRoot);
    Iterator<Var> varIter = globalScope.getVars();
    List<Var> varsToRemove = Lists.newArrayList();
    while(varIter.hasNext()){
      Var oldVar = varIter.next();
      if(scriptName.equals(oldVar.getInputName())) {
        varsToRemove.add(oldVar);
      }
    }
    for (Var var : varsToRemove) {
      globalScope.undeclare(var);
      globalScope.getTypeOfThis().toObjectType().removeProperty(var.getName());
    }
    GlobalScopeBuilder scopeBuilder = new GlobalScopeBuilder(globalScope);
    NodeTraversal.traverse(compiler, scriptRoot, scopeBuilder);
  }
  
  abstract private class AbstractScopeBuilder implements NodeTraversal.Callback  {
    final Scope scope;
    final private List<DeferredSetType> deferredSetTypes = Lists.newArrayList();
    final private List<Node> nonExternFunctions = Lists.newArrayList();
    private List<Node> lentObjectLiterals = null;
    final private List<StubDeclaration> stubDeclarations = Lists.newArrayList();
    private String sourceName = null;
    private InputId inputId;
    private AbstractScopeBuilder(Scope scope) {
      super();
      this.scope = scope;
    }
    private EnumType createEnumTypeFromNodes(Node rValue, String name, JSDocInfo info, Node lValueNode) {
      Preconditions.checkNotNull(info);
      Preconditions.checkState(info.hasEnumParameterType());
      EnumType enumType = null;
      if(rValue != null && rValue.isQualifiedName()) {
        Var var = scope.getVar(rValue.getQualifiedName());
        if(var != null && var.getType() instanceof EnumType) {
          enumType = (EnumType)var.getType();
        }
      }
      if(enumType == null) {
        JSType elementsType = info.getEnumParameterType().evaluate(scope, typeRegistry);
        enumType = typeRegistry.createEnumType(name, rValue, elementsType);
        if(rValue != null && rValue.isObjectLit()) {
          Node key = rValue.getFirstChild();
          while(key != null){
            String keyName = NodeUtil.getStringValue(key);
            if(keyName == null) {
              compiler.report(JSError.make(sourceName, key, ENUM_NOT_CONSTANT, keyName));
            }
            else 
              if(!codingConvention.isValidEnumKey(keyName)) {
                compiler.report(JSError.make(sourceName, key, ENUM_NOT_CONSTANT, keyName));
              }
              else {
                enumType.defineElement(keyName, key);
              }
            key = key.getNext();
          }
        }
      }
      if(name != null && scope.isGlobal()) {
        typeRegistry.declareType(name, enumType.getElementsType());
      }
      return enumType;
    }
    private FunctionType createFunctionTypeFromNodes(@Nullable() Node rValue, @Nullable() String name, @Nullable() JSDocInfo info, @Nullable() Node lvalueNode) {
      FunctionType functionType = null;
      if(rValue != null && rValue.isQualifiedName() && scope.isGlobal()) {
        Var var = scope.getVar(rValue.getQualifiedName());
        if(var != null && var.getType() != null && var.getType().isFunctionType()) {
          FunctionType aliasedType = var.getType().toMaybeFunctionType();
          if((aliasedType.isConstructor() || aliasedType.isInterface()) && !aliasedType.isNativeObjectType()) {
            functionType = aliasedType;
            if(name != null && scope.isGlobal()) {
              typeRegistry.declareType(name, functionType.getInstanceType());
            }
          }
        }
      }
      if(functionType == null) {
        Node errorRoot = rValue == null ? lvalueNode : rValue;
        boolean isFnLiteral = rValue != null && rValue.isFunction();
        Node fnRoot = isFnLiteral ? rValue : null;
        Node parametersNode = isFnLiteral ? rValue.getFirstChild().getNext() : null;
        Node fnBlock = isFnLiteral ? parametersNode.getNext() : null;
        if(info != null && info.hasType()) {
          JSType type = info.getType().evaluate(scope, typeRegistry);
          type = type.restrictByNotNullOrUndefined();
          if(type.isFunctionType()) {
            functionType = type.toMaybeFunctionType();
            functionType.setJSDocInfo(info);
          }
        }
        if(functionType == null) {
          Node ownerNode = NodeUtil.getBestLValueOwner(lvalueNode);
          String ownerName = NodeUtil.getBestLValueName(ownerNode);
          Var ownerVar = null;
          String propName = null;
          ObjectType ownerType = null;
          if(ownerName != null) {
            ownerVar = scope.getVar(ownerName);
            if(ownerVar != null) {
              ownerType = ObjectType.cast(ownerVar.getType());
            }
            if(name != null) {
              propName = name.substring(ownerName.length() + 1);
            }
          }
          FunctionType overriddenType = null;
          if(ownerType != null && propName != null) {
            overriddenType = findOverriddenFunction(ownerType, propName);
          }
          FunctionTypeBuilder builder = new FunctionTypeBuilder(name, compiler, errorRoot, sourceName, scope).setContents(getFunctionAnalysisResults(fnRoot)).inferFromOverriddenFunction(overriddenType, parametersNode).inferTemplateTypeName(info).inferReturnType(info).inferInheritance(info);
          boolean searchedForThisType = false;
          if(ownerType != null && ownerType.isFunctionPrototypeType() && ownerType.getOwnerFunction().hasInstanceType()) {
            builder.inferThisType(info, ownerType.getOwnerFunction().getInstanceType());
            searchedForThisType = true;
          }
          else 
            if(ownerNode != null && ownerNode.isThis()) {
              JSType injectedThisType = ownerNode.getJSType();
              builder.inferThisType(info, injectedThisType == null ? scope.getTypeOfThis() : injectedThisType);
              searchedForThisType = true;
            }
          if(!searchedForThisType) {
            builder.inferThisType(info);
          }
          functionType = builder.inferParameterTypes(parametersNode, info).buildAndRegister();
        }
      }
      return functionType;
    }
    private FunctionType findOverriddenFunction(ObjectType ownerType, String propName) {
      JSType propType = ownerType.getPropertyType(propName);
      if(propType != null && propType.isFunctionType()) {
        return propType.toMaybeFunctionType();
      }
      else {
        for (ObjectType iface : ownerType.getCtorImplementedInterfaces()) {
          propType = iface.getPropertyType(propName);
          if(propType != null && propType.isFunctionType()) {
            return propType.toMaybeFunctionType();
          }
        }
      }
      return null;
    }
    private FunctionType getFunctionType(@Nullable() Var v) {
      JSType t = v == null ? null : v.getType();
      ObjectType o = t == null ? null : t.dereference();
      return JSType.toMaybeFunctionType(o);
    }
    private JSType getDeclaredType(String sourceName, JSDocInfo info, Node lValue, @Nullable() Node rValue) {
      if(info != null && info.hasType()) {
        return getDeclaredTypeInAnnotation(sourceName, lValue, info);
      }
      else 
        if(rValue != null && rValue.isFunction() && shouldUseFunctionLiteralType(JSType.toMaybeFunctionType(rValue.getJSType()), info, lValue)) {
          return rValue.getJSType();
        }
        else 
          if(info != null) {
            if(info.hasEnumParameterType()) {
              if(rValue != null && rValue.isObjectLit()) {
                return rValue.getJSType();
              }
              else {
                return createEnumTypeFromNodes(rValue, lValue.getQualifiedName(), info, lValue);
              }
            }
            else 
              if(info.isConstructor() || info.isInterface()) {
                return createFunctionTypeFromNodes(rValue, lValue.getQualifiedName(), info, lValue);
              }
              else {
                if(info.isConstant()) {
                  JSType knownType = null;
                  if(rValue != null) {
                    JSDocInfo rValueInfo = rValue.getJSDocInfo();
                    if(rValueInfo != null && rValueInfo.hasType()) {
                      return rValueInfo.getType().evaluate(scope, typeRegistry);
                    }
                    else 
                      if(rValue.getJSType() != null && !rValue.getJSType().isUnknownType()) {
                        return rValue.getJSType();
                      }
                      else 
                        if(rValue.isOr()) {
                          Node firstClause = rValue.getFirstChild();
                          Node secondClause = firstClause.getNext();
                          boolean namesMatch = firstClause.isName() && lValue.isName() && firstClause.getString().equals(lValue.getString());
                          if(namesMatch && secondClause.getJSType() != null && !secondClause.getJSType().isUnknownType()) {
                            return secondClause.getJSType();
                          }
                        }
                  }
                }
              }
          }
      return getDeclaredTypeInAnnotation(sourceName, lValue, info);
    }
    private JSType getDeclaredTypeInAnnotation(String sourceName, Node node, JSDocInfo info) {
      JSType jsType = null;
      Node objNode = node.isGetProp() ? node.getFirstChild() : NodeUtil.isObjectLitKey(node, node.getParent()) ? node.getParent() : null;
      if(info != null) {
        if(info.hasType()) {
          jsType = info.getType().evaluate(scope, typeRegistry);
        }
        else 
          if(FunctionTypeBuilder.isFunctionTypeDeclaration(info)) {
            String fnName = node.getQualifiedName();
            jsType = createFunctionTypeFromNodes(null, fnName, info, node);
          }
      }
      return jsType;
    }
    private ObjectType getObjectSlot(String slotName) {
      Var ownerVar = scope.getVar(slotName);
      if(ownerVar != null) {
        JSType ownerVarType = ownerVar.getType();
        return ObjectType.cast(ownerVarType == null ? null : ownerVarType.restrictByNotNullOrUndefined());
      }
      return null;
    }
    private Scope getQnameRootScope(Node n) {
      Node root = NodeUtil.getRootOfQualifiedName(n);
      if(root.isName()) {
        Var var = scope.getVar(root.getString());
        if(var != null) {
          return var.getScope();
        }
      }
      return null;
    }
    private boolean isQnameRootedInGlobalScope(Node n) {
      Scope scope = getQnameRootScope(n);
      return scope != null && scope.isGlobal();
    }
    private boolean isQualifiedNameInferred(String qName, Node n, JSDocInfo info, Node rhsValue, JSType valueType) {
      if(valueType == null) {
        return true;
      }
      boolean inferred = true;
      if(info != null) {
        inferred = !(info.hasType() || info.hasEnumParameterType() || (info.isConstant() && valueType != null && !valueType.isUnknownType()) || FunctionTypeBuilder.isFunctionTypeDeclaration(info));
      }
      if(inferred && rhsValue != null && rhsValue.isFunction()) {
        if(info != null) {
          return false;
        }
        else 
          if(!scope.isDeclared(qName, false) && n.isUnscopedQualifiedName()) {
            for(com.google.javascript.rhino.Node current = n.getParent(); !(current.isScript() || current.isFunction()); current = current.getParent()) {
              if(NodeUtil.isControlStructure(current)) {
                return true;
              }
            }
            AstFunctionContents contents = getFunctionAnalysisResults(scope.getRootNode());
            if(contents == null || !contents.getEscapedQualifiedNames().contains(qName)) {
              return false;
            }
          }
      }
      return inferred;
    }
    @Override() final public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      inputId = t.getInputId();
      if(n.isFunction() || n.isScript()) {
        Preconditions.checkNotNull(inputId);
        sourceName = NodeUtil.getSourceName(n);
      }
      boolean descend = parent == null || !parent.isFunction() || n == parent.getFirstChild() || parent == scope.getRootNode();
      if(descend) {
        if(NodeUtil.isStatementParent(n)) {
          for(com.google.javascript.rhino.Node child = n.getFirstChild(); child != null; child = child.getNext()) {
            if(NodeUtil.isHoistedFunctionDeclaration(child)) {
              defineFunctionLiteral(child);
            }
          }
        }
      }
      return descend;
    }
    private boolean shouldUseFunctionLiteralType(FunctionType type, JSDocInfo info, Node lValue) {
      if(info != null) {
        return true;
      }
      if(lValue != null && NodeUtil.isObjectLitKey(lValue, lValue.getParent())) {
        return false;
      }
      return scope.isGlobal() || !type.isReturnTypeInferred();
    }
    private void applyDelegateRelationship(DelegateRelationship delegateRelationship) {
      ObjectType delegatorObject = ObjectType.cast(typeRegistry.getType(delegateRelationship.delegator));
      ObjectType delegateBaseObject = ObjectType.cast(typeRegistry.getType(delegateRelationship.delegateBase));
      ObjectType delegateSuperObject = ObjectType.cast(typeRegistry.getType(codingConvention.getDelegateSuperclassName()));
      if(delegatorObject != null && delegateBaseObject != null && delegateSuperObject != null) {
        FunctionType delegatorCtor = delegatorObject.getConstructor();
        FunctionType delegateBaseCtor = delegateBaseObject.getConstructor();
        FunctionType delegateSuperCtor = delegateSuperObject.getConstructor();
        if(delegatorCtor != null && delegateBaseCtor != null && delegateSuperCtor != null) {
          FunctionParamBuilder functionParamBuilder = new FunctionParamBuilder(typeRegistry);
          functionParamBuilder.addRequiredParams(getNativeType(U2U_CONSTRUCTOR_TYPE));
          FunctionType findDelegate = typeRegistry.createFunctionType(typeRegistry.createDefaultObjectUnion(delegateBaseObject), functionParamBuilder.build());
          FunctionType delegateProxy = typeRegistry.createConstructorType(delegateBaseObject.getReferenceName() + DELEGATE_PROXY_SUFFIX, null, null, null, null);
          delegateProxy.setPrototypeBasedOn(delegateBaseObject);
          codingConvention.applyDelegateRelationship(delegateSuperObject, delegateBaseObject, delegatorObject, delegateProxy, findDelegate);
          delegateProxyPrototypes.add(delegateProxy.getPrototype());
        }
      }
    }
    void assertDefinitionNode(Node n, int type) {
      Preconditions.checkState(sourceName != null);
      Preconditions.checkState(n.getType() == type);
    }
    private void attachLiteralTypes(NodeTraversal t, Node n) {
      switch (n.getType()){
        case Token.NULL:
        n.setJSType(getNativeType(NULL_TYPE));
        break ;
        case Token.VOID:
        n.setJSType(getNativeType(VOID_TYPE));
        break ;
        case Token.STRING:
        n.setJSType(getNativeType(STRING_TYPE));
        break ;
        case Token.NUMBER:
        n.setJSType(getNativeType(NUMBER_TYPE));
        break ;
        case Token.TRUE:
        case Token.FALSE:
        n.setJSType(getNativeType(BOOLEAN_TYPE));
        break ;
        case Token.REGEXP:
        n.setJSType(getNativeType(REGEXP_TYPE));
        break ;
        case Token.OBJECTLIT:
        JSDocInfo info = n.getJSDocInfo();
        if(info != null && info.getLendsName() != null) {
          if(lentObjectLiterals == null) {
            lentObjectLiterals = Lists.newArrayList();
          }
          lentObjectLiterals.add(n);
        }
        else {
          defineObjectLiteral(n);
        }
        break ;
      }
    }
    private void checkForCallingConventionDefiningCalls(Node n, Map<String, String> delegateCallingConventions) {
      codingConvention.checkForCallingConventionDefiningCalls(n, delegateCallingConventions);
    }
    private void checkForClassDefiningCalls(NodeTraversal t, Node n, Node parent) {
      SubclassRelationship relationship = codingConvention.getClassesDefinedByCall(n);
      if(relationship != null) {
        FunctionType superCtor = getFunctionType(scope.getVar(relationship.superclassName));
        FunctionType subCtor = getFunctionType(scope.getVar(relationship.subclassName));
        if(superCtor != null && superCtor.isConstructor() && subCtor != null && subCtor.isConstructor()) {
          ObjectType superClass = superCtor.getInstanceType();
          ObjectType subClass = subCtor.getInstanceType();
          superCtor = superClass.getConstructor();
          subCtor = subClass.getConstructor();
          if(relationship.type == SubclassType.INHERITS && !superClass.isEmptyType() && !subClass.isEmptyType()) {
            validator.expectSuperType(t, n, superClass, subClass);
          }
          if(superCtor != null && subCtor != null) {
            codingConvention.applySubclassRelationship(superCtor, subCtor, relationship.type);
          }
        }
      }
      String singletonGetterClassName = codingConvention.getSingletonGetterClassName(n);
      if(singletonGetterClassName != null) {
        ObjectType objectType = ObjectType.cast(typeRegistry.getType(singletonGetterClassName));
        if(objectType != null) {
          FunctionType functionType = objectType.getConstructor();
          if(functionType != null) {
            FunctionType getterType = typeRegistry.createFunctionType(objectType);
            codingConvention.applySingletonGetter(functionType, getterType, objectType);
          }
        }
      }
      DelegateRelationship delegateRelationship = codingConvention.getDelegateRelationship(n);
      if(delegateRelationship != null) {
        applyDelegateRelationship(delegateRelationship);
      }
      ObjectLiteralCast objectLiteralCast = codingConvention.getObjectLiteralCast(n);
      if(objectLiteralCast != null) {
        if(objectLiteralCast.diagnosticType == null) {
          ObjectType type = ObjectType.cast(typeRegistry.getType(objectLiteralCast.typeName));
          if(type != null && type.getConstructor() != null) {
            setDeferredType(objectLiteralCast.objectNode, type);
          }
          else {
            compiler.report(JSError.make(t.getSourceName(), n, CONSTRUCTOR_EXPECTED));
          }
        }
        else {
          compiler.report(JSError.make(t.getSourceName(), n, objectLiteralCast.diagnosticType));
        }
      }
    }
    void defineCatch(Node n) {
      assertDefinitionNode(n, Token.CATCH);
      Node catchName = n.getFirstChild();
      defineSlot(catchName, n, getDeclaredType(sourceName, catchName.getJSDocInfo(), catchName, null));
    }
    void defineFunctionLiteral(Node n) {
      assertDefinitionNode(n, Token.FUNCTION);
      Node lValue = NodeUtil.getBestLValue(n);
      JSDocInfo info = NodeUtil.getBestJSDocInfo(n);
      String functionName = NodeUtil.getBestLValueName(lValue);
      FunctionType functionType = createFunctionTypeFromNodes(n, functionName, info, lValue);
      setDeferredType(n, functionType);
      if(NodeUtil.isFunctionDeclaration(n)) {
        defineSlot(n.getFirstChild(), n, functionType);
      }
    }
    private void defineName(Node name, Node var, JSDocInfo info) {
      Node value = name.getFirstChild();
      JSType type = getDeclaredType(sourceName, info, name, value);
      if(type == null) {
        type = name.isFromExterns() ? unknownType : null;
      }
      defineSlot(name, var, type);
    }
    private void defineObjectLiteral(Node objectLit) {
      JSType type = null;
      JSDocInfo info = objectLit.getJSDocInfo();
      if(info != null && info.getLendsName() != null) {
        String lendsName = info.getLendsName();
        Var lendsVar = scope.getVar(lendsName);
        if(lendsVar == null) {
          compiler.report(JSError.make(sourceName, objectLit, UNKNOWN_LENDS, lendsName));
        }
        else {
          type = lendsVar.getType();
          if(type == null) {
            type = unknownType;
          }
          if(!type.isSubtype(typeRegistry.getNativeType(OBJECT_TYPE))) {
            compiler.report(JSError.make(sourceName, objectLit, LENDS_ON_NON_OBJECT, lendsName, type.toString()));
            type = null;
          }
          else {
            objectLit.setJSType(type);
          }
        }
      }
      info = NodeUtil.getBestJSDocInfo(objectLit);
      Node lValue = NodeUtil.getBestLValue(objectLit);
      String lValueName = NodeUtil.getBestLValueName(lValue);
      boolean createdEnumType = false;
      if(info != null && info.hasEnumParameterType()) {
        type = createEnumTypeFromNodes(objectLit, lValueName, info, lValue);
        createdEnumType = true;
      }
      if(type == null) {
        type = typeRegistry.createAnonymousObjectType(info);
      }
      setDeferredType(objectLit, type);
      processObjectLitProperties(objectLit, ObjectType.cast(objectLit.getJSType()), !createdEnumType);
    }
    private void defineSlot(Node name, Node parent, JSType type) {
      defineSlot(name, parent, type, type == null);
    }
    void defineSlot(Node n, Node parent, JSType type, boolean inferred) {
      Preconditions.checkArgument(inferred || type != null);
      if(n.isName()) {
        Preconditions.checkArgument(parent.isFunction() || parent.isVar() || parent.isParamList() || parent.isCatch());
      }
      else {
        Preconditions.checkArgument(n.isGetProp() && (parent.isAssign() || parent.isExprResult()));
      }
      defineSlot(n, parent, n.getQualifiedName(), type, inferred);
    }
    void defineSlot(Node n, Node parent, String variableName, JSType type, boolean inferred) {
      Preconditions.checkArgument(!variableName.isEmpty());
      boolean isGlobalVar = n.isName() && scope.isGlobal();
      boolean shouldDeclareOnGlobalThis = isGlobalVar && (parent.isVar() || parent.isFunction());
      Scope scopeToDeclareIn = scope;
      if(n.isGetProp() && !scope.isGlobal() && isQnameRootedInGlobalScope(n)) {
        Scope globalScope = scope.getGlobalScope();
        if(!globalScope.isDeclared(variableName, false)) {
          scopeToDeclareIn = scope.getGlobalScope();
        }
      }
      boolean isExtern = n.isFromExterns();
      Var newVar = null;
      CompilerInput input = compiler.getInput(inputId);
      if(scopeToDeclareIn.isDeclared(variableName, false)) {
        Var oldVar = scopeToDeclareIn.getVar(variableName);
        newVar = validator.expectUndeclaredVariable(sourceName, input, n, parent, oldVar, variableName, type);
      }
      else {
        if(type != null) {
          setDeferredType(n, type);
        }
        newVar = scopeToDeclareIn.declare(variableName, n, type, input, inferred);
        if(type instanceof EnumType) {
          Node initialValue = newVar.getInitialValue();
          boolean isValidValue = initialValue != null && (initialValue.isObjectLit() || initialValue.isQualifiedName());
          if(!isValidValue) {
            compiler.report(JSError.make(sourceName, n, ENUM_INITIALIZER));
          }
        }
      }
      FunctionType fnType = JSType.toMaybeFunctionType(type);
      if(fnType != null && !type.isEmptyType()) {
        if((fnType.isConstructor() || fnType.isInterface()) && variableName.equals(fnType.getReferenceName())) {
          finishConstructorDefinition(n, variableName, fnType, scopeToDeclareIn, input, newVar);
        }
      }
      if(shouldDeclareOnGlobalThis) {
        ObjectType globalThis = typeRegistry.getNativeObjectType(GLOBAL_THIS);
        if(inferred) {
          globalThis.defineInferredProperty(variableName, type == null ? getNativeType(JSTypeNative.NO_TYPE) : type, n);
        }
        else {
          globalThis.defineDeclaredProperty(variableName, type, n);
        }
      }
      if(isGlobalVar && "Window".equals(variableName) && type != null && type.isFunctionType() && type.isConstructor()) {
        FunctionType globalThisCtor = typeRegistry.getNativeObjectType(GLOBAL_THIS).getConstructor();
        globalThisCtor.getInstanceType().clearCachedValues();
        globalThisCtor.getPrototype().clearCachedValues();
        globalThisCtor.setPrototypeBasedOn((type.toMaybeFunctionType()).getInstanceType());
      }
    }
    void defineVar(Node n) {
      assertDefinitionNode(n, Token.VAR);
      JSDocInfo info = n.getJSDocInfo();
      if(n.hasMoreThanOneChild()) {
        if(info != null) {
          compiler.report(JSError.make(sourceName, n, MULTIPLE_VAR_DEF));
        }
        for (Node name : n.children()) {
          defineName(name, n, name.getJSDocInfo());
        }
      }
      else {
        Node name = n.getFirstChild();
        defineName(name, n, (info != null) ? info : name.getJSDocInfo());
      }
    }
    private void finishConstructorDefinition(Node n, String variableName, FunctionType fnType, Scope scopeToDeclareIn, CompilerInput input, Var newVar) {
      FunctionType superClassCtor = fnType.getSuperClassConstructor();
      Property prototypeSlot = fnType.getSlot("prototype");
      prototypeSlot.setNode(n);
      String prototypeName = variableName + ".prototype";
      Var prototypeVar = scopeToDeclareIn.getVar(prototypeName);
      if(prototypeVar != null && prototypeVar.scope == scopeToDeclareIn) {
        scopeToDeclareIn.undeclare(prototypeVar);
      }
      scopeToDeclareIn.declare(prototypeName, n, prototypeSlot.getType(), input, superClassCtor == null || superClassCtor.getInstanceType().isEquivalentTo(getNativeType(OBJECT_TYPE)));
      if(newVar.getInitialValue() == null && !n.isFromExterns()) {
        compiler.report(JSError.make(sourceName, n, fnType.isConstructor() ? CTOR_INITIALIZER : IFACE_INITIALIZER, variableName));
      }
    }
    void maybeDeclareQualifiedName(NodeTraversal t, JSDocInfo info, Node n, Node parent, Node rhsValue) {
      Node ownerNode = n.getFirstChild();
      String ownerName = ownerNode.getQualifiedName();
      String qName = n.getQualifiedName();
      String propName = n.getLastChild().getString();
      Preconditions.checkArgument(qName != null && ownerName != null);
      JSType valueType = getDeclaredType(t.getSourceName(), info, n, rhsValue);
      if(valueType == null && rhsValue != null) {
        valueType = rhsValue.getJSType();
      }
      if("prototype".equals(propName)) {
        Var qVar = scope.getVar(qName);
        if(qVar != null) {
          ObjectType qVarType = ObjectType.cast(qVar.getType());
          if(qVarType != null && rhsValue != null && rhsValue.isObjectLit()) {
            typeRegistry.resetImplicitPrototype(rhsValue.getJSType(), qVarType.getImplicitPrototype());
          }
          else 
            if(!qVar.isTypeInferred()) {
              return ;
            }
          if(qVar.getScope() == scope) {
            scope.undeclare(qVar);
          }
        }
      }
      if(valueType == null) {
        if(parent.isExprResult()) {
          stubDeclarations.add(new StubDeclaration(n, t.getInput() != null && t.getInput().isExtern(), ownerName));
        }
        return ;
      }
      boolean inferred = isQualifiedNameInferred(qName, n, info, rhsValue, valueType);
      if(!inferred) {
        ObjectType ownerType = getObjectSlot(ownerName);
        if(ownerType != null) {
          boolean isExtern = t.getInput() != null && t.getInput().isExtern();
          if((!ownerType.hasOwnProperty(propName) || ownerType.isPropertyTypeInferred(propName)) && ((isExtern && !ownerType.isNativeObjectType()) || !ownerType.isInstanceType())) {
            ownerType.defineDeclaredProperty(propName, valueType, n);
          }
        }
        defineSlot(n, parent, valueType, inferred);
      }
      else 
        if(rhsValue != null && rhsValue.isTrue()) {
          FunctionType ownerType = JSType.toMaybeFunctionType(getObjectSlot(ownerName));
          if(ownerType != null) {
            JSType ownerTypeOfThis = ownerType.getTypeOfThis();
            String delegateName = codingConvention.getDelegateSuperclassName();
            JSType delegateType = delegateName == null ? null : typeRegistry.getType(delegateName);
            if(delegateType != null && ownerTypeOfThis.isSubtype(delegateType)) {
              defineSlot(n, parent, getNativeType(BOOLEAN_TYPE), true);
            }
          }
        }
    }
    void processObjectLitProperties(Node objLit, ObjectType objLitType, boolean declareOnOwner) {
      for(com.google.javascript.rhino.Node keyNode = objLit.getFirstChild(); keyNode != null; keyNode = keyNode.getNext()) {
        Node value = keyNode.getFirstChild();
        String memberName = NodeUtil.getObjectLitKeyName(keyNode);
        JSDocInfo info = keyNode.getJSDocInfo();
        JSType valueType = getDeclaredType(keyNode.getSourceFileName(), info, keyNode, value);
        JSType keyType = objLitType.isEnumType() ? objLitType.toMaybeEnumType().getElementsType() : NodeUtil.getObjectLitKeyTypeFromValueType(keyNode, valueType);
        String qualifiedName = NodeUtil.getBestLValueName(keyNode);
        if(qualifiedName != null) {
          boolean inferred = keyType == null;
          defineSlot(keyNode, objLit, qualifiedName, keyType, inferred);
        }
        else 
          if(keyType != null) {
            setDeferredType(keyNode, keyType);
          }
        if(keyType != null && objLitType != null && declareOnOwner) {
          boolean isExtern = keyNode.isFromExterns();
          objLitType.defineDeclaredProperty(memberName, keyType, keyNode);
        }
      }
    }
    void resolveStubDeclarations() {
      for (StubDeclaration stub : stubDeclarations) {
        Node n = stub.node;
        Node parent = n.getParent();
        String qName = n.getQualifiedName();
        String propName = n.getLastChild().getString();
        String ownerName = stub.ownerName;
        boolean isExtern = stub.isExtern;
        if(scope.isDeclared(qName, false)) {
          continue ;
        }
        ObjectType ownerType = getObjectSlot(ownerName);
        defineSlot(n, parent, unknownType, true);
        if(ownerType != null && (isExtern || ownerType.isFunctionPrototypeType())) {
          ownerType.defineInferredProperty(propName, unknownType, n);
        }
        else {
          typeRegistry.registerPropertyOnType(propName, ownerType == null ? unknownType : ownerType);
        }
      }
    }
    void resolveTypes() {
      for (DeferredSetType deferred : deferredSetTypes) {
        deferred.resolve(scope);
      }
      Iterator<Var> vars = scope.getVars();
      while(vars.hasNext()){
        vars.next().resolveType(typeParsingErrorReporter);
      }
      typeRegistry.resolveTypesInScope(scope);
    }
    void setDeferredType(Node node, JSType type) {
      deferredSetTypes.add(new DeferredSetType(node, type));
    }
    @Override() public void visit(NodeTraversal t, Node n, Node parent) {
      inputId = t.getInputId();
      attachLiteralTypes(t, n);
      switch (n.getType()){
        case Token.CALL:
        checkForClassDefiningCalls(t, n, parent);
        checkForCallingConventionDefiningCalls(n, delegateCallingConventions);
        break ;
        case Token.FUNCTION:
        if(t.getInput() == null || !t.getInput().isExtern()) {
          nonExternFunctions.add(n);
        }
        if(!NodeUtil.isHoistedFunctionDeclaration(n)) {
          defineFunctionLiteral(n);
        }
        break ;
        case Token.ASSIGN:
        Node firstChild = n.getFirstChild();
        if(firstChild.isGetProp() && firstChild.isQualifiedName()) {
          maybeDeclareQualifiedName(t, n.getJSDocInfo(), firstChild, n, firstChild.getNext());
        }
        break ;
        case Token.CATCH:
        defineCatch(n);
        break ;
        case Token.VAR:
        defineVar(n);
        break ;
        case Token.GETPROP:
        if(parent.isExprResult() && n.isQualifiedName()) {
          maybeDeclareQualifiedName(t, n.getJSDocInfo(), n, parent, null);
        }
        break ;
      }
      if(n.getParent() != null && NodeUtil.isStatement(n) && lentObjectLiterals != null) {
        for (Node objLit : lentObjectLiterals) {
          defineObjectLiteral(objLit);
        }
        lentObjectLiterals.clear();
      }
    }
    
    final private class CollectProperties extends AbstractShallowStatementCallback  {
      final private JSType thisType;
      CollectProperties(JSType thisType) {
        super();
        this.thisType = thisType;
      }
      private void maybeCollectMember(NodeTraversal t, Node member, Node nodeWithJsDocInfo, @Nullable() Node value) {
        JSDocInfo info = nodeWithJsDocInfo.getJSDocInfo();
        if(info == null || !member.isGetProp() || !member.getFirstChild().isThis()) {
          return ;
        }
        member.getFirstChild().setJSType(thisType);
        JSType jsType = getDeclaredType(t.getSourceName(), info, member, value);
        Node name = member.getLastChild();
        if(jsType != null && (name.isName() || name.isString()) && thisType.toObjectType() != null) {
          thisType.toObjectType().defineDeclaredProperty(name.getString(), jsType, member);
        }
      }
      @Override() public void visit(NodeTraversal t, Node n, Node parent) {
        if(n.isExprResult()) {
          Node child = n.getFirstChild();
          switch (child.getType()){
            case Token.ASSIGN:
            maybeCollectMember(t, child.getFirstChild(), child, child.getLastChild());
            break ;
            case Token.GETPROP:
            maybeCollectMember(t, child, child, null);
            break ;
          }
        }
      }
    }
  }
  
  private class DeferredSetType  {
    final Node node;
    final JSType type;
    DeferredSetType(Node node, JSType type) {
      super();
      Preconditions.checkNotNull(node);
      Preconditions.checkNotNull(type);
      this.node = node;
      this.type = type;
      node.setJSType(type);
    }
    void resolve(Scope scope) {
      node.setJSType(type.resolve(typeParsingErrorReporter, scope));
    }
  }
  
  private static class DiscoverEnumsAndTypedefs extends AbstractShallowStatementCallback  {
    final private JSTypeRegistry registry;
    DiscoverEnumsAndTypedefs(JSTypeRegistry registry) {
      super();
      this.registry = registry;
    }
    private void identifyNameNode(Node nameNode, Node valueNode, JSDocInfo info) {
      if(nameNode.isQualifiedName()) {
        if(info != null) {
          if(info.hasEnumParameterType()) {
            registry.identifyNonNullableName(nameNode.getQualifiedName());
          }
          else 
            if(info.hasTypedefType()) {
              registry.identifyNonNullableName(nameNode.getQualifiedName());
            }
        }
      }
    }
    @Override() public void visit(NodeTraversal t, Node node, Node parent) {
      Node nameNode = null;
      switch (node.getType()){
        case Token.VAR:
        Node var_1206 = node.getFirstChild();
        for(com.google.javascript.rhino.Node child = var_1206; child != null; child = child.getNext()) {
          identifyNameNode(child, child.getFirstChild(), NodeUtil.getBestJSDocInfo(child));
        }
        break ;
        case Token.EXPR_RESULT:
        Node firstChild = node.getFirstChild();
        if(firstChild.isAssign()) {
          identifyNameNode(firstChild.getFirstChild(), firstChild.getLastChild(), firstChild.getJSDocInfo());
        }
        else {
          identifyNameNode(firstChild, null, firstChild.getJSDocInfo());
        }
        break ;
      }
    }
  }
  
  private static class FirstOrderFunctionAnalyzer extends AbstractScopedCallback implements CompilerPass  {
    final private AbstractCompiler compiler;
    final private Map<Node, AstFunctionContents> data;
    FirstOrderFunctionAnalyzer(AbstractCompiler compiler, Map<Node, AstFunctionContents> outParam) {
      super();
      this.compiler = compiler;
      this.data = outParam;
    }
    @Override() public void enterScope(NodeTraversal t) {
      if(!t.inGlobalScope()) {
        Node n = t.getScopeRoot();
        data.put(n, new AstFunctionContents(n));
      }
    }
    @Override() public void process(Node externs, Node root) {
      if(externs == null) {
        NodeTraversal.traverse(compiler, root, this);
      }
      else {
        NodeTraversal.traverseRoots(compiler, ImmutableList.of(externs, root), this);
      }
    }
    @Override() public void visit(NodeTraversal t, Node n, Node parent) {
      if(t.inGlobalScope()) {
        return ;
      }
      if(n.isReturn() && n.getFirstChild() != null) {
        data.get(t.getScopeRoot()).recordNonEmptyReturn();
      }
      if(t.getScopeDepth() <= 1) {
        return ;
      }
      if(n.isName() && NodeUtil.isLValue(n) && !NodeUtil.isBleedingFunctionName(n)) {
        String name = n.getString();
        Scope scope = t.getScope();
        Var var = scope.getVar(name);
        if(var != null) {
          Scope ownerScope = var.getScope();
          if(ownerScope.isLocal()) {
            data.get(ownerScope.getRootNode()).recordAssignedName(name);
          }
          if(scope != ownerScope && ownerScope.isLocal()) {
            data.get(ownerScope.getRootNode()).recordEscapedVarName(name);
          }
        }
      }
      else 
        if(n.isGetProp() && n.isUnscopedQualifiedName() && NodeUtil.isLValue(n)) {
          String name = NodeUtil.getRootOfQualifiedName(n).getString();
          Scope scope = t.getScope();
          Var var = scope.getVar(name);
          if(var != null) {
            Scope ownerScope = var.getScope();
            if(scope != ownerScope && ownerScope.isLocal()) {
              data.get(ownerScope.getRootNode()).recordEscapedQualifiedName(n.getQualifiedName());
            }
          }
        }
    }
  }
  
  final private class GlobalScopeBuilder extends AbstractScopeBuilder  {
    private GlobalScopeBuilder(Scope scope) {
      super(scope);
    }
    private void checkForTypedef(NodeTraversal t, Node candidate, JSDocInfo info) {
      if(info == null || !info.hasTypedefType()) {
        return ;
      }
      String typedef = candidate.getQualifiedName();
      if(typedef == null) {
        return ;
      }
      typeRegistry.declareType(typedef, unknownType);
      JSType realType = info.getTypedefType().evaluate(scope, typeRegistry);
      if(realType == null) {
        compiler.report(JSError.make(t.getSourceName(), candidate, MALFORMED_TYPEDEF, typedef));
      }
      typeRegistry.overwriteDeclaredType(typedef, realType);
      if(candidate.isGetProp()) {
        defineSlot(candidate, candidate.getParent(), getNativeType(NO_TYPE), false);
      }
    }
    @Override() void maybeDeclareQualifiedName(NodeTraversal t, JSDocInfo info, Node n, Node parent, Node rhsValue) {
      checkForTypedef(t, n, info);
      super.maybeDeclareQualifiedName(t, info, n, parent, rhsValue);
    }
    @Override() public void visit(NodeTraversal t, Node n, Node parent) {
      super.visit(t, n, parent);
      switch (n.getType()){
        case Token.VAR:
        if(n.hasOneChild()) {
          checkForTypedef(t, n.getFirstChild(), n.getJSDocInfo());
        }
        break ;
      }
    }
  }
  
  final private class LocalScopeBuilder extends AbstractScopeBuilder  {
    private LocalScopeBuilder(Scope scope) {
      super(scope);
    }
    void build() {
      NodeTraversal.traverse(compiler, scope.getRootNode(), this);
      AstFunctionContents contents = getFunctionAnalysisResults(scope.getRootNode());
      if(contents != null) {
        for (String varName : contents.getEscapedVarNames()) {
          Var v = scope.getVar(varName);
          Preconditions.checkState(v.getScope() == scope);
          v.markEscaped();
        }
        for (Multiset.Entry<String> entry : contents.getAssignedNameCounts().entrySet()) {
          Var v = scope.getVar(entry.getElement());
          Preconditions.checkState(v.getScope() == scope);
          if(entry.getCount() == 1) {
            v.markAssignedExactlyOnce();
          }
        }
      }
    }
    private void declareArguments(Node functionNode) {
      Node astParameters = functionNode.getFirstChild().getNext();
      Node iifeArgumentNode = null;
      if(NodeUtil.isCallOrNewTarget(functionNode)) {
        iifeArgumentNode = functionNode.getNext();
      }
      Node body = astParameters.getNext();
      FunctionType functionType = JSType.toMaybeFunctionType(functionNode.getJSType());
      if(functionType != null) {
        Node jsDocParameters = functionType.getParametersNode();
        if(jsDocParameters != null) {
          Node jsDocParameter = jsDocParameters.getFirstChild();
          for (Node astParameter : astParameters.children()) {
            JSType paramType = jsDocParameter == null ? unknownType : jsDocParameter.getJSType();
            boolean inferred = paramType == null || paramType == unknownType;
            if(iifeArgumentNode != null && inferred) {
              String argumentName = iifeArgumentNode.getQualifiedName();
              Var argumentVar = argumentName == null || scope.getParent() == null ? null : scope.getParent().getVar(argumentName);
              if(argumentVar != null && !argumentVar.isTypeInferred()) {
                paramType = argumentVar.getType();
              }
            }
            if(paramType == null) {
              paramType = unknownType;
            }
            defineSlot(astParameter, functionNode, paramType, inferred);
            if(jsDocParameter != null) {
              jsDocParameter = jsDocParameter.getNext();
            }
            if(iifeArgumentNode != null) {
              iifeArgumentNode = iifeArgumentNode.getNext();
            }
          }
        }
      }
    }
    private void handleFunctionInputs(Node fnNode) {
      Node fnNameNode = fnNode.getFirstChild();
      String fnName = fnNameNode.getString();
      if(!fnName.isEmpty()) {
        Scope.Var fnVar = scope.getVar(fnName);
        if(fnVar == null || (fnVar.getNameNode() != null && fnVar.getInitialValue() != fnNode)) {
          defineSlot(fnNameNode, fnNode, fnNode.getJSType(), false);
        }
      }
      declareArguments(fnNode);
    }
    @Override() public void visit(NodeTraversal t, Node n, Node parent) {
      if(n == scope.getRootNode()) 
        return ;
      if(n.isParamList() && parent == scope.getRootNode()) {
        handleFunctionInputs(parent);
        return ;
      }
      super.visit(t, n, parent);
    }
  }
  
  final private static class StubDeclaration  {
    final private Node node;
    final private boolean isExtern;
    final private String ownerName;
    private StubDeclaration(Node node, boolean isExtern, String ownerName) {
      super();
      this.node = node;
      this.isExtern = isExtern;
      this.ownerName = ownerName;
    }
  }
}