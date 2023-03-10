package com.google.javascript.jscomp;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.CodingConvention.SubclassRelationship;
import com.google.javascript.jscomp.DefinitionsRemover.Definition;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.*;

class RemoveUnusedVars implements CompilerPass, OptimizeCalls.CallGraphCompilerPass  {
  final private AbstractCompiler compiler;
  final private CodingConvention codingConvention;
  final private boolean removeGlobals;
  private boolean preserveFunctionExpressionNames;
  final private Set<Var> referenced = Sets.newHashSet();
  final private List<Var> maybeUnreferenced = Lists.newArrayList();
  final private List<Scope> allFunctionScopes = Lists.newArrayList();
  final private Multimap<Var, Assign> assignsByVar = ArrayListMultimap.create();
  final private Map<Node, Assign> assignsByNode = Maps.newHashMap();
  final private Multimap<Var, Node> classDefiningCalls = ArrayListMultimap.create();
  final private Multimap<Var, Continuation> continuations = ArrayListMultimap.create();
  private boolean modifyCallSites;
  private CallSiteOptimizer callSiteOptimizer;
  RemoveUnusedVars(AbstractCompiler compiler, boolean removeGlobals, boolean preserveFunctionExpressionNames, boolean modifyCallSites) {
    super();
    this.compiler = compiler;
    this.codingConvention = compiler.getCodingConvention();
    this.removeGlobals = removeGlobals;
    this.preserveFunctionExpressionNames = preserveFunctionExpressionNames;
    this.modifyCallSites = modifyCallSites;
  }
  private static Node getFunctionArgList(Node function) {
    return function.getFirstChild().getNext();
  }
  private boolean isRemovableVar(Var var) {
    if(!removeGlobals && var.isGlobal()) {
      return false;
    }
    if(referenced.contains(var)) {
      return false;
    }
    if(codingConvention.isExported(var.getName())) {
      return false;
    }
    return true;
  }
  private boolean markReferencedVar(Var var) {
    if(referenced.add(var)) {
      for (Continuation c : continuations.get(var)) {
        c.apply();
      }
      return true;
    }
    return false;
  }
  private void collectMaybeUnreferencedVars(Scope scope) {
    for(java.util.Iterator<com.google.javascript.jscomp.Scope.Var> it = scope.getVars(); it.hasNext(); ) {
      Var var = it.next();
      if(isRemovableVar(var)) {
        maybeUnreferenced.add(var);
      }
    }
  }
  private void interpretAssigns() {
    boolean changes = false;
    do {
      changes = false;
      for(int current = 0; current < maybeUnreferenced.size(); current++) {
        Var var = maybeUnreferenced.get(current);
        if(referenced.contains(var)) {
          maybeUnreferenced.remove(current);
          current--;
        }
        else {
          boolean assignedToUnknownValue = false;
          boolean hasPropertyAssign = false;
          if(var.getParentNode().isVar() && !NodeUtil.isForIn(var.getParentNode().getParent())) {
            Node value = var.getInitialValue();
            assignedToUnknownValue = value != null && !NodeUtil.isLiteralValue(value, true);
          }
          else {
            assignedToUnknownValue = true;
          }
          boolean maybeEscaped = false;
          for (Assign assign : assignsByVar.get(var)) {
            if(assign.isPropertyAssign) {
              hasPropertyAssign = true;
            }
            else 
              if(!NodeUtil.isLiteralValue(assign.assignNode.getLastChild(), true)) {
                assignedToUnknownValue = true;
              }
            if(assign.maybeAliased) {
              maybeEscaped = true;
            }
          }
          if((assignedToUnknownValue || maybeEscaped) && hasPropertyAssign) {
            changes = markReferencedVar(var) || changes;
            maybeUnreferenced.remove(current);
            current--;
          }
        }
      }
    }while(changes);
  }
  @Override() public void process(Node externs, Node root) {
    Preconditions.checkState(compiler.getLifeCycleStage().isNormalized());
    SimpleDefinitionFinder defFinder = null;
    if(modifyCallSites) {
      defFinder = new SimpleDefinitionFinder(compiler);
      defFinder.process(externs, root);
    }
    process(externs, root, defFinder);
  }
  @Override() public void process(Node externs, Node root, SimpleDefinitionFinder defFinder) {
    if(modifyCallSites) {
      Preconditions.checkNotNull(defFinder);
      callSiteOptimizer = new CallSiteOptimizer(compiler, defFinder);
    }
    traverseAndRemoveUnusedReferences(root);
    if(callSiteOptimizer != null) {
      callSiteOptimizer.applyChanges();
    }
  }
  private void removeAllAssigns(Var var) {
    for (Assign assign : assignsByVar.get(var)) {
      assign.remove();
      compiler.reportCodeChange();
    }
  }
  private void removeUnreferencedFunctionArgs(Scope fnScope) {
    if(!removeGlobals) {
      return ;
    }
    Node function = fnScope.getRootNode();
    Preconditions.checkState(function.isFunction());
    if(NodeUtil.isGetOrSetKey(function.getParent())) {
      return ;
    }
    Node argList = getFunctionArgList(function);
    boolean modifyCallers = modifyCallSites && callSiteOptimizer.canModifyCallers(function);
    if(!modifyCallers) {
      Node lastArg;
      while((lastArg = argList.getLastChild()) != null){
        Var var = fnScope.getVar(lastArg.getString());
        if(!referenced.contains(var)) {
          argList.removeChild(lastArg);
          compiler.reportCodeChange();
        }
        else {
          break ;
        }
      }
    }
    else {
      callSiteOptimizer.optimize(fnScope, referenced);
    }
  }
  private void removeUnreferencedVars() {
    CodingConvention convention = codingConvention;
    for(java.util.Iterator<com.google.javascript.jscomp.Scope.Var> it = maybeUnreferenced.iterator(); it.hasNext(); ) {
      Var var = it.next();
      for (Node exprCallNode : classDefiningCalls.get(var)) {
        NodeUtil.removeChild(exprCallNode.getParent(), exprCallNode);
        compiler.reportCodeChange();
      }
      removeAllAssigns(var);
      compiler.addToDebugLog("Unreferenced var: " + var.name);
      Node nameNode = var.nameNode;
      Node toRemove = nameNode.getParent();
      Node parent = toRemove.getParent();
      Preconditions.checkState(toRemove.isVar() || toRemove.isFunction() || toRemove.isParamList() && parent.isFunction(), "We should only declare vars and functions and function args");
      if(toRemove.isParamList() && parent.isFunction()) {
      }
      else 
        if(NodeUtil.isFunctionExpression(toRemove)) {
          if(!preserveFunctionExpressionNames) {
            toRemove.getFirstChild().setString("");
            compiler.reportCodeChange();
          }
        }
        else 
          if(parent != null && parent.isFor() && parent.getChildCount() < 4) {
          }
          else 
            if(toRemove.isVar() && nameNode.hasChildren() && NodeUtil.mayHaveSideEffects(nameNode.getFirstChild(), compiler)) {
              if(toRemove.getChildCount() == 1) {
                parent.replaceChild(toRemove, IR.exprResult(nameNode.removeFirstChild()));
                compiler.reportCodeChange();
              }
            }
            else 
              if(toRemove.isVar() && toRemove.getChildCount() > 1) {
                toRemove.removeChild(nameNode);
                compiler.reportCodeChange();
              }
              else 
                if(parent != null) {
                  NodeUtil.removeChild(parent, toRemove);
                  compiler.reportCodeChange();
                }
    }
  }
  private void traverseAndRemoveUnusedReferences(Node root) {
    Scope scope = new SyntacticScopeCreator(compiler).createScope(root, null);
    traverseNode(root, null, scope);
    if(removeGlobals) {
      collectMaybeUnreferencedVars(scope);
    }
    interpretAssigns();
    removeUnreferencedVars();
    for (Scope fnScope : allFunctionScopes) {
      removeUnreferencedFunctionArgs(fnScope);
    }
  }
  private void traverseFunction(Node n, Scope parentScope) {
    Preconditions.checkState(n.getChildCount() == 3);
    Preconditions.checkState(n.isFunction());
    final Node body = n.getLastChild();
    Preconditions.checkState(body.getNext() == null && body.isBlock());
    Scope fnScope = new SyntacticScopeCreator(compiler).createScope(n, parentScope);
    traverseNode(body, n, fnScope);
    collectMaybeUnreferencedVars(fnScope);
    allFunctionScopes.add(fnScope);
  }
  private void traverseNode(Node n, Node parent, Scope scope) {
    int type = n.getType();
    Var var = null;
    switch (type){
      case Token.FUNCTION:
      if(NodeUtil.isFunctionDeclaration(n)) {
        var = scope.getVar(n.getFirstChild().getString());
      }
      if(var != null && isRemovableVar(var)) {
        continuations.put(var, new Continuation(n, scope));
      }
      else {
        traverseFunction(n, scope);
      }
      return ;
      case Token.ASSIGN:
      Assign maybeAssign = Assign.maybeCreateAssign(n);
      if(maybeAssign != null) {
        var = scope.getVar(maybeAssign.nameNode.getString());
        if(var != null) {
          assignsByVar.put(var, maybeAssign);
          assignsByNode.put(maybeAssign.nameNode, maybeAssign);
          if(isRemovableVar(var) && !maybeAssign.mayHaveSecondarySideEffects) {
            continuations.put(var, new Continuation(n, scope));
            return ;
          }
        }
      }
      break ;
      case Token.CALL:
      Var modifiedVar = null;
      SubclassRelationship subclassRelationship = codingConvention.getClassesDefinedByCall(n);
      if(subclassRelationship != null) {
        modifiedVar = scope.getVar(subclassRelationship.subclassName);
      }
      else {
        String className = codingConvention.getSingletonGetterClassName(n);
        if(className != null) {
          modifiedVar = scope.getVar(className);
        }
      }
      if(modifiedVar != null && modifiedVar.isGlobal() && !referenced.contains(modifiedVar)) {
        classDefiningCalls.put(modifiedVar, parent);
        continuations.put(modifiedVar, new Continuation(n, scope));
        return ;
      }
      break ;
      case Token.NAME:
      var = scope.getVar(n.getString());
      if(parent.isVar()) {
        Node value = n.getFirstChild();
        if(value != null && var != null && isRemovableVar(var) && !NodeUtil.mayHaveSideEffects(value, compiler)) {
          continuations.put(var, new Continuation(n, scope));
          return ;
        }
      }
      else {
        if("arguments".equals(n.getString()) && scope.isLocal()) {
          Node lp = scope.getRootNode().getFirstChild().getNext();
          for(com.google.javascript.rhino.Node a = lp.getFirstChild(); a != null; a = a.getNext()) {
            markReferencedVar(scope.getVar(a.getString()));
          }
        }
        if(var != null) {
          if(isRemovableVar(var)) {
            if(!assignsByNode.containsKey(n)) {
              markReferencedVar(var);
            }
          }
          else {
            markReferencedVar(var);
          }
        }
      }
      break ;
    }
    for(com.google.javascript.rhino.Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      traverseNode(c, n, scope);
    }
  }
  
