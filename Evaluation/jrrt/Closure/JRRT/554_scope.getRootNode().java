package com.google.javascript.jscomp;
import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.AbstractShallowCallback;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.graph.GraphReachability;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

class UnreachableCodeElimination extends AbstractPostOrderCallback implements CompilerPass, ScopedCallback  {
  final private static Logger logger = Logger.getLogger(UnreachableCodeElimination.class.getName());
  final private AbstractCompiler compiler;
  final private boolean removeNoOpStatements;
  UnreachableCodeElimination(AbstractCompiler compiler, boolean removeNoOpStatements) {
    super();
    this.compiler = compiler;
    this.removeNoOpStatements = removeNoOpStatements;
  }
  @Override() public void enterScope(NodeTraversal t) {
  }
  @Override() public void exitScope(NodeTraversal t) {
    Scope scope = t.getScope();
    ControlFlowAnalysis cfa = new ControlFlowAnalysis(compiler, false, false);
    Node var_554 = scope.getRootNode();
    cfa.process(null, var_554);
    ControlFlowGraph<Node> cfg = cfa.getCfg();
    new GraphReachability<Node, ControlFlowGraph.Branch>(cfg).compute(cfg.getEntry().getValue());
    Node root = scope.getRootNode();
    if(scope.isLocal()) {
      root = root.getLastChild();
    }
    NodeTraversal.traverse(compiler, root, new EliminationPass(cfg));
  }
  @Override() public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }
  @Override() public void visit(NodeTraversal t, Node n, Node parent) {
  }
  
  private class EliminationPass extends AbstractShallowCallback  {
    final private ControlFlowGraph<Node> cfg;
    private EliminationPass(ControlFlowGraph<Node> cfg) {
      super();
      this.cfg = cfg;
    }
    private Node computeFollowing(Node n) {
      Node next = ControlFlowAnalysis.computeFollowNode(n);
      while(next != null && next.isBlock()){
        if(next.hasChildren()) {
          next = next.getFirstChild();
        }
        else {
          next = computeFollowing(next);
        }
      }
      return next;
    }
    @SuppressWarnings(value = {"fallthrough", }) private Node tryRemoveUnconditionalBranching(Node n) {
      if(n == null) {
        return n;
      }
      DiGraphNode<Node, Branch> gNode = cfg.getDirectedGraphNode(n);
      if(gNode == null) {
        return n;
      }
      switch (n.getType()){
        case Token.RETURN:
        if(n.hasChildren()) {
          break ;
        }
        case Token.BREAK:
        case Token.CONTINUE:
        List<DiGraphEdge<Node, Branch>> outEdges = gNode.getOutEdges();
        if(outEdges.size() == 1 && (n.getNext() == null || n.getNext().isFunction())) {
          Preconditions.checkState(outEdges.get(0).getValue() == Branch.UNCOND);
          Node fallThrough = computeFollowing(n);
          Node nextCfgNode = outEdges.get(0).getDestination().getValue();
          if(nextCfgNode == fallThrough) {
            removeDeadExprStatementSafely(n);
            return fallThrough;
          }
        }
      }
      return n;
    }
    private void removeDeadExprStatementSafely(Node n) {
      Node parent = n.getParent();
      if(n.isEmpty() || (n.isBlock() && !n.hasChildren())) {
        return ;
      }
      if(NodeUtil.isForIn(parent)) {
        return ;
      }
      switch (n.getType()){
        case Token.DO:
        return ;
        case Token.BLOCK:
        if(parent.isTry()) {
          if(NodeUtil.isTryCatchNodeContainer(n)) {
            return ;
          }
        }
        break ;
        case Token.CATCH:
        Node tryNode = parent.getParent();
        NodeUtil.maybeAddFinally(tryNode);
        break ;
      }
      if(n.isVar() && !n.getFirstChild().hasChildren()) {
        return ;
      }
      NodeUtil.redeclareVarsInsideBranch(n);
      compiler.reportCodeChange();
      if(logger.isLoggable(Level.FINE)) {
        logger.fine("Removing " + n.toString());
      }
      NodeUtil.removeChild(n.getParent(), n);
    }
    @Override() public void visit(NodeTraversal t, Node n, Node parent) {
      if(parent == null) {
        return ;
      }
      if(n.isFunction() || n.isScript()) {
        return ;
      }
      DiGraphNode<Node, Branch> gNode = cfg.getDirectedGraphNode(n);
      if(gNode == null) {
        return ;
      }
      if(gNode.getAnnotation() != GraphReachability.REACHABLE || (removeNoOpStatements && !NodeUtil.mayHaveSideEffects(n, compiler))) {
        removeDeadExprStatementSafely(n);
        return ;
      }
      tryRemoveUnconditionalBranching(n);
    }
  }
}