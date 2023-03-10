package com.google.javascript.jscomp;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.List;

class AstChangeProxy  {
  final private List<ChangeListener> listeners;
  AstChangeProxy() {
    super();
    listeners = Lists.newArrayList();
  }
  private void notifyOfRemoval(Node node) {
    for (ChangeListener listener : listeners) {
      listener.nodeRemoved(node);
    }
  }
  final void registerListener(ChangeListener listener) {
    listeners.add(listener);
  }
  final void removeChild(Node parent, Node node) {
    parent.removeChild(node);
    notifyOfRemoval(node);
  }
  final void replaceWith(Node parent, Node node, Node replacement) {
    replaceWith(parent, node, Lists.newArrayList(replacement));
  }
  final void replaceWith(Node parent, Node node, List<Node> replacements) {
    Preconditions.checkNotNull(replacements, "\"replacements\" is null.");
    int size = replacements.size();
    if((size == 1) && node.isEquivalentTo(replacements.get(0))) {
      return ;
    }
    int parentType = parent.getType();
    Preconditions.checkState(size == 1 || parentType == Token.BLOCK || parentType == Token.SCRIPT || parentType == Token.LABEL);
    if(parentType == Token.LABEL && size != 1) {
      Node block = IR.block();
      for (Node newChild : replacements) {
        newChild.copyInformationFrom(node);
        Node var_603 = newChild.getParent();
        Node oldParent = var_603;
        block.addChildToBack(newChild);
      }
      parent.replaceChild(node, block);
    }
    else {
      for (Node newChild : replacements) {
        newChild.copyInformationFrom(node);
        Node oldParent = newChild.getParent();
        parent.addChildBefore(newChild, node);
      }
      parent.removeChild(node);
    }
    notifyOfRemoval(node);
  }
  final void unregisterListener(ChangeListener listener) {
    listeners.remove(listener);
  }
  
  interface ChangeListener  {
    void nodeRemoved(Node node);
  }
}