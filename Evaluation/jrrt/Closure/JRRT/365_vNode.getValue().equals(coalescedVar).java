package com.google.javascript.jscomp;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.ControlFlowGraph.AbstractCfgNodeTraversalCallback;
import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.DataFlowAnalysis.FlowState;
import com.google.javascript.jscomp.LiveVariablesAnalysis.LiveVariableLattice;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.jscomp.graph.GraphColoring;
import com.google.javascript.jscomp.graph.GraphColoring.GreedyGraphColoring;
import com.google.javascript.jscomp.graph.GraphNode;
import com.google.javascript.jscomp.graph.LinkedUndirectedGraph;
import com.google.javascript.jscomp.graph.UndiGraph;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.Set;

class CoalesceVariableNames extends AbstractPostOrderCallback implements CompilerPass, ScopedCallback  {
  final private AbstractCompiler compiler;
  final private Deque<GraphColoring<Var, Void>> colorings;
  final private boolean usePseudoNames;
  final private static Comparator<Var> coloringTieBreaker = new Comparator<Var>() {
      @Override() public int compare(Var v1, Var v2) {
        return v1.index - v2.index;
      }
  };
  CoalesceVariableNames(AbstractCompiler compiler, boolean usePseudoNames) {
    super();
    Preconditions.checkState(!compiler.getLifeCycleStage().isNormalized());
    this.compiler = compiler;
    colorings = Lists.newLinkedList();
    this.usePseudoNames = usePseudoNames;
  }
  private UndiGraph<Var, Void> computeVariableNamesInterferenceGraph(NodeTraversal t, ControlFlowGraph<Node> cfg, Set<Var> escaped) {
    UndiGraph<Var, Void> interferenceGraph = LinkedUndirectedGraph.create();
    Scope scope = t.getScope();
    for(java.util.Iterator<com.google.javascript.jscomp.Scope.Var> i = scope.getVars(); i.hasNext(); ) {
      Var v = i.next();
      if(!escaped.contains(v)) {
        if(!v.getParentNode().isFunction()) {
          interferenceGraph.createNode(v);
        }
      }
    }
    for(java.util.Iterator<com.google.javascript.jscomp.Scope.Var> i1 = scope.getVars(); i1.hasNext(); ) {
      Var v1 = i1.next();
      NEXT_VAR_PAIR:
        for(java.util.Iterator<com.google.javascript.jscomp.Scope.Var> i2 = scope.getVars(); i2.hasNext(); ) {
          Var v2 = i2.next();
          if(v1.index >= v2.index) {
            continue ;
          }
          if(!interferenceGraph.hasNode(v1) || !interferenceGraph.hasNode(v2)) {
            continue NEXT_VAR_PAIR;
          }
          if(v1.getParentNode().isParamList() && v2.getParentNode().isParamList()) {
            interferenceGraph.connectIfNotFound(v1, null, v2);
            continue NEXT_VAR_PAIR;
          }
          NEXT_CROSS_CFG_NODE:
            for (DiGraphNode<Node, Branch> cfgNode : cfg.getDirectedGraphNodes()) {
              if(cfg.isImplicitReturn(cfgNode)) {
                continue NEXT_CROSS_CFG_NODE;
              }
              FlowState<LiveVariableLattice> state = cfgNode.getAnnotation();
              if((state.getIn().isLive(v1) && state.getIn().isLive(v2)) || (state.getOut().isLive(v1) && state.getOut().isLive(v2))) {
                interferenceGraph.connectIfNotFound(v1, null, v2);
                continue NEXT_VAR_PAIR;
              }
            }
          NEXT_INTRA_CFG_NODE:
            for (DiGraphNode<Node, Branch> cfgNode : cfg.getDirectedGraphNodes()) {
              if(cfg.isImplicitReturn(cfgNode)) {
                continue NEXT_INTRA_CFG_NODE;
              }
              FlowState<LiveVariableLattice> state = cfgNode.getAnnotation();
              boolean v1OutLive = state.getOut().isLive(v1);
              boolean v2OutLive = state.getOut().isLive(v2);
              CombinedLiveRangeChecker checker = new CombinedLiveRangeChecker(new LiveRangeChecker(v1, v2OutLive ? null : v2), new LiveRangeChecker(v2, v1OutLive ? null : v1));
              NodeTraversal.traverse(compiler, cfgNode.getValue(), checker);
              if(checker.connectIfCrossed(interferenceGraph)) {
                continue NEXT_VAR_PAIR;
              }
            }
        }
    }
    return interferenceGraph;
  }
  private static boolean shouldOptimizeScope(Scope scope) {
    if(scope.isGlobal()) {
      return false;
    }
    if(LiveVariablesAnalysis.MAX_VARIABLES_TO_ANALYZE < scope.getVarCount()) {
      return false;
    }
    return true;
  }
  @Override() public void enterScope(NodeTraversal t) {
    Scope scope = t.getScope();
    if(!shouldOptimizeScope(scope)) {
      return ;
    }
    ControlFlowGraph<Node> cfg = t.getControlFlowGraph();
    LiveVariablesAnalysis liveness = new LiveVariablesAnalysis(cfg, scope, compiler);
    if(scope.getRootNode().getFirstChild().getNext().getChildCount() == 2) {
      liveness.markAllParametersEscaped();
    }
    liveness.analyze();
    UndiGraph<Var, Void> interferenceGraph = computeVariableNamesInterferenceGraph(t, cfg, liveness.getEscapedLocals());
    GraphColoring<Var, Void> coloring = new GreedyGraphColoring<Var, Void>(interferenceGraph, coloringTieBreaker);
    coloring.color();
    colorings.push(coloring);
  }
  @Override() public void exitScope(NodeTraversal t) {
    if(!shouldOptimizeScope(t.getScope())) {
      return ;
    }
    colorings.pop();
  }
  @Override() public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }
  private void removeVarDeclaration(Node name) {
    Node var = name.getParent();
    Node parent = var.getParent();
    if(NodeUtil.isForIn(parent)) {
      var.removeChild(name);
      parent.replaceChild(var, name);
    }
    else 
      if(var.hasOneChild()) {
        if(name.hasChildren()) {
          Node value = name.removeFirstChild();
          var.removeChild(name);
          Node assign = IR.assign(name, value).srcref(name);
          if(!parent.isFor()) {
            assign = NodeUtil.newExpr(assign);
          }
          parent.replaceChild(var, assign);
        }
        else {
          NodeUtil.removeChild(parent, var);
        }
      }
      else {
        if(!name.hasChildren()) {
          var.removeChild(name);
        }
      }
  }
  @Override() public void visit(NodeTraversal t, Node n, Node parent) {
    if(colorings.isEmpty() || !n.isName() || parent.isFunction()) {
      return ;
    }
    Var var = t.getScope().getVar(n.getString());
    GraphNode<Var, ?> vNode = colorings.peek().getGraph().getNode(var);
    if(vNode == null) {
      return ;
    }
    Var coalescedVar = colorings.peek().getPartitionSuperNode(var);
    if(!usePseudoNames) {
      boolean var_365 = vNode.getValue().equals(coalescedVar);
      if(var_365) {
        return ;
      }
      n.setString(coalescedVar.name);
      compiler.reportCodeChange();
      if(parent.isVar()) {
        removeVarDeclaration(n);
      }
    }
    else {
      String pseudoName = null;
      Set<String> allMergedNames = Sets.newTreeSet();
      for(java.util.Iterator<com.google.javascript.jscomp.Scope.Var> i = t.getScope().getVars(); i.hasNext(); ) {
        Var iVar = i.next();
        if(colorings.peek().getGraph().getNode(iVar) != null && coalescedVar.equals(colorings.peek().getPartitionSuperNode(iVar))) {
          allMergedNames.add(iVar.name);
        }
      }
      if(allMergedNames.size() == 1) {
        return ;
      }
      pseudoName = Joiner.on("_").join(allMergedNames);
      while(t.getScope().isDeclared(pseudoName, true)){
        pseudoName += "$";
      }
      n.setString(pseudoName);
      compiler.reportCodeChange();
      if(!vNode.getValue().equals(coalescedVar) && parent.isVar()) {
        removeVarDeclaration(n);
      }
    }
  }
  
  private static class CombinedLiveRangeChecker extends AbstractCfgNodeTraversalCallback  {
    final private LiveRangeChecker callback1;
    final private LiveRangeChecker callback2;
    CombinedLiveRangeChecker(LiveRangeChecker callback1, LiveRangeChecker callback2) {
      super();
      this.callback1 = callback1;
      this.callback2 = callback2;
    }
    boolean connectIfCrossed(UndiGraph<Var, Void> interferenceGraph) {
      if(callback1.crossed || callback2.crossed) {
        Var v1 = callback1.getDef();
        Var v2 = callback2.getDef();
        interferenceGraph.connectIfNotFound(v1, null, v2);
        return true;
      }
      return false;
    }
    @Override() public void visit(NodeTraversal t, Node n, Node parent) {
      if(LiveRangeChecker.shouldVisit(n)) {
        callback1.visit(t, n, parent);
        callback2.visit(t, n, parent);
      }
    }
  }
  
  private static class LiveRangeChecker extends AbstractCfgNodeTraversalCallback  {
    boolean defFound = false;
    boolean crossed = false;
    final private Var def;
    final private Var use;
    public LiveRangeChecker(Var def, Var use) {
      super();
      this.def = def;
      this.use = use;
    }
    Var getDef() {
      return def;
    }
    private static boolean isAssignTo(Var var, Node n, Node parent) {
      if(n.isName() && var.getName().equals(n.getString()) && parent != null) {
        if(parent.isParamList()) {
          return true;
        }
        else 
          if(parent.isVar()) {
            return n.hasChildren();
          }
        return false;
      }
      else {
        Node name = n.getFirstChild();
        return name != null && name.isName() && var.getName().equals(name.getString()) && NodeUtil.isAssignmentOp(n);
      }
    }
    private static boolean isReadFrom(Var var, Node name) {
      return name != null && name.isName() && var.getName().equals(name.getString()) && !NodeUtil.isVarOrSimpleAssignLhs(name, name.getParent());
    }
    public static boolean shouldVisit(Node n) {
      return (n.isName() || (n.hasChildren() && n.getFirstChild().isName()));
    }
    @Override() public void visit(NodeTraversal t, Node n, Node parent) {
      if(!defFound && isAssignTo(def, n, parent)) {
        defFound = true;
      }
      if(defFound && (use == null || isReadFrom(use, n))) {
        crossed = true;
      }
    }
  }
}