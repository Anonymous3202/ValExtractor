package com.google.javascript.jscomp;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.List;
import java.util.Set;

class RemoveUnusedClassProperties implements CompilerPass, NodeTraversal.Callback  {
  final AbstractCompiler compiler;
  private boolean inExterns;
  private Set<String> used = Sets.newHashSet();
  private List<Node> candidates = Lists.newArrayList();
  RemoveUnusedClassProperties(AbstractCompiler compiler) {
    super();
    this.compiler = compiler;
  }
  private boolean isPinningPropertyUse(Node n) {
    Node parent = n.getParent();
    if(n == parent.getFirstChild()) {
      if(parent.isAssign()) {
        return false;
      }
      else 
        if(NodeUtil.isAssignmentOp(parent) || parent.isInc() || parent.isDec()) {
          return NodeUtil.isExpressionResultUsed(parent);
        }
    }
    return true;
  }
  @Override() public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    if(n.isScript()) {
      this.inExterns = n.getStaticSourceFile().isExtern();
    }
    return true;
  }
  @Override() public void process(Node externs, Node root) {
    NodeTraversal.traverseRoots(compiler, this, externs, root);
    removeUnused();
  }
  private void removeUnused() {
    for (Node n : candidates) {
      Preconditions.checkState(n.isGetProp());
      if(!used.contains(n.getLastChild().getString())) {
        Node parent = n.getParent();
        if(NodeUtil.isAssignmentOp(parent)) {
          Node assign = parent;
          Preconditions.checkState(assign != null && NodeUtil.isAssignmentOp(assign) && assign.getFirstChild() == n);
          assign.getParent().replaceChild(assign, assign.getLastChild().detachFromParent());
        }
        else 
          if(parent.isInc() || parent.isDec()) {
            parent.getParent().replaceChild(parent, IR.number(0));
          }
          else {
            throw new IllegalStateException("unexpected: " + parent);
          }
        compiler.reportCodeChange();
      }
    }
  }
  @Override() public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()){
      case Token.GETPROP:
      {
        String propName = n.getLastChild().getString();
        if(inExterns || isPinningPropertyUse(n)) {
          used.add(propName);
        }
        else {
          Node var_526 = n.getFirstChild();
          if(var_526.isThis()) {
            candidates.add(n);
          }
        }
        break ;
      }
      case Token.CALL:
      Node target = n.getFirstChild();
      if(n.hasMoreThanOneChild() && target.isName() && target.getString().equals(NodeUtil.JSC_PROPERTY_NAME_FN)) {
        Node propName = target.getNext();
        if(propName.isString()) {
          used.add(propName.getString());
        }
      }
      break ;
    }
  }
}