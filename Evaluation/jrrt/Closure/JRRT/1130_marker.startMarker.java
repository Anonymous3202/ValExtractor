package com.google.javascript.jscomp;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import javax.annotation.Nullable;

class CreateSyntheticBlocks implements CompilerPass  {
  final static DiagnosticType UNMATCHED_START_MARKER = DiagnosticType.warning("JSC_UNMATCHED_START_MARKER", "Unmatched {0}");
  final static DiagnosticType UNMATCHED_END_MARKER = DiagnosticType.warning("JSC_UNMATCHED_END_MARKER", "Unmatched {1} - {0} not in the same block");
  final static DiagnosticType INVALID_MARKER_USAGE = DiagnosticType.warning("JSC_INVALID_MARKER_USAGE", "Marker {0} can only be used in a simple " + "call expression");
  final private AbstractCompiler compiler;
  final private String startMarkerName;
  final private String endMarkerName;
  final private Deque<Node> markerStack = new ArrayDeque<Node>();
  final private List<Marker> validMarkers = Lists.newArrayList();
  public CreateSyntheticBlocks(AbstractCompiler compiler, String startMarkerName, String endMarkerName) {
    super();
    this.compiler = compiler;
    this.startMarkerName = startMarkerName;
    this.endMarkerName = endMarkerName;
  }
  private Node childAfter(Node parent, @Nullable() Node siblingBefore) {
    if(siblingBefore == null) {
      return parent.getFirstChild();
    }
    else {
      return siblingBefore.getNext();
    }
  }
  private Node removeChildAfter(Node parent, @Nullable() Node siblingBefore) {
    if(siblingBefore == null) {
      return parent.removeFirstChild();
    }
    else {
      return parent.removeChildAfter(siblingBefore);
    }
  }
  private void addBlocks(Marker marker) {
    Node originalParent = marker.endMarker.getParent();
    Node outerBlock = IR.block();
    outerBlock.setIsSyntheticBlock(true);
    Node var_1130 = marker.startMarker;
    originalParent.addChildBefore(outerBlock, var_1130);
    Node innerBlock = IR.block();
    innerBlock.setIsSyntheticBlock(true);
    moveSiblingExclusive(originalParent, innerBlock, marker.startMarker, marker.endMarker);
    outerBlock.addChildToBack(originalParent.removeChildAfter(outerBlock));
    outerBlock.addChildToBack(innerBlock);
    outerBlock.addChildToBack(originalParent.removeChildAfter(outerBlock));
    compiler.reportCodeChange();
  }
  private void moveSiblingExclusive(Node src, Node dest, @Nullable() Node start, @Nullable() Node end) {
    while(childAfter(src, start) != end){
      Node child = removeChildAfter(src, start);
      dest.addChildToBack(child);
    }
  }
  @Override() public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, new Callback());
    for (Node node : markerStack) {
      compiler.report(JSError.make(NodeUtil.getSourceName(node), node, UNMATCHED_START_MARKER, startMarkerName));
    }
    for (Marker marker : validMarkers) {
      addBlocks(marker);
    }
  }
  
  private class Callback extends AbstractPostOrderCallback  {
    @Override() public void visit(NodeTraversal t, Node n, Node parent) {
      if(!n.isCall() || !n.getFirstChild().isName()) {
        return ;
      }
      Node callTarget = n.getFirstChild();
      String callName = callTarget.getString();
      if(startMarkerName.equals(callName)) {
        if(!parent.isExprResult()) {
          compiler.report(t.makeError(n, INVALID_MARKER_USAGE, startMarkerName));
          return ;
        }
        markerStack.push(parent);
        return ;
      }
      if(!endMarkerName.equals(callName)) {
        return ;
      }
      Node endMarkerNode = parent;
      if(!endMarkerNode.isExprResult()) {
        compiler.report(t.makeError(n, INVALID_MARKER_USAGE, endMarkerName));
        return ;
      }
      if(markerStack.isEmpty()) {
        compiler.report(t.makeError(n, UNMATCHED_END_MARKER, startMarkerName, endMarkerName));
        return ;
      }
      Node startMarkerNode = markerStack.pop();
      if(endMarkerNode.getParent() != startMarkerNode.getParent()) {
        compiler.report(t.makeError(n, UNMATCHED_END_MARKER, startMarkerName, endMarkerName));
        return ;
      }
      validMarkers.add(new Marker(startMarkerNode, endMarkerNode));
    }
  }
  
  private class Marker  {
    final Node startMarker;
    final Node endMarker;
    public Marker(Node startMarker, Node endMarker) {
      super();
      this.startMarker = startMarker;
      this.endMarker = endMarker;
    }
  }
}