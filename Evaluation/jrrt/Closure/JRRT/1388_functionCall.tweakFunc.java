package com.google.javascript.jscomp;
import com.google.common.base.CharMatcher;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;

class ProcessTweaks implements CompilerPass  {
  final private AbstractCompiler compiler;
  final private boolean stripTweaks;
  final private SortedMap<String, Node> compilerDefaultValueOverrides;
  final private static CharMatcher ID_MATCHER = CharMatcher.inRange('a', 'z').or(CharMatcher.inRange('A', 'Z')).or(CharMatcher.anyOf("0123456789_."));
  final static DiagnosticType UNKNOWN_TWEAK_WARNING = DiagnosticType.warning("JSC_UNKNOWN_TWEAK_WARNING", "no tweak registered with ID {0}");
  final static DiagnosticType TWEAK_MULTIPLY_REGISTERED_ERROR = DiagnosticType.error("JSC_TWEAK_MULTIPLY_REGISTERED_ERROR", "Tweak {0} has already been registered.");
  final static DiagnosticType NON_LITERAL_TWEAK_ID_ERROR = DiagnosticType.error("JSC_NON_LITERAL_TWEAK_ID_ERROR", "tweak ID must be a string literal");
  final static DiagnosticType INVALID_TWEAK_DEFAULT_VALUE_WARNING = DiagnosticType.warning("JSC_INVALID_TWEAK_DEFAULT_VALUE_WARNING", "tweak {0} registered with {1} must have a default value that is a " + "literal of type {2}");
  final static DiagnosticType NON_GLOBAL_TWEAK_INIT_ERROR = DiagnosticType.error("JSC_NON_GLOBAL_TWEAK_INIT_ERROR", "tweak declaration {0} must occur in the global scope");
  final static DiagnosticType TWEAK_OVERRIDE_AFTER_REGISTERED_ERROR = DiagnosticType.error("JSC_TWEAK_OVERRIDE_AFTER_REGISTERED_ERROR", "Cannot override the default value of tweak {0} after it has been " + "registered");
  final static DiagnosticType TWEAK_WRONG_GETTER_TYPE_WARNING = DiagnosticType.warning("JSC_TWEAK_WRONG_GETTER_TYPE_WARNING", "tweak getter function {0} used for tweak registered using {1}");
  final static DiagnosticType INVALID_TWEAK_ID_ERROR = DiagnosticType.error("JSC_INVALID_TWEAK_ID_ERROR", "tweak ID contains illegal characters. Only letters, numbers, _ " + "and . are allowed");
  final private static Map<String, TweakFunction> TWEAK_FUNCTIONS_MAP;
  static {
    TWEAK_FUNCTIONS_MAP = Maps.newHashMap();
    for (TweakFunction func : TweakFunction.values()) {
      TWEAK_FUNCTIONS_MAP.put(func.getName(), func);
    }
  }
  ProcessTweaks(AbstractCompiler compiler, boolean stripTweaks, Map<String, Node> compilerDefaultValueOverrides) {
    super();
    this.compiler = compiler;
    this.stripTweaks = stripTweaks;
    this.compilerDefaultValueOverrides = Maps.newTreeMap();
    this.compilerDefaultValueOverrides.putAll(compilerDefaultValueOverrides);
  }
  private CollectTweaksResult collectTweaks(Node root) {
    CollectTweaks pass = new CollectTweaks();
    NodeTraversal.traverse(compiler, root, pass);
    Map<String, TweakInfo> tweakInfos = pass.allTweaks;
    for (TweakInfo tweakInfo : tweakInfos.values()) {
      tweakInfo.emitAllWarnings();
    }
    return new CollectTweaksResult(tweakInfos, pass.getOverridesCalls);
  }
  private Node createCompilerDefaultValueOverridesVarNode(Node sourceInformationNode) {
    Node objNode = IR.objectlit().srcref(sourceInformationNode);
    for (Entry<String, Node> entry : compilerDefaultValueOverrides.entrySet()) {
      Node objKeyNode = IR.stringKey(entry.getKey()).copyInformationFrom(sourceInformationNode);
      Node objValueNode = entry.getValue().cloneNode().copyInformationFrom(sourceInformationNode);
      objKeyNode.addChildToBack(objValueNode);
      objNode.addChildToBack(objKeyNode);
    }
    return objNode;
  }
  private boolean replaceGetCompilerOverridesCalls(List<TweakFunctionCall> calls) {
    for (TweakFunctionCall call : calls) {
      Node callNode = call.callNode;
      Node objNode = createCompilerDefaultValueOverridesVarNode(callNode);
      callNode.getParent().replaceChild(callNode, objNode);
    }
    return !calls.isEmpty();
  }
  private boolean stripAllCalls(Map<String, TweakInfo> tweakInfos) {
    for (TweakInfo tweakInfo : tweakInfos.values()) {
      boolean isRegistered = tweakInfo.isRegistered();
      for (TweakFunctionCall functionCall : tweakInfo.functionCalls) {
        Node callNode = functionCall.callNode;
        Node parent = callNode.getParent();
        TweakFunction var_1388 = functionCall.tweakFunc;
        if(var_1388.isGetterFunction()) {
          Node newValue;
          if(isRegistered) {
            newValue = tweakInfo.getDefaultValueNode().cloneNode();
          }
          else {
            TweakFunction registerFunction = functionCall.tweakFunc.registerFunction;
            newValue = registerFunction.createDefaultValueNode();
          }
          parent.replaceChild(callNode, newValue);
        }
        else {
          Node voidZeroNode = IR.voidNode(IR.number(0).srcref(callNode)).srcref(callNode);
          parent.replaceChild(callNode, voidZeroNode);
        }
      }
    }
    return !tweakInfos.isEmpty();
  }
  private void applyCompilerDefaultValueOverrides(Map<String, TweakInfo> tweakInfos) {
    for (Entry<String, Node> entry : compilerDefaultValueOverrides.entrySet()) {
      String tweakId = entry.getKey();
      TweakInfo tweakInfo = tweakInfos.get(tweakId);
      if(tweakInfo == null) {
        compiler.report(JSError.make(UNKNOWN_TWEAK_WARNING, tweakId));
      }
      else {
        TweakFunction registerFunc = tweakInfo.registerCall.tweakFunc;
        Node value = entry.getValue();
        if(!registerFunc.isValidNodeType(value.getType())) {
          compiler.report(JSError.make(INVALID_TWEAK_DEFAULT_VALUE_WARNING, tweakId, registerFunc.getName(), registerFunc.getExpectedTypeName()));
        }
        else {
          tweakInfo.defaultValueNode = value;
        }
      }
    }
  }
  @Override() public void process(Node externs, Node root) {
    CollectTweaksResult result = collectTweaks(root);
    applyCompilerDefaultValueOverrides(result.tweakInfos);
    boolean changed = false;
    if(stripTweaks) {
      changed = stripAllCalls(result.tweakInfos);
    }
    else 
      if(!compilerDefaultValueOverrides.isEmpty()) {
        changed = replaceGetCompilerOverridesCalls(result.getOverridesCalls);
      }
    if(changed) {
      compiler.reportCodeChange();
    }
  }
  
