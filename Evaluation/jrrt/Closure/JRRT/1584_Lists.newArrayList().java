package com.google.javascript.jscomp;
import static com.google.javascript.jscomp.TypeCheck.BAD_IMPLEMENTED_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.FUNCTION_FUNCTION_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.VOID_TYPE;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSTypeExpression;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionBuilder;
import com.google.javascript.rhino.jstype.FunctionParamBuilder;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

final class FunctionTypeBuilder  {
  final private String fnName;
  final private AbstractCompiler compiler;
  final private CodingConvention codingConvention;
  final private JSTypeRegistry typeRegistry;
  final private Node errorRoot;
  final private String sourceName;
  final private Scope scope;
  private FunctionContents contents = UnknownFunctionContents.get();
  private JSType returnType = null;
  private boolean returnTypeInferred = false;
  private List<ObjectType> implementedInterfaces = null;
  private List<ObjectType> extendedInterfaces = null;
  private ObjectType baseType = null;
  private JSType thisType = null;
  private boolean isConstructor = false;
  private boolean makesStructs = false;
  private boolean makesDicts = false;
  private boolean isInterface = false;
  private Node parametersNode = null;
  private ImmutableList<String> templateTypeNames = ImmutableList.of();
  final static DiagnosticType EXTENDS_WITHOUT_TYPEDEF = DiagnosticType.warning("JSC_EXTENDS_WITHOUT_TYPEDEF", "@extends used without @constructor or @interface for {0}");
  final static DiagnosticType EXTENDS_NON_OBJECT = DiagnosticType.warning("JSC_EXTENDS_NON_OBJECT", "{0} @extends non-object type {1}");
  final static DiagnosticType RESOLVED_TAG_EMPTY = DiagnosticType.warning("JSC_RESOLVED_TAG_EMPTY", "Could not resolve type in {0} tag of {1}");
  final static DiagnosticType IMPLEMENTS_WITHOUT_CONSTRUCTOR = DiagnosticType.warning("JSC_IMPLEMENTS_WITHOUT_CONSTRUCTOR", "@implements used without @constructor or @interface for {0}");
  final static DiagnosticType CONSTRUCTOR_REQUIRED = DiagnosticType.warning("JSC_CONSTRUCTOR_REQUIRED", "{0} used without @constructor for {1}");
  final static DiagnosticType VAR_ARGS_MUST_BE_LAST = DiagnosticType.warning("JSC_VAR_ARGS_MUST_BE_LAST", "variable length argument must be last");
  final static DiagnosticType OPTIONAL_ARG_AT_END = DiagnosticType.warning("JSC_OPTIONAL_ARG_AT_END", "optional arguments must be at the end");
  final static DiagnosticType INEXISTANT_PARAM = DiagnosticType.warning("JSC_INEXISTANT_PARAM", "parameter {0} does not appear in {1}\'\'s parameter list");
  final static DiagnosticType TYPE_REDEFINITION = DiagnosticType.warning("JSC_TYPE_REDEFINITION", "attempted re-definition of type {0}\n" + "found   : {1}\n" + "expected: {2}");
  final static DiagnosticType TEMPLATE_TYPE_DUPLICATED = DiagnosticType.warning("JSC_TEMPLATE_TYPE_DUPLICATED", "Only one parameter type must be the template type");
  final static DiagnosticType TEMPLATE_TYPE_EXPECTED = DiagnosticType.warning("JSC_TEMPLATE_TYPE_EXPECTED", "The template type must be a parameter type");
  final static DiagnosticType THIS_TYPE_NON_OBJECT = DiagnosticType.warning("JSC_THIS_TYPE_NON_OBJECT", "@this type of a function must be an object\n" + "Actual type: {0}");
  FunctionTypeBuilder(String fnName, AbstractCompiler compiler, Node errorRoot, String sourceName, Scope scope) {
    super();
    Preconditions.checkNotNull(errorRoot);
    this.fnName = fnName == null ? "" : fnName;
    this.codingConvention = compiler.getCodingConvention();
    this.typeRegistry = compiler.getTypeRegistry();
    this.errorRoot = errorRoot;
    this.sourceName = sourceName;
    this.compiler = compiler;
    this.scope = scope;
  }
  FunctionType buildAndRegister() {
    if(returnType == null) {
      if(!contents.mayHaveNonEmptyReturns() && !contents.mayHaveSingleThrow() && !contents.mayBeFromExterns()) {
        returnType = typeRegistry.getNativeType(VOID_TYPE);
        returnTypeInferred = true;
      }
    }
    if(returnType == null) {
      returnType = typeRegistry.getNativeType(UNKNOWN_TYPE);
    }
    if(parametersNode == null) {
      throw new IllegalStateException("All Function types must have params and a return type");
    }
    FunctionType fnType;
    if(isConstructor) {
      fnType = getOrCreateConstructor();
    }
    else 
      if(isInterface) {
        fnType = typeRegistry.createInterfaceType(fnName, contents.getSourceNode());
        if(getScopeDeclaredIn().isGlobal() && !fnName.isEmpty()) {
          typeRegistry.declareType(fnName, fnType.getInstanceType());
        }
        maybeSetBaseType(fnType);
      }
      else {
        fnType = new FunctionBuilder(typeRegistry).withName(fnName).withSourceNode(contents.getSourceNode()).withParamsNode(parametersNode).withReturnType(returnType, returnTypeInferred).withTypeOfThis(thisType).withTemplateKeys(templateTypeNames).build();
        maybeSetBaseType(fnType);
      }
    if(implementedInterfaces != null) {
      fnType.setImplementedInterfaces(implementedInterfaces);
    }
    if(extendedInterfaces != null) {
      fnType.setExtendedInterfaces(extendedInterfaces);
    }
    typeRegistry.clearTemplateTypeNames();
    return fnType;
  }
  private FunctionType getOrCreateConstructor() {
    FunctionType fnType = typeRegistry.createConstructorType(fnName, contents.getSourceNode(), parametersNode, returnType, null);
    JSType existingType = typeRegistry.getType(fnName);
    if(makesStructs) {
      fnType.setStruct();
    }
    else 
      if(makesDicts) {
        fnType.setDict();
      }
    if(existingType != null) {
      boolean isInstanceObject = existingType.isInstanceType();
      if(isInstanceObject || fnName.equals("Function")) {
        FunctionType existingFn = isInstanceObject ? existingType.toObjectType().getConstructor() : typeRegistry.getNativeFunctionType(FUNCTION_FUNCTION_TYPE);
        if(existingFn.getSource() == null) {
          existingFn.setSource(contents.getSourceNode());
        }
        if(!existingFn.hasEqualCallType(fnType)) {
          reportWarning(TYPE_REDEFINITION, fnName, fnType.toString(), existingFn.toString());
        }
        return existingFn;
      }
      else {
      }
    }
    maybeSetBaseType(fnType);
    if(getScopeDeclaredIn().isGlobal() && !fnName.isEmpty()) {
      typeRegistry.declareType(fnName, fnType.getInstanceType());
    }
    return fnType;
  }
  FunctionTypeBuilder inferFromOverriddenFunction(@Nullable() FunctionType oldType, @Nullable() Node paramsParent) {
    if(oldType == null) {
      return this;
    }
    returnType = oldType.getReturnType();
    returnTypeInferred = oldType.isReturnTypeInferred();
    if(paramsParent == null) {
      parametersNode = oldType.getParametersNode();
      if(parametersNode == null) {
        parametersNode = new FunctionParamBuilder(typeRegistry).build();
      }
    }
    else {
      FunctionParamBuilder paramBuilder = new FunctionParamBuilder(typeRegistry);
      Iterator<Node> oldParams = oldType.getParameters().iterator();
      boolean warnedAboutArgList = false;
      boolean oldParamsListHitOptArgs = false;
      for(com.google.javascript.rhino.Node currentParam = paramsParent.getFirstChild(); currentParam != null; currentParam = currentParam.getNext()) {
        if(oldParams.hasNext()) {
          Node oldParam = oldParams.next();
          Node newParam = paramBuilder.newParameterFromNode(oldParam);
          oldParamsListHitOptArgs = oldParamsListHitOptArgs || oldParam.isVarArgs() || oldParam.isOptionalArg();
          if(currentParam.getNext() != null && newParam.isVarArgs()) {
            newParam.setVarArgs(false);
            newParam.setOptionalArg(true);
          }
        }
        else {
          warnedAboutArgList |= addParameter(paramBuilder, typeRegistry.getNativeType(UNKNOWN_TYPE), warnedAboutArgList, codingConvention.isOptionalParameter(currentParam) || oldParamsListHitOptArgs, codingConvention.isVarArgsParameter(currentParam));
        }
      }
      while(oldParams.hasNext()){
        paramBuilder.newOptionalParameterFromNode(oldParams.next());
      }
      parametersNode = paramBuilder.build();
    }
    return this;
  }
  FunctionTypeBuilder inferInheritance(@Nullable() JSDocInfo info) {
    if(info != null) {
      isConstructor = info.isConstructor();
      makesStructs = info.makesStructs();
      makesDicts = info.makesDicts();
      isInterface = info.isInterface();
      if(makesStructs && !isConstructor) {
        reportWarning(CONSTRUCTOR_REQUIRED, "@struct", fnName);
      }
      else 
        if(makesDicts && !isConstructor) {
          reportWarning(CONSTRUCTOR_REQUIRED, "@dict", fnName);
        }
      if(info.hasBaseType()) {
        if(isConstructor) {
          JSType maybeBaseType = info.getBaseType().evaluate(scope, typeRegistry);
          if(maybeBaseType != null && maybeBaseType.setValidator(new ExtendedTypeValidator())) {
            baseType = (ObjectType)maybeBaseType;
          }
        }
        else {
          reportWarning(EXTENDS_WITHOUT_TYPEDEF, fnName);
        }
      }
      if(info.getImplementedInterfaceCount() > 0) {
        if(isConstructor) {
          java.util.ArrayList<ObjectType> var_1584 = Lists.newArrayList();
          implementedInterfaces = var_1584;
          for (JSTypeExpression t : info.getImplementedInterfaces()) {
            JSType maybeInterType = t.evaluate(scope, typeRegistry);
            if(maybeInterType != null && maybeInterType.setValidator(new ImplementedTypeValidator())) {
              implementedInterfaces.add((ObjectType)maybeInterType);
            }
          }
        }
        else 
          if(isInterface) {
            reportWarning(TypeCheck.CONFLICTING_IMPLEMENTED_TYPE, fnName);
          }
          else {
            reportWarning(CONSTRUCTOR_REQUIRED, "@implements", fnName);
          }
      }
      if(isInterface) {
        extendedInterfaces = Lists.newArrayList();
        for (JSTypeExpression t : info.getExtendedInterfaces()) {
          JSType maybeInterfaceType = t.evaluate(scope, typeRegistry);
          if(maybeInterfaceType != null && maybeInterfaceType.setValidator(new ExtendedTypeValidator())) {
            extendedInterfaces.add((ObjectType)maybeInterfaceType);
          }
        }
      }
    }
    return this;
  }
  FunctionTypeBuilder inferParameterTypes(JSDocInfo info) {
    Node lp = IR.paramList();
    for (String name : info.getParameterNames()) {
      lp.addChildToBack(IR.name(name));
    }
    return inferParameterTypes(lp, info);
  }
  FunctionTypeBuilder inferParameterTypes(@Nullable() Node argsParent, @Nullable() JSDocInfo info) {
    if(argsParent == null) {
      if(info == null) {
        return this;
      }
      else {
        return inferParameterTypes(info);
      }
    }
    Node oldParameterType = null;
    if(parametersNode != null) {
      oldParameterType = parametersNode.getFirstChild();
    }
    FunctionParamBuilder builder = new FunctionParamBuilder(typeRegistry);
    boolean warnedAboutArgList = false;
    Set<String> allJsDocParams = (info == null) ? Sets.<String>newHashSet() : Sets.newHashSet(info.getParameterNames());
    boolean foundTemplateType = false;
    boolean isVarArgs = false;
    for (Node arg : argsParent.children()) {
      String argumentName = arg.getString();
      allJsDocParams.remove(argumentName);
      JSType parameterType = null;
      boolean isOptionalParam = isOptionalParameter(arg, info);
      isVarArgs = isVarArgsParameter(arg, info);
      if(info != null && info.hasParameterType(argumentName)) {
        parameterType = info.getParameterType(argumentName).evaluate(scope, typeRegistry);
      }
      else 
        if(oldParameterType != null && oldParameterType.getJSType() != null) {
          parameterType = oldParameterType.getJSType();
          isOptionalParam = oldParameterType.isOptionalArg();
          isVarArgs = oldParameterType.isVarArgs();
        }
        else {
          parameterType = typeRegistry.getNativeType(UNKNOWN_TYPE);
        }
      warnedAboutArgList |= addParameter(builder, parameterType, warnedAboutArgList, isOptionalParam, isVarArgs);
      if(oldParameterType != null) {
        oldParameterType = oldParameterType.getNext();
      }
    }
    if(!isVarArgs) {
      while(oldParameterType != null && !isVarArgs){
        builder.newParameterFromNode(oldParameterType);
        oldParameterType = oldParameterType.getNext();
      }
    }
    for (String inexistentName : allJsDocParams) {
      reportWarning(INEXISTANT_PARAM, inexistentName, fnName);
    }
    parametersNode = builder.build();
    return this;
  }
  FunctionTypeBuilder inferReturnType(@Nullable() JSDocInfo info) {
    if(info != null && info.hasReturnType()) {
      returnType = info.getReturnType().evaluate(scope, typeRegistry);
      returnTypeInferred = false;
    }
    return this;
  }
  FunctionTypeBuilder inferTemplateTypeName(@Nullable() JSDocInfo info) {
    if(info != null) {
      templateTypeNames = info.getTemplateTypeNames();
      typeRegistry.setTemplateTypeNames(templateTypeNames);
    }
    return this;
  }
  FunctionTypeBuilder inferThisType(JSDocInfo info) {
    JSType maybeThisType = null;
    if(info != null && info.hasThisType()) {
      maybeThisType = info.getThisType().evaluate(scope, typeRegistry).restrictByNotNullOrUndefined();
    }
    if(maybeThisType != null) {
      thisType = maybeThisType;
    }
    return this;
  }
  FunctionTypeBuilder inferThisType(JSDocInfo info, JSType type) {
    inferThisType(info);
    if(thisType == null) {
      ObjectType objType = ObjectType.cast(type);
      if(objType != null && (info == null || !info.hasType())) {
        thisType = objType;
      }
    }
    return this;
  }
  FunctionTypeBuilder setContents(@Nullable() FunctionContents contents) {
    if(contents != null) {
      this.contents = contents;
    }
    return this;
  }
  private Scope getScopeDeclaredIn() {
    int dotIndex = fnName.indexOf(".");
    if(dotIndex != -1) {
      String rootVarName = fnName.substring(0, dotIndex);
      Var rootVar = scope.getVar(rootVarName);
      if(rootVar != null) {
        return rootVar.getScope();
      }
    }
    return scope;
  }
  private boolean addParameter(FunctionParamBuilder builder, JSType paramType, boolean warnedAboutArgList, boolean isOptional, boolean isVarArgs) {
    boolean emittedWarning = false;
    if(isOptional) {
      if(!builder.addOptionalParams(paramType) && !warnedAboutArgList) {
        reportWarning(VAR_ARGS_MUST_BE_LAST);
        emittedWarning = true;
      }
    }
    else 
      if(isVarArgs) {
        if(!builder.addVarArgs(paramType) && !warnedAboutArgList) {
          reportWarning(VAR_ARGS_MUST_BE_LAST);
          emittedWarning = true;
        }
      }
      else {
        if(!builder.addRequiredParams(paramType) && !warnedAboutArgList) {
          if(builder.hasVarArgs()) {
            reportWarning(VAR_ARGS_MUST_BE_LAST);
          }
          else {
            reportWarning(OPTIONAL_ARG_AT_END);
          }
          emittedWarning = true;
        }
      }
    return emittedWarning;
  }
  private static boolean hasMoreTagsToResolve(ObjectType objectType) {
    Preconditions.checkArgument(objectType.isUnknownType());
    if(objectType.getImplicitPrototype() != null) {
      if(objectType.getImplicitPrototype().isResolved()) {
        return false;
      }
      else {
        return true;
      }
    }
    else {
      FunctionType ctor = objectType.getConstructor();
      if(ctor != null) {
        for (ObjectType interfaceType : ctor.getExtendedInterfaces()) {
          if(!interfaceType.isResolved()) {
            return true;
          }
        }
      }
      return false;
    }
  }
  static boolean isFunctionTypeDeclaration(JSDocInfo info) {
    return info.getParameterCount() > 0 || info.hasReturnType() || info.hasThisType() || info.isConstructor() || info.isInterface();
  }
  private boolean isOptionalParameter(Node param, @Nullable() JSDocInfo info) {
    if(codingConvention.isOptionalParameter(param)) {
      return true;
    }
    String paramName = param.getString();
    return info != null && info.hasParameterType(paramName) && info.getParameterType(paramName).isOptionalArg();
  }
  private boolean isVarArgsParameter(Node param, @Nullable() JSDocInfo info) {
    if(codingConvention.isVarArgsParameter(param)) {
      return true;
    }
    String paramName = param.getString();
    return info != null && info.hasParameterType(paramName) && info.getParameterType(paramName).isVarArgs();
  }
  private void maybeSetBaseType(FunctionType fnType) {
    if(!fnType.isInterface() && baseType != null) {
      fnType.setPrototypeBasedOn(baseType);
    }
  }
  private void reportError(DiagnosticType error, String ... args) {
    compiler.report(JSError.make(sourceName, errorRoot, error, args));
  }
  private void reportWarning(DiagnosticType warning, String ... args) {
    compiler.report(JSError.make(sourceName, errorRoot, warning, args));
  }
  
