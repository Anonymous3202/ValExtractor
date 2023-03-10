package com.google.javascript.jscomp.graph;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
public class LinkedDirectedGraph<E extends java.lang.Object, N extends java.lang.Object> extends DiGraph<N, E> implements GraphvizGraph  {
  final protected Map<N, LinkedDirectedGraphNode<N, E>> nodes = Maps.newHashMap();
  final private boolean useNodeAnnotations;
  final private boolean useEdgeAnnotations;
  protected LinkedDirectedGraph(boolean useNodeAnnotations, boolean useEdgeAnnotations) {
    super();
    this.useNodeAnnotations = useNodeAnnotations;
    this.useEdgeAnnotations = useEdgeAnnotations;
  }
  @Override() public Collection<GraphNode<N, E>> getNodes() {
    return Collections.<GraphNode<N, E>>unmodifiableCollection(nodes.values());
  }
  @Override() public DiGraphNode<N, E> createDirectedGraphNode(N nodeValue) {
    LinkedDirectedGraphNode<N, E> node = nodes.get(nodeValue);
    if(node == null) {
      node = useNodeAnnotations ? new AnnotatedLinkedDirectedGraphNode<N, E>(nodeValue) : new LinkedDirectedGraphNode<N, E>(nodeValue);
      nodes.put(nodeValue, node);
    }
    return node;
  }
  @Override() public DiGraphNode<N, E> getDirectedGraphNode(N nodeValue) {
    return nodes.get(nodeValue);
  }
  @Override() public GraphEdge<N, E> getFirstEdge(N n1, N n2) {
    DiGraphNode<N, E> dNode1 = getNodeOrFail(n1);
    DiGraphNode<N, E> dNode2 = getNodeOrFail(n2);
    for (DiGraphEdge<N, E> outEdge : dNode1.getOutEdges()) {
      DiGraphNode<N, E> var_2167 = outEdge.getDestination();
      if(var_2167 == dNode2) {
        return outEdge;
      }
    }
    for (DiGraphEdge<N, E> outEdge : dNode2.getOutEdges()) {
      if(outEdge.getDestination() == dNode1) {
        return outEdge;
      }
    }
    return null;
  }
  @Override() public GraphNode<N, E> createNode(N value) {
    return createDirectedGraphNode(value);
  }
  @Override() public GraphNode<N, E> getNode(N nodeValue) {
    return getDirectedGraphNode(nodeValue);
  }
  @Override() public Iterable<DiGraphNode<N, E>> getDirectedGraphNodes() {
    return Collections.<DiGraphNode<N, E>>unmodifiableCollection(nodes.values());
  }
  @Override() public Iterator<GraphNode<N, E>> getNeighborNodesIterator(N value) {
    LinkedDirectedGraphNode<N, E> node = nodes.get(value);
    Preconditions.checkNotNull(node);
    return node.neighborIterator();
  }
  public static  <E extends java.lang.Object, N extends java.lang.Object> LinkedDirectedGraph<N, E> create() {
    return new LinkedDirectedGraph<N, E>(true, true);
  }
  public static  <E extends java.lang.Object, N extends java.lang.Object> LinkedDirectedGraph<N, E> createWithEdgeAnnotations() {
    return new LinkedDirectedGraph<N, E>(false, true);
  }
  public static  <E extends java.lang.Object, N extends java.lang.Object> LinkedDirectedGraph<N, E> createWithNodeAnnotations() {
    return new LinkedDirectedGraph<N, E>(true, false);
  }
  public static  <E extends java.lang.Object, N extends java.lang.Object> LinkedDirectedGraph<N, E> createWithoutAnnotations() {
    return new LinkedDirectedGraph<N, E>(false, false);
  }
  @Override() public List<DiGraphEdge<N, E>> getDirectedGraphEdges(N n1, N n2) {
    DiGraphNode<N, E> dNode1 = getNodeOrFail(n1);
    DiGraphNode<N, E> dNode2 = getNodeOrFail(n2);
    List<DiGraphEdge<N, E>> edges = Lists.newArrayList();
    for (DiGraphEdge<N, E> outEdge : dNode1.getOutEdges()) {
      if(outEdge.getDestination() == dNode2) {
        edges.add(outEdge);
      }
    }
    return edges;
  }
  @Override() public List<DiGraphEdge<N, E>> getInEdges(N nodeValue) {
    LinkedDirectedGraphNode<N, E> node = getNodeOrFail(nodeValue);
    return Collections.<DiGraphEdge<N, E>>unmodifiableList(node.getInEdges());
  }
  @Override() public List<DiGraphEdge<N, E>> getOutEdges(N nodeValue) {
    LinkedDirectedGraphNode<N, E> node = getNodeOrFail(nodeValue);
    return Collections.<DiGraphEdge<N, E>>unmodifiableList(node.getOutEdges());
  }
  @Override() public List<DiGraphNode<N, E>> getDirectedPredNodes(DiGraphNode<N, E> dNode) {
    if(dNode == null) {
      throw new IllegalArgumentException(dNode + " is null");
    }
    List<DiGraphNode<N, E>> nodeList = Lists.newArrayList();
    for (DiGraphEdge<N, E> edge : dNode.getInEdges()) {
      nodeList.add(edge.getSource());
    }
    return nodeList;
  }
  @Override() public List<DiGraphNode<N, E>> getDirectedPredNodes(N nodeValue) {
    return getDirectedPredNodes(nodes.get(nodeValue));
  }
  @Override() public List<DiGraphNode<N, E>> getDirectedSuccNodes(DiGraphNode<N, E> dNode) {
    if(dNode == null) {
      throw new IllegalArgumentException(dNode + " is null");
    }
    List<DiGraphNode<N, E>> nodeList = Lists.newArrayList();
    for (DiGraphEdge<N, E> edge : dNode.getOutEdges()) {
      nodeList.add(edge.getDestination());
    }
    return nodeList;
  }
  @Override() public List<DiGraphNode<N, E>> getDirectedSuccNodes(N nodeValue) {
    return getDirectedSuccNodes(nodes.get(nodeValue));
  }
  @Override() public List<GraphEdge<N, E>> getEdges() {
    List<GraphEdge<N, E>> result = Lists.newArrayList();
    for (DiGraphNode<N, E> node : nodes.values()) {
      for (DiGraphEdge<N, E> edge : node.getOutEdges()) {
        result.add(edge);
      }
    }
    return Collections.unmodifiableList(result);
  }
  @Override() public List<GraphEdge<N, E>> getEdges(N n1, N n2) {
    List<DiGraphEdge<N, E>> forwardEdges = getDirectedGraphEdges(n1, n2);
    List<DiGraphEdge<N, E>> backwardEdges = getDirectedGraphEdges(n2, n1);
    int totalSize = forwardEdges.size() + backwardEdges.size();
    List<GraphEdge<N, E>> edges = Lists.newArrayListWithCapacity(totalSize);
    edges.addAll(forwardEdges);
    edges.addAll(backwardEdges);
    return edges;
  }
  public List<GraphNode<N, E>> getNeighborNodes(DiGraphNode<N, E> node) {
    List<GraphNode<N, E>> result = Lists.newArrayList();
    for(java.util.Iterator<com.google.javascript.jscomp.graph.GraphNode<com.google.javascript.jscomp.graph.LinkedDirectedGraph@N, com.google.javascript.jscomp.graph.LinkedDirectedGraph@E>> i = ((LinkedDirectedGraphNode<N, E>)node).neighborIterator(); i.hasNext(); ) {
      result.add(i.next());
    }
    return result;
  }
  @Override() public List<GraphNode<N, E>> getNeighborNodes(N value) {
    DiGraphNode<N, E> node = getDirectedGraphNode(value);
    return getNeighborNodes(node);
  }
  @Override() public List<GraphvizEdge> getGraphvizEdges() {
    List<GraphvizEdge> edgeList = Lists.newArrayList();
    for (LinkedDirectedGraphNode<N, E> node : nodes.values()) {
      for (DiGraphEdge<N, E> edge : node.getOutEdges()) {
        edgeList.add((LinkedDirectedGraphEdge<N, E>)edge);
      }
    }
    return edgeList;
  }
  @Override() public List<GraphvizNode> getGraphvizNodes() {
    List<GraphvizNode> nodeList = Lists.newArrayListWithCapacity(nodes.size());
    for (LinkedDirectedGraphNode<N, E> node : nodes.values()) {
      nodeList.add(node);
    }
    return nodeList;
  }
  @Override() public String getName() {
    return "LinkedGraph";
  }
  @Override() public SubGraph<N, E> newSubGraph() {
    return new SimpleSubGraph<N, E>(this);
  }
  private boolean isConnectedInDirection(N n1, Predicate<E> edgeMatcher, N n2) {
    DiGraphNode<N, E> dNode1 = getNodeOrFail(n1);
    DiGraphNode<N, E> dNode2 = getNodeOrFail(n2);
    for (DiGraphEdge<N, E> outEdge : dNode1.getOutEdges()) {
      if(outEdge.getDestination() == dNode2 && edgeMatcher.apply(outEdge.getValue())) {
        return true;
      }
    }
    return false;
  }
  @Override() public boolean isConnectedInDirection(N n1, N n2) {
    return isConnectedInDirection(n1, Predicates.<E>alwaysTrue(), n2);
  }
  @Override() public boolean isConnectedInDirection(N n1, E edgeValue, N n2) {
    return isConnectedInDirection(n1, Predicates.equalTo(edgeValue), n2);
  }
  @Override() public boolean isDirected() {
    return true;
  }
  @Override() public int getNodeDegree(N value) {
    DiGraphNode<N, E> node = getNodeOrFail(value);
    return node.getInEdges().size() + node.getOutEdges().size();
  }
  @Override() public void connect(N srcValue, E edgeValue, N destValue) {
    LinkedDirectedGraphNode<N, E> src = getNodeOrFail(srcValue);
    LinkedDirectedGraphNode<N, E> dest = getNodeOrFail(destValue);
    LinkedDirectedGraphEdge<N, E> edge = useEdgeAnnotations ? new AnnotatedLinkedDirectedGraphEdge<N, E>(src, edgeValue, dest) : new LinkedDirectedGraphEdge<N, E>(src, edgeValue, dest);
    src.getOutEdges().add(edge);
    dest.getInEdges().add(edge);
  }
  @Override() public void disconnect(N n1, N n2) {
    disconnectInDirection(n1, n2);
    disconnectInDirection(n2, n1);
  }
  @Override() public void disconnectInDirection(N srcValue, N destValue) {
    LinkedDirectedGraphNode<N, E> src = getNodeOrFail(srcValue);
    LinkedDirectedGraphNode<N, E> dest = getNodeOrFail(destValue);
    for (DiGraphEdge<?, E> edge : getDirectedGraphEdges(srcValue, destValue)) {
      src.getOutEdges().remove(edge);
      dest.getInEdges().remove(edge);
    }
  }
  static class AnnotatedLinkedDirectedGraphEdge<E extends java.lang.Object, N extends java.lang.Object> extends LinkedDirectedGraphEdge<N, E>  {
    protected Annotation annotation;
    AnnotatedLinkedDirectedGraphEdge(DiGraphNode<N, E> sourceNode, E edgeValue, DiGraphNode<N, E> destNode) {
      super(sourceNode, edgeValue, destNode);
    }
    @SuppressWarnings(value = {"unchecked", }) @Override() public  <A extends com.google.javascript.jscomp.graph.Annotation> A getAnnotation() {
      return (A)annotation;
    }
    @Override() public void setAnnotation(Annotation data) {
      annotation = data;
    }
  }
  static class AnnotatedLinkedDirectedGraphNode<E extends java.lang.Object, N extends java.lang.Object> extends LinkedDirectedGraphNode<N, E>  {
    protected Annotation annotation;
    AnnotatedLinkedDirectedGraphNode(N nodeValue) {
      super(nodeValue);
    }
    @SuppressWarnings(value = {"unchecked", }) @Override() public  <A extends com.google.javascript.jscomp.graph.Annotation> A getAnnotation() {
      return (A)annotation;
    }
    @Override() public void setAnnotation(Annotation data) {
      annotation = data;
    }
  }
  static class LinkedDirectedGraphEdge<E extends java.lang.Object, N extends java.lang.Object> implements DiGraphEdge<N, E>, GraphvizEdge  {
    private DiGraphNode<N, E> sourceNode;
    private DiGraphNode<N, E> destNode;
    final protected E value;
    LinkedDirectedGraphEdge(DiGraphNode<N, E> sourceNode, E edgeValue, DiGraphNode<N, E> destNode) {
      super();
      this.value = edgeValue;
      this.sourceNode = sourceNode;
      this.destNode = destNode;
    }
    @Override() public  <A extends com.google.javascript.jscomp.graph.Annotation> A getAnnotation() {
      throw new UnsupportedOperationException("Graph initialized with edge annotations turned off");
    }
    @Override() public DiGraphNode<N, E> getDestination() {
      return destNode;
    }
    @Override() public DiGraphNode<N, E> getSource() {
      return sourceNode;
    }
    @Override() public E getValue() {
      return value;
    }
    @Override() public GraphNode<N, E> getNodeA() {
      return sourceNode;
    }
    @Override() public GraphNode<N, E> getNodeB() {
      return destNode;
    }
    @Override() public String getColor() {
      return "black";
    }
    @Override() public String getLabel() {
      return value != null ? value.toString() : "null";
    }
    @Override() public String getNode1Id() {
      return ((LinkedDirectedGraphNode<N, E>)sourceNode).getId();
    }
    @Override() public String getNode2Id() {
      return ((LinkedDirectedGraphNode<N, E>)destNode).getId();
    }
    @Override() public String toString() {
      return sourceNode.toString() + " -> " + destNode.toString();
    }
    @Override() public void setAnnotation(Annotation data) {
      throw new UnsupportedOperationException("Graph initialized with edge annotations turned off");
    }
    @Override() public void setDestination(DiGraphNode<N, E> node) {
      destNode = node;
    }
    @Override() public void setSource(DiGraphNode<N, E> node) {
      sourceNode = node;
    }
  }
  static class LinkedDirectedGraphNode<E extends java.lang.Object, N extends java.lang.Object> implements DiGraphNode<N, E>, GraphvizNode  {
    List<DiGraphEdge<N, E>> inEdgeList = Lists.newArrayList();
    List<DiGraphEdge<N, E>> outEdgeList = Lists.newArrayList();
    final protected N value;
    LinkedDirectedGraphNode(N nodeValue) {
      super();
      this.value = nodeValue;
    }
    @Override() public  <A extends com.google.javascript.jscomp.graph.Annotation> A getAnnotation() {
      throw new UnsupportedOperationException("Graph initialized with node annotations turned off");
    }
    private Iterator<GraphNode<N, E>> neighborIterator() {
      return new NeighborIterator();
    }
    @Override() public List<DiGraphEdge<N, E>> getInEdges() {
      return inEdgeList;
    }
    @Override() public List<DiGraphEdge<N, E>> getOutEdges() {
      return outEdgeList;
    }
    @Override() public N getValue() {
      return value;
    }
    @Override() public String getColor() {
      return "white";
    }
    @Override() public String getId() {
      return "LDN" + hashCode();
    }
    @Override() public String getLabel() {
      return value != null ? value.toString() : "null";
    }
    @Override() public String toString() {
      return getLabel();
    }
    @Override() public void setAnnotation(Annotation data) {
      throw new UnsupportedOperationException("Graph initialized with node annotations turned off");
    }
    
    private class NeighborIterator implements Iterator<GraphNode<N, E>>  {
      final private Iterator<DiGraphEdge<N, E>> in = inEdgeList.iterator();
      final private Iterator<DiGraphEdge<N, E>> out = outEdgeList.iterator();
      @Override() public GraphNode<N, E> next() {
        boolean isOut = !in.hasNext();
        Iterator<DiGraphEdge<N, E>> curIterator = isOut ? out : in;
        DiGraphEdge<N, E> s = curIterator.next();
        return isOut ? s.getDestination() : s.getSource();
      }
      @Override() public boolean hasNext() {
        return in.hasNext() || out.hasNext();
      }
      @Override() public void remove() {
        throw new UnsupportedOperationException("Remove not supported.");
      }
    }
  }
}