package com.google.javascript.jscomp;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.AbstractCompiler.LifeCycleStage;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.graph.Graph.GraphEdge;
import com.google.javascript.jscomp.graph.LinkedUndirectedGraph;
import com.google.javascript.jscomp.graph.UndiGraph;
import com.google.javascript.jscomp.graph.UndiGraph.UndiGraphEdge;
import com.google.javascript.jscomp.graph.UndiGraph.UndiGraphNode;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TokenStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.annotation.Nullable;

class RenameProperties implements CompilerPass  {
  final private AbstractCompiler compiler;
  final private boolean generatePseudoNames;
  final private VariableMap prevUsedPropertyMap;
  final private List<Node> stringNodesToRename = new ArrayList<Node>();
  final private Map<Node, Node> callNodeToParentMap = new HashMap<Node, Node>();
  final private char[] reservedCharacters;
  final private Map<String, Property> propertyMap = new HashMap<String, Property>();
  final private UndiGraph<Property, PropertyAffinity> affinityGraph;
  final private Set<String> externedNames = new HashSet<String>(Arrays.asList("prototype"));
  final private Set<String> quotedNames = new HashSet<String>();
  final private static Comparator<Property> FREQUENCY_COMPARATOR = new Comparator<Property>() {
      @Override() public int compare(Property p1, Property p2) {
        if(p1.numOccurrences != p2.numOccurrences) {
          return p2.numOccurrences - p1.numOccurrences;
        }
        else 
          if(p1.affinityScore != p2.affinityScore) {
            return p2.affinityScore - p1.affinityScore;
          }
        return p1.oldName.compareTo(p2.oldName);
      }
  };
  final static String RENAME_PROPERTY_FUNCTION_NAME = "JSCompiler_renameProperty";
  final static DiagnosticType BAD_CALL = DiagnosticType.error("JSC_BAD_RENAME_PROPERTY_FUNCTION_NAME_CALL", "Bad " + RENAME_PROPERTY_FUNCTION_NAME + " call - " + "argument must be a string literal");
  final static DiagnosticType BAD_ARG = DiagnosticType.error("JSC_BAD_RENAME_PROPERTY_FUNCTION_NAME_ARG", "Bad " + RENAME_PROPERTY_FUNCTION_NAME + " argument - " + "\'{0}\' is not a valid JavaScript identifier");
  RenameProperties(AbstractCompiler compiler, boolean affinity, boolean generatePseudoNames) {
    this(compiler, affinity, generatePseudoNames, null, null);
  }
  RenameProperties(AbstractCompiler compiler, boolean affinity, boolean generatePseudoNames, VariableMap prevUsedPropertyMap) {
    this(compiler, affinity, generatePseudoNames, prevUsedPropertyMap, null);
  }
  RenameProperties(AbstractCompiler compiler, boolean affinity, boolean generatePseudoNames, VariableMap prevUsedPropertyMap, @Nullable() char[] reservedCharacters) {
    super();
    this.compiler = compiler;
    this.generatePseudoNames = generatePseudoNames;
    this.prevUsedPropertyMap = prevUsedPropertyMap;
    this.reservedCharacters = reservedCharacters;
    if(affinity) {
      this.affinityGraph = LinkedUndirectedGraph.createWithoutAnnotations();
    }
    else {
      this.affinityGraph = null;
    }
  }
  VariableMap getPropertyMap() {
    ImmutableMap.Builder<String, String> map = ImmutableMap.builder();
    for (Property p : propertyMap.values()) {
      if(p.newName != null) {
        map.put(p.oldName, p.newName);
      }
    }
    return new VariableMap(map.build());
  }
  private void computeAffinityScores() {
    for (Property p : propertyMap.values()) {
      UndiGraphNode<Property, PropertyAffinity> node = affinityGraph.getUndirectedGraphNode(p);
      int affinityScore = 0;
      for(java.util.Iterator<com.google.javascript.jscomp.graph.UndiGraph.UndiGraphEdge<com.google.javascript.jscomp.RenameProperties.Property, com.google.javascript.jscomp.RenameProperties.PropertyAffinity>> edgeIterator = node.getNeighborEdgesIterator(); edgeIterator.hasNext(); ) {
        UndiGraphEdge<Property, PropertyAffinity> edge = edgeIterator.next();
        affinityScore += edge.getValue().affinity + (node == edge.getNodeA() ? edge.getNodeB().getValue().numOccurrences : edge.getNodeA().getValue().numOccurrences);
      }
      node.getValue().affinityScore = affinityScore;
    }
  }
  private void generateNames(Set<Property> props, Set<String> reservedNames) {
    NameGenerator nameGen = new NameGenerator(reservedNames, "", reservedCharacters);
    for (Property p : props) {
      if(generatePseudoNames) {
        p.newName = "$" + p.oldName + "$";
      }
      else {
        if(p.newName == null) {
          p.newName = nameGen.generateNextName();
        }
      }
      reservedNames.add(p.newName);
      compiler.addToDebugLog(p.oldName + " => " + p.newName);
    }
  }
  @Override() public void process(Node externs, Node root) {
    Preconditions.checkState(compiler.getLifeCycleStage().isNormalized());
    NodeTraversal.traverse(compiler, externs, new ProcessExterns());
    NodeTraversal.traverse(compiler, root, new ProcessProperties());
    Set<String> reservedNames = new HashSet<String>(externedNames.size() + quotedNames.size());
    reservedNames.addAll(externedNames);
    reservedNames.addAll(quotedNames);
    if(prevUsedPropertyMap != null) {
      reusePropertyNames(reservedNames, propertyMap.values());
    }
    compiler.addToDebugLog("JS property assignments:");
    if(affinityGraph != null) {
      computeAffinityScores();
    }
    Set<Property> propsByFreq = new TreeSet<Property>(FREQUENCY_COMPARATOR);
    propsByFreq.addAll(propertyMap.values());
    generateNames(propsByFreq, reservedNames);
    boolean changed = false;
    for (Node n : stringNodesToRename) {
      String oldName = n.getString();
      Property var_1367 = propertyMap.get(oldName);
      Property p = var_1367;
      if(p != null && p.newName != null) {
        Preconditions.checkState(oldName.equals(p.oldName));
        n.setString(p.newName);
        changed = changed || !p.newName.equals(oldName);
      }
    }
    for (Map.Entry<Node, Node> nodeEntry : callNodeToParentMap.entrySet()) {
      Node parent = nodeEntry.getValue();
      Node firstArg = nodeEntry.getKey().getFirstChild().getNext();
      StringBuilder sb = new StringBuilder();
      for (String oldName : firstArg.getString().split("[.]")) {
        Property p = propertyMap.get(oldName);
        String replacement;
        if(p != null && p.newName != null) {
          Preconditions.checkState(oldName.equals(p.oldName));
          replacement = p.newName;
        }
        else {
          replacement = oldName;
        }
        if(sb.length() > 0) {
          sb.append('.');
        }
        sb.append(replacement);
      }
      parent.replaceChild(nodeEntry.getKey(), IR.string(sb.toString()));
      changed = true;
    }
    if(changed) {
      compiler.reportCodeChange();
    }
    compiler.setLifeCycleStage(LifeCycleStage.NORMALIZED_OBFUSCATED);
  }
  private void reusePropertyNames(Set<String> reservedNames, Collection<Property> allProps) {
    for (Property prop : allProps) {
      String prevName = prevUsedPropertyMap.lookupNewName(prop.oldName);
      if(!generatePseudoNames && prevName != null) {
        if(reservedNames.contains(prevName)) {
          continue ;
        }
        prop.newName = prevName;
        reservedNames.add(prevName);
      }
    }
  }
  
