package com.google.javascript.jscomp;
import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.AbstractCompiler;
import com.google.javascript.jscomp.CompilerPass;
import com.google.javascript.jscomp.NodeTraversal;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeUtil;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.TernaryValue;

class MinimizeExitPoints extends AbstractPostOrderCallback implements CompilerPass  {
  AbstractCompiler compiler;
  MinimizeExitPoints(AbstractCompiler compiler) {
    super();
    this.compiler = compiler;
  }
  private static boolean matchingExitNode(Node n, int type, String labelName) {
    if(n.getType() == type) {
      if(type == Token.RETURN) {
        return !n.hasChildren();
      }
      else {
        if(labelName == null) {
          return !n.hasChildren();
        }
        else {
          return n.hasChildren() && labelName.equals(n.getFirstChild().getString());
        }
      }
    }
    return false;
  }
  private static void moveAllFollowing(Node start, Node srcParent, Node destParent) {
    Node var_576 = start.getNext();
    for(com.google.javascript.rhino.Node n = var_576; n != null; n = start.getNext()) {
      boolean isFunctionDeclaration = NodeUtil.isFunctionDeclaration(n);
      srcParent.removeChild(n);
      if(isFunctionDeclaration) {
        destParent.addChildToFront(n);
      }
      else {
        destParent.addChildToBack(n);
      }
    }
  }
  @Override() public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }
  void tryMinimizeExits(Node n, int exitType, String labelName) {
    if(matchingExitNode(n, exitType, labelName)) {
      NodeUtil.removeChild(n.getParent(), n);
      compiler.reportCodeChange();
      return ;
    }
    if(n.isIf()) {
      Node ifBlock = n.getFirstChild().getNext();
      tryMinimizeExits(ifBlock, exitType, labelName);
      Node elseBlock = ifBlock.getNext();
      if(elseBlock != null) {
        tryMinimizeExits(elseBlock, exitType, labelName);
      }
      return ;
    }
    if(n.isTry()) {
      Node tryBlock = n.getFirstChild();
      tryMinimizeExits(tryBlock, exitType, labelName);
      Node allCatchNodes = NodeUtil.getCatchBlock(n);
      if(NodeUtil.hasCatchHandler(allCatchNodes)) {
        Preconditions.checkState(allCatchNodes.hasOneChild());
        Node catchNode = allCatchNodes.getFirstChild();
        Node catchCodeBlock = catchNode.getLastChild();
        tryMinimizeExits(catchCodeBlock, exitType, labelName);
      }
      if(NodeUtil.hasFinally(n)) {
        Node finallyBlock = n.getLastChild();
        tryMinimizeExits(finallyBlock, exitType, labelName);
      }
    }
    if(n.isLabel()) {
      Node labelBlock = n.getLastChild();
      tryMinimizeExits(labelBlock, exitType, labelName);
    }
    if(!n.isBlock() || n.getLastChild() == null) {
      return ;
    }
    for (Node c : n.children()) {
      if(c.isIf()) {
        Node ifTree = c;
        Node trueBlock;
        Node falseBlock;
        trueBlock = ifTree.getFirstChild().getNext();
        falseBlock = trueBlock.getNext();
        tryMinimizeIfBlockExits(trueBlock, falseBlock, ifTree, exitType, labelName);
        trueBlock = ifTree.getFirstChild().getNext();
        falseBlock = trueBlock.getNext();
        if(falseBlock != null) {
          tryMinimizeIfBlockExits(falseBlock, trueBlock, ifTree, exitType, labelName);
        }
      }
      if(c == n.getLastChild()) {
        break ;
      }
    }
    for(com.google.javascript.rhino.Node c = n.getLastChild(); c != null; c = n.getLastChild()) {
      tryMinimizeExits(c, exitType, labelName);
      if(c == n.getLastChild()) {
        break ;
      }
    }
  }
  private void tryMinimizeIfBlockExits(Node srcBlock, Node destBlock, Node ifNode, int exitType, String labelName) {
    Node exitNodeParent = null;
    Node exitNode = null;
    if(srcBlock.isBlock()) {
      if(!srcBlock.hasChildren()) {
        return ;
      }
      exitNodeParent = srcBlock;
      exitNode = exitNodeParent.getLastChild();
    }
    else {
      exitNodeParent = ifNode;
      exitNode = srcBlock;
    }
    if(!matchingExitNode(exitNode, exitType, labelName)) {
      return ;
    }
    if(ifNode.getNext() != null) {
      Node newDestBlock = IR.block().srcref(ifNode);
      if(destBlock == null) {
        ifNode.addChildToBack(newDestBlock);
      }
      else 
        if(destBlock.isEmpty()) {
          ifNode.replaceChild(destBlock, newDestBlock);
        }
        else 
          if(destBlock.isBlock()) {
            newDestBlock = destBlock;
          }
          else {
            ifNode.replaceChild(destBlock, newDestBlock);
            newDestBlock.addChildToBack(destBlock);
          }
      moveAllFollowing(ifNode, ifNode.getParent(), newDestBlock);
    }
    NodeUtil.removeChild(exitNodeParent, exitNode);
    compiler.reportCodeChange();
  }
  @Override() public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()){
      case Token.LABEL:
      tryMinimizeExits(n.getLastChild(), Token.BREAK, n.getFirstChild().getString());
      break ;
      case Token.FOR:
      case Token.WHILE:
      tryMinimizeExits(NodeUtil.getLoopCodeBlock(n), Token.CONTINUE, null);
      break ;
      case Token.DO:
      tryMinimizeExits(NodeUtil.getLoopCodeBlock(n), Token.CONTINUE, null);
      Node cond = NodeUtil.getConditionExpression(n);
      if(NodeUtil.getImpureBooleanValue(cond) == TernaryValue.FALSE) {
        tryMinimizeExits(n.getFirstChild(), Token.BREAK, null);
      }
      break ;
      case Token.FUNCTION:
      tryMinimizeExits(n.getLastChild(), Token.RETURN, null);
      break ;
    }
  }
}