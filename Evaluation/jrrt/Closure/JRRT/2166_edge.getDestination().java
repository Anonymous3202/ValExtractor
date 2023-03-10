package com.google.javascript.jscomp.graph;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.graph.DiGraph;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphNode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
final public class FixedPointGraphTraversal<E extends java.lang.Object, N extends java.lang.Object>  {
  final private EdgeCallback<N, E> callback;
  final public static String NON_HALTING_ERROR_MSG = "Fixed point computation not halting";
  public FixedPointGraphTraversal(EdgeCallback<N, E> callback) {
    super();
    this.callback = callback;
  }
  public static  <EDGE extends java.lang.Object, NODE extends java.lang.Object> FixedPointGraphTraversal<NODE, EDGE> newTraversal(EdgeCallback<NODE, EDGE> callback) {
    return new FixedPointGraphTraversal<NODE, EDGE>(callback);
  }
  public void computeFixedPoint(DiGraph<N, E> graph) {
    Set<N> nodes = Sets.newHashSet();
    for (DiGraphNode<N, E> node : graph.getDirectedGraphNodes()) {
      nodes.add(node.getValue());
    }
    computeFixedPoint(graph, nodes);
  }
  public void computeFixedPoint(DiGraph<N, E> graph, N entry) {
    Set<N> entrySet = Sets.newHashSet();
    entrySet.add(entry);
    computeFixedPoint(graph, entrySet);
  }
  public void computeFixedPoint(DiGraph<N, E> graph, Set<N> entrySet) {
    int cycleCount = 0;
    long nodeCount = graph.getNodes().size();
    long maxIterations = Math.max(nodeCount * nodeCount * nodeCount, 100);
    LinkedHashSet<DiGraphNode<N, E>> workSet = Sets.newLinkedHashSet();
    for (N n : entrySet) {
      workSet.add(graph.getDirectedGraphNode(n));
    }
    for(; !workSet.isEmpty() && cycleCount < maxIterations; cycleCount++) {
      DiGraphNode<N, E> source = workSet.iterator().next();
      N sourceValue = source.getValue();
      workSet.remove(source);
      List<DiGraphEdge<N, E>> outEdges = source.getOutEdges();
      for (DiGraphEdge<N, E> edge : outEdges) {
        DiGraphNode<N, E> var_2166 = edge.getDestination();
        N destNode = var_2166.getValue();
        if(callback.traverseEdge(sourceValue, edge.getValue(), destNode)) {
          workSet.add(edge.getDestination());
        }
      }
    }
    Preconditions.checkState(cycleCount != maxIterations, NON_HALTING_ERROR_MSG);
  }
  public static interface EdgeCallback<Node extends java.lang.Object, Edge extends java.lang.Object>  {
    boolean traverseEdge(Node source, Edge e, Node destination);
  }
}