  private static class Assign  {
    final Node assignNode;
    final Node nameNode;
    final boolean isPropertyAssign;
    final boolean mayHaveSecondarySideEffects;
    final boolean maybeAliased;
    Assign(Node assignNode, Node nameNode, boolean isPropertyAssign) {
      super();
      Preconditions.checkState(NodeUtil.isAssignmentOp(assignNode));
      this.assignNode = assignNode;
      this.nameNode = nameNode;
      this.isPropertyAssign = isPropertyAssign;
      this.maybeAliased = NodeUtil.isExpressionResultUsed(assignNode);
      this.mayHaveSecondarySideEffects = maybeAliased || NodeUtil.mayHaveSideEffects(assignNode.getFirstChild()) || NodeUtil.mayHaveSideEffects(assignNode.getLastChild());
    }
    static Assign maybeCreateAssign(Node assignNode) {
      Preconditions.checkState(NodeUtil.isAssignmentOp(assignNode));
      boolean isPropAssign = false;
      Node current = assignNode.getFirstChild();
      if(NodeUtil.isGet(current)) {
        current = current.getFirstChild();
        isPropAssign = true;
        if(current.isGetProp() && current.getLastChild().getString().equals("prototype")) {
          current = current.getFirstChild();
        }
      }
      if(current.isName()) {
        return new Assign(assignNode, current, isPropAssign);
      }
      return null;
    }
    void remove() {
      Node parent = assignNode.getParent();
      if(mayHaveSecondarySideEffects) {
        Node var_621 = assignNode.getLastChild().detachFromParent();
        Node replacement = var_621;
        for(com.google.javascript.rhino.Node current = assignNode.getFirstChild(); !current.isName(); current = current.getFirstChild()) {
          if(current.isGetElem()) {
            replacement = IR.comma(current.getLastChild().detachFromParent(), replacement);
            replacement.copyInformationFrom(current);
          }
        }
        parent.replaceChild(assignNode, replacement);
      }
      else {
        Node gramps = parent.getParent();
        if(parent.isExprResult()) {
          gramps.removeChild(parent);
        }
        else {
          parent.replaceChild(assignNode, assignNode.getLastChild().detachFromParent());
        }
      }
    }
  }
  