  static class AstFunctionContents implements FunctionContents  {
    final private Node n;
    private boolean hasNonEmptyReturns = false;
    private Set<String> escapedVarNames;
    private Set<String> escapedQualifiedNames;
    final private Multiset<String> assignedVarNames = HashMultiset.create();
    AstFunctionContents(Node n) {
      super();
      this.n = n;
    }
    @Override() public Iterable<String> getEscapedVarNames() {
      return escapedVarNames == null ? ImmutableList.<String>of() : escapedVarNames;
    }
    @Override() public Multiset<String> getAssignedNameCounts() {
      return assignedVarNames;
    }
    @Override() public Node getSourceNode() {
      return n;
    }
    @Override() public Set<String> getEscapedQualifiedNames() {
      return escapedQualifiedNames == null ? ImmutableSet.<String>of() : escapedQualifiedNames;
    }
    @Override() public boolean mayBeFromExterns() {
      return n.isFromExterns();
    }
    @Override() public boolean mayHaveNonEmptyReturns() {
      return hasNonEmptyReturns;
    }
    @Override() public boolean mayHaveSingleThrow() {
      Node block = n.getLastChild();
      return block.hasOneChild() && block.getFirstChild().isThrow();
    }
    void recordAssignedName(String name) {
      assignedVarNames.add(name);
    }
    void recordEscapedQualifiedName(String name) {
      if(escapedQualifiedNames == null) {
        escapedQualifiedNames = Sets.newHashSet();
      }
      escapedQualifiedNames.add(name);
    }
    void recordEscapedVarName(String name) {
      if(escapedVarNames == null) {
        escapedVarNames = Sets.newHashSet();
      }
      escapedVarNames.add(name);
    }
    void recordNonEmptyReturn() {
      hasNonEmptyReturns = true;
    }
  }
  
