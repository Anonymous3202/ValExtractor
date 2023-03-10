package com.google.javascript.jscomp;
import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.ControlFlowGraph.AbstractCfgNodeTraversalCallback;
import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.jscomp.graph.GraphNode;
import com.google.javascript.jscomp.graph.LatticeElement;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import javax.annotation.Nullable;

final class MustBeReachingVariableDef extends DataFlowAnalysis<Node, MustBeReachingVariableDef.MustDef>  {
  final private Scope jsScope;
  final private AbstractCompiler compiler;
  final private Set<Var> escaped;
  MustBeReachingVariableDef(ControlFlowGraph<Node> cfg, Scope jsScope, AbstractCompiler compiler) {
    super(cfg, new MustDefJoin());
    this.jsScope = jsScope;
    this.compiler = compiler;
    this.escaped = Sets.newHashSet();
    computeEscaped(jsScope, escaped, compiler);
  }
  Definition getDef(String name, Node useNode) {
    Preconditions.checkArgument(getCfg().hasNode(useNode));
    GraphNode<Node, Branch> n = getCfg().getNode(useNode);
    FlowState<MustDef> state = n.getAnnotation();
    return state.getIn().reachingDef.get(jsScope.getVar(name));
  }
  @Override() MustDef createEntryLattice() {
    return new MustDef(jsScope.getVars());
  }
  @Override() MustDef createInitialEstimateLattice() {
    return new MustDef();
  }
  @Override() MustDef flowThrough(Node n, MustDef input) {
    MustDef output = new MustDef(input);
    computeMustDef(n, n, output, false);
    return output;
  }
  Node getDefNode(String name, Node useNode) {
    Definition def = getDef(name, useNode);
    return def == null ? null : def.node;
  }
  boolean dependsOnOuterScopeVars(Definition def) {
    if(def.unknownDependencies) {
      return true;
    }
    for (Var s : def.depends) {
      if(s.scope != jsScope) {
        return true;
      }
    }
    return false;
  }
  @Override() boolean isForward() {
    return true;
  }
  private boolean isParameter(Var v) {
    return v.getParentNode().isParamList();
  }
  private void addToDefIfLocal(String name, @Nullable() Node node, @Nullable() Node rValue, MustDef def) {
    Var var = jsScope.getVar(name);
    if(var == null || var.scope != jsScope) {
      return ;
    }
    for (Var other : def.reachingDef.keySet()) {
      Definition otherDef = def.reachingDef.get(other);
      if(otherDef == null) {
        continue ;
      }
      if(otherDef.depends.contains(var)) {
        def.reachingDef.put(other, null);
      }
    }
    if(!escaped.contains(var)) {
      if(node == null) {
        def.reachingDef.put(var, null);
      }
      else {
        Definition definition = new Definition(node);
        if(rValue != null) {
          computeDependence(definition, rValue);
        }
        def.reachingDef.put(var, definition);
      }
    }
  }
  private void computeDependence(final Definition def, Node rValue) {
    NodeTraversal.traverse(compiler, rValue, new AbstractCfgNodeTraversalCallback() {
        @Override() public void visit(NodeTraversal t, Node n, Node parent) {
          if(n.isName()) {
            Var dep = jsScope.getVar(n.getString());
            if(dep == null) {
              def.unknownDependencies = true;
            }
            else {
              def.depends.add(dep);
            }
          }
        }
    });
  }
  private void computeMustDef(Node n, Node cfgNode, MustDef output, boolean conditional) {
    switch (n.getType()){
      case Token.BLOCK:
      case Token.FUNCTION:
      return ;
      case Token.WHILE:
      case Token.DO:
      case Token.IF:
      computeMustDef(NodeUtil.getConditionExpression(n), cfgNode, output, conditional);
      return ;
      case Token.FOR:
      if(!NodeUtil.isForIn(n)) {
        computeMustDef(NodeUtil.getConditionExpression(n), cfgNode, output, conditional);
      }
      else {
        Node lhs = n.getFirstChild();
        Node rhs = lhs.getNext();
        if(lhs.isVar()) {
          lhs = lhs.getLastChild();
        }
        if(lhs.isName()) {
          addToDefIfLocal(lhs.getString(), cfgNode, rhs, output);
        }
      }
      return ;
      case Token.AND:
      case Token.OR:
      computeMustDef(n.getFirstChild(), cfgNode, output, conditional);
      computeMustDef(n.getLastChild(), cfgNode, output, true);
      return ;
      case Token.HOOK:
      computeMustDef(n.getFirstChild(), cfgNode, output, conditional);
      Node var_1092 = n.getFirstChild();
      computeMustDef(var_1092.getNext(), cfgNode, output, true);
      computeMustDef(n.getLastChild(), cfgNode, output, true);
      return ;
      case Token.VAR:
      for(com.google.javascript.rhino.Node c = n.getFirstChild(); c != null; c = c.getNext()) {
        if(c.hasChildren()) {
          computeMustDef(c.getFirstChild(), cfgNode, output, conditional);
          addToDefIfLocal(c.getString(), conditional ? null : cfgNode, c.getFirstChild(), output);
        }
      }
      return ;
      default:
      if(NodeUtil.isAssignmentOp(n)) {
        if(n.getFirstChild().isName()) {
          Node name = n.getFirstChild();
          computeMustDef(name.getNext(), cfgNode, output, conditional);
          addToDefIfLocal(name.getString(), conditional ? null : cfgNode, n.getLastChild(), output);
          return ;
        }
        else 
          if(NodeUtil.isGet(n.getFirstChild())) {
            Node obj = n.getFirstChild().getFirstChild();
            if(obj.isName() && "arguments".equals(obj.getString())) {
              escapeParameters(output);
            }
          }
      }
      if(n.isName() && "arguments".equals(n.getString())) {
        escapeParameters(output);
      }
      if(n.isDec() || n.isInc()) {
        Node target = n.getFirstChild();
        if(target.isName()) {
          addToDefIfLocal(target.getString(), conditional ? null : cfgNode, null, output);
          return ;
        }
      }
      for(com.google.javascript.rhino.Node c = n.getFirstChild(); c != null; c = c.getNext()) {
        computeMustDef(c, cfgNode, output, conditional);
      }
    }
  }
  private void escapeParameters(MustDef output) {
    for(java.util.Iterator<com.google.javascript.jscomp.Scope.Var> i = jsScope.getVars(); i.hasNext(); ) {
      Var v = i.next();
      if(isParameter(v)) {
        output.reachingDef.put(v, null);
      }
    }
    for (Entry<Var, Definition> pair : output.reachingDef.entrySet()) {
      Definition value = pair.getValue();
      if(value == null) {
        continue ;
      }
      for (Var dep : value.depends) {
        if(isParameter(dep)) {
          output.reachingDef.put(pair.getKey(), null);
        }
      }
    }
  }
  