  private static class CallSiteOptimizer  {
    final private AbstractCompiler compiler;
    final private SimpleDefinitionFinder defFinder;
    final private List<Node> toRemove = Lists.newArrayList();
    final private List<Node> toReplaceWithZero = Lists.newArrayList();
    CallSiteOptimizer(AbstractCompiler compiler, SimpleDefinitionFinder defFinder) {
      super();
      this.compiler = compiler;
      this.defFinder = defFinder;
    }
    private Definition getFunctionDefinition(Node function) {
      DefinitionSite definitionSite = defFinder.getDefinitionForFunction(function);
      Preconditions.checkNotNull(definitionSite);
      Definition definition = definitionSite.definition;
      Preconditions.checkState(!definitionSite.inExterns);
      Preconditions.checkState(definition.getRValue() == function);
      return definition;
    }
    private static Node getArgumentForCallOrNewOrDotCall(UseSite site, final int argIndex) {
      int adjustedArgIndex = argIndex;
      Node parent = site.node.getParent();
      if(NodeUtil.isFunctionObjectCall(parent)) {
        adjustedArgIndex++;
      }
      return NodeUtil.getArgumentForCallOrNew(parent, adjustedArgIndex);
    }
    private boolean canChangeSignature(Node function) {
      Definition definition = getFunctionDefinition(function);
      CodingConvention convention = compiler.getCodingConvention();
      Preconditions.checkState(!definition.isExtern());
      Collection<UseSite> useSites = defFinder.getUseSites(definition);
      for (UseSite site : useSites) {
        Node parent = site.node.getParent();
        if(parent == null) {
          continue ;
        }
        if(parent.isCall() && convention.getClassesDefinedByCall(parent) != null) {
          continue ;
        }
        if(!SimpleDefinitionFinder.isCallOrNewSite(site)) {
          if(!(parent.isGetProp() && NodeUtil.isFunctionObjectCall(parent.getParent()))) {
            return false;
          }
        }
        if(NodeUtil.isFunctionObjectApply(parent)) {
          return false;
        }
        Node nameNode = site.node;
        Collection<Definition> singleSiteDefinitions = defFinder.getDefinitionsReferencedAt(nameNode);
        Preconditions.checkState(singleSiteDefinitions.size() == 1);
        Preconditions.checkState(singleSiteDefinitions.contains(definition));
      }
      return true;
    }
    boolean canModifyCallers(Node function) {
      if(NodeUtil.isVarArgsFunction(function)) {
        return false;
      }
      DefinitionSite defSite = defFinder.getDefinitionForFunction(function);
      if(defSite == null) {
        return false;
      }
      Definition definition = defSite.definition;
      if(!SimpleDefinitionFinder.isSimpleFunctionDeclaration(function)) {
        return false;
      }
      return defFinder.canModifyDefinition(definition);
    }
    private boolean canRemoveArgFromCallSites(Node function, int argIndex) {
      Definition definition = getFunctionDefinition(function);
      for (UseSite site : defFinder.getUseSites(definition)) {
        if(isModifiableCallSite(site)) {
          Node arg = getArgumentForCallOrNewOrDotCall(site, argIndex);
          if(arg != null && NodeUtil.mayHaveSideEffects(arg, compiler)) {
            return false;
          }
        }
        else {
          return false;
        }
      }
      return true;
    }
    private static boolean isModifiableCallSite(UseSite site) {
      return SimpleDefinitionFinder.isCallOrNewSite(site) && !NodeUtil.isFunctionObjectApply(site.node.getParent());
    }
    private boolean markUnreferencedFunctionArgs(Scope scope, Node function, Set<Var> referenced, Node param, int paramIndex, boolean canChangeSignature) {
      if(param != null) {
        boolean hasFollowing = markUnreferencedFunctionArgs(scope, function, referenced, param.getNext(), paramIndex + 1, canChangeSignature);
        Var var = scope.getVar(param.getString());
        if(!referenced.contains(var)) {
          Preconditions.checkNotNull(var);
          boolean modifyAllCallSites = canChangeSignature || !hasFollowing;
          if(modifyAllCallSites) {
            modifyAllCallSites = canRemoveArgFromCallSites(function, paramIndex);
          }
          tryRemoveArgFromCallSites(function, paramIndex, modifyAllCallSites);
          if(modifyAllCallSites || !hasFollowing) {
            toRemove.add(param);
            return hasFollowing;
          }
        }
        return true;
      }
      else {
        tryRemoveAllFollowingArgs(function, paramIndex - 1);
        return false;
      }
    }
    public void applyChanges() {
      for (Node n : toRemove) {
        n.getParent().removeChild(n);
        compiler.reportCodeChange();
      }
      for (Node n : toReplaceWithZero) {
        n.getParent().replaceChild(n, IR.number(0).srcref(n));
        compiler.reportCodeChange();
      }
    }
    public void optimize(Scope fnScope, Set<Var> referenced) {
      Node function = fnScope.getRootNode();
      Preconditions.checkState(function.isFunction());
      Node argList = getFunctionArgList(function);
      boolean changeCallSignature = canChangeSignature(function);
      markUnreferencedFunctionArgs(fnScope, function, referenced, argList.getFirstChild(), 0, changeCallSignature);
    }
    private void tryRemoveAllFollowingArgs(Node function, final int argIndex) {
      Definition definition = getFunctionDefinition(function);
      for (UseSite site : defFinder.getUseSites(definition)) {
        if(!isModifiableCallSite(site)) {
          continue ;
        }
        Node arg = getArgumentForCallOrNewOrDotCall(site, argIndex + 1);
        while(arg != null){
          if(!NodeUtil.mayHaveSideEffects(arg)) {
            toRemove.add(arg);
          }
          arg = arg.getNext();
        }
      }
    }
    private void tryRemoveArgFromCallSites(Node function, int argIndex, boolean canModifyAllSites) {
      Definition definition = getFunctionDefinition(function);
      for (UseSite site : defFinder.getUseSites(definition)) {
        if(isModifiableCallSite(site)) {
          Node arg = getArgumentForCallOrNewOrDotCall(site, argIndex);
          if(arg != null) {
            Node argParent = arg.getParent();
            if(canModifyAllSites || (arg.getNext() == null && !NodeUtil.mayHaveSideEffects(arg, compiler))) {
              toRemove.add(arg);
            }
            else {
              if(!NodeUtil.mayHaveSideEffects(arg, compiler) && (!arg.isNumber() || arg.getDouble() != 0)) {
                toReplaceWithZero.add(arg);
              }
            }
          }
        }
      }
    }
  }
  
  private class Continuation  {
    final private Node node;
    final private Scope scope;
    Continuation(Node node, Scope scope) {
      super();
      this.node = node;
      this.scope = scope;
    }
    void apply() {
      if(NodeUtil.isFunctionDeclaration(node)) {
        traverseFunction(node, scope);
      }
      else {
        for(com.google.javascript.rhino.Node child = node.getFirstChild(); child != null; child = child.getNext()) {
          traverseNode(child, node, scope);
        }
      }
    }
  }
}