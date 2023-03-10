package com.google.javascript.jscomp;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicates;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.TernaryValue;
import javax.annotation.Nullable;

class PeepholeRemoveDeadCode extends AbstractPeepholeOptimization  {
  private Node getConditionalStatementCondition(Node n) {
    if(n.isIf()) {
      return NodeUtil.getConditionExpression(n);
    }
    else {
      Preconditions.checkState(isExprConditional(n));
      return n.getFirstChild().getFirstChild();
    }
  }
  private Node getSimpleAssignmentName(Node n) {
    Preconditions.checkState(isSimpleAssignment(n));
    if(NodeUtil.isExprAssign(n)) {
      return n.getFirstChild().getFirstChild();
    }
    else {
      return n.getFirstChild();
    }
  }
  private Node getSimpleAssignmentValue(Node n) {
    Preconditions.checkState(isSimpleAssignment(n));
    return n.getFirstChild().getLastChild();
  }
  @Override() Node optimizeSubtree(Node subtree) {
    switch (subtree.getType()){
      case Token.ASSIGN:
      return tryFoldAssignment(subtree);
      case Token.COMMA:
      return tryFoldComma(subtree);
      case Token.SCRIPT:
      case Token.BLOCK:
      return tryOptimizeBlock(subtree);
      case Token.EXPR_RESULT:
      subtree = tryFoldExpr(subtree);
      return subtree;
      case Token.HOOK:
      return tryFoldHook(subtree);
      case Token.SWITCH:
      return tryOptimizeSwitch(subtree);
      case Token.IF:
      return tryFoldIf(subtree);
      case Token.WHILE:
      return tryFoldWhile(subtree);
      case Token.FOR:
      {
        Node condition = NodeUtil.getConditionExpression(subtree);
        if(condition != null) {
          tryFoldForCondition(condition);
        }
      }
      return tryFoldFor(subtree);
      case Token.DO:
      return tryFoldDo(subtree);
      case Token.TRY:
      return tryFoldTry(subtree);
      default:
      return subtree;
    }
  }
  private Node tryFoldAssignment(Node subtree) {
    Preconditions.checkState(subtree.isAssign());
    Node left = subtree.getFirstChild();
    Node right = subtree.getLastChild();
    if(left.isName() && right.isName() && left.getString().equals(right.getString())) {
      subtree.getParent().replaceChild(subtree, right.detachFromParent());
      reportCodeChange();
      return right;
    }
    return subtree;
  }
  private Node tryFoldComma(Node n) {
    Node parent = n.getParent();
    Node left = n.getFirstChild();
    Node right = left.getNext();
    left = trySimplifyUnusedResult(left);
    if(left == null || !mayHaveSideEffects(left)) {
      n.removeChild(right);
      parent.replaceChild(n, right);
      reportCodeChange();
      return right;
    }
    return n;
  }
  Node tryFoldDo(Node n) {
    Preconditions.checkArgument(n.isDo());
    Node cond = NodeUtil.getConditionExpression(n);
    if(NodeUtil.getImpureBooleanValue(cond) != TernaryValue.FALSE) {
      return n;
    }
    if(hasBreakOrContinue(n)) {
      return n;
    }
    Preconditions.checkState(NodeUtil.isControlStructureCodeBlock(n, n.getFirstChild()));
    Node block = n.removeFirstChild();
    Node parent = n.getParent();
    parent.replaceChild(n, block);
    if(mayHaveSideEffects(cond)) {
      Node condStatement = IR.exprResult(cond.detachFromParent()).srcref(cond);
      parent.addChildAfter(condStatement, block);
    }
    reportCodeChange();
    return n;
  }
  private Node tryFoldExpr(Node subtree) {
    Node result = trySimplifyUnusedResult(subtree.getFirstChild());
    if(result == null) {
      Node parent = subtree.getParent();
      if(parent.isLabel()) {
        Node replacement = IR.block().srcref(subtree);
        parent.replaceChild(subtree, replacement);
        subtree = replacement;
      }
      else {
        subtree.detachFromParent();
        subtree = null;
      }
    }
    return subtree;
  }
  Node tryFoldFor(Node n) {
    Preconditions.checkArgument(n.isFor());
    if(NodeUtil.isForIn(n)) {
      return n;
    }
    Node init = n.getFirstChild();
    Node cond = init.getNext();
    Node increment = cond.getNext();
    if(!init.isEmpty() && !init.isVar()) {
      init = trySimplifyUnusedResult(init, false);
    }
    if(!increment.isEmpty()) {
      increment = trySimplifyUnusedResult(increment, false);
    }
    if(!n.getFirstChild().isEmpty()) {
      return n;
    }
    if(NodeUtil.getImpureBooleanValue(cond) != TernaryValue.FALSE) {
      return n;
    }
    NodeUtil.redeclareVarsInsideBranch(n);
    if(!mayHaveSideEffects(cond)) {
      NodeUtil.removeChild(n.getParent(), n);
    }
    else {
      Node statement = IR.exprResult(cond.detachFromParent()).copyInformationFrom(cond);
      n.getParent().replaceChild(n, statement);
    }
    reportCodeChange();
    return null;
  }
  private Node tryFoldHook(Node n) {
    Preconditions.checkState(n.isHook());
    Node parent = n.getParent();
    Preconditions.checkNotNull(parent);
    Node cond = n.getFirstChild();
    Node thenBody = cond.getNext();
    Node elseBody = thenBody.getNext();
    TernaryValue condValue = NodeUtil.getImpureBooleanValue(cond);
    if(condValue == TernaryValue.UNKNOWN) {
      if(!areNodesEqualForInlining(thenBody, elseBody)) {
        return n;
      }
    }
    n.detachChildren();
    Node branchToKeep = condValue.toBoolean(true) ? thenBody : elseBody;
    Node replacement;
    if(mayHaveSideEffects(cond)) {
      replacement = IR.comma(cond, branchToKeep).srcref(n);
    }
    else {
      replacement = branchToKeep;
    }
    parent.replaceChild(n, replacement);
    reportCodeChange();
    return replacement;
  }
  private Node tryFoldIf(Node n) {
    Preconditions.checkState(n.isIf());
    Node parent = n.getParent();
    Preconditions.checkNotNull(parent);
    int type = n.getType();
    Node cond = n.getFirstChild();
    Node thenBody = cond.getNext();
    Node elseBody = thenBody.getNext();
    if(elseBody != null && !mayHaveSideEffects(elseBody)) {
      n.removeChild(elseBody);
      elseBody = null;
      reportCodeChange();
    }
    if(!mayHaveSideEffects(thenBody) && elseBody != null) {
      n.removeChild(elseBody);
      n.replaceChild(thenBody, elseBody);
      Node notCond = new Node(Token.NOT);
      n.replaceChild(cond, notCond);
      notCond.addChildToFront(cond);
      cond = notCond;
      thenBody = cond.getNext();
      elseBody = null;
      reportCodeChange();
    }
    if(!mayHaveSideEffects(thenBody) && elseBody == null) {
      if(mayHaveSideEffects(cond)) {
        n.removeChild(cond);
        Node replacement = NodeUtil.newExpr(cond);
        parent.replaceChild(n, replacement);
        reportCodeChange();
        return replacement;
      }
      else {
        NodeUtil.removeChild(parent, n);
        reportCodeChange();
        return null;
      }
    }
    TernaryValue condValue = NodeUtil.getImpureBooleanValue(cond);
    if(condValue == TernaryValue.UNKNOWN) {
      return n;
    }
    if(mayHaveSideEffects(cond)) {
      boolean newConditionValue = condValue == TernaryValue.TRUE;
      if(!newConditionValue && elseBody == null) {
        elseBody = IR.block().srcref(n);
        n.addChildToBack(elseBody);
      }
      Node newCond = NodeUtil.booleanNode(newConditionValue);
      n.replaceChild(cond, newCond);
      Node branchToKeep = newConditionValue ? thenBody : elseBody;
      branchToKeep.addChildToFront(IR.exprResult(cond).srcref(cond));
      reportCodeChange();
      cond = newCond;
    }
    boolean condTrue = condValue.toBoolean(true);
    if(n.getChildCount() == 2) {
      Preconditions.checkState(type == Token.IF);
      if(condTrue) {
        Node thenStmt = n.getFirstChild().getNext();
        n.removeChild(thenStmt);
        parent.replaceChild(n, thenStmt);
        reportCodeChange();
        return thenStmt;
      }
      else {
        NodeUtil.redeclareVarsInsideBranch(n);
        NodeUtil.removeChild(parent, n);
        reportCodeChange();
        return null;
      }
    }
    else {
      Node trueBranch = n.getFirstChild().getNext();
      Node falseBranch = trueBranch.getNext();
      Node branchToKeep = condTrue ? trueBranch : falseBranch;
      Node branchToRemove = condTrue ? falseBranch : trueBranch;
      NodeUtil.redeclareVarsInsideBranch(branchToRemove);
      n.removeChild(branchToKeep);
      parent.replaceChild(n, branchToKeep);
      reportCodeChange();
      return branchToKeep;
    }
  }
  private Node tryFoldTry(Node n) {
    Preconditions.checkState(n.isTry());
    Node body = n.getFirstChild();
    Node catchBlock = body.getNext();
    Node finallyBlock = catchBlock.getNext();
    if(!catchBlock.hasChildren() && (finallyBlock == null || !finallyBlock.hasChildren())) {
      n.removeChild(body);
      n.getParent().replaceChild(n, body);
      reportCodeChange();
      return body;
    }
    if(!body.hasChildren()) {
      NodeUtil.redeclareVarsInsideBranch(catchBlock);
      if(finallyBlock != null) {
        n.removeChild(finallyBlock);
        n.getParent().replaceChild(n, finallyBlock);
      }
      else {
        n.getParent().removeChild(n);
      }
      reportCodeChange();
      return finallyBlock;
    }
    return n;
  }
  Node tryFoldWhile(Node n) {
    Preconditions.checkArgument(n.isWhile());
    Node cond = NodeUtil.getConditionExpression(n);
    if(NodeUtil.getPureBooleanValue(cond) != TernaryValue.FALSE) {
      return n;
    }
    NodeUtil.redeclareVarsInsideBranch(n);
    NodeUtil.removeChild(n.getParent(), n);
    reportCodeChange();
    return null;
  }
  Node tryOptimizeBlock(Node n) {
    for(com.google.javascript.rhino.Node c = n.getFirstChild(); c != null; ) {
      Node next = c.getNext();
      if(!isUnremovableNode(c) && !mayHaveSideEffects(c)) {
        n.removeChild(c);
        reportCodeChange();
      }
      else {
        tryOptimizeConditionalAfterAssign(c);
      }
      c = next;
    }
    if(n.isSyntheticBlock() || n.getParent() == null) {
      return n;
    }
    if(NodeUtil.tryMergeBlock(n)) {
      reportCodeChange();
      return null;
    }
    return n;
  }
  private Node tryOptimizeDefaultCase(Node n) {
    Preconditions.checkState(n.isSwitch());
    Node lastNonRemovable = n.getFirstChild();
    for(com.google.javascript.rhino.Node c = n.getFirstChild().getNext(); c != null; c = c.getNext()) {
      if(c.isDefaultCase()) {
        Node caseToRemove = lastNonRemovable.getNext();
        for(com.google.javascript.rhino.Node next; caseToRemove != c; caseToRemove = next) {
          next = caseToRemove.getNext();
          removeCase(n, caseToRemove);
        }
        Node prevCase = (lastNonRemovable == n.getFirstChild()) ? null : lastNonRemovable;
        if(isUselessCase(c, prevCase)) {
          removeCase(n, c);
          return null;
        }
        return c;
      }
      else {
        Preconditions.checkState(c.isCase());
        if(c.getLastChild().hasChildren() || mayHaveSideEffects(c.getFirstChild())) {
          lastNonRemovable = c;
        }
      }
    }
    return null;
  }
  private Node tryOptimizeSwitch(Node n) {
    Preconditions.checkState(n.isSwitch());
    Node defaultCase = tryOptimizeDefaultCase(n);
    if(defaultCase == null) {
      Node cond = n.getFirstChild();
      Node prev = null;
      Node next = null;
      Node cur;
      for(cur = cond.getNext(); cur != null; cur = next) {
        next = cur.getNext();
        if(!mayHaveSideEffects(cur.getFirstChild()) && isUselessCase(cur, prev)) {
          removeCase(n, cur);
        }
        else {
          prev = cur;
        }
      }
      if(NodeUtil.isLiteralValue(cond, false)) {
        Node caseLabel;
        TernaryValue caseMatches = TernaryValue.TRUE;
        Node var_790 = cond.getNext();
        for(cur = var_790; cur != null; cur = next) {
          next = cur.getNext();
          caseLabel = cur.getFirstChild();
          caseMatches = PeepholeFoldConstants.evaluateComparison(Token.SHEQ, cond, caseLabel);
          if(caseMatches == TernaryValue.TRUE) {
            break ;
          }
          else 
            if(caseMatches == TernaryValue.UNKNOWN) {
              break ;
            }
            else {
              removeCase(n, cur);
            }
        }
        if(caseMatches != TernaryValue.UNKNOWN) {
          Node block;
          Node lastStm;
          while(cur != null){
            block = cur.getLastChild();
            lastStm = block.getLastChild();
            cur = cur.getNext();
            if(lastStm != null && lastStm.isBreak()) {
              block.removeChild(lastStm);
              reportCodeChange();
              break ;
            }
          }
          for(; cur != null; cur = next) {
            next = cur.getNext();
            removeCase(n, cur);
          }
          cur = cond.getNext();
          if(cur != null && cur.getNext() == null) {
            block = cur.getLastChild();
            if(!(NodeUtil.containsType(block, Token.BREAK, NodeUtil.MATCH_NOT_FUNCTION))) {
              cur.removeChild(block);
              n.getParent().replaceChild(n, block);
              reportCodeChange();
              return block;
            }
          }
        }
      }
    }
    if(n.hasOneChild()) {
      Node condition = n.removeFirstChild();
      Node replacement = IR.exprResult(condition).srcref(n);
      n.getParent().replaceChild(n, replacement);
      reportCodeChange();
      return replacement;
    }
    return null;
  }
  private Node trySimplifyUnusedResult(Node n) {
    return trySimplifyUnusedResult(n, true);
  }
  private Node trySimplifyUnusedResult(Node n, boolean removeUnused) {
    Node result = n;
    switch (n.getType()){
      case Token.HOOK:
      Node trueNode = trySimplifyUnusedResult(n.getFirstChild().getNext());
      Node falseNode = trySimplifyUnusedResult(n.getLastChild());
      if(trueNode == null && falseNode != null) {
        n.setType(Token.OR);
        Preconditions.checkState(n.getChildCount() == 2);
      }
      else 
        if(trueNode != null && falseNode == null) {
          n.setType(Token.AND);
          Preconditions.checkState(n.getChildCount() == 2);
        }
        else 
          if(trueNode == null && falseNode == null) {
            result = trySimplifyUnusedResult(n.getFirstChild());
          }
          else {
            result = n;
          }
      break ;
      case Token.AND:
      case Token.OR:
      Node conditionalResultNode = trySimplifyUnusedResult(n.getLastChild());
      if(conditionalResultNode == null) {
        Preconditions.checkState(n.hasOneChild());
        result = trySimplifyUnusedResult(n.getFirstChild());
      }
      break ;
      case Token.FUNCTION:
      result = null;
      break ;
      case Token.COMMA:
      Node left = trySimplifyUnusedResult(n.getFirstChild());
      Node right = trySimplifyUnusedResult(n.getLastChild());
      if(left == null && right == null) {
        result = null;
      }
      else 
        if(left == null) {
          result = right;
        }
        else 
          if(right == null) {
            result = left;
          }
          else {
            result = n;
          }
      break ;
      default:
      if(!nodeTypeMayHaveSideEffects(n)) {
        Node resultList = null;
        for(com.google.javascript.rhino.Node next, c = n.getFirstChild(); c != null; c = next) {
          next = c.getNext();
          c = trySimplifyUnusedResult(c);
          if(c != null) {
            c.detachFromParent();
            if(resultList == null) {
              resultList = c;
            }
            else {
              resultList = IR.comma(resultList, c).srcref(c);
            }
          }
        }
        result = resultList;
      }
    }
    if(n != result) {
      Node parent = n.getParent();
      if(result == null) {
        if(removeUnused) {
          parent.removeChild(n);
        }
        else {
          result = IR.empty().srcref(n);
          parent.replaceChild(n, result);
        }
      }
      else {
        if(result.getParent() != null) {
          result.detachFromParent();
        }
        n.getParent().replaceChild(n, result);
      }
      reportCodeChange();
    }
    return result;
  }
  boolean hasBreakOrContinue(Node n) {
    return NodeUtil.has(n, Predicates.<Node>or(new NodeUtil.MatchNodeType(Token.BREAK), new NodeUtil.MatchNodeType(Token.CONTINUE)), NodeUtil.MATCH_NOT_FUNCTION);
  }
  private boolean isConditionalStatement(Node n) {
    return n != null && (n.isIf() || isExprConditional(n));
  }
  private boolean isExit(Node n) {
    switch (n.getType()){
      case Token.BREAK:
      case Token.CONTINUE:
      case Token.RETURN:
      case Token.THROW:
      return true;
      default:
      return false;
    }
  }
  private boolean isExprConditional(Node n) {
    if(n.isExprResult()) {
      switch (n.getFirstChild().getType()){
        case Token.HOOK:
        case Token.AND:
        case Token.OR:
        return true;
      }
    }
    return false;
  }
  private boolean isSimpleAssignment(Node n) {
    if(NodeUtil.isExprAssign(n) && n.getFirstChild().getFirstChild().isName()) {
      return true;
    }
    else 
      if(n.isVar() && n.hasOneChild() && n.getFirstChild().getFirstChild() != null) {
        return true;
      }
    return false;
  }
  private boolean isUnremovableNode(Node n) {
    return (n.isBlock() && n.isSyntheticBlock()) || n.isScript();
  }
  private boolean isUselessCase(Node caseNode, @Nullable() Node previousCase) {
    Preconditions.checkState(previousCase == null || previousCase.getNext() == caseNode);
    Node switchNode = caseNode.getParent();
    if(switchNode.getLastChild() != caseNode && previousCase != null) {
      Node previousBlock = previousCase.getLastChild();
      if(!previousBlock.hasChildren() || !isExit(previousBlock.getLastChild())) {
        return false;
      }
    }
    Node executingCase = caseNode;
    while(executingCase != null){
      Preconditions.checkState(executingCase.isDefaultCase() || executingCase.isCase());
      Preconditions.checkState(caseNode == executingCase || !executingCase.isDefaultCase());
      Node block = executingCase.getLastChild();
      Preconditions.checkState(block.isBlock());
      if(block.hasChildren()) {
        for (Node blockChild : block.children()) {
          switch (blockChild.getType()){
            case Token.BREAK:
            return blockChild.getFirstChild() == null;
            case Token.VAR:
            if(blockChild.hasOneChild() && blockChild.getFirstChild().getFirstChild() == null) {
              continue ;
            }
            return false;
            default:
            return false;
          }
        }
      }
      else {
        executingCase = executingCase.getNext();
      }
    }
    return true;
  }
  private void removeCase(Node switchNode, Node caseNode) {
    NodeUtil.redeclareVarsInsideBranch(caseNode);
    switchNode.removeChild(caseNode);
    reportCodeChange();
  }
  private void tryFoldForCondition(Node forCondition) {
    if(NodeUtil.getPureBooleanValue(forCondition) == TernaryValue.TRUE) {
      forCondition.getParent().replaceChild(forCondition, IR.empty());
      reportCodeChange();
    }
  }
  private void tryOptimizeConditionalAfterAssign(Node n) {
    Node next = n.getNext();
    if(isSimpleAssignment(n) && isConditionalStatement(next)) {
      Node lhsAssign = getSimpleAssignmentName(n);
      Node condition = getConditionalStatementCondition(next);
      if(lhsAssign.isName() && condition.isName() && lhsAssign.getString().equals(condition.getString())) {
        Node rhsAssign = getSimpleAssignmentValue(n);
        TernaryValue value = NodeUtil.getImpureBooleanValue(rhsAssign);
        if(value != TernaryValue.UNKNOWN) {
          Node replacementConditionNode = NodeUtil.booleanNode(value.toBoolean(true));
          condition.getParent().replaceChild(condition, replacementConditionNode);
          reportCodeChange();
        }
      }
    }
  }
}