package com.google.javascript.jscomp;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.CodingConvention.SubclassRelationship;
import com.google.javascript.jscomp.GatherSideEffectSubexpressionsCallback.GetReplacementSideEffectSubexpressions;
import com.google.javascript.jscomp.GatherSideEffectSubexpressionsCallback.SideEffectAccumulator;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.jscomp.graph.DiGraph;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.jscomp.graph.FixedPointGraphTraversal;
import com.google.javascript.jscomp.graph.FixedPointGraphTraversal.EdgeCallback;
import com.google.javascript.jscomp.graph.LinkedDirectedGraph;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class NameAnalyzer implements CompilerPass  {
  final private AbstractCompiler compiler;
  final private Map<String, JsName> allNames = Maps.newTreeMap();
  private DiGraph<JsName, RefType> referenceGraph = LinkedDirectedGraph.createWithoutAnnotations();
  final private ListMultimap<Node, NameInformation> scopes = LinkedListMultimap.create();
  final private static String PROTOTYPE_SUBSTRING = ".prototype.";
  final private static int PROTOTYPE_SUBSTRING_LEN = PROTOTYPE_SUBSTRING.length();
  final private static int PROTOTYPE_SUFFIX_LEN = ".prototype".length();
  final private static String WINDOW = "window";
  final private static String FUNCTION = "Function";
  final static Set<String> DEFAULT_GLOBAL_NAMES = ImmutableSet.of("window", "goog.global");
  final private boolean removeUnreferenced;
  final private Set<String> globalNames;
  final private AstChangeProxy changeProxy;
  final private Set<String> externalNames = Sets.newHashSet();
  final private List<RefNode> refNodes = Lists.newArrayList();
  final private Map<String, AliasSet> aliases = Maps.newHashMap();
  final private static Predicate<Node> NON_LOCAL_RESULT_PREDICATE = new Predicate<Node>() {
      @Override() public boolean apply(Node input) {
        if(input.isCall()) {
          return false;
        }
        return true;
      }
  };
  NameAnalyzer(AbstractCompiler compiler, boolean removeUnreferenced) {
    super();
    this.compiler = compiler;
    this.removeUnreferenced = removeUnreferenced;
    this.globalNames = DEFAULT_GLOBAL_NAMES;
    this.changeProxy = new AstChangeProxy();
  }
  private JsName getName(String name, boolean canCreate) {
    if(canCreate) {
      createName(name);
    }
    return allNames.get(name);
  }
  private List<NameInformation> getDependencyScope(Node n) {
    for (Node node : n.getAncestors()) {
      List<NameInformation> refs = scopes.get(node);
      if(!refs.isEmpty()) {
        return refs;
      }
    }
    return Collections.emptyList();
  }
  private List<NameInformation> getEnclosingFunctionDependencyScope(NodeTraversal t) {
    Node function = t.getEnclosingFunction();
    if(function == null) {
      return Collections.emptyList();
    }
    List<NameInformation> refs = scopes.get(function);
    if(!refs.isEmpty()) {
      return refs;
    }
    Node parent = function.getParent();
    if(parent != null) {
      while(parent.isHook()){
        parent = parent.getParent();
      }
      if(parent.isName()) {
        return scopes.get(parent);
      }
      if(parent.isAssign()) {
        return scopes.get(parent);
      }
    }
    return Collections.emptyList();
  }
  private List<Node> getRhsSubexpressions(Node n) {
    switch (n.getType()){
      case Token.EXPR_RESULT:
      return getRhsSubexpressions(n.getFirstChild());
      case Token.FUNCTION:
      return Collections.emptyList();
      case Token.NAME:
      {
        Node rhs = n.getFirstChild();
        if(rhs != null) {
          return Lists.newArrayList(rhs);
        }
        else {
          return Collections.emptyList();
        }
      }
      case Token.ASSIGN:
      {
        Node lhs = n.getFirstChild();
        Node rhs = lhs.getNext();
        return Lists.newArrayList(lhs, rhs);
      }
      case Token.VAR:
      {
        List<Node> nodes = Lists.newArrayList();
        for (Node child : n.children()) {
          nodes.addAll(getRhsSubexpressions(child));
        }
        return nodes;
      }
      default:
      throw new IllegalArgumentException("AstChangeProxy::getRhs " + n);
    }
  }
  private List<Node> getSideEffectNodes(Node n) {
    List<Node> subexpressions = Lists.newArrayList();
    NodeTraversal.traverse(compiler, n, new GatherSideEffectSubexpressionsCallback(compiler, new GetReplacementSideEffectSubexpressions(compiler, subexpressions)));
    List<Node> replacements = Lists.newArrayListWithExpectedSize(subexpressions.size());
    for (Node subexpression : subexpressions) {
      replacements.add(NodeUtil.newExpr(subexpression));
    }
    return replacements;
  }
  private NameInformation createNameInformation(NodeTraversal t, Node n) {
    Node parent = n.getParent();
    String name = "";
    Node rootNameNode = n;
    boolean bNameWasShortened = false;
    while(true){
      if(NodeUtil.isGet(rootNameNode)) {
        Node prop = rootNameNode.getLastChild();
        if(rootNameNode.isGetProp()) {
          name = "." + prop.getString() + name;
        }
        else {
          bNameWasShortened = true;
          name = "";
        }
        rootNameNode = rootNameNode.getFirstChild();
      }
      else 
        if(NodeUtil.isObjectLitKey(rootNameNode, rootNameNode.getParent())) {
          name = "." + rootNameNode.getString() + name;
          Node objLit = rootNameNode.getParent();
          Node objLitParent = objLit.getParent();
          if(objLitParent.isAssign()) {
            rootNameNode = objLitParent.getFirstChild();
          }
          else 
            if(objLitParent.isName()) {
              rootNameNode = objLitParent;
            }
            else 
              if(objLitParent.isStringKey()) {
                rootNameNode = objLitParent;
              }
              else {
                return null;
              }
        }
        else {
          break ;
        }
    }
    if(parent.isCall() && t.inGlobalScope()) {
      CodingConvention convention = compiler.getCodingConvention();
      SubclassRelationship classes = convention.getClassesDefinedByCall(parent);
      if(classes != null) {
        NameInformation nameInfo = new NameInformation();
        nameInfo.name = classes.subclassName;
        nameInfo.onlyAffectsClassDef = true;
        nameInfo.superclass = classes.superclassName;
        return nameInfo;
      }
      String singletonGetterClass = convention.getSingletonGetterClassName(parent);
      if(singletonGetterClass != null) {
        NameInformation nameInfo = new NameInformation();
        nameInfo.name = singletonGetterClass;
        nameInfo.onlyAffectsClassDef = true;
        return nameInfo;
      }
    }
    switch (rootNameNode.getType()){
      case Token.NAME:
      if(!bNameWasShortened && n.isGetProp() && parent.isAssign() && "prototype".equals(n.getLastChild().getString())) {
        if(createNameInformation(t, n.getFirstChild()) != null) {
          name = rootNameNode.getString() + name;
          name = name.substring(0, name.length() - PROTOTYPE_SUFFIX_LEN);
          NameInformation nameInfo = new NameInformation();
          nameInfo.name = name;
          return nameInfo;
        }
        else {
          return null;
        }
      }
      return createNameInformation(rootNameNode.getString() + name, t.getScope(), rootNameNode);
      case Token.THIS:
      if(t.inGlobalScope()) {
        NameInformation nameInfo = new NameInformation();
        if(name.indexOf('.') == 0) {
          nameInfo.name = name.substring(1);
        }
        else {
          nameInfo.name = name;
        }
        nameInfo.isExternallyReferenceable = true;
        return nameInfo;
      }
      return null;
      default:
      return null;
    }
  }
  private NameInformation createNameInformation(String name, Scope scope, Node rootNameNode) {
    String rootName = rootNameNode.getString();
    Var v = scope.getVar(rootName);
    boolean isExtern = (v == null && externalNames.contains(rootName));
    boolean isGlobalRef = (v != null && v.isGlobal()) || isExtern || rootName.equals(WINDOW);
    if(!isGlobalRef) {
      return null;
    }
    NameInformation nameInfo = new NameInformation();
    int idx = name.indexOf(PROTOTYPE_SUBSTRING);
    if(idx != -1) {
      nameInfo.isPrototype = true;
      nameInfo.prototypeClass = name.substring(0, idx);
      nameInfo.prototypeProperty = name.substring(idx + PROTOTYPE_SUBSTRING_LEN);
    }
    nameInfo.name = name;
    nameInfo.isExternallyReferenceable = isExtern || isExternallyReferenceable(scope, name);
    return nameInfo;
  }
  private Node collapseReplacements(List<Node> replacements) {
    Node expr = null;
    for (Node rep : replacements) {
      if(rep.isExprResult()) {
        rep = rep.getFirstChild();
        rep.detachFromParent();
      }
      if(expr == null) {
        expr = rep;
      }
      else {
        expr = IR.comma(expr, rep);
      }
    }
    return expr;
  }
  String getHtmlReport() {
    StringBuilder sb = new StringBuilder();
    sb.append("<html><body><style type=\"text/css\">" + "body, td, p {font-family: Arial; font-size: 83%} " + "ul {margin-top:2px; margin-left:0px; padding-left:1em;} " + "li {margin-top:3px; margin-left:24px; padding-left:0px;" + "padding-bottom: 4px}</style>");
    sb.append("OVERALL STATS<ul>");
    appendListItem(sb, "Total Names: " + countOf(TriState.BOTH, TriState.BOTH));
    appendListItem(sb, "Total Classes: " + countOf(TriState.TRUE, TriState.BOTH));
    appendListItem(sb, "Total Static Functions: " + countOf(TriState.FALSE, TriState.BOTH));
    appendListItem(sb, "Referenced Names: " + countOf(TriState.BOTH, TriState.TRUE));
    appendListItem(sb, "Referenced Classes: " + countOf(TriState.TRUE, TriState.TRUE));
    appendListItem(sb, "Referenced Functions: " + countOf(TriState.FALSE, TriState.TRUE));
    sb.append("</ul>");
    sb.append("ALL NAMES<ul>\n");
    for (JsName node : allNames.values()) {
      sb.append("<li>" + nameAnchor(node.name) + "<ul>");
      if(node.prototypeNames.size() > 0) {
        sb.append("<li>PROTOTYPES: ");
        Iterator<String> protoIter = node.prototypeNames.iterator();
        while(protoIter.hasNext()){
          sb.append(protoIter.next());
          if(protoIter.hasNext()) {
            sb.append(", ");
          }
        }
      }
      if(referenceGraph.hasNode(node)) {
        List<DiGraphEdge<JsName, RefType>> refersTo = referenceGraph.getOutEdges(node);
        if(refersTo.size() > 0) {
          sb.append("<li>REFERS TO: ");
          Iterator<DiGraphEdge<JsName, RefType>> toIter = refersTo.iterator();
          while(toIter.hasNext()){
            sb.append(nameLink(toIter.next().getDestination().getValue().name));
            if(toIter.hasNext()) {
              sb.append(", ");
            }
          }
        }
        List<DiGraphEdge<JsName, RefType>> referencedBy = referenceGraph.getInEdges(node);
        if(referencedBy.size() > 0) {
          sb.append("<li>REFERENCED BY: ");
          Iterator<DiGraphEdge<JsName, RefType>> fromIter = refersTo.iterator();
          while(fromIter.hasNext()){
            sb.append(nameLink(fromIter.next().getDestination().getValue().name));
            if(fromIter.hasNext()) {
              sb.append(", ");
            }
          }
        }
      }
      sb.append("</li>");
      sb.append("</ul></li>");
    }
    sb.append("</ul>");
    sb.append("</body></html>");
    return sb.toString();
  }
  private String nameAnchor(String name) {
    return "<a name=\"" + name + "\">" + name + "</a>";
  }
  private String nameLink(String name) {
    return "<a href=\"#" + name + "\">" + name + "</a>";
  }
  private boolean isExternallyReferenceable(Scope scope, String name) {
    if(compiler.getCodingConvention().isExported(name)) {
      return true;
    }
    if(scope.isLocal()) {
      return false;
    }
    for (String s : globalNames) {
      if(name.startsWith(s)) {
        return true;
      }
    }
    return false;
  }
  private boolean valueConsumedByParent(Node n, Node parent) {
    if(NodeUtil.isAssignmentOp(parent)) {
      return parent.getLastChild() == n;
    }
    switch (parent.getType()){
      case Token.NAME:
      case Token.RETURN:
      return true;
      case Token.AND:
      case Token.OR:
      case Token.HOOK:
      return parent.getFirstChild() == n;
      case Token.FOR:
      return parent.getFirstChild().getNext() == n;
      case Token.IF:
      case Token.WHILE:
      return parent.getFirstChild() == n;
      case Token.DO:
      return parent.getLastChild() == n;
      default:
      return false;
    }
  }
  private int countOf(TriState isClass, TriState referenced) {
    int count = 0;
    for (JsName name : allNames.values()) {
      boolean nodeIsClass = name.prototypeNames.size() > 0;
      boolean classMatch = isClass == TriState.BOTH || (nodeIsClass && isClass == TriState.TRUE) || (!nodeIsClass && isClass == TriState.FALSE);
      boolean referenceMatch = referenced == TriState.BOTH || (name.referenced && referenced == TriState.TRUE) || (!name.referenced && referenced == TriState.FALSE);
      if(classMatch && referenceMatch && !name.externallyDefined) {
        count++;
      }
    }
    return count;
  }
  private void appendListItem(StringBuilder sb, String text) {
    sb.append("<li>" + text + "</li>\n");
  }
  private void calculateReferences() {
    JsName window = getName(WINDOW, true);
    window.referenced = true;
    JsName function = getName(FUNCTION, true);
    function.referenced = true;
    FixedPointGraphTraversal.newTraversal(new ReferencePropagationCallback()).computeFixedPoint(referenceGraph);
  }
  private void createName(String name) {
    JsName jsn = allNames.get(name);
    if(jsn == null) {
      jsn = new JsName();
      jsn.name = name;
      allNames.put(name, jsn);
    }
  }
  @Override() public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, externs, new ProcessExternals());
    NodeTraversal.traverse(compiler, root, new FindDependencyScopes());
    NodeTraversal.traverse(compiler, root, new HoistVariableAndFunctionDeclarations());
    NodeTraversal.traverse(compiler, root, new FindDeclarationsAndSetters());
    NodeTraversal.traverse(compiler, root, new FindReferences());
    referenceParentNames();
    referenceAliases();
    calculateReferences();
    if(removeUnreferenced) {
      removeUnreferenced();
    }
  }
  private void recordAlias(String fromName, String toName) {
    recordReference(fromName, toName, RefType.REGULAR);
    AliasSet toNameAliasSet = aliases.get(toName);
    AliasSet fromNameAliasSet = aliases.get(fromName);
    AliasSet resultSet = null;
    if(toNameAliasSet == null && fromNameAliasSet == null) {
      resultSet = new AliasSet(toName, fromName);
    }
    else 
      if(toNameAliasSet != null && fromNameAliasSet != null) {
        resultSet = toNameAliasSet;
        Set<String> var_296 = fromNameAliasSet.names;
        resultSet.names.addAll(var_296);
        for (String name : fromNameAliasSet.names) {
          aliases.put(name, resultSet);
        }
      }
      else 
        if(toNameAliasSet != null) {
          resultSet = toNameAliasSet;
          resultSet.names.add(fromName);
        }
        else {
          resultSet = fromNameAliasSet;
          resultSet.names.add(toName);
        }
    aliases.put(fromName, resultSet);
    aliases.put(toName, resultSet);
  }
  private void recordReference(String fromName, String toName, RefType depType) {
    if(fromName.equals(toName)) {
      return ;
    }
    JsName from = getName(fromName, true);
    JsName to = getName(toName, true);
    referenceGraph.createNode(from);
    referenceGraph.createNode(to);
    if(!referenceGraph.isConnectedInDirection(from, depType, to)) {
      referenceGraph.connect(from, depType, to);
    }
  }
  private void referenceAliases() {
    for (Map.Entry<String, AliasSet> entry : aliases.entrySet()) {
      JsName name = getName(entry.getKey(), false);
      if(name.hasWrittenDescendants || name.hasInstanceOfReference) {
        for (String alias : entry.getValue().names) {
          recordReference(alias, entry.getKey(), RefType.REGULAR);
        }
      }
    }
  }
  private void referenceParentNames() {
    Set<JsName> allNamesCopy = Sets.newHashSet(allNames.values());
    for (JsName name : allNamesCopy) {
      String curName = name.name;
      JsName curJsName = name;
      while(curName.indexOf('.') != -1){
        String parentName = curName.substring(0, curName.lastIndexOf('.'));
        if(!globalNames.contains(parentName)) {
          JsName parentJsName = getName(parentName, true);
          recordReference(curJsName.name, parentJsName.name, RefType.REGULAR);
          recordReference(parentJsName.name, curJsName.name, RefType.REGULAR);
          curJsName = parentJsName;
        }
        curName = parentName;
      }
    }
  }
  void removeUnreferenced() {
    RemoveListener listener = new RemoveListener();
    changeProxy.registerListener(listener);
    for (RefNode refNode : refNodes) {
      JsName name = refNode.name();
      if(!name.referenced && !name.externallyDefined) {
        refNode.remove();
      }
    }
    changeProxy.unregisterListener(listener);
  }
  private void replaceTopLevelExpressionWithRhs(Node parent, Node n) {
    switch (parent.getType()){
      case Token.BLOCK:
      case Token.SCRIPT:
      case Token.FOR:
      case Token.LABEL:
      break ;
      default:
      throw new IllegalArgumentException("Unsupported parent node type in replaceWithRhs " + Token.name(parent.getType()));
    }
    switch (n.getType()){
      case Token.EXPR_RESULT:
      case Token.FUNCTION:
      case Token.VAR:
      break ;
      case Token.ASSIGN:
      Preconditions.checkArgument(parent.isFor(), "Unsupported assignment in replaceWithRhs. parent: %s", Token.name(parent.getType()));
      break ;
      default:
      throw new IllegalArgumentException("Unsupported node type in replaceWithRhs " + Token.name(n.getType()));
    }
    List<Node> replacements = Lists.newArrayList();
    for (Node rhs : getRhsSubexpressions(n)) {
      replacements.addAll(getSideEffectNodes(rhs));
    }
    if(parent.isFor()) {
      if(replacements.isEmpty()) {
        replacements.add(IR.empty());
      }
      else {
        Node expr = collapseReplacements(replacements);
        replacements.clear();
        replacements.add(expr);
      }
    }
    changeProxy.replaceWith(parent, n, replacements);
  }
  private void replaceWithRhs(Node parent, Node n) {
    if(valueConsumedByParent(n, parent)) {
      List<Node> replacements = getRhsSubexpressions(n);
      List<Node> newReplacements = Lists.newArrayList();
      for(int i = 0; i < replacements.size() - 1; i++) {
        newReplacements.addAll(getSideEffectNodes(replacements.get(i)));
      }
      Node valueExpr = replacements.get(replacements.size() - 1);
      valueExpr.detachFromParent();
      newReplacements.add(valueExpr);
      changeProxy.replaceWith(parent, n, collapseReplacements(newReplacements));
    }
    else 
      if(n.isAssign() && !parent.isFor()) {
        Node replacement = n.getLastChild();
        replacement.detachFromParent();
        changeProxy.replaceWith(parent, n, replacement);
      }
      else {
        replaceTopLevelExpressionWithRhs(parent, n);
      }
  }
  
  private static class AliasSet  {
    Set<String> names = Sets.newHashSet();
    AliasSet(String name1, String name2) {
      super();
      names.add(name1);
      names.add(name2);
    }
  }
  
  private class ClassDefiningFunctionNode extends SpecialReferenceNode  {
    ClassDefiningFunctionNode(JsName name, Node node) {
      super(name, node);
      Preconditions.checkState(node.isCall());
    }
    @Override() public void remove() {
      Preconditions.checkState(node.isCall());
      Node parent = getParent();
      if(parent.isExprResult()) {
        changeProxy.removeChild(getGramps(), parent);
      }
      else {
        changeProxy.replaceWith(parent, node, IR.voidNode(IR.number(0)));
      }
    }
  }
  
  private class FindDeclarationsAndSetters extends AbstractPostOrderCallback  {
    private void recordPrototypeSet(String className, String prototypeProperty, Node node) {
      JsName name = getName(className, true);
      name.prototypeNames.add(prototypeProperty);
      refNodes.add(new PrototypeSetNode(name, node));
      recordWriteOnProperties(className);
    }
    private void recordSet(String name, Node node) {
      JsName jsn = getName(name, true);
      JsNameRefNode nameRefNode = new JsNameRefNode(jsn, node);
      refNodes.add(nameRefNode);
      if(node.isGetElem()) {
        recordWriteOnProperties(name);
      }
      else 
        if(name.indexOf('.') != -1) {
          recordWriteOnProperties(name.substring(0, name.lastIndexOf('.')));
        }
    }
    private void recordWriteOnProperties(String parentName) {
      do {
        JsName parent = getName(parentName, true);
        if(parent.hasWrittenDescendants) {
          return ;
        }
        else {
          parent.hasWrittenDescendants = true;
        }
        if(parentName.indexOf('.') == -1) {
          return ;
        }
        parentName = parentName.substring(0, parentName.lastIndexOf('.'));
      }while(true);
    }
    @Override() public void visit(NodeTraversal t, Node n, Node parent) {
      if(t.inGlobalScope()) {
        if(NodeUtil.isVarDeclaration(n)) {
          NameInformation ns = createNameInformation(t, n);
          Preconditions.checkNotNull(ns);
          recordSet(ns.name, n);
        }
        else 
          if(NodeUtil.isFunctionDeclaration(n)) {
            Node nameNode = n.getFirstChild();
            NameInformation ns = createNameInformation(t, nameNode);
            if(ns != null) {
              JsName nameInfo = getName(nameNode.getString(), true);
              recordSet(nameInfo.name, nameNode);
            }
          }
          else 
            if(NodeUtil.isObjectLitKey(n, parent)) {
              NameInformation ns = createNameInformation(t, n);
              if(ns != null) {
                recordSet(ns.name, n);
              }
            }
      }
      if(n.isAssign()) {
        Node nameNode = n.getFirstChild();
        NameInformation ns = createNameInformation(t, nameNode);
        if(ns != null) {
          if(ns.isPrototype) {
            recordPrototypeSet(ns.prototypeClass, ns.prototypeProperty, n);
          }
          else {
            recordSet(ns.name, nameNode);
          }
        }
      }
      else 
        if(n.isCall()) {
          Node nameNode = n.getFirstChild();
          NameInformation ns = createNameInformation(t, nameNode);
          if(ns != null && ns.onlyAffectsClassDef) {
            JsName name = getName(ns.name, true);
            refNodes.add(new ClassDefiningFunctionNode(name, n));
          }
        }
    }
  }
  
  private class FindDependencyScopes extends AbstractPostOrderCallback  {
    private void recordAssignment(NodeTraversal t, Node n, Node recordNode) {
      Node nameNode = n.getFirstChild();
      Node parent = n.getParent();
      NameInformation ns = createNameInformation(t, nameNode);
      if(ns != null) {
        if(parent.isFor() && !NodeUtil.isForIn(parent)) {
          if(parent.getFirstChild().getNext() != n) {
            recordDepScope(recordNode, ns);
          }
          else {
            recordDepScope(nameNode, ns);
          }
        }
        else {
          recordDepScope(recordNode, ns);
        }
      }
    }
    private void recordConsumers(NodeTraversal t, Node n, Node recordNode) {
      Node parent = n.getParent();
      switch (parent.getType()){
        case Token.ASSIGN:
        if(n == parent.getLastChild()) {
          recordAssignment(t, parent, recordNode);
        }
        recordConsumers(t, parent, recordNode);
        break ;
        case Token.NAME:
        NameInformation ns = createNameInformation(t, parent);
        recordDepScope(recordNode, ns);
        break ;
        case Token.OR:
        recordConsumers(t, parent, recordNode);
        break ;
        case Token.AND:
        case Token.COMMA:
        case Token.HOOK:
        if(n != parent.getFirstChild()) {
          recordConsumers(t, parent, recordNode);
        }
        break ;
      }
    }
    private void recordDepScope(Node node, NameInformation name) {
      Preconditions.checkNotNull(name);
      scopes.put(node, name);
    }
    @Override() public void visit(NodeTraversal t, Node n, Node parent) {
      if(!t.inGlobalScope()) {
        return ;
      }
      if(n.isAssign()) {
        recordAssignment(t, n, n);
        if(!NodeUtil.isImmutableResult(n.getLastChild())) {
          recordConsumers(t, n, n);
        }
      }
      else 
        if(NodeUtil.isVarDeclaration(n)) {
          NameInformation ns = createNameInformation(t, n);
          recordDepScope(n, ns);
        }
        else 
          if(NodeUtil.isFunctionDeclaration(n)) {
            NameInformation ns = createNameInformation(t, n.getFirstChild());
            recordDepScope(n, ns);
          }
          else 
            if(NodeUtil.isExprCall(n)) {
              Node callNode = n.getFirstChild();
              Node nameNode = callNode.getFirstChild();
              NameInformation ns = createNameInformation(t, nameNode);
              if(ns != null && ns.onlyAffectsClassDef) {
                recordDepScope(n, ns);
              }
            }
    }
  }
  
  private class FindReferences implements Callback  {
    Set<Node> nodesToKeep;
    FindReferences() {
      super();
      nodesToKeep = Sets.newHashSet();
    }
    private boolean maybeHiddenAlias(String name, Node n) {
      Node parent = n.getParent();
      if(NodeUtil.isVarOrSimpleAssignLhs(n, parent)) {
        Node rhs = (parent.isVar()) ? n.getFirstChild() : parent.getLastChild();
        return (rhs != null && !NodeUtil.evaluatesToLocalValue(rhs, NON_LOCAL_RESULT_PREDICATE));
      }
      return false;
    }
    private boolean maybeRecordAlias(String name, Node parent, NameInformation referring, String referringName) {
      boolean isPrototypePropAssignment = parent.isAssign() && NodeUtil.isPrototypeProperty(parent.getFirstChild());
      if((parent.isName() || parent.isAssign()) && !isPrototypePropAssignment && referring != null && scopes.get(parent).contains(referring)) {
        recordAlias(referringName, name);
        return true;
      }
      return false;
    }
    @Override() public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      if(parent == null) {
        return true;
      }
      if(n.isFor()) {
        if(!NodeUtil.isForIn(n)) {
          Node decl = n.getFirstChild();
          Node pred = decl.getNext();
          Node step = pred.getNext();
          addSimplifiedExpression(decl, n);
          addSimplifiedExpression(pred, n);
          addSimplifiedExpression(step, n);
        }
        else {
          Node decl = n.getFirstChild();
          Node iter = decl.getNext();
          addAllChildren(decl);
          addAllChildren(iter);
        }
      }
      if(parent.isVar() || parent.isExprResult() || parent.isReturn() || parent.isThrow()) {
        addSimplifiedExpression(n, parent);
      }
      if((parent.isIf() || parent.isWhile() || parent.isWith() || parent.isSwitch() || parent.isCase()) && parent.getFirstChild() == n) {
        addAllChildren(n);
      }
      if(parent.isDo() && parent.getLastChild() == n) {
        addAllChildren(n);
      }
      return true;
    }
    private void addAllChildren(Node n) {
      nodesToKeep.add(n);
      for(com.google.javascript.rhino.Node child = n.getFirstChild(); child != null; child = child.getNext()) {
        addAllChildren(child);
      }
    }
    private void addSimplifiedChildren(Node n) {
      NodeTraversal.traverse(compiler, n, new GatherSideEffectSubexpressionsCallback(compiler, new NodeAccumulator()));
    }
    private void addSimplifiedExpression(Node n, Node parent) {
      if(parent.isVar()) {
        Node value = n.getFirstChild();
        if(value != null) {
          addSimplifiedChildren(value);
        }
      }
      else 
        if(n.isAssign() && (parent.isExprResult() || parent.isFor() || parent.isReturn())) {
          for (Node child : n.children()) {
            addSimplifiedChildren(child);
          }
        }
        else 
          if(n.isCall() && parent.isExprResult()) {
            addSimplifiedChildren(n);
          }
          else {
            addAllChildren(n);
          }
    }
    private void maybeRecordReferenceOrAlias(NodeTraversal t, Node n, Node parent, NameInformation nameInfo, NameInformation referring) {
      String referringName = "";
      if(referring != null) {
        referringName = referring.isPrototype ? referring.prototypeClass : referring.name;
      }
      String name = nameInfo.name;
      if(maybeHiddenAlias(name, n)) {
        recordAlias(name, WINDOW);
      }
      if(nameInfo.isExternallyReferenceable) {
        recordReference(WINDOW, name, RefType.REGULAR);
        maybeRecordAlias(name, parent, referring, referringName);
        return ;
      }
      if(NodeUtil.isVarOrSimpleAssignLhs(n, parent)) {
        if(referring != null) {
          recordReference(referringName, name, RefType.REGULAR);
        }
        return ;
      }
      if(nodesToKeep.contains(n)) {
        List<NameInformation> functionScopes = getEnclosingFunctionDependencyScope(t);
        if(!functionScopes.isEmpty()) {
          for (NameInformation functionScope : functionScopes) {
            recordReference(functionScope.name, name, RefType.REGULAR);
          }
        }
        else {
          recordReference(WINDOW, name, RefType.REGULAR);
          if(referring != null) {
            maybeRecordAlias(name, parent, referring, referringName);
          }
        }
      }
      else 
        if(referring != null) {
          if(!maybeRecordAlias(name, parent, referring, referringName)) {
            RefType depType = referring.onlyAffectsClassDef ? RefType.INHERITANCE : RefType.REGULAR;
            recordReference(referringName, name, depType);
          }
        }
        else {
          for (Node ancestor : n.getAncestors()) {
            if(NodeUtil.isAssignmentOp(ancestor) || ancestor.isFunction()) {
              recordReference(WINDOW, name, RefType.REGULAR);
              break ;
            }
          }
        }
    }
    private void recordAliases(List<NameInformation> referers) {
      int size = referers.size();
      for(int i = 0; i < size; i++) {
        for(int j = i + 1; j < size; j++) {
          recordAlias(referers.get(i).name, referers.get(j).name);
          recordAlias(referers.get(j).name, referers.get(i).name);
        }
      }
    }
    @Override() public void visit(NodeTraversal t, Node n, Node parent) {
      if(!(n.isName() || NodeUtil.isGet(n) && !parent.isGetProp())) {
        return ;
      }
      NameInformation nameInfo = createNameInformation(t, n);
      if(nameInfo == null) {
        return ;
      }
      if(nameInfo.onlyAffectsClassDef) {
        if(nameInfo.superclass != null) {
          recordReference(nameInfo.name, nameInfo.superclass, RefType.INHERITANCE);
        }
        String nodeName = n.getQualifiedName();
        if(nodeName != null) {
          recordReference(nameInfo.name, nodeName, RefType.REGULAR);
        }
        return ;
      }
      if(parent.isInstanceOf() && parent.getLastChild() == n && n.isQualifiedName()) {
        JsName checkedClass = getName(nameInfo.name, true);
        refNodes.add(new InstanceOfCheckNode(checkedClass, n));
        checkedClass.hasInstanceOfReference = true;
        return ;
      }
      List<NameInformation> referers = getDependencyScope(n);
      if(referers.isEmpty()) {
        maybeRecordReferenceOrAlias(t, n, parent, nameInfo, null);
      }
      else {
        for (NameInformation referring : referers) {
          maybeRecordReferenceOrAlias(t, n, parent, nameInfo, referring);
        }
        recordAliases(referers);
      }
    }
    
    private class NodeAccumulator implements SideEffectAccumulator  {
      @Override() public boolean classDefiningCallsHaveSideEffects() {
        return false;
      }
      @Override() public void keepSimplifiedHookExpression(Node hook, boolean thenHasSideEffects, boolean elseHasSideEffects) {
        Node condition = hook.getFirstChild();
        Node thenBranch = condition.getNext();
        Node elseBranch = thenBranch.getNext();
        addAllChildren(condition);
        if(thenHasSideEffects) {
          addSimplifiedChildren(thenBranch);
        }
        if(elseHasSideEffects) {
          addSimplifiedChildren(elseBranch);
        }
      }
      @Override() public void keepSimplifiedShortCircuitExpression(Node original) {
        Node condition = original.getFirstChild();
        Node thenBranch = condition.getNext();
        addAllChildren(condition);
        addSimplifiedChildren(thenBranch);
      }
      @Override() public void keepSubTree(Node original) {
        addAllChildren(original);
      }
    }
  }
  
  private class HoistVariableAndFunctionDeclarations extends NodeTraversal.AbstractShallowCallback  {
    @Override() public void visit(NodeTraversal t, Node n, Node parent) {
      if(NodeUtil.isVarDeclaration(n)) {
        NameInformation ns = createNameInformation(t, n);
        Preconditions.checkNotNull(ns, "NameInformation is null");
        createName(ns.name);
      }
      else 
        if(NodeUtil.isFunctionDeclaration(n)) {
          Node nameNode = n.getFirstChild();
          NameInformation ns = createNameInformation(t, nameNode);
          Preconditions.checkNotNull(ns, "NameInformation is null");
          createName(nameNode.getString());
        }
    }
  }
  
  private class InstanceOfCheckNode extends SpecialReferenceNode  {
    InstanceOfCheckNode(JsName name, Node node) {
      super(name, node);
      Preconditions.checkState(node.isQualifiedName());
      Preconditions.checkState(getParent().isInstanceOf());
    }
    @Override() public void remove() {
      changeProxy.replaceWith(getGramps(), getParent(), IR.falseNode());
    }
  }
  
  private static class JsName implements Comparable<JsName>  {
    String name;
    List<String> prototypeNames = Lists.newArrayList();
    boolean externallyDefined = false;
    boolean referenced = false;
    boolean hasWrittenDescendants = false;
    boolean hasInstanceOfReference = false;
    @Override() public String toString() {
      StringBuilder out = new StringBuilder();
      out.append(name);
      if(prototypeNames.size() > 0) {
        out.append(" (CLASS)\n");
        out.append(" - FUNCTIONS: ");
        Iterator<String> pIter = prototypeNames.iterator();
        while(pIter.hasNext()){
          out.append(pIter.next());
          if(pIter.hasNext()) {
            out.append(", ");
          }
        }
      }
      return out.toString();
    }
    @Override() public int compareTo(JsName rhs) {
      return this.name.compareTo(rhs.name);
    }
  }
  
  private class JsNameRefNode implements RefNode  {
    JsName name;
    @SuppressWarnings(value = {"unused", }) Node node;
    Node parent;
    JsNameRefNode(JsName name, Node node) {
      super();
      this.name = name;
      this.node = node;
      this.parent = node.getParent();
    }
    @Override() public JsName name() {
      return name;
    }
    @Override() public void remove() {
      Node containingNode = parent.getParent();
      switch (parent.getType()){
        case Token.VAR:
        Preconditions.checkState(parent.hasOneChild());
        replaceWithRhs(containingNode, parent);
        break ;
        case Token.FUNCTION:
        replaceWithRhs(containingNode, parent);
        break ;
        case Token.ASSIGN:
        if(containingNode.isExprResult()) {
          replaceWithRhs(containingNode.getParent(), containingNode);
        }
        else {
          replaceWithRhs(containingNode, parent);
        }
        break ;
        case Token.OBJECTLIT:
        break ;
      }
    }
  }
  
  private static class NameInformation  {
    String name;
    boolean isExternallyReferenceable = false;
    boolean isPrototype = false;
    String prototypeClass = null;
    String prototypeProperty = null;
    String superclass = null;
    boolean onlyAffectsClassDef = false;
  }
  
  private class ProcessExternals extends AbstractPostOrderCallback  {
    @Override() public void visit(NodeTraversal t, Node n, Node parent) {
      NameInformation ns = null;
      if(NodeUtil.isVarDeclaration(n)) {
        ns = createNameInformation(t, n);
      }
      else 
        if(NodeUtil.isFunctionDeclaration(n)) {
          ns = createNameInformation(t, n.getFirstChild());
        }
      if(ns != null) {
        JsName jsName = getName(ns.name, true);
        jsName.externallyDefined = true;
        externalNames.add(ns.name);
      }
    }
  }
  
  private class PrototypeSetNode extends JsNameRefNode  {
    PrototypeSetNode(JsName name, Node parent) {
      super(name, parent.getFirstChild());
      Preconditions.checkState(parent.isAssign());
    }
    @Override() public void remove() {
      Node gramps = parent.getParent();
      if(gramps.isExprResult()) {
        changeProxy.removeChild(gramps.getParent(), gramps);
      }
      else {
        changeProxy.replaceWith(gramps, parent, parent.getLastChild().detachFromParent());
      }
    }
  }
  
  interface RefNode  {
    JsName name();
    void remove();
  }
  private static enum RefType {
    REGULAR(),

    INHERITANCE(),

  ;
  private RefType() {
  }
  }
  
  private static class ReferencePropagationCallback implements EdgeCallback<JsName, RefType>  {
    @Override() public boolean traverseEdge(JsName from, RefType callSite, JsName to) {
      if(from.referenced && !to.referenced) {
        to.referenced = true;
        return true;
      }
      else {
        return false;
      }
    }
  }
  
  private class RemoveListener implements AstChangeProxy.ChangeListener  {
    @Override() public void nodeRemoved(Node n) {
      compiler.reportCodeChange();
    }
  }
  
  abstract private class SpecialReferenceNode implements RefNode  {
    JsName name;
    Node node;
    SpecialReferenceNode(JsName name, Node node) {
      super();
      this.name = name;
      this.node = node;
    }
    @Override() public JsName name() {
      return name;
    }
    Node getGramps() {
      return node.getParent() == null ? null : node.getParent().getParent();
    }
    Node getParent() {
      return node.getParent();
    }
  }
  private enum TriState {
    TRUE(),

    FALSE(),

    BOTH(),

  ;
  private TriState() {
  }
  }
}