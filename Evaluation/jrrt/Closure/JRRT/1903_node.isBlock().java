package com.google.javascript.jscomp;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

final class ControlFlowAnalysis implements Callback, CompilerPass  {
  final private AbstractCompiler compiler;
  private ControlFlowGraph<Node> cfg;
  private Map<Node, Integer> astPosition;
  private Map<DiGraphNode<Node, Branch>, Integer> nodePriorities;
  final private Comparator<DiGraphNode<Node, Branch>> priorityComparator = new Comparator<DiGraphNode<Node, Branch>>() {
      @Override() public int compare(DiGraphNode<Node, Branch> a, DiGraphNode<Node, Branch> b) {
        return astPosition.get(a.getValue()) - astPosition.get(b.getValue());
      }
  };
  private int astPositionCounter;
  private int priorityCounter;
  final private boolean shouldTraverseFunctions;
  final private boolean edgeAnnotations;
  private Node root;
  final private Deque<Node> exceptionHandler = new ArrayDeque<Node>();
  final private Multimap<Node, Node> finallyMap = HashMultimap.create();
  ControlFlowAnalysis(AbstractCompiler compiler, boolean shouldTraverseFunctions, boolean edgeAnnotations) {
    super();
    this.compiler = compiler;
    this.shouldTraverseFunctions = shouldTraverseFunctions;
    this.edgeAnnotations = edgeAnnotations;
  }
  ControlFlowGraph<Node> getCfg() {
    return cfg;
  }
  static Node computeFallThrough(Node n) {
    switch (n.getType()){
      case Token.DO:
      return computeFallThrough(n.getFirstChild());
      case Token.FOR:
      if(NodeUtil.isForIn(n)) {
        return n.getFirstChild().getNext();
      }
      return computeFallThrough(n.getFirstChild());
      case Token.LABEL:
      return computeFallThrough(n.getLastChild());
      default:
      return n;
    }
  }
  static Node computeFollowNode(Node node) {
    return computeFollowNode(node, node, null);
  }
  static Node computeFollowNode(Node node, ControlFlowAnalysis cfa) {
    return computeFollowNode(node, node, cfa);
  }
  private static Node computeFollowNode(Node fromNode, Node node, ControlFlowAnalysis cfa) {
    Node parent = node.getParent();
    if(parent == null || parent.isFunction() || (cfa != null && node == cfa.root)) {
      return null;
    }
    switch (parent.getType()){
      case Token.IF:
      return computeFollowNode(fromNode, parent, cfa);
      case Token.CASE:
      case Token.DEFAULT_CASE:
      if(parent.getNext() != null) {
        if(parent.getNext().isCase()) {
          return parent.getNext().getFirstChild().getNext();
        }
        else 
          if(parent.getNext().isDefaultCase()) {
            return parent.getNext().getFirstChild();
          }
          else {
            Preconditions.checkState(false, "Not reachable");
          }
      }
      else {
        return computeFollowNode(fromNode, parent, cfa);
      }
      break ;
      case Token.FOR:
      if(NodeUtil.isForIn(parent)) {
        return parent;
      }
      else {
        return parent.getFirstChild().getNext().getNext();
      }
      case Token.WHILE:
      case Token.DO:
      return parent;
      case Token.TRY:
      if(parent.getFirstChild() == node) {
        if(NodeUtil.hasFinally(parent)) {
          return computeFallThrough(parent.getLastChild());
        }
        else {
          return computeFollowNode(fromNode, parent, cfa);
        }
      }
      else 
        if(NodeUtil.getCatchBlock(parent) == node) {
          if(NodeUtil.hasFinally(parent)) {
            return computeFallThrough(node.getNext());
          }
          else {
            return computeFollowNode(fromNode, parent, cfa);
          }
        }
        else 
          if(parent.getLastChild() == node) {
            if(cfa != null) {
              for (Node finallyNode : cfa.finallyMap.get(parent)) {
                cfa.createEdge(fromNode, Branch.ON_EX, finallyNode);
              }
            }
            return computeFollowNode(fromNode, parent, cfa);
          }
    }
    Node nextSibling = node.getNext();
    while(nextSibling != null && nextSibling.isFunction()){
      nextSibling = nextSibling.getNext();
    }
    if(nextSibling != null) {
      return computeFallThrough(nextSibling);
    }
    else {
      return computeFollowNode(fromNode, parent, cfa);
    }
  }
  static Node getCatchHandlerForBlock(Node block) {
    if(block.isBlock() && block.getParent().isTry() && block.getParent().getFirstChild() == block) {
      for(com.google.javascript.rhino.Node s = block.getNext(); s != null; s = s.getNext()) {
        if(NodeUtil.hasCatchHandler(s)) {
          return s.getFirstChild();
        }
      }
    }
    return null;
  }
  static Node getExceptionHandler(Node n) {
    for(com.google.javascript.rhino.Node cur = n; !cur.isScript() && !cur.isFunction(); cur = cur.getParent()) {
      Node catchNode = getCatchHandlerForBlock(cur);
      if(catchNode != null) {
        return catchNode;
      }
    }
    return null;
  }
  private static Node getNextSiblingOfType(Node first, int ... types) {
    for(com.google.javascript.rhino.Node c = first; c != null; c = c.getNext()) {
      for (int type : types) {
        if(c.getType() == type) {
          return c;
        }
      }
    }
    return null;
  }
  static boolean isBreakStructure(Node n, boolean labeled) {
    switch (n.getType()){
      case Token.FOR:
      case Token.DO:
      case Token.WHILE:
      case Token.SWITCH:
      return true;
      case Token.BLOCK:
      case Token.IF:
      case Token.TRY:
      return labeled;
      default:
      return false;
    }
  }
  public static boolean isBreakTarget(Node target, String label) {
    return isBreakStructure(target, label != null) && matchLabel(target.getParent(), label);
  }
  static boolean isContinueStructure(Node n) {
    switch (n.getType()){
      case Token.FOR:
      case Token.DO:
      case Token.WHILE:
      return true;
      default:
      return false;
    }
  }
  private static boolean isContinueTarget(Node target, Node parent, String label) {
    return isContinueStructure(target) && matchLabel(parent, label);
  }
  private static boolean matchLabel(Node target, String label) {
    if(label == null) {
      return true;
    }
    while(target.isLabel()){
      if(target.getFirstChild().getString().equals(label)) {
        return true;
      }
      target = target.getParent();
    }
    return false;
  }
  public static boolean mayThrowException(Node n) {
    switch (n.getType()){
      case Token.CALL:
      case Token.GETPROP:
      case Token.GETELEM:
      case Token.THROW:
      case Token.NEW:
      case Token.ASSIGN:
      case Token.INC:
      case Token.DEC:
      case Token.INSTANCEOF:
      return true;
      case Token.FUNCTION:
      return false;
    }
    for(com.google.javascript.rhino.Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      if(!ControlFlowGraph.isEnteringNewCfgNode(c) && mayThrowException(c)) {
        return true;
      }
    }
    return false;
  }
  @Override() public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
    astPosition.put(n, astPositionCounter++);
    switch (n.getType()){
      case Token.FUNCTION:
      if(shouldTraverseFunctions || n == cfg.getEntry().getValue()) {
        exceptionHandler.push(n);
        return true;
      }
      return false;
      case Token.TRY:
      exceptionHandler.push(n);
      return true;
    }
    if(parent != null) {
      switch (parent.getType()){
        case Token.FOR:
        return n == parent.getLastChild();
        case Token.IF:
        case Token.WHILE:
        case Token.WITH:
        return n != parent.getFirstChild();
        case Token.DO:
        return n != parent.getFirstChild().getNext();
        case Token.SWITCH:
        case Token.CASE:
        case Token.CATCH:
        case Token.LABEL:
        return n != parent.getFirstChild();
        case Token.FUNCTION:
        return n == parent.getFirstChild().getNext().getNext();
        case Token.CONTINUE:
        case Token.BREAK:
        case Token.EXPR_RESULT:
        case Token.VAR:
        case Token.RETURN:
        case Token.THROW:
        return false;
        case Token.TRY:
        if(n == parent.getFirstChild().getNext()) {
          Preconditions.checkState(exceptionHandler.peek() == parent);
          exceptionHandler.pop();
        }
      }
    }
    return true;
  }
  private void connectToPossibleExceptionHandler(Node cfgNode, Node target) {
    if(mayThrowException(target) && !exceptionHandler.isEmpty()) {
      Node lastJump = cfgNode;
      for (Node handler : exceptionHandler) {
        if(handler.isFunction()) {
          return ;
        }
        Preconditions.checkState(handler.isTry());
        Node catchBlock = NodeUtil.getCatchBlock(handler);
        if(!NodeUtil.hasCatchHandler(catchBlock)) {
          if(lastJump == cfgNode) {
            createEdge(cfgNode, Branch.ON_EX, handler.getLastChild());
          }
          else {
            finallyMap.put(lastJump, handler.getLastChild());
          }
        }
        else {
          if(lastJump == cfgNode) {
            createEdge(cfgNode, Branch.ON_EX, catchBlock);
            return ;
          }
          else {
            finallyMap.put(lastJump, catchBlock);
          }
        }
        lastJump = handler;
      }
    }
  }
  private void createEdge(Node fromNode, ControlFlowGraph.Branch branch, Node toNode) {
    cfg.createNode(fromNode);
    cfg.createNode(toNode);
    cfg.connectIfNotFound(fromNode, branch, toNode);
  }
  private void handleBreak(Node node) {
    String label = null;
    if(node.hasChildren()) {
      label = node.getFirstChild().getString();
    }
    Node cur;
    Node previous = null;
    Node lastJump;
    Node parent = node.getParent();
    for(cur = node, lastJump = node; !isBreakTarget(cur, label); cur = parent, parent = parent.getParent()) {
      if(cur.isTry() && NodeUtil.hasFinally(cur) && cur.getLastChild() != previous) {
        if(lastJump == node) {
          createEdge(lastJump, Branch.UNCOND, computeFallThrough(cur.getLastChild()));
        }
        else {
          finallyMap.put(lastJump, computeFallThrough(cur.getLastChild()));
        }
        lastJump = cur;
      }
      if(parent == null) {
        if(compiler.isIdeMode()) {
          return ;
        }
        else {
          throw new IllegalStateException("Cannot find break target.");
        }
      }
      previous = cur;
    }
    if(lastJump == node) {
      createEdge(lastJump, Branch.UNCOND, computeFollowNode(cur, this));
    }
    else {
      finallyMap.put(lastJump, computeFollowNode(cur, this));
    }
  }
  private void handleCase(Node node) {
    createEdge(node, Branch.ON_TRUE, node.getFirstChild().getNext());
    Node next = getNextSiblingOfType(node.getNext(), Token.CASE);
    if(next != null) {
      Preconditions.checkState(next.isCase());
      createEdge(node, Branch.ON_FALSE, next);
    }
    else {
      Node parent = node.getParent();
      Node deflt = getNextSiblingOfType(parent.getFirstChild().getNext(), Token.DEFAULT_CASE);
      if(deflt != null) {
        createEdge(node, Branch.ON_FALSE, deflt);
      }
      else {
        createEdge(node, Branch.ON_FALSE, computeFollowNode(node, this));
      }
    }
    connectToPossibleExceptionHandler(node, node.getFirstChild());
  }
  private void handleCatch(Node node) {
    createEdge(node, Branch.UNCOND, node.getLastChild());
  }
  private void handleContinue(Node node) {
    String label = null;
    if(node.hasChildren()) {
      label = node.getFirstChild().getString();
    }
    Node cur;
    Node previous = null;
    Node lastJump;
    Node parent = node.getParent();
    for(cur = node, lastJump = node; !isContinueTarget(cur, parent, label); cur = parent, parent = parent.getParent()) {
      if(cur.isTry() && NodeUtil.hasFinally(cur) && cur.getLastChild() != previous) {
        if(lastJump == node) {
          createEdge(lastJump, Branch.UNCOND, cur.getLastChild());
        }
        else {
          finallyMap.put(lastJump, computeFallThrough(cur.getLastChild()));
        }
        lastJump = cur;
      }
      Preconditions.checkState(parent != null, "Cannot find continue target.");
      previous = cur;
    }
    Node iter = cur;
    if(cur.getChildCount() == 4) {
      iter = cur.getFirstChild().getNext().getNext();
    }
    if(lastJump == node) {
      createEdge(node, Branch.UNCOND, iter);
    }
    else {
      finallyMap.put(lastJump, iter);
    }
  }
  private void handleDefault(Node node) {
    createEdge(node, Branch.UNCOND, node.getFirstChild());
  }
  private void handleDo(Node node) {
    createEdge(node, Branch.ON_TRUE, computeFallThrough(node.getFirstChild()));
    createEdge(node, Branch.ON_FALSE, computeFollowNode(node, this));
    connectToPossibleExceptionHandler(node, NodeUtil.getConditionExpression(node));
  }
  private void handleExpr(Node node) {
    createEdge(node, Branch.UNCOND, computeFollowNode(node, this));
    connectToPossibleExceptionHandler(node, node);
  }
  private void handleFor(Node forNode) {
    if(forNode.getChildCount() == 4) {
      Node init = forNode.getFirstChild();
      Node cond = init.getNext();
      Node iter = cond.getNext();
      Node body = iter.getNext();
      createEdge(init, Branch.UNCOND, forNode);
      createEdge(forNode, Branch.ON_TRUE, computeFallThrough(body));
      createEdge(forNode, Branch.ON_FALSE, computeFollowNode(forNode, this));
      createEdge(iter, Branch.UNCOND, forNode);
      connectToPossibleExceptionHandler(init, init);
      connectToPossibleExceptionHandler(forNode, cond);
      connectToPossibleExceptionHandler(iter, iter);
    }
    else {
      Node item = forNode.getFirstChild();
      Node collection = item.getNext();
      Node body = collection.getNext();
      createEdge(collection, Branch.UNCOND, forNode);
      createEdge(forNode, Branch.ON_TRUE, computeFallThrough(body));
      createEdge(forNode, Branch.ON_FALSE, computeFollowNode(forNode, this));
      connectToPossibleExceptionHandler(forNode, collection);
    }
  }
  private void handleFunction(Node node) {
    Preconditions.checkState(node.getChildCount() >= 3);
    createEdge(node, Branch.UNCOND, computeFallThrough(node.getFirstChild().getNext().getNext()));
    Preconditions.checkState(exceptionHandler.peek() == node);
    exceptionHandler.pop();
  }
  private void handleIf(Node node) {
    Node thenBlock = node.getFirstChild().getNext();
    Node elseBlock = thenBlock.getNext();
    createEdge(node, Branch.ON_TRUE, computeFallThrough(thenBlock));
    if(elseBlock == null) {
      createEdge(node, Branch.ON_FALSE, computeFollowNode(node, this));
    }
    else {
      createEdge(node, Branch.ON_FALSE, computeFallThrough(elseBlock));
    }
    connectToPossibleExceptionHandler(node, NodeUtil.getConditionExpression(node));
  }
  private void handleReturn(Node node) {
    Node lastJump = null;
    for(java.util.Iterator<com.google.javascript.rhino.Node> iter = exceptionHandler.iterator(); iter.hasNext(); ) {
      Node curHandler = iter.next();
      if(curHandler.isFunction()) {
        break ;
      }
      if(NodeUtil.hasFinally(curHandler)) {
        if(lastJump == null) {
          createEdge(node, Branch.UNCOND, curHandler.getLastChild());
        }
        else {
          finallyMap.put(lastJump, computeFallThrough(curHandler.getLastChild()));
        }
        lastJump = curHandler;
      }
    }
    if(node.hasChildren()) {
      connectToPossibleExceptionHandler(node, node.getFirstChild());
    }
    if(lastJump == null) {
      createEdge(node, Branch.UNCOND, null);
    }
    else {
      finallyMap.put(lastJump, null);
    }
  }
  private void handleStmt(Node node) {
    createEdge(node, Branch.UNCOND, computeFollowNode(node, this));
    connectToPossibleExceptionHandler(node, node);
  }
  private void handleStmtList(Node node) {
    Node parent = node.getParent();
    boolean var_1903 = node.isBlock();
    if(var_1903 && parent != null && parent.isTry() && NodeUtil.getCatchBlock(parent) == node && !NodeUtil.hasCatchHandler(node)) {
      return ;
    }
    Node child = node.getFirstChild();
    while(child != null && child.isFunction()){
      child = child.getNext();
    }
    if(child != null) {
      createEdge(node, Branch.UNCOND, computeFallThrough(child));
    }
    else {
      createEdge(node, Branch.UNCOND, computeFollowNode(node, this));
    }
    if(parent != null) {
      switch (parent.getType()){
        case Token.DEFAULT_CASE:
        case Token.CASE:
        case Token.TRY:
        break ;
        default:
        if(node.isBlock() && node.isSyntheticBlock()) {
          createEdge(node, Branch.SYN_BLOCK, computeFollowNode(node, this));
        }
        break ;
      }
    }
  }
  private void handleSwitch(Node node) {
    Node next = getNextSiblingOfType(node.getFirstChild().getNext(), Token.CASE, Token.EMPTY);
    if(next != null) {
      createEdge(node, Branch.UNCOND, next);
    }
    else {
      if(node.getFirstChild().getNext() != null) {
        createEdge(node, Branch.UNCOND, node.getFirstChild().getNext());
      }
      else {
        createEdge(node, Branch.UNCOND, computeFollowNode(node, this));
      }
    }
    connectToPossibleExceptionHandler(node, node.getFirstChild());
  }
  private void handleThrow(Node node) {
    connectToPossibleExceptionHandler(node, node);
  }
  private void handleTry(Node node) {
    createEdge(node, Branch.UNCOND, node.getFirstChild());
  }
  private void handleWhile(Node node) {
    createEdge(node, Branch.ON_TRUE, computeFallThrough(node.getFirstChild().getNext()));
    createEdge(node, Branch.ON_FALSE, computeFollowNode(node, this));
    connectToPossibleExceptionHandler(node, NodeUtil.getConditionExpression(node));
  }
  private void handleWith(Node node) {
    createEdge(node, Branch.UNCOND, node.getLastChild());
    connectToPossibleExceptionHandler(node, node.getFirstChild());
  }
  private void prioritizeFromEntryNode(DiGraphNode<Node, Branch> entry) {
    PriorityQueue<DiGraphNode<Node, Branch>> worklist = new PriorityQueue<DiGraphNode<Node, Branch>>(10, priorityComparator);
    worklist.add(entry);
    while(!worklist.isEmpty()){
      DiGraphNode<Node, Branch> current = worklist.remove();
      if(nodePriorities.containsKey(current)) {
        continue ;
      }
      nodePriorities.put(current, ++priorityCounter);
      List<DiGraphNode<Node, Branch>> successors = cfg.getDirectedSuccNodes(current);
      for (DiGraphNode<Node, Branch> candidate : successors) {
        worklist.add(candidate);
      }
    }
  }
  @Override() public void process(Node externs, Node root) {
    this.root = root;
    astPositionCounter = 0;
    astPosition = Maps.newHashMap();
    nodePriorities = Maps.newHashMap();
    cfg = new AstControlFlowGraph(computeFallThrough(root), nodePriorities, edgeAnnotations);
    NodeTraversal.traverse(compiler, root, this);
    astPosition.put(null, ++astPositionCounter);
    priorityCounter = 0;
    DiGraphNode<Node, Branch> entry = cfg.getEntry();
    prioritizeFromEntryNode(entry);
    if(shouldTraverseFunctions) {
      for (DiGraphNode<Node, Branch> candidate : cfg.getDirectedGraphNodes()) {
        Node value = candidate.getValue();
        if(value != null && value.isFunction()) {
          Preconditions.checkState(!nodePriorities.containsKey(candidate) || candidate == entry);
          prioritizeFromEntryNode(candidate);
        }
      }
    }
    for (DiGraphNode<Node, Branch> candidate : cfg.getDirectedGraphNodes()) {
      if(!nodePriorities.containsKey(candidate)) {
        nodePriorities.put(candidate, ++priorityCounter);
      }
    }
    nodePriorities.put(cfg.getImplicitReturn(), ++priorityCounter);
  }
  @Override() public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()){
      case Token.IF:
      handleIf(n);
      return ;
      case Token.WHILE:
      handleWhile(n);
      return ;
      case Token.DO:
      handleDo(n);
      return ;
      case Token.FOR:
      handleFor(n);
      return ;
      case Token.SWITCH:
      handleSwitch(n);
      return ;
      case Token.CASE:
      handleCase(n);
      return ;
      case Token.DEFAULT_CASE:
      handleDefault(n);
      return ;
      case Token.BLOCK:
      case Token.SCRIPT:
      handleStmtList(n);
      return ;
      case Token.FUNCTION:
      handleFunction(n);
      return ;
      case Token.EXPR_RESULT:
      handleExpr(n);
      return ;
      case Token.THROW:
      handleThrow(n);
      return ;
      case Token.TRY:
      handleTry(n);
      return ;
      case Token.CATCH:
      handleCatch(n);
      return ;
      case Token.BREAK:
      handleBreak(n);
      return ;
      case Token.CONTINUE:
      handleContinue(n);
      return ;
      case Token.RETURN:
      handleReturn(n);
      return ;
      case Token.WITH:
      handleWith(n);
      return ;
      case Token.LABEL:
      return ;
      default:
      handleStmt(n);
      return ;
    }
  }
  
  private static class AstControlFlowGraph extends ControlFlowGraph<Node>  {
    final private Map<DiGraphNode<Node, Branch>, Integer> priorities;
    private AstControlFlowGraph(Node entry, Map<DiGraphNode<Node, Branch>, Integer> priorities, boolean edgeAnnotations) {
      super(entry, true, edgeAnnotations);
      this.priorities = priorities;
    }
    @Override() public Comparator<DiGraphNode<Node, Branch>> getOptionalNodeComparator(boolean isForward) {
      if(isForward) {
        return new Comparator<DiGraphNode<Node, Branch>>() {
            @Override() public int compare(DiGraphNode<Node, Branch> n1, DiGraphNode<Node, Branch> n2) {
              return getPosition(n1) - getPosition(n2);
            }
        };
      }
      else {
        return new Comparator<DiGraphNode<Node, Branch>>() {
            @Override() public int compare(DiGraphNode<Node, Branch> n1, DiGraphNode<Node, Branch> n2) {
              return getPosition(n2) - getPosition(n1);
            }
        };
      }
    }
    private int getPosition(DiGraphNode<Node, Branch> n) {
      Integer priority = priorities.get(n);
      Preconditions.checkNotNull(priority);
      return priority;
    }
  }
}