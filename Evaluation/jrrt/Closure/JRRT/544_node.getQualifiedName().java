package com.google.javascript.jscomp;
import com.google.common.base.Preconditions;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.DefinitionsRemover.Definition;
import com.google.javascript.jscomp.DefinitionsRemover.ExternalNameOnlyDefinition;
import com.google.javascript.jscomp.DefinitionsRemover.UnknownDefinition;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import java.util.Collection;
import java.util.List;
import java.util.Map;

class SimpleDefinitionFinder implements CompilerPass, DefinitionProvider  {
  final private AbstractCompiler compiler;
  final private Map<Node, DefinitionSite> definitionSiteMap;
  final private Multimap<String, Definition> nameDefinitionMultimap;
  final private Multimap<String, UseSite> nameUseSiteMultimap;
  public SimpleDefinitionFinder(AbstractCompiler compiler) {
    super();
    this.compiler = compiler;
    this.definitionSiteMap = Maps.newLinkedHashMap();
    this.nameDefinitionMultimap = LinkedHashMultimap.create();
    this.nameUseSiteMultimap = LinkedHashMultimap.create();
  }
  @Override() public Collection<Definition> getDefinitionsReferencedAt(Node useSite) {
    if(definitionSiteMap.containsKey(useSite)) {
      return null;
    }
    if(useSite.isGetProp()) {
      String propName = useSite.getLastChild().getString();
      if(propName.equals("apply") || propName.equals("call")) {
        useSite = useSite.getFirstChild();
      }
    }
    String name = getSimplifiedName(useSite);
    if(name != null) {
      Collection<Definition> defs = nameDefinitionMultimap.get(name);
      if(!defs.isEmpty()) {
        return defs;
      }
      else {
        return null;
      }
    }
    else {
      return null;
    }
  }
  public Collection<DefinitionSite> getDefinitionSites() {
    return definitionSiteMap.values();
  }
  Collection<UseSite> getUseSites(Definition definition) {
    String name = getSimplifiedName(definition.getLValue());
    return nameUseSiteMultimap.get(name);
  }
  private DefinitionSite getDefinitionAt(Node node) {
    return definitionSiteMap.get(node);
  }
  DefinitionSite getDefinitionForFunction(Node function) {
    Preconditions.checkState(function.isFunction());
    return getDefinitionAt(getNameNodeFromFunctionNode(function));
  }
  static Node getNameNodeFromFunctionNode(Node function) {
    Preconditions.checkState(function.isFunction());
    if(NodeUtil.isFunctionDeclaration(function)) {
      return function.getFirstChild();
    }
    else {
      Node parent = function.getParent();
      if(NodeUtil.isVarDeclaration(parent)) {
        return parent;
      }
      else 
        if(parent.isAssign()) {
          return parent.getFirstChild();
        }
        else 
          if(NodeUtil.isObjectLitKey(parent, parent.getParent())) {
            return parent;
          }
    }
    return null;
  }
  private static String getSimplifiedName(Node node) {
    if(node.isName()) {
      String name = node.getString();
      if(name != null && !name.isEmpty()) {
        return name;
      }
      else {
        return null;
      }
    }
    else 
      if(node.isGetProp()) {
        return "this." + node.getLastChild().getString();
      }
    return null;
  }
  boolean canModifyDefinition(Definition definition) {
    if(isExported(definition)) {
      return false;
    }
    Collection<UseSite> useSites = getUseSites(definition);
    if(useSites.isEmpty()) {
      return false;
    }
    for (UseSite site : useSites) {
      Node nameNode = site.node;
      Collection<Definition> singleSiteDefinitions = getDefinitionsReferencedAt(nameNode);
      if(singleSiteDefinitions.size() > 1) {
        return false;
      }
      Preconditions.checkState(!singleSiteDefinitions.isEmpty());
      Preconditions.checkState(singleSiteDefinitions.contains(definition));
    }
    return true;
  }
  static boolean isCallOrNewSite(UseSite use) {
    Node call = use.node.getParent();
    if(call == null) {
      return false;
    }
    return NodeUtil.isCallOrNew(call) && call.getFirstChild() == use.node;
  }
  private boolean isExported(Definition definition) {
    Node lValue = definition.getLValue();
    if(lValue == null) {
      return true;
    }
    String partialName;
    if(lValue.isGetProp()) {
      partialName = lValue.getLastChild().getString();
    }
    else 
      if(lValue.isName()) {
        partialName = lValue.getString();
      }
      else {
        return true;
      }
    CodingConvention codingConvention = compiler.getCodingConvention();
    if(codingConvention.isExported(partialName)) {
      return true;
    }
    return false;
  }
  static boolean isSimpleFunctionDeclaration(Node fn) {
    Node parent = fn.getParent();
    Node gramps = parent.getParent();
    Node nameNode = SimpleDefinitionFinder.getNameNodeFromFunctionNode(fn);
    if(nameNode != null && nameNode.isName()) {
      String name = nameNode.getString();
      if(name.equals(NodeUtil.JSC_PROPERTY_NAME_FN) || name.equals(ObjectPropertyStringPreprocess.EXTERN_OBJECT_PROPERTY_STRING)) {
        return false;
      }
    }
    if(NodeUtil.isFunctionDeclaration(fn)) {
      return true;
    }
    if(fn.getFirstChild().getString().isEmpty() && (NodeUtil.isExprAssign(gramps) || parent.isName())) {
      return true;
    }
    return false;
  }
  @Override() public void process(Node externs, Node source) {
    NodeTraversal.traverse(compiler, externs, new DefinitionGatheringCallback(true));
    NodeTraversal.traverse(compiler, source, new DefinitionGatheringCallback(false));
    NodeTraversal.traverse(compiler, source, new UseSiteGatheringCallback());
  }
  void removeReferences(Node node) {
    if(DefinitionsRemover.isDefinitionNode(node)) {
      DefinitionSite defSite = definitionSiteMap.get(node);
      if(defSite != null) {
        Definition def = defSite.definition;
        String name = getSimplifiedName(def.getLValue());
        if(name != null) {
          this.definitionSiteMap.remove(node);
          this.nameDefinitionMultimap.remove(name, node);
        }
      }
    }
    else {
      Node useSite = node;
      if(useSite.isGetProp()) {
        String propName = useSite.getLastChild().getString();
        if(propName.equals("apply") || propName.equals("call")) {
          useSite = useSite.getFirstChild();
        }
      }
      String name = getSimplifiedName(useSite);
      if(name != null) {
        this.nameUseSiteMultimap.remove(name, new UseSite(useSite, null, null));
      }
    }
    for (Node child : node.children()) {
      removeReferences(child);
    }
  }
  