  final private class CollectTweaks extends AbstractPostOrderCallback  {
    final Map<String, TweakInfo> allTweaks = Maps.newHashMap();
    final List<TweakFunctionCall> getOverridesCalls = Lists.newArrayList();
    @SuppressWarnings(value = {"incomplete-switch", }) @Override() public void visit(NodeTraversal t, Node n, Node parent) {
      if(!n.isCall()) {
        return ;
      }
      String callName = n.getFirstChild().getQualifiedName();
      TweakFunction tweakFunc = TWEAK_FUNCTIONS_MAP.get(callName);
      if(tweakFunc == null) {
        return ;
      }
      if(tweakFunc == TweakFunction.GET_COMPILER_OVERRIDES) {
        getOverridesCalls.add(new TweakFunctionCall(t.getSourceName(), tweakFunc, n));
        return ;
      }
      Node tweakIdNode = n.getFirstChild().getNext();
      if(!tweakIdNode.isString()) {
        compiler.report(t.makeError(tweakIdNode, NON_LITERAL_TWEAK_ID_ERROR));
        return ;
      }
      String tweakId = tweakIdNode.getString();
      TweakInfo tweakInfo = allTweaks.get(tweakId);
      if(tweakInfo == null) {
        tweakInfo = new TweakInfo(tweakId);
        allTweaks.put(tweakId, tweakInfo);
      }
      switch (tweakFunc){
        case REGISTER_BOOLEAN:
        case REGISTER_NUMBER:
        case REGISTER_STRING:
        if(!ID_MATCHER.matchesAllOf(tweakId)) {
          compiler.report(t.makeError(tweakIdNode, INVALID_TWEAK_ID_ERROR));
        }
        if(!t.inGlobalScope()) {
          compiler.report(t.makeError(n, NON_GLOBAL_TWEAK_INIT_ERROR, tweakId));
          break ;
        }
        if(tweakInfo.isRegistered()) {
          compiler.report(t.makeError(n, TWEAK_MULTIPLY_REGISTERED_ERROR, tweakId));
          break ;
        }
        Node tweakDefaultValueNode = tweakIdNode.getNext().getNext();
        tweakInfo.addRegisterCall(t.getSourceName(), tweakFunc, n, tweakDefaultValueNode);
        break ;
        case OVERRIDE_DEFAULT_VALUE:
        if(!t.inGlobalScope()) {
          compiler.report(t.makeError(n, NON_GLOBAL_TWEAK_INIT_ERROR, tweakId));
          break ;
        }
        if(tweakInfo.isRegistered()) {
          compiler.report(t.makeError(n, TWEAK_OVERRIDE_AFTER_REGISTERED_ERROR, tweakId));
          break ;
        }
        tweakDefaultValueNode = tweakIdNode.getNext();
        tweakInfo.addOverrideDefaultValueCall(t.getSourceName(), tweakFunc, n, tweakDefaultValueNode);
        break ;
        case GET_BOOLEAN:
        case GET_NUMBER:
        case GET_STRING:
        tweakInfo.addGetterCall(t.getSourceName(), tweakFunc, n);
      }
    }
  }
  
