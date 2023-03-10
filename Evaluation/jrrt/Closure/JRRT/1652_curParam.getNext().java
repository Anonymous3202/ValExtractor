package com.google.javascript.jscomp;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.List;

final class ClosureOptimizePrimitives implements CompilerPass  {
  final private AbstractCompiler compiler;
  ClosureOptimizePrimitives(AbstractCompiler compiler) {
    super();
    this.compiler = compiler;
  }
  private boolean canOptimizeObjectCreate(Node firstParam) {
    Node curParam = firstParam;
    while(curParam != null){
      if(!curParam.isString() && !curParam.isNumber()) {
        return false;
      }
      Node var_1652 = curParam.getNext();
      curParam = var_1652;
      if(curParam == null) {
        return false;
      }
      curParam = curParam.getNext();
    }
    return true;
  }
  @Override() public void process(Node externs, Node root) {
    FindObjectCreateCalls pass = new FindObjectCreateCalls();
    NodeTraversal.traverse(compiler, root, pass);
    processObjectCreateCalls(pass.callNodes);
  }
  private void processObjectCreateCalls(List<Node> callNodes) {
    for (Node callNode : callNodes) {
      Node curParam = callNode.getFirstChild().getNext();
      if(canOptimizeObjectCreate(curParam)) {
        Node objNode = IR.objectlit().srcref(callNode);
        while(curParam != null){
          Node keyNode = curParam;
          Node valueNode = curParam.getNext();
          curParam = valueNode.getNext();
          callNode.removeChild(keyNode);
          callNode.removeChild(valueNode);
          if(!keyNode.isString()) {
            keyNode = IR.string(NodeUtil.getStringValue(keyNode)).srcref(keyNode);
          }
          keyNode.setType(Token.STRING_KEY);
          keyNode.setQuotedString();
          objNode.addChildToBack(IR.propdef(keyNode, valueNode));
        }
        callNode.getParent().replaceChild(callNode, objNode);
        compiler.reportCodeChange();
      }
    }
  }
  
  private class FindObjectCreateCalls extends AbstractPostOrderCallback  {
    List<Node> callNodes = Lists.newArrayList();
    @Override() public void visit(NodeTraversal t, Node n, Node parent) {
      if(n.isCall()) {
        String fnName = n.getFirstChild().getQualifiedName();
        if("goog$object$create".equals(fnName) || "goog.object.create".equals(fnName)) {
          callNodes.add(n);
        }
      }
    }
  }
}