  private class DefinitionGatheringCallback extends AbstractPostOrderCallback  {
    private boolean inExterns;
    DefinitionGatheringCallback(boolean inExterns) {
      super();
      this.inExterns = inExterns;
    }
    private boolean jsdocContainsDeclarations(Node node) {
      JSDocInfo info = node.getJSDocInfo();
      return (info != null && info.containsDeclaration());
    }
    @Override() public void visit(NodeTraversal traversal, Node node, Node parent) {
      if(inExterns && node.isName() && parent.isParamList()) {
        return ;
      }
      Definition def = DefinitionsRemover.getDefinition(node, inExterns);
      if(def != null) {
        String name = getSimplifiedName(def.getLValue());
        if(name != null) {
          Node rValue = def.getRValue();
          if((rValue != null) && !NodeUtil.isImmutableValue(rValue) && !rValue.isFunction()) {
            Definition unknownDef = new UnknownDefinition(def.getLValue(), inExterns);
            def = unknownDef;
          }
          if(inExterns) {
            List<Definition> stubsToRemove = Lists.newArrayList();
            String var_544 = node.getQualifiedName();
            String qualifiedName = var_544;
            if(qualifiedName != null) {
              for (Definition prevDef : nameDefinitionMultimap.get(name)) {
                if(prevDef instanceof ExternalNameOnlyDefinition && !jsdocContainsDeclarations(node)) {
                  String prevName = prevDef.getLValue().getQualifiedName();
                  if(qualifiedName.equals(prevName)) {
                    stubsToRemove.add(prevDef);
                  }
                }
              }
              for (Definition prevDef : stubsToRemove) {
                nameDefinitionMultimap.remove(name, prevDef);
              }
            }
          }
          nameDefinitionMultimap.put(name, def);
          definitionSiteMap.put(node, new DefinitionSite(node, def, traversal.getModule(), traversal.inGlobalScope(), inExterns));
        }
      }
      if(inExterns && (parent != null) && parent.isExprResult()) {
        String name = getSimplifiedName(node);
        if(name != null) {
          boolean dropStub = false;
          if(!jsdocContainsDeclarations(node)) {
            String qualifiedName = node.getQualifiedName();
            if(qualifiedName != null) {
              for (Definition prevDef : nameDefinitionMultimap.get(name)) {
                String prevName = prevDef.getLValue().getQualifiedName();
                if(qualifiedName.equals(prevName)) {
                  dropStub = true;
                  break ;
                }
              }
            }
          }
          if(!dropStub) {
            Definition definition = new ExternalNameOnlyDefinition(node);
            nameDefinitionMultimap.put(name, definition);
            definitionSiteMap.put(node, new DefinitionSite(node, definition, traversal.getModule(), traversal.inGlobalScope(), inExterns));
          }
        }
      }
    }
  }
  
  private class UseSiteGatheringCallback extends AbstractPostOrderCallback  {
    @Override() public void visit(NodeTraversal traversal, Node node, Node parent) {
      Collection<Definition> defs = getDefinitionsReferencedAt(node);
      if(defs == null) {
        return ;
      }
      Definition first = defs.iterator().next();
      String name = getSimplifiedName(first.getLValue());
      Preconditions.checkNotNull(name);
      nameUseSiteMultimap.put(name, new UseSite(node, traversal.getScope(), traversal.getModule()));
    }
  }
}