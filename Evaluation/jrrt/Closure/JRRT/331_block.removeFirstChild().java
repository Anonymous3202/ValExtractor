package com.google.javascript.jscomp;
import com.google.common.base.Preconditions;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

public class StatementFusion extends AbstractPeepholeOptimization  {
  private static Node fuseExpressionIntoExpression(Node exp1, Node exp2) {
    Node comma = new Node(Token.COMMA, exp1);
    comma.copyInformationFrom(exp2);
    if(exp2.isComma()) {
      Node leftMostChild = exp2;
      while(leftMostChild.isComma()){
        leftMostChild = leftMostChild.getFirstChild();
      }
      Node parent = leftMostChild.getParent();
      comma.addChildToBack(leftMostChild.detachFromParent());
      parent.addChildToFront(comma);
      return exp2;
    }
    else {
      comma.addChildToBack(exp2);
      return comma;
    }
  }
  @Override() Node optimizeSubtree(Node n) {
    if(!n.getParent().isFunction() && canFuseIntoOneStatement(n)) {
      fuseIntoOneStatement(n);
      reportCodeChange();
    }
    return n;
  }
  private boolean canFuseIntoOneStatement(Node block) {
    if(!block.isBlock()) {
      return false;
    }
    if(!block.hasChildren() || block.hasOneChild()) {
      return false;
    }
    Node last = block.getLastChild();
    for(com.google.javascript.rhino.Node c = block.getFirstChild(); c != null; c = c.getNext()) {
      if(!c.isExprResult() && c != last) {
        return false;
      }
    }
    switch (last.getType()){
      case Token.IF:
      case Token.THROW:
      case Token.SWITCH:
      case Token.EXPR_RESULT:
      return true;
      case Token.RETURN:
      return last.hasChildren();
      case Token.FOR:
      return NodeUtil.isForIn(last) && !mayHaveSideEffects(last.getFirstChild());
    }
    return false;
  }
  private static void fuseExpresssonIntoFirstChild(Node exp, Node stmt) {
    Node val = stmt.removeFirstChild();
    Node comma = fuseExpressionIntoExpression(exp, val);
    stmt.addChildToFront(comma);
  }
  private static void fuseExpresssonIntoSecondChild(Node exp, Node stmt) {
    Node val = stmt.removeChildAfter(stmt.getFirstChild());
    Node comma = fuseExpressionIntoExpression(exp, val);
    stmt.addChildAfter(comma, stmt.getFirstChild());
  }
  private void fuseIntoOneStatement(Node block) {
    Node var_331 = block.removeFirstChild();
    Node cur = var_331;
    Node commaTree = cur.removeFirstChild();
    while(block.hasMoreThanOneChild()){
      Node next = block.removeFirstChild().removeFirstChild();
      commaTree = fuseExpressionIntoExpression(commaTree, next);
    }
    Preconditions.checkState(block.hasOneChild());
    Node last = block.getLastChild();
    switch (last.getType()){
      case Token.IF:
      case Token.RETURN:
      case Token.THROW:
      case Token.SWITCH:
      case Token.EXPR_RESULT:
      fuseExpresssonIntoFirstChild(commaTree, last);
      return ;
      case Token.FOR:
      if(NodeUtil.isForIn(last)) {
        fuseExpresssonIntoSecondChild(commaTree, last);
      }
      return ;
      default:
      throw new IllegalStateException("Statement fusion missing.");
    }
  }
}