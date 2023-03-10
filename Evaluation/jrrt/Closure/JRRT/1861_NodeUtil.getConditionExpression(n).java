package com.google.javascript.jscomp;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.graph.GraphNode;
import com.google.javascript.jscomp.graph.LatticeElement;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.Collection;
import java.util.List;
import java.util.Set;

class MaybeReachingVariableUse extends DataFlowAnalysis<Node, MaybeReachingVariableUse.ReachingUses>  {
  final private Scope jsScope;
  final private Set<Var> escaped;
  MaybeReachingVariableUse(ControlFlowGraph<Node> cfg, Scope jsScope, AbstractCompiler compiler) {
    super(cfg, new ReachingUsesJoinOp());
    this.jsScope = jsScope;
    this.escaped = Sets.newHashSet();
    computeEscaped(jsScope, escaped, compiler);
  }
  Collection<Node> getUses(String name, Node defNode) {
    GraphNode<Node, Branch> n = getCfg().getNode(defNode);
    Preconditions.checkNotNull(n);
    FlowState<ReachingUses> state = n.getAnnotation();
    return state.getOut().mayUseMap.get(jsScope.getVar(name));
  }
  @Override() ReachingUses createEntryLattice() {
    return new ReachingUses();
  }
  @Override() ReachingUses createInitialEstimateLattice() {
    return new ReachingUses();
  }
  @Override() ReachingUses flowThrough(Node n, ReachingUses input) {
    ReachingUses output = new ReachingUses(input);
    boolean conditional = hasExceptionHandler(n);
    computeMayUse(n, n, output, conditional);
    return output;
  }
  private boolean hasExceptionHandler(Node cfgNode) {
    List<DiGraphEdge<Node, Branch>> branchEdges = getCfg().getOutEdges(cfgNode);
    for (DiGraphEdge<Node, Branch> edge : branchEdges) {
      if(edge.getValue() == Branch.ON_EX) {
        return true;
      }
    }
    return false;
  }
  @Override() boolean isForward() {
    return false;
  }
  private void addToUseIfLocal(String name, Node node, ReachingUses use) {
    Var var = jsScope.getVar(name);
    if(var == null || var.scope != jsScope) {
      return ;
    }
    if(!escaped.contains(var)) {
      use.mayUseMap.put(var, node);
    }
  }
  private void computeMayUse(Node n, Node cfgNode, ReachingUses output, boolean conditional) {
    switch (n.getType()){
      case Token.BLOCK:
      case Token.FUNCTION:
      return ;
      case Token.NAME:
      addToUseIfLocal(n.getString(), cfgNode, output);
      return ;
      case Token.WHILE:
      case Token.DO:
      case Token.IF:
      Node var_1861 = NodeUtil.getConditionExpression(n);
      computeMayUse(var_1861, cfgNode, output, conditional);
      return ;
      case Token.FOR:
      if(!NodeUtil.isForIn(n)) {
        computeMayUse(NodeUtil.getConditionExpression(n), cfgNode, output, conditional);
      }
      else {
        Node lhs = n.getFirstChild();
        Node rhs = lhs.getNext();
        if(lhs.isVar()) {
          lhs = lhs.getLastChild();
        }
        if(lhs.isName() && !conditional) {
          removeFromUseIfLocal(lhs.getString(), output);
        }
        computeMayUse(rhs, cfgNode, output, conditional);
      }
      return ;
      case Token.AND:
      case Token.OR:
      computeMayUse(n.getLastChild(), cfgNode, output, true);
      computeMayUse(n.getFirstChild(), cfgNode, output, conditional);
      return ;
      case Token.HOOK:
      computeMayUse(n.getLastChild(), cfgNode, output, true);
      computeMayUse(n.getFirstChild().getNext(), cfgNode, output, true);
      computeMayUse(n.getFirstChild(), cfgNode, output, conditional);
      return ;
      case Token.VAR:
      Node varName = n.getFirstChild();
      Preconditions.checkState(n.hasChildren(), "AST should be normalized");
      if(varName.hasChildren()) {
        computeMayUse(varName.getFirstChild(), cfgNode, output, conditional);
        if(!conditional) {
          removeFromUseIfLocal(varName.getString(), output);
        }
      }
      return ;
      default:
      if(NodeUtil.isAssignmentOp(n) && n.getFirstChild().isName()) {
        Node name = n.getFirstChild();
        if(!conditional) {
          removeFromUseIfLocal(name.getString(), output);
        }
        if(!n.isAssign()) {
          addToUseIfLocal(name.getString(), cfgNode, output);
        }
        computeMayUse(name.getNext(), cfgNode, output, conditional);
      }
      else {
        for(com.google.javascript.rhino.Node c = n.getLastChild(); c != null; c = n.getChildBefore(c)) {
          computeMayUse(c, cfgNode, output, conditional);
        }
      }
    }
  }
  private void removeFromUseIfLocal(String name, ReachingUses use) {
    Var var = jsScope.getVar(name);
    if(var == null || var.scope != jsScope) {
      return ;
    }
    if(!escaped.contains(var)) {
      use.mayUseMap.removeAll(var);
    }
  }
  
  final static class ReachingUses implements LatticeElement  {
    final Multimap<Var, Node> mayUseMap;
    public ReachingUses() {
      super();
      mayUseMap = HashMultimap.create();
    }
    public ReachingUses(ReachingUses other) {
      super();
      mayUseMap = HashMultimap.create(other.mayUseMap);
    }
    @Override() public boolean equals(Object other) {
      return (other instanceof ReachingUses) && ((ReachingUses)other).mayUseMap.equals(this.mayUseMap);
    }
    @Override() public int hashCode() {
      return mayUseMap.hashCode();
    }
  }
  
  private static class ReachingUsesJoinOp implements JoinOp<ReachingUses>  {
    @Override() public ReachingUses apply(List<ReachingUses> from) {
      ReachingUses result = new ReachingUses();
      for (ReachingUses uses : from) {
        result.mayUseMap.putAll(uses.mayUseMap);
      }
      return result;
    }
  }
}