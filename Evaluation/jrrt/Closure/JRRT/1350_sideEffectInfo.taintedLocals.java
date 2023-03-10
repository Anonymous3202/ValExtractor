package com.google.javascript.jscomp;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.javascript.jscomp.DefinitionsRemover.Definition;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.jscomp.graph.DiGraph;
import com.google.javascript.jscomp.graph.FixedPointGraphTraversal;
import com.google.javascript.jscomp.graph.FixedPointGraphTraversal.EdgeCallback;
import com.google.javascript.jscomp.graph.LinkedDirectedGraph;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

class PureFunctionIdentifier implements CompilerPass  {
  final static DiagnosticType INVALID_NO_SIDE_EFFECT_ANNOTATION = DiagnosticType.error("JSC_INVALID_NO_SIDE_EFFECT_ANNOTATION", "@nosideeffects may only appear in externs files.");
  final static DiagnosticType INVALID_MODIFIES_ANNOTATION = DiagnosticType.error("JSC_INVALID_MODIFIES_ANNOTATION", "@modifies may only appear in externs files.");
  final private AbstractCompiler compiler;
  final private DefinitionProvider definitionProvider;
  final private Map<Node, FunctionInformation> functionSideEffectMap;
  final private List<Node> allFunctionCalls;
  private Node externs;
  private Node root;
  public PureFunctionIdentifier(AbstractCompiler compiler, DefinitionProvider definitionProvider) {
    super();
    this.compiler = compiler;
    this.definitionProvider = definitionProvider;
    this.functionSideEffectMap = Maps.newHashMap();
    this.allFunctionCalls = Lists.newArrayList();
    this.externs = null;
    this.root = null;
  }
  private static Collection<Definition> getCallableDefinitions(DefinitionProvider definitionProvider, Node name) {
    if(name.isGetProp() || name.isName()) {
      List<Definition> result = Lists.newArrayList();
      Collection<Definition> decls = definitionProvider.getDefinitionsReferencedAt(name);
      if(decls == null) {
        return null;
      }
      for (Definition current : decls) {
        Node rValue = current.getRValue();
        if((rValue != null) && rValue.isFunction()) {
          result.add(current);
        }
        else {
          return null;
        }
      }
      return result;
    }
    else 
      if(name.isOr() || name.isHook()) {
        Node firstVal;
        if(name.isHook()) {
          firstVal = name.getFirstChild().getNext();
        }
        else {
          firstVal = name.getFirstChild();
        }
        Collection<Definition> defs1 = getCallableDefinitions(definitionProvider, firstVal);
        Collection<Definition> defs2 = getCallableDefinitions(definitionProvider, firstVal.getNext());
        if(defs1 != null && defs2 != null) {
          defs1.addAll(defs2);
          return defs1;
        }
        else {
          return null;
        }
      }
      else 
        if(NodeUtil.isFunctionExpression(name)) {
          return Lists.newArrayList((Definition)new DefinitionsRemover.FunctionExpressionDefinition(name, false));
        }
        else {
          return null;
        }
  }
  private static Node getCallThisObject(Node callSite) {
    Node callTarget = callSite.getFirstChild();
    if(!NodeUtil.isGet(callTarget)) {
      return null;
    }
    String propString = callTarget.getLastChild().getString();
    if(propString.equals("call") || propString.equals("apply")) {
      return callTarget.getNext();
    }
    else {
      return callTarget.getFirstChild();
    }
  }
  String getDebugReport() {
    Preconditions.checkNotNull(externs);
    Preconditions.checkNotNull(root);
    StringBuilder sb = new StringBuilder();
    FunctionNames functionNames = new FunctionNames(compiler);
    functionNames.process(null, externs);
    functionNames.process(null, root);
    sb.append("Pure functions:\n");
    for (Map.Entry<Node, FunctionInformation> entry : functionSideEffectMap.entrySet()) {
      Node function = entry.getKey();
      FunctionInformation functionInfo = entry.getValue();
      boolean isPure = functionInfo.mayBePure() && !functionInfo.mayHaveSideEffects();
      if(isPure) {
        sb.append("  " + functionNames.getFunctionName(function) + "\n");
      }
    }
    sb.append("\n");
    for (Map.Entry<Node, FunctionInformation> entry : functionSideEffectMap.entrySet()) {
      Node function = entry.getKey();
      FunctionInformation functionInfo = entry.getValue();
      Set<String> depFunctionNames = Sets.newHashSet();
      for (Node callSite : functionInfo.getCallsInFunctionBody()) {
        Collection<Definition> defs = getCallableDefinitions(definitionProvider, callSite.getFirstChild());
        if(defs == null) {
          depFunctionNames.add("<null def list>");
          continue ;
        }
        for (Definition def : defs) {
          depFunctionNames.add(functionNames.getFunctionName(def.getRValue()));
        }
      }
      sb.append(functionNames.getFunctionName(function) + " " + functionInfo.toString() + " Calls: " + depFunctionNames + "\n");
    }
    return sb.toString();
  }
  private static boolean isCallOrApply(Node callSite) {
    Node callTarget = callSite.getFirstChild();
    if(NodeUtil.isGet(callTarget)) {
      String propString = callTarget.getLastChild().getString();
      if(propString.equals("call") || propString.equals("apply")) {
        return true;
      }
    }
    return false;
  }
  private static boolean isIncDec(Node n) {
    int type = n.getType();
    return (type == Token.INC || type == Token.DEC);
  }
  @SuppressWarnings(value = {"unused", }) private static boolean isKnownLocalValue(final Node value) {
    Predicate<Node> taintingPredicate = new Predicate<Node>() {
        @Override() public boolean apply(Node value) {
          switch (value.getType()){
            case Token.ASSIGN:
            return false;
            case Token.THIS:
            return false;
            case Token.NAME:
            return false;
            case Token.GETELEM:
            case Token.GETPROP:
            return false;
            case Token.CALL:
            return false;
          }
          return false;
        }
    };
    return NodeUtil.evaluatesToLocalValue(value, taintingPredicate);
  }
  private void markPureFunctionCalls() {
    for (Node callNode : allFunctionCalls) {
      Node name = callNode.getFirstChild();
      Collection<Definition> defs = getCallableDefinitions(definitionProvider, name);
      Node.SideEffectFlags flags = new Node.SideEffectFlags();
      if(defs == null) {
        flags.setMutatesGlobalState();
        flags.setThrows();
        flags.setReturnsTainted();
      }
      else {
        flags.clearAllFlags();
        for (Definition def : defs) {
          FunctionInformation functionInfo = functionSideEffectMap.get(def.getRValue());
          Preconditions.checkNotNull(functionInfo);
          if(functionInfo.mutatesGlobalState()) {
            flags.setMutatesGlobalState();
          }
          if(functionInfo.functionThrows) {
            flags.setThrows();
          }
          if(!callNode.isNew()) {
            if(functionInfo.taintsThis) {
              flags.setMutatesThis();
            }
          }
          if(functionInfo.taintsReturn) {
            flags.setReturnsTainted();
          }
          if(flags.areAllFlagsSet()) {
            break ;
          }
        }
      }
      if(callNode.isCall()) {
        Preconditions.checkState(compiler != null);
        if(!NodeUtil.functionCallHasSideEffects(callNode, compiler)) {
          flags.clearSideEffectFlags();
        }
      }
      else 
        if(callNode.isNew()) {
          if(!NodeUtil.constructorCallHasSideEffects(callNode)) {
            flags.clearSideEffectFlags();
          }
        }
      callNode.setSideEffectFlags(flags.valueOf());
    }
  }
  @Override() public void process(Node externsAst, Node srcAst) {
    if(externs != null || root != null) {
      throw new IllegalStateException("It is illegal to call PureFunctionIdentifier.process " + "twice the same instance.  Please use a new " + "PureFunctionIdentifier instance each time.");
    }
    externs = externsAst;
    root = srcAst;
    NodeTraversal.traverse(compiler, externs, new FunctionAnalyzer(true));
    NodeTraversal.traverse(compiler, root, new FunctionAnalyzer(false));
    propagateSideEffects();
    markPureFunctionCalls();
  }
  private void propagateSideEffects() {
    DiGraph<FunctionInformation, Node> sideEffectGraph = LinkedDirectedGraph.createWithoutAnnotations();
    for (FunctionInformation functionInfo : functionSideEffectMap.values()) {
      sideEffectGraph.createNode(functionInfo);
    }
    for (FunctionInformation functionInfo : functionSideEffectMap.values()) {
      if(!functionInfo.mayHaveSideEffects()) {
        continue ;
      }
      for (Node callSite : functionInfo.getCallsInFunctionBody()) {
        Node callee = callSite.getFirstChild();
        Collection<Definition> defs = getCallableDefinitions(definitionProvider, callee);
        if(defs == null) {
          functionInfo.setTaintsUnknown();
          break ;
        }
        for (Definition def : defs) {
          Node defValue = def.getRValue();
          FunctionInformation dep = functionSideEffectMap.get(defValue);
          Preconditions.checkNotNull(dep);
          sideEffectGraph.connect(dep, callSite, functionInfo);
        }
      }
    }
    FixedPointGraphTraversal.newTraversal(new SideEffectPropagationCallback()).computeFixedPoint(sideEffectGraph);
    for (FunctionInformation functionInfo : functionSideEffectMap.values()) {
      if(functionInfo.mayBePure()) {
        functionInfo.setIsPure();
      }
    }
  }
  
  static class Driver implements CompilerPass  {
    final private AbstractCompiler compiler;
    final private String reportPath;
    final private boolean useNameReferenceGraph;
    Driver(AbstractCompiler compiler, String reportPath, boolean useNameReferenceGraph) {
      super();
      this.compiler = compiler;
      this.reportPath = reportPath;
      this.useNameReferenceGraph = useNameReferenceGraph;
    }
    @Override() public void process(Node externs, Node root) {
      DefinitionProvider definitionProvider = null;
      if(useNameReferenceGraph) {
        NameReferenceGraphConstruction graphBuilder = new NameReferenceGraphConstruction(compiler);
        graphBuilder.process(externs, root);
        definitionProvider = graphBuilder.getNameReferenceGraph();
      }
      else {
        SimpleDefinitionFinder defFinder = new SimpleDefinitionFinder(compiler);
        defFinder.process(externs, root);
        definitionProvider = defFinder;
      }
      PureFunctionIdentifier pureFunctionIdentifier = new PureFunctionIdentifier(compiler, definitionProvider);
      pureFunctionIdentifier.process(externs, root);
      if(reportPath != null) {
        try {
          Files.write(pureFunctionIdentifier.getDebugReport(), new File(reportPath), Charsets.UTF_8);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }
  }
  
  private class FunctionAnalyzer implements ScopedCallback  {
    final private boolean inExterns;
    FunctionAnalyzer(boolean inExterns) {
      super();
      this.inExterns = inExterns;
    }
    private JSDocInfo getJSDocInfoForFunction(Node node, Node parent, Node gramp) {
      JSDocInfo info = node.getJSDocInfo();
      if(info != null) {
        return info;
      }
      else 
        if(parent.isName()) {
          return gramp.hasOneChild() ? gramp.getJSDocInfo() : null;
        }
        else 
          if(parent.isAssign()) {
            return parent.getJSDocInfo();
          }
          else {
            return null;
          }
    }
    private boolean hasNoSideEffectsAnnotation(JSDocInfo docInfo) {
      Preconditions.checkNotNull(docInfo);
      return docInfo.isNoSideEffects();
    }
    private boolean hasSideEffectsArgumentsAnnotation(JSDocInfo docInfo) {
      Preconditions.checkNotNull(docInfo);
      Set<String> modifies = docInfo.getModifies();
      return (modifies.size() > 1 || (modifies.size() == 1 && !modifies.contains("this")));
    }
    private boolean hasSideEffectsThisAnnotation(JSDocInfo docInfo) {
      Preconditions.checkNotNull(docInfo);
      return (docInfo.getModifies().contains("this"));
    }
    private boolean isLocalValueType(JSType jstype, boolean recurse) {
      Preconditions.checkNotNull(jstype);
      JSType subtype = jstype.getGreatestSubtype(compiler.getTypeRegistry().getNativeType(JSTypeNative.OBJECT_TYPE));
      return subtype.isNoType();
    }
    @Override() public boolean shouldTraverse(NodeTraversal traversal, Node node, Node parent) {
      if(node.isFunction()) {
        Node gramp = parent.getParent();
        visitFunction(traversal, node, parent, gramp);
      }
      return true;
    }
    @Override() public void enterScope(NodeTraversal t) {
    }
    @Override() public void exitScope(NodeTraversal t) {
      if(t.inGlobalScope()) {
        return ;
      }
      FunctionInformation sideEffectInfo = functionSideEffectMap.get(t.getScopeRoot());
      if(sideEffectInfo.mutatesGlobalState()) {
        sideEffectInfo.resetLocalVars();
        return ;
      }
      for(java.util.Iterator<com.google.javascript.jscomp.Scope.Var> i = t.getScope().getVars(); i.hasNext(); ) {
        Var v = i.next();
        boolean localVar = false;
        if(v.getParentNode().isVar()) {
          sideEffectInfo.knownLocals.add(v.getName());
          localVar = true;
        }
        if(!localVar || sideEffectInfo.blacklisted.contains(v)) {
          Set<Var> var_1350 = sideEffectInfo.taintedLocals;
          if(var_1350.contains(v)) {
            sideEffectInfo.setTaintsUnknown();
            sideEffectInfo.resetLocalVars();
            break ;
          }
        }
      }
      sideEffectInfo.taintedLocals = null;
      sideEffectInfo.blacklisted = null;
    }
    @Override() public void visit(NodeTraversal traversal, Node node, Node parent) {
      if(inExterns) {
        return ;
      }
      if(!NodeUtil.nodeTypeMayHaveSideEffects(node) && !node.isReturn()) {
        return ;
      }
      if(node.isCall() || node.isNew()) {
        allFunctionCalls.add(node);
      }
      Node enclosingFunction = traversal.getEnclosingFunction();
      if(enclosingFunction != null) {
        FunctionInformation sideEffectInfo = functionSideEffectMap.get(enclosingFunction);
        Preconditions.checkNotNull(sideEffectInfo);
        if(NodeUtil.isAssignmentOp(node)) {
          visitAssignmentOrUnaryOperator(sideEffectInfo, traversal.getScope(), node, node.getFirstChild(), node.getLastChild());
        }
        else {
          switch (node.getType()){
            case Token.CALL:
            case Token.NEW:
            visitCall(sideEffectInfo, node);
            break ;
            case Token.DELPROP:
            case Token.DEC:
            case Token.INC:
            visitAssignmentOrUnaryOperator(sideEffectInfo, traversal.getScope(), node, node.getFirstChild(), null);
            break ;
            case Token.NAME:
            Preconditions.checkArgument(NodeUtil.isVarDeclaration(node));
            Node value = node.getFirstChild();
            if(value != null && !NodeUtil.evaluatesToLocalValue(value)) {
              Scope scope = traversal.getScope();
              Var var = scope.getVar(node.getString());
              sideEffectInfo.blacklistLocal(var);
            }
            break ;
            case Token.THROW:
            visitThrow(sideEffectInfo);
            break ;
            case Token.RETURN:
            if(node.hasChildren() && !NodeUtil.evaluatesToLocalValue(node.getFirstChild())) {
              sideEffectInfo.setTaintsReturn();
            }
            break ;
            default:
            throw new IllegalArgumentException("Unhandled side effect node type " + Token.name(node.getType()));
          }
        }
      }
    }
    private void visitAssignmentOrUnaryOperator(FunctionInformation sideEffectInfo, Scope scope, Node op, Node lhs, Node rhs) {
      if(lhs.isName()) {
        Var var = scope.getVar(lhs.getString());
        if(var == null || var.scope != scope) {
          sideEffectInfo.setTaintsGlobalState();
        }
        else {
          Preconditions.checkState(NodeUtil.isAssignmentOp(op) || isIncDec(op) || op.isDelProp());
          if(rhs != null && op.isAssign() && !NodeUtil.evaluatesToLocalValue(rhs)) {
            sideEffectInfo.blacklistLocal(var);
          }
        }
      }
      else 
        if(NodeUtil.isGet(lhs)) {
          if(lhs.getFirstChild().isThis()) {
            sideEffectInfo.setTaintsThis();
          }
          else {
            Var var = null;
            Node objectNode = lhs.getFirstChild();
            if(objectNode.isName()) {
              var = scope.getVar(objectNode.getString());
            }
            if(var == null || var.scope != scope) {
              sideEffectInfo.setTaintsUnknown();
            }
            else {
              sideEffectInfo.addTaintedLocalObject(var);
            }
          }
        }
        else {
          sideEffectInfo.setTaintsUnknown();
        }
    }
    private void visitCall(FunctionInformation sideEffectInfo, Node node) {
      if(node.isCall() && !NodeUtil.functionCallHasSideEffects(node, compiler)) {
        return ;
      }
      if(node.isNew() && !NodeUtil.constructorCallHasSideEffects(node)) {
        return ;
      }
      sideEffectInfo.appendCall(node);
    }
    private void visitFunction(NodeTraversal traversal, Node node, Node parent, Node gramp) {
      Preconditions.checkArgument(!functionSideEffectMap.containsKey(node));
      FunctionInformation sideEffectInfo = new FunctionInformation(inExterns);
      functionSideEffectMap.put(node, sideEffectInfo);
      if(inExterns) {
        JSType jstype = node.getJSType();
        boolean knownLocalResult = false;
        FunctionType functionType = JSType.toMaybeFunctionType(jstype);
        if(functionType != null) {
          JSType jstypeReturn = functionType.getReturnType();
          if(isLocalValueType(jstypeReturn, true)) {
            knownLocalResult = true;
          }
        }
        if(!knownLocalResult) {
          sideEffectInfo.setTaintsReturn();
        }
      }
      JSDocInfo info = getJSDocInfoForFunction(node, parent, gramp);
      if(info != null) {
        boolean hasSpecificSideEffects = false;
        if(hasSideEffectsThisAnnotation(info)) {
          if(inExterns) {
            hasSpecificSideEffects = true;
            sideEffectInfo.setTaintsThis();
          }
          else {
            traversal.report(node, INVALID_MODIFIES_ANNOTATION);
          }
        }
        if(hasSideEffectsArgumentsAnnotation(info)) {
          if(inExterns) {
            hasSpecificSideEffects = true;
            sideEffectInfo.setTaintsArguments();
          }
          else {
            traversal.report(node, INVALID_MODIFIES_ANNOTATION);
          }
        }
        if(inExterns && !info.getThrownTypes().isEmpty()) {
          hasSpecificSideEffects = true;
          sideEffectInfo.setFunctionThrows();
        }
        if(!hasSpecificSideEffects) {
          if(hasNoSideEffectsAnnotation(info)) {
            if(inExterns) {
              sideEffectInfo.setIsPure();
            }
            else {
              traversal.report(node, INVALID_NO_SIDE_EFFECT_ANNOTATION);
            }
          }
          else 
            if(inExterns) {
              sideEffectInfo.setTaintsGlobalState();
            }
        }
      }
      else {
        if(inExterns) {
          sideEffectInfo.setTaintsGlobalState();
        }
      }
    }
    private void visitThrow(FunctionInformation sideEffectInfo) {
      sideEffectInfo.setFunctionThrows();
    }
  }
  
  private static class FunctionInformation  {
    final private boolean extern;
    final private List<Node> callsInFunctionBody = Lists.newArrayList();
    private Set<Var> blacklisted = Sets.newHashSet();
    private Set<Var> taintedLocals = Sets.newHashSet();
    private Set<String> knownLocals = Sets.newHashSet();
    private boolean pureFunction = false;
    private boolean functionThrows = false;
    private boolean taintsGlobalState = false;
    private boolean taintsThis = false;
    private boolean taintsArguments = false;
    private boolean taintsUnknown = false;
    private boolean taintsReturn = false;
    FunctionInformation(boolean extern) {
      super();
      this.extern = extern;
      checkInvariant();
    }
    List<Node> getCallsInFunctionBody() {
      return callsInFunctionBody;
    }
    @Override() public String toString() {
      List<String> status = Lists.newArrayList();
      if(extern) {
        status.add("extern");
      }
      if(pureFunction) {
        status.add("pure");
      }
      if(taintsThis) {
        status.add("this");
      }
      if(taintsGlobalState) {
        status.add("global");
      }
      if(functionThrows) {
        status.add("throw");
      }
      if(taintsUnknown) {
        status.add("complex");
      }
      return "Side effects: " + status.toString();
    }
    boolean functionThrows() {
      return functionThrows;
    }
    boolean mayBePure() {
      return !(functionThrows || taintsGlobalState || taintsThis || taintsArguments || taintsUnknown);
    }
    boolean mayHaveSideEffects() {
      return !pureFunction;
    }
    boolean mutatesGlobalState() {
      return taintsGlobalState || taintsArguments || taintsUnknown;
    }
    boolean mutatesThis() {
      return taintsThis;
    }
    void addTaintedLocalObject(Var var) {
      taintedLocals.add(var);
    }
    void appendCall(Node callNode) {
      callsInFunctionBody.add(callNode);
    }
    public void blacklistLocal(Var var) {
      blacklisted.add(var);
    }
    private void checkInvariant() {
      boolean invariant = mayBePure() || mayHaveSideEffects();
      if(!invariant) {
        throw new IllegalStateException("Invariant failed.  " + toString());
      }
    }
    void resetLocalVars() {
      blacklisted = null;
      taintedLocals = null;
      knownLocals = Collections.emptySet();
    }
    void setFunctionThrows() {
      functionThrows = true;
      checkInvariant();
    }
    void setIsPure() {
      pureFunction = true;
      checkInvariant();
    }
    void setTaintsArguments() {
      taintsArguments = true;
      checkInvariant();
    }
    void setTaintsGlobalState() {
      taintsGlobalState = true;
      checkInvariant();
    }
    void setTaintsReturn() {
      taintsReturn = true;
      checkInvariant();
    }
    void setTaintsThis() {
      taintsThis = true;
      checkInvariant();
    }
    void setTaintsUnknown() {
      taintsUnknown = true;
      checkInvariant();
    }
  }
  
  private static class SideEffectPropagationCallback implements EdgeCallback<FunctionInformation, Node>  {
    @Override() public boolean traverseEdge(FunctionInformation callee, Node callSite, FunctionInformation caller) {
      Preconditions.checkArgument(callSite.isCall() || callSite.isNew());
      boolean changed = false;
      if(!caller.mutatesGlobalState() && callee.mutatesGlobalState()) {
        caller.setTaintsGlobalState();
        changed = true;
      }
      if(!caller.functionThrows() && callee.functionThrows()) {
        caller.setFunctionThrows();
        changed = true;
      }
      if(callee.mutatesThis()) {
        if(!callSite.isNew()) {
          Node objectNode = getCallThisObject(callSite);
          if(objectNode != null && objectNode.isName() && !isCallOrApply(callSite)) {
            String name = objectNode.getString();
            if(!caller.mutatesGlobalState()) {
              caller.setTaintsGlobalState();
              changed = true;
            }
          }
          else 
            if(objectNode != null && objectNode.isThis()) {
              if(!caller.mutatesThis()) {
                caller.setTaintsThis();
                changed = true;
              }
            }
            else 
              if(objectNode != null && NodeUtil.evaluatesToLocalValue(objectNode) && !isCallOrApply(callSite)) {
              }
              else 
                if(!caller.mutatesGlobalState()) {
                  caller.setTaintsGlobalState();
                  changed = true;
                }
        }
      }
      return changed;
    }
  }
}