  final private static class CollectTweaksResult  {
    final Map<String, TweakInfo> tweakInfos;
    final List<TweakFunctionCall> getOverridesCalls;
    CollectTweaksResult(Map<String, TweakInfo> tweakInfos, List<TweakFunctionCall> getOverridesCalls) {
      super();
      this.tweakInfos = tweakInfos;
      this.getOverridesCalls = getOverridesCalls;
    }
  }
  private static enum TweakFunction {
    REGISTER_BOOLEAN("goog.tweak.registerBoolean", "boolean", Token.TRUE, Token.FALSE),

    REGISTER_NUMBER("goog.tweak.registerNumber", "number", Token.NUMBER),

    REGISTER_STRING("goog.tweak.registerString", "string", Token.STRING),

    OVERRIDE_DEFAULT_VALUE("goog.tweak.overrideDefaultValue"),

    GET_COMPILER_OVERRIDES("goog.tweak.getCompilerOverrides_"),

    GET_BOOLEAN("goog.tweak.getBoolean", REGISTER_BOOLEAN),

    GET_NUMBER("goog.tweak.getNumber", REGISTER_NUMBER),

    GET_STRING("goog.tweak.getString", REGISTER_STRING),

  ;
    final String name;
    final String expectedTypeName;
    final int validNodeTypeA;
    final int validNodeTypeB;
    final TweakFunction registerFunction;
  private TweakFunction(String name) {
  }
  private TweakFunction(String name, String expectedTypeName, int validNodeTypeA) {
  }
  private TweakFunction(String name, String expectedTypeName, int validNodeTypeA, int validNodeTypeB) {
  }
  private TweakFunction(String name, TweakFunction registerFunction) {
  }
  private TweakFunction(String name, String expectedTypeName, int validNodeTypeA, int validNodeTypeB, TweakFunction registerFunction) {
      this.name = name;
      this.expectedTypeName = expectedTypeName;
      this.validNodeTypeA = validNodeTypeA;
      this.validNodeTypeB = validNodeTypeB;
      this.registerFunction = registerFunction;
  }
    boolean isValidNodeType(int type) {
      return type == validNodeTypeA || type == validNodeTypeB;
    }
    boolean isCorrectRegisterFunction(TweakFunction registerFunction) {
      Preconditions.checkNotNull(registerFunction);
      return this.registerFunction == registerFunction;
    }
    boolean isGetterFunction() {
      return registerFunction != null;
    }
    String getName() {
      return name;
    }
    String getExpectedTypeName() {
      return expectedTypeName;
    }
    Node createDefaultValueNode() {
      switch (this){
        case REGISTER_BOOLEAN:
        return IR.falseNode();
        case REGISTER_NUMBER:
        return IR.number(0);
        case REGISTER_STRING:
        return IR.string("");
        default:
        throw new IllegalStateException();
      }
    }
  }
  
