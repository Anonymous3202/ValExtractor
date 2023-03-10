package com.google.javascript.jscomp;
import com.google.common.base.Preconditions;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

class ExploitAssigns extends AbstractPeepholeOptimization  {
  @Override() Node optimizeSubtree(Node subtree) {
    for(com.google.javascript.rhino.Node child = subtree.getFirstChild(); child != null; ) {
      Node next = child.getNext();
      if(NodeUtil.isExprAssign(child)) {
        collapseAssign(child.getFirstChild(), child, subtree);
      }
      child = next;
    }
    return subtree;
  }
  private boolean collapseAssignEqualTo(Node expr, Node exprParent, Node value) {
    Node assign = expr.getFirstChild();
    Node parent = exprParent;
    Node next = expr.getNext();
    while(next != null){
      switch (next.getType()){
        case Token.AND:
        case Token.OR:
        case Token.HOOK:
        case Token.IF:
        case Token.RETURN:
        case Token.EXPR_RESULT:
        parent = next;
        next = next.getFirstChild();
        break ;
        case Token.VAR:
        if(next.getFirstChild().hasChildren()) {
          Node var_227 = next.getFirstChild();
          parent = var_227;
          next = parent.getFirstChild();
          break ;
        }
        return false;
        case Token.GETPROP:
        case Token.NAME:
        if(next.isQualifiedName()) {
          String nextName = next.getQualifiedName();
          if(value.isQualifiedName() && nextName.equals(value.getQualifiedName())) {
            if(!isSafeReplacement(next, assign)) {
              return false;
            }
            exprParent.removeChild(expr);
            expr.removeChild(assign);
            parent.replaceChild(next, assign);
            return true;
          }
        }
        return false;
        case Token.ASSIGN:
        Node leftSide = next.getFirstChild();
        if(leftSide.isName() || leftSide.isGetProp() && leftSide.getFirstChild().isThis()) {
          parent = next;
          next = leftSide.getNext();
          break ;
        }
        else {
          return false;
        }
        default:
        if(NodeUtil.isImmutableValue(next) && next.isEquivalentTo(value)) {
          exprParent.removeChild(expr);
          expr.removeChild(assign);
          parent.replaceChild(next, assign);
          return true;
        }
        return false;
      }
    }
    return false;
  }
  private boolean isCollapsibleValue(Node value, boolean isLValue) {
    switch (value.getType()){
      case Token.GETPROP:
      return !isLValue || value.getFirstChild().isThis();
      case Token.NAME:
      return true;
      default:
      return NodeUtil.isImmutableValue(value);
    }
  }
  private boolean isNameAssignedTo(String name, Node node) {
    for(com.google.javascript.rhino.Node c = node.getFirstChild(); c != null; c = c.getNext()) {
      if(isNameAssignedTo(name, c)) {
        return true;
      }
    }
    if(node.isName()) {
      Node parent = node.getParent();
      if(parent.isAssign() && parent.getFirstChild() == node) {
        if(name.equals(node.getString())) {
          return true;
        }
      }
    }
    return false;
  }
  private boolean isSafeReplacement(Node node, Node replacement) {
    if(node.isName()) {
      return true;
    }
    Preconditions.checkArgument(node.isGetProp());
    Node name = node.getFirstChild();
    if(name.isName() && isNameAssignedTo(name.getString(), replacement)) {
      return false;
    }
    return true;
  }
  private void collapseAssign(Node assign, Node expr, Node exprParent) {
    Node leftValue = assign.getFirstChild();
    Node rightValue = leftValue.getNext();
    if(isCollapsibleValue(leftValue, true) && collapseAssignEqualTo(expr, exprParent, leftValue)) {
      reportCodeChange();
    }
    else 
      if(isCollapsibleValue(rightValue, false) && collapseAssignEqualTo(expr, exprParent, rightValue)) {
        reportCodeChange();
      }
      else 
        if(rightValue.isAssign()) {
          collapseAssign(rightValue, expr, exprParent);
        }
  }
}