  static class Definition  {
    final Node node;
    final Set<Var> depends = Sets.newHashSet();
    private boolean unknownDependencies = false;
    Definition(Node node) {
      super();
      this.node = node;
    }
    @Override() public boolean equals(Object other) {
      if(!(other instanceof Definition)) {
        return false;
      }
      Definition otherDef = (Definition)other;
      return otherDef.node == node;
    }
  }
  
  final static class MustDef implements LatticeElement  {
    final Map<Var, Definition> reachingDef;
    public MustDef() {
      super();
      reachingDef = Maps.newHashMap();
    }
    public MustDef(Iterator<Var> vars) {
      this();
      while(vars.hasNext()){
        Var var = vars.next();
        reachingDef.put(var, new Definition(var.scope.getRootNode()));
      }
    }
    public MustDef(MustDef other) {
      super();
      reachingDef = Maps.newHashMap(other.reachingDef);
    }
    @Override() public boolean equals(Object other) {
      return (other instanceof MustDef) && ((MustDef)other).reachingDef.equals(this.reachingDef);
    }
  }
  
  private static class MustDefJoin extends JoinOp.BinaryJoinOp<MustDef>  {
    @Override() public MustDef apply(MustDef a, MustDef b) {
      MustDef result = new MustDef();
      Map<Var, Definition> resultMap = result.reachingDef;
      for (Map.Entry<Var, Definition> varEntry : a.reachingDef.entrySet()) {
        Var var = varEntry.getKey();
        Definition aDef = varEntry.getValue();
        if(aDef == null) {
          resultMap.put(var, null);
          continue ;
        }
        Node aNode = aDef.node;
        if(b.reachingDef.containsKey(var)) {
          Definition bDef = b.reachingDef.get(var);
          if(aDef.equals(bDef)) {
            resultMap.put(var, aDef);
          }
          else {
            resultMap.put(var, null);
          }
        }
        else {
          resultMap.put(var, aDef);
        }
      }
      for (Map.Entry<Var, Definition> entry : b.reachingDef.entrySet()) {
        Var var = entry.getKey();
        if(!a.reachingDef.containsKey(var)) {
          resultMap.put(var, entry.getValue());
        }
      }
      return result;
    }
  }
}