  final private static class TweakFunctionCall  {
    final String sourceName;
    final TweakFunction tweakFunc;
    final Node callNode;
    final Node valueNode;
    TweakFunctionCall(String sourceName, TweakFunction tweakFunc, Node callNode) {
      this(sourceName, tweakFunc, callNode, null);
    }
    TweakFunctionCall(String sourceName, TweakFunction tweakFunc, Node callNode, Node valueNode) {
      super();
      this.sourceName = sourceName;
      this.callNode = callNode;
      this.tweakFunc = tweakFunc;
      this.valueNode = valueNode;
    }
    Node getIdNode() {
      return callNode.getFirstChild().getNext();
    }
  }
  
  final private class TweakInfo  {
    final String tweakId;
    final List<TweakFunctionCall> functionCalls;
    TweakFunctionCall registerCall;
    Node defaultValueNode;
    TweakInfo(String tweakId) {
      super();
      this.tweakId = tweakId;
      functionCalls = Lists.newArrayList();
    }
    Node getDefaultValueNode() {
      Preconditions.checkState(isRegistered());
      if(defaultValueNode != null) {
        return defaultValueNode;
      }
      if(registerCall.valueNode != null) {
        return registerCall.valueNode;
      }
      return registerCall.tweakFunc.createDefaultValueNode();
    }
    boolean isRegistered() {
      return registerCall != null;
    }
    void addGetterCall(String sourceName, TweakFunction tweakFunc, Node callNode) {
      functionCalls.add(new TweakFunctionCall(sourceName, tweakFunc, callNode));
    }
    void addOverrideDefaultValueCall(String sourceName, TweakFunction tweakFunc, Node callNode, Node defaultValueNode) {
      functionCalls.add(new TweakFunctionCall(sourceName, tweakFunc, callNode, defaultValueNode));
      this.defaultValueNode = defaultValueNode;
    }
    void addRegisterCall(String sourceName, TweakFunction tweakFunc, Node callNode, Node defaultValueNode) {
      registerCall = new TweakFunctionCall(sourceName, tweakFunc, callNode, defaultValueNode);
      functionCalls.add(registerCall);
    }
    void emitAllTypeWarnings() {
      for (TweakFunctionCall call : functionCalls) {
        Node valueNode = call.valueNode;
        TweakFunction tweakFunc = call.tweakFunc;
        TweakFunction registerFunc = registerCall.tweakFunc;
        if(valueNode != null) {
          if(!registerFunc.isValidNodeType(valueNode.getType())) {
            compiler.report(JSError.make(call.sourceName, valueNode, INVALID_TWEAK_DEFAULT_VALUE_WARNING, tweakId, registerFunc.getName(), registerFunc.getExpectedTypeName()));
          }
        }
        else 
          if(tweakFunc.isGetterFunction()) {
            if(!tweakFunc.isCorrectRegisterFunction(registerFunc)) {
              compiler.report(JSError.make(call.sourceName, call.callNode, TWEAK_WRONG_GETTER_TYPE_WARNING, tweakFunc.getName(), registerFunc.getName()));
            }
          }
      }
    }
    void emitAllWarnings() {
      if(isRegistered()) {
        emitAllTypeWarnings();
      }
      else {
        emitUnknownTweakErrors();
      }
    }
    void emitUnknownTweakErrors() {
      for (TweakFunctionCall call : functionCalls) {
        compiler.report(JSError.make(call.sourceName, call.getIdNode(), UNKNOWN_TWEAK_WARNING, tweakId));
      }
    }
  }
}