  private class ExtendedTypeValidator implements Predicate<JSType>  {
    @Override() public boolean apply(JSType type) {
      ObjectType objectType = ObjectType.cast(type);
      if(objectType == null) {
        reportWarning(EXTENDS_NON_OBJECT, fnName, type.toString());
        return false;
      }
      else 
        if(objectType.isEmptyType()) {
          reportWarning(RESOLVED_TAG_EMPTY, "@extends", fnName);
          return false;
        }
        else 
          if(objectType.isUnknownType()) {
            if(hasMoreTagsToResolve(objectType)) {
              return true;
            }
            else {
              reportWarning(RESOLVED_TAG_EMPTY, "@extends", fnName);
              return false;
            }
          }
          else {
            return true;
          }
    }
  }
  
  static interface FunctionContents  {
    Iterable<String> getEscapedVarNames();
    Multiset<String> getAssignedNameCounts();
    Node getSourceNode();
    Set<String> getEscapedQualifiedNames();
    boolean mayBeFromExterns();
    boolean mayHaveNonEmptyReturns();
    boolean mayHaveSingleThrow();
  }
  
  private class ImplementedTypeValidator implements Predicate<JSType>  {
    @Override() public boolean apply(JSType type) {
      ObjectType objectType = ObjectType.cast(type);
      if(objectType == null) {
        reportError(BAD_IMPLEMENTED_TYPE, fnName);
        return false;
      }
      else 
        if(objectType.isEmptyType()) {
          reportWarning(RESOLVED_TAG_EMPTY, "@implements", fnName);
          return false;
        }
        else 
          if(objectType.isUnknownType()) {
            if(hasMoreTagsToResolve(objectType)) {
              return true;
            }
            else {
              reportWarning(RESOLVED_TAG_EMPTY, "@implements", fnName);
              return false;
            }
          }
          else {
            return true;
          }
    }
  }
  
  static class UnknownFunctionContents implements FunctionContents  {
    private static UnknownFunctionContents singleton = new UnknownFunctionContents();
    static FunctionContents get() {
      return singleton;
    }
    @Override() public Iterable<String> getEscapedVarNames() {
      return ImmutableList.of();
    }
    @Override() public Multiset<String> getAssignedNameCounts() {
      return ImmutableMultiset.of();
    }
    @Override() public Node getSourceNode() {
      return null;
    }
    @Override() public Set<String> getEscapedQualifiedNames() {
      return ImmutableSet.of();
    }
    @Override() public boolean mayBeFromExterns() {
      return true;
    }
    @Override() public boolean mayHaveNonEmptyReturns() {
      return true;
    }
    @Override() public boolean mayHaveSingleThrow() {
      return true;
    }
  }
}