  private class ProcessExterns extends AbstractPostOrderCallback  {
    @Override() public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()){
        case Token.GETPROP:
        Node dest = n.getFirstChild().getNext();
        if(dest.isString()) {
          externedNames.add(dest.getString());
        }
        break ;
        case Token.OBJECTLIT:
        for(com.google.javascript.rhino.Node child = n.getFirstChild(); child != null; child = child.getNext()) {
          externedNames.add(child.getString());
        }
        break ;
      }
    }
  }
  
  private class ProcessProperties extends AbstractPostOrderCallback implements ScopedCallback  {
    private Set<Property> currentHighAffinityProperties = null;
    private void countCallCandidates(NodeTraversal t, Node callNode) {
      Node firstArg = callNode.getFirstChild().getNext();
      if(!firstArg.isString()) {
        t.report(callNode, BAD_CALL);
        return ;
      }
      for (String name : firstArg.getString().split("[.]")) {
        if(!TokenStream.isJSIdentifier(name)) {
          t.report(callNode, BAD_ARG, name);
          continue ;
        }
        if(!externedNames.contains(name)) {
          countPropertyOccurrence(name);
        }
      }
    }
    private void countPropertyOccurrence(String name) {
      Property prop = propertyMap.get(name);
      if(prop == null) {
        prop = new Property(name);
        propertyMap.put(name, prop);
        if(affinityGraph != null) {
          affinityGraph.createNode(prop);
        }
      }
      prop.numOccurrences++;
      if(currentHighAffinityProperties != null) {
        currentHighAffinityProperties.add(prop);
      }
    }
    @Override() public void enterScope(NodeTraversal t) {
      if(!t.inGlobalScope() && t.getScope().getParent().isGlobal()) {
        currentHighAffinityProperties = Sets.newHashSet();
      }
    }
    @Override() public void exitScope(NodeTraversal t) {
      if(affinityGraph == null) {
        return ;
      }
      if(!t.inGlobalScope() && t.getScope().getParent().isGlobal()) {
        for (Property p1 : currentHighAffinityProperties) {
          for (Property p2 : currentHighAffinityProperties) {
            if(p1.oldName.compareTo(p2.oldName) < 0) {
              GraphEdge<Property, PropertyAffinity> edge = affinityGraph.getFirstEdge(p1, p2);
              if(edge == null) {
                affinityGraph.connect(p1, new PropertyAffinity(1), p2);
              }
              else {
                edge.getValue().increase();
              }
            }
          }
        }
        currentHighAffinityProperties = null;
      }
    }
    private void maybeMarkCandidate(Node n) {
      String name = n.getString();
      if(!externedNames.contains(name)) {
        stringNodesToRename.add(n);
        countPropertyOccurrence(name);
      }
    }
    @Override() public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()){
        case Token.GETPROP:
        Node propNode = n.getFirstChild().getNext();
        if(propNode.isString()) {
          maybeMarkCandidate(propNode);
        }
        break ;
        case Token.OBJECTLIT:
        for(com.google.javascript.rhino.Node key = n.getFirstChild(); key != null; key = key.getNext()) {
          if(!key.isQuotedString()) {
            maybeMarkCandidate(key);
          }
          else {
            quotedNames.add(key.getString());
          }
        }
        break ;
        case Token.GETELEM:
        Node child = n.getLastChild();
        if(child != null && child.isString()) {
          quotedNames.add(child.getString());
        }
        break ;
        case Token.CALL:
        Node fnName = n.getFirstChild();
        if(fnName.isName() && RENAME_PROPERTY_FUNCTION_NAME.equals(fnName.getString())) {
          callNodeToParentMap.put(n, parent);
          countCallCandidates(t, n);
        }
        break ;
        case Token.FUNCTION:
        if(NodeUtil.isFunctionDeclaration(n)) {
          String name = n.getFirstChild().getString();
          if(RENAME_PROPERTY_FUNCTION_NAME.equals(name)) {
            if(parent.isExprResult()) {
              parent.detachFromParent();
            }
            else {
              parent.removeChild(n);
            }
            compiler.reportCodeChange();
          }
        }
        else 
          if(parent.isName() && RENAME_PROPERTY_FUNCTION_NAME.equals(parent.getString())) {
            Node varNode = parent.getParent();
            if(varNode.isVar()) {
              varNode.removeChild(parent);
              if(!varNode.hasChildren()) {
                varNode.detachFromParent();
              }
              compiler.reportCodeChange();
            }
          }
        break ;
      }
    }
  }
  
  private class Property  {
    final String oldName;
    String newName;
    int numOccurrences;
    int affinityScore = 0;
    Property(String name) {
      super();
      this.oldName = name;
    }
  }
  
  private class PropertyAffinity  {
    private int affinity = 0;
    private PropertyAffinity(int affinity) {
      super();
      this.affinity = affinity;
    }
    private void increase() {
      affinity++;
    }
  }
}