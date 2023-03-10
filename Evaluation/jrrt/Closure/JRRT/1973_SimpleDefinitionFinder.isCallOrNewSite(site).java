package com.google.javascript.jscomp;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.AbstractCompiler.LifeCycleStage;
import com.google.javascript.jscomp.DefinitionsRemover.Definition;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.Collection;
import java.util.List;

class OptimizeParameters implements CompilerPass, OptimizeCalls.CallGraphCompilerPass  {
  final private AbstractCompiler compiler;
  private List<Node> removedNodes = Lists.newArrayList();
  OptimizeParameters(AbstractCompiler compiler) {
    super();
    this.compiler = compiler;
  }
  private Node eliminateCallParamAt(SimpleDefinitionFinder defFinder, Parameter p, Node call, int argIndex) {
    Preconditions.checkArgument(NodeUtil.isCallOrNew(call), "Node must be a call or new.");
    Node formalArgPtr = NodeUtil.getArgumentForCallOrNew(call, argIndex);
    if(formalArgPtr != null) {
      call.removeChild(formalArgPtr);
      if(p.getArg() != formalArgPtr) {
        removedNodes.add(formalArgPtr);
      }
      compiler.reportCodeChange();
    }
    return formalArgPtr;
  }
  private Node eliminateFunctionParamAt(Node function, int argIndex) {
    Preconditions.checkArgument(function.isFunction(), "Node must be a function.");
    Node formalArgPtr = NodeUtil.getArgumentForFunction(function, argIndex);
    if(formalArgPtr != null) {
      function.getFirstChild().getNext().removeChild(formalArgPtr);
    }
    return formalArgPtr;
  }
  private boolean adjustForSideEffects(List<Parameter> parameters) {
    boolean anyMovable = false;
    boolean seenUnmovableSideEffects = false;
    boolean seenUnmoveableSideEfffected = false;
    for(int i = parameters.size() - 1; i >= 0; i--) {
      Parameter current = parameters.get(i);
      if(current.shouldRemove && ((seenUnmovableSideEffects && current.canBeSideEffected()) || (seenUnmoveableSideEfffected && current.hasSideEffects()))) {
        current.shouldRemove = false;
      }
      if(current.shouldRemove) {
        anyMovable = true;
      }
      else {
        if(current.canBeSideEffected) {
          seenUnmoveableSideEfffected = true;
        }
        if(current.hasSideEffects) {
          seenUnmovableSideEffects = true;
        }
      }
    }
    return anyMovable;
  }
  private boolean buildParameterList(List<Parameter> parameters, Node cur, Scope s) {
    boolean anyMovable = false;
    while((cur = cur.getNext()) != null){
      boolean movable = isMovableValue(cur, s);
      Parameter p = new Parameter(cur, movable);
      setParameterSideEffectInfo(p, cur);
      parameters.add(p);
      if(movable) {
        anyMovable = true;
      }
    }
    return anyMovable;
  }
  private boolean canChangeSignature(DefinitionSite definitionSite, SimpleDefinitionFinder defFinder) {
    Definition definition = definitionSite.definition;
    if(definitionSite.inExterns) {
      return false;
    }
    Node rValue = definition.getRValue();
    if(rValue == null || !rValue.isFunction() || NodeUtil.isVarArgsFunction(rValue)) {
      return false;
    }
    if(!SimpleDefinitionFinder.isSimpleFunctionDeclaration(rValue)) {
      return false;
    }
    if(!defFinder.canModifyDefinition(definition)) {
      return false;
    }
    Collection<UseSite> useSites = defFinder.getUseSites(definition);
    if(useSites.isEmpty()) {
      return false;
    }
    for (UseSite site : useSites) {
      if(!SimpleDefinitionFinder.isCallOrNewSite(site)) {
        return false;
      }
      Node nameNode = site.node;
      Collection<Definition> singleSiteDefinitions = defFinder.getDefinitionsReferencedAt(nameNode);
      if(singleSiteDefinitions.size() > 1) {
        return false;
      }
      Preconditions.checkState(!singleSiteDefinitions.isEmpty());
      Preconditions.checkState(singleSiteDefinitions.contains(definition));
    }
    return true;
  }
  private boolean eliminateParamsAfter(Node fnNode, Node argNode) {
    if(argNode != null) {
      eliminateParamsAfter(fnNode, argNode.getNext());
      argNode.detachFromParent();
      Node var = IR.var(argNode).copyInformationFrom(argNode);
      fnNode.getLastChild().addChildrenToFront(var);
      compiler.reportCodeChange();
      return true;
    }
    return false;
  }
  private boolean eliminateParamsAfter(Node function, int argIndex) {
    boolean paramRemoved = false;
    Node formalArgPtr = function.getFirstChild().getNext().getFirstChild();
    while(argIndex != 0 && formalArgPtr != null){
      formalArgPtr = formalArgPtr.getNext();
      argIndex--;
    }
    return eliminateParamsAfter(function, formalArgPtr);
  }
  private boolean findFixedParameters(List<Parameter> parameters, Node cur) {
    boolean anyMovable = false;
    int index = 0;
    while((cur = cur.getNext()) != null){
      Parameter p;
      if(index >= parameters.size()) {
        p = new Parameter(cur, false);
        parameters.add(p);
      }
      else {
        p = parameters.get(index);
        if(p.shouldRemove()) {
          Node value = p.getArg();
          if(!cur.isEquivalentTo(value)) {
            p.setShouldRemove(false);
          }
          else {
            anyMovable = true;
          }
        }
      }
      setParameterSideEffectInfo(p, cur);
      index++;
    }
    for(; index < parameters.size(); index++) {
      parameters.get(index).setShouldRemove(false);
    }
    return anyMovable;
  }
  private boolean isMovableValue(Node n, Scope s) {
    switch (n.getType()){
      case Token.THIS:
      return false;
      case Token.FUNCTION:
      return false;
      case Token.NAME:
      if(n.getString().equals("arguments")) {
        return false;
      }
      else {
        Var v = s.getVar(n.getString());
        if(v != null && (v.isLocal() || v.nameNode.getParent().isCatch())) {
          return false;
        }
      }
      break ;
    }
    for(com.google.javascript.rhino.Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      if(!isMovableValue(c, s)) {
        return false;
      }
    }
    return true;
  }
  private void addVariableToFunction(Node function, Node varName, Node value) {
    Preconditions.checkArgument(function.isFunction(), "Node must be a function.");
    Node block = function.getLastChild();
    Preconditions.checkArgument(block.isBlock(), "Node must be a block.");
    Preconditions.checkState(value.getParent() == null);
    Node stmt;
    if(varName != null) {
      stmt = NodeUtil.newVarNode(varName.getString(), value);
    }
    else {
      stmt = IR.exprResult(value);
    }
    block.addChildToFront(stmt);
    compiler.reportCodeChange();
  }
  private void optimizeCallSite(SimpleDefinitionFinder defFinder, List<Parameter> parameters, Node call) {
    for(int index = parameters.size() - 1; index >= 0; index--) {
      Parameter p = parameters.get(index);
      if(p.shouldRemove()) {
        eliminateCallParamAt(defFinder, p, call, index);
      }
    }
  }
  private void optimizeFunctionDefinition(List<Parameter> parameters, Node function) {
    for(int index = parameters.size() - 1; index >= 0; index--) {
      if(parameters.get(index).shouldRemove()) {
        Node paramName = eliminateFunctionParamAt(function, index);
        addVariableToFunction(function, paramName, parameters.get(index).getArg());
      }
    }
  }
  @Override() @VisibleForTesting() public void process(Node externs, Node root) {
    Preconditions.checkState(compiler.getLifeCycleStage() == LifeCycleStage.NORMALIZED);
    SimpleDefinitionFinder defFinder = new SimpleDefinitionFinder(compiler);
    defFinder.process(externs, root);
    process(externs, root, defFinder);
  }
  @Override() public void process(Node externs, Node root, SimpleDefinitionFinder definitions) {
    for (DefinitionSite defSite : definitions.getDefinitionSites()) {
      if(canChangeSignature(defSite, definitions)) {
        tryEliminateConstantArgs(defSite, definitions);
        tryEliminateOptionalArgs(defSite, definitions);
      }
    }
    for (Node n : removedNodes) {
      definitions.removeReferences(n);
    }
  }
  private void setParameterSideEffectInfo(Parameter p, Node value) {
    if(!p.hasSideEffects()) {
      p.setHasSideEffects(NodeUtil.mayHaveSideEffects(value, compiler));
    }
    if(!p.canBeSideEffected()) {
      p.setCanBeSideEffected(NodeUtil.canBeSideEffected(value));
    }
  }
  private void tryEliminateConstantArgs(DefinitionSite defSite, SimpleDefinitionFinder defFinder) {
    List<Parameter> parameters = Lists.newArrayList();
    boolean firstCall = true;
    Definition definition = defSite.definition;
    Collection<UseSite> useSites = defFinder.getUseSites(definition);
    boolean continueLooking = false;
    for (UseSite site : useSites) {
      boolean var_1973 = SimpleDefinitionFinder.isCallOrNewSite(site);
      Preconditions.checkState(var_1973);
      Node call = site.node.getParent();
      Node cur = call.getFirstChild();
      if(firstCall) {
        continueLooking = buildParameterList(parameters, cur, site.scope);
        firstCall = false;
      }
      else {
        continueLooking = findFixedParameters(parameters, cur);
      }
      if(!continueLooking) {
        return ;
      }
    }
    continueLooking = adjustForSideEffects(parameters);
    if(!continueLooking) {
      return ;
    }
    for (UseSite site : useSites) {
      Preconditions.checkState(SimpleDefinitionFinder.isCallOrNewSite(site));
      Node call = site.node.getParent();
      optimizeCallSite(defFinder, parameters, call);
    }
    Node function = definition.getRValue();
    if(function.isFunction()) {
      optimizeFunctionDefinition(parameters, function);
    }
  }
  private void tryEliminateOptionalArgs(DefinitionSite defSite, SimpleDefinitionFinder defFinder) {
    int maxArgs = -1;
    Definition definition = defSite.definition;
    Collection<UseSite> useSites = defFinder.getUseSites(definition);
    for (UseSite site : useSites) {
      Preconditions.checkState(SimpleDefinitionFinder.isCallOrNewSite(site));
      Node call = site.node.getParent();
      int numArgs = call.getChildCount() - 1;
      if(numArgs > maxArgs) {
        maxArgs = numArgs;
      }
    }
    eliminateParamsAfter(definition.getRValue(), maxArgs);
  }
  
  private static class Parameter  {
    final private Node arg;
    private boolean shouldRemove;
    private boolean hasSideEffects;
    private boolean canBeSideEffected;
    public Parameter(Node arg, boolean shouldRemove) {
      super();
      this.shouldRemove = shouldRemove;
      this.arg = arg;
    }
    public Node getArg() {
      return arg;
    }
    public boolean canBeSideEffected() {
      return canBeSideEffected;
    }
    public boolean hasSideEffects() {
      return hasSideEffects;
    }
    public boolean shouldRemove() {
      return shouldRemove;
    }
    public void setCanBeSideEffected(boolean canBeSideEffected) {
      this.canBeSideEffected = canBeSideEffected;
    }
    public void setHasSideEffects(boolean hasSideEffects) {
      this.hasSideEffects = hasSideEffects;
    }
    public void setShouldRemove(boolean value) {
      shouldRemove = value;
    }
  }
}