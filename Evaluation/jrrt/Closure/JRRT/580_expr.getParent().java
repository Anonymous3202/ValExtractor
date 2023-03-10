package com.google.javascript.jscomp;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.DefinitionsRemover.Definition;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import java.util.Collection;
import java.util.List;

class DevirtualizePrototypeMethods implements OptimizeCalls.CallGraphCompilerPass, SpecializationAwareCompilerPass  {
  final private AbstractCompiler compiler;
  private SpecializeModule.SpecializationState specializationState;
  DevirtualizePrototypeMethods(AbstractCompiler compiler) {
    super();
    this.compiler = compiler;
  }
  private String getMethodName(Node node) {
    if(node.isGetProp()) {
      return node.getLastChild().getString();
    }
    else 
      if(node.isStringKey()) {
        return node.getString();
      }
      else {
        throw new IllegalStateException("unexpected");
      }
  }
  private String getRewrittenMethodName(String originalMethodName) {
    return "JSCompiler_StaticMethods_" + originalMethodName;
  }
  private static boolean isCall(UseSite site) {
    Node node = site.node;
    Node parent = node.getParent();
    return (parent.getFirstChild() == node) && parent.isCall();
  }
  private boolean isEligibleDefinition(SimpleDefinitionFinder defFinder, DefinitionSite definitionSite) {
    Definition definition = definitionSite.definition;
    JSModule definitionModule = definitionSite.module;
    Node rValue = definition.getRValue();
    if(rValue == null || !rValue.isFunction() || NodeUtil.isVarArgsFunction(rValue)) {
      return false;
    }
    Node lValue = definition.getLValue();
    if((lValue == null) || !lValue.isGetProp()) {
      return false;
    }
    CodingConvention codingConvention = compiler.getCodingConvention();
    if(codingConvention.isExported(lValue.getLastChild().getString())) {
      return false;
    }
    Collection<UseSite> useSites = defFinder.getUseSites(definition);
    if(useSites.isEmpty()) {
      return false;
    }
    JSModuleGraph moduleGraph = compiler.getModuleGraph();
    for (UseSite site : useSites) {
      if(!isCall(site)) {
        return false;
      }
      Node nameNode = site.node;
      if(specializationState != null && !specializationState.canFixupSpecializedFunctionContainingNode(nameNode)) {
        return false;
      }
      Collection<Definition> singleSiteDefinitions = defFinder.getDefinitionsReferencedAt(nameNode);
      if(singleSiteDefinitions.size() > 1) {
        return false;
      }
      Preconditions.checkState(!singleSiteDefinitions.isEmpty());
      Preconditions.checkState(singleSiteDefinitions.contains(definition));
      JSModule callModule = site.module;
      if((definitionModule != callModule) && ((callModule == null) || !moduleGraph.dependsOn(callModule, definitionModule))) {
        return false;
      }
    }
    return true;
  }
  private static boolean isPrototypeMethodDefinition(Node node) {
    Node parent = node.getParent();
    if(parent == null) {
      return false;
    }
    Node gramp = parent.getParent();
    if(gramp == null) {
      return false;
    }
    if(node.isGetProp()) {
      if(parent.getFirstChild() != node) {
        return false;
      }
      if(!NodeUtil.isExprAssign(gramp)) {
        return false;
      }
      Node functionNode = parent.getLastChild();
      if((functionNode == null) || !functionNode.isFunction()) {
        return false;
      }
      Node nameNode = node.getFirstChild();
      return nameNode.isGetProp() && nameNode.getLastChild().getString().equals("prototype");
    }
    else 
      if(node.isStringKey()) {
        Preconditions.checkState(parent.isObjectLit());
        if(!gramp.isAssign()) {
          return false;
        }
        if(gramp.getLastChild() != parent) {
          return false;
        }
        Node greatGramp = gramp.getParent();
        if(greatGramp == null || !greatGramp.isExprResult()) {
          return false;
        }
        Node functionNode = node.getFirstChild();
        if((functionNode == null) || !functionNode.isFunction()) {
          return false;
        }
        Node target = gramp.getFirstChild();
        return target.isGetProp() && target.getLastChild().getString().equals("prototype");
      }
      else {
        return false;
      }
  }
  @Override() public void enableSpecialization(SpecializeModule.SpecializationState state) {
    this.specializationState = state;
  }
  private void fixFunctionType(Node functionNode) {
    FunctionType type = JSType.toMaybeFunctionType(functionNode.getJSType());
    if(type != null) {
      JSTypeRegistry typeRegistry = compiler.getTypeRegistry();
      List<JSType> parameterTypes = Lists.newArrayList();
      parameterTypes.add(type.getTypeOfThis());
      for (Node param : type.getParameters()) {
        parameterTypes.add(param.getJSType());
      }
      ObjectType thisType = typeRegistry.getNativeObjectType(JSTypeNative.UNKNOWN_TYPE);
      JSType returnType = type.getReturnType();
      JSType newType = typeRegistry.createFunctionType(thisType, returnType, parameterTypes);
      functionNode.setJSType(newType);
    }
  }
  @Override() public void process(Node externs, Node root) {
    SimpleDefinitionFinder defFinder = new SimpleDefinitionFinder(compiler);
    defFinder.process(externs, root);
    process(externs, root, defFinder);
  }
  @Override() public void process(Node externs, Node root, SimpleDefinitionFinder definitions) {
    for (DefinitionSite defSite : definitions.getDefinitionSites()) {
      rewriteDefinitionIfEligible(defSite, definitions);
    }
  }
  private void replaceReferencesToThis(Node node, String name) {
    if(node.isFunction()) {
      return ;
    }
    for (Node child : node.children()) {
      if(child.isThis()) {
        Node newName = IR.name(name);
        newName.setJSType(child.getJSType());
        node.replaceChild(child, newName);
      }
      else {
        replaceReferencesToThis(child, name);
      }
    }
  }
  private void rewriteCallSites(SimpleDefinitionFinder defFinder, Definition definition, String newMethodName) {
    Collection<UseSite> useSites = defFinder.getUseSites(definition);
    for (UseSite site : useSites) {
      Node node = site.node;
      Node parent = node.getParent();
      Node objectNode = node.getFirstChild();
      node.removeChild(objectNode);
      parent.replaceChild(node, objectNode);
      parent.addChildToFront(IR.name(newMethodName).srcref(node));
      Preconditions.checkState(parent.isCall());
      parent.putBooleanProp(Node.FREE_CALL, true);
      compiler.reportCodeChange();
      if(specializationState != null) {
        specializationState.reportSpecializedFunctionContainingNode(parent);
      }
    }
  }
  private void rewriteDefinition(Node node, String newMethodName) {
    boolean isObjLitDefKey = node.isStringKey();
    Node parent = node.getParent();
    Node refNode = isObjLitDefKey ? node : parent.getFirstChild();
    Node newNameNode = IR.name(newMethodName).copyInformationFrom(refNode);
    Node newVarNode = IR.var(newNameNode).copyInformationFrom(refNode);
    Node functionNode;
    if(!isObjLitDefKey) {
      Preconditions.checkState(parent.isAssign());
      functionNode = parent.getLastChild();
      Node expr = parent.getParent();
      Node var_580 = expr.getParent();
      Node block = var_580;
      parent.removeChild(functionNode);
      newNameNode.addChildToFront(functionNode);
      block.replaceChild(expr, newVarNode);
      if(specializationState != null) {
        specializationState.reportRemovedFunction(functionNode, block);
      }
    }
    else {
      Preconditions.checkState(parent.isObjectLit());
      functionNode = node.getFirstChild();
      Node assign = parent.getParent();
      Node expr = assign.getParent();
      Node block = expr.getParent();
      node.removeChild(functionNode);
      parent.removeChild(node);
      newNameNode.addChildToFront(functionNode);
      block.addChildAfter(newVarNode, expr);
      if(specializationState != null) {
        specializationState.reportRemovedFunction(functionNode, block);
      }
    }
    String self = newMethodName + "$self";
    Node argList = functionNode.getFirstChild().getNext();
    argList.addChildToFront(IR.name(self).copyInformationFrom(functionNode));
    Node body = functionNode.getLastChild();
    replaceReferencesToThis(body, self);
    fixFunctionType(functionNode);
    compiler.reportCodeChange();
  }
  private void rewriteDefinitionIfEligible(DefinitionSite defSite, SimpleDefinitionFinder defFinder) {
    if(defSite.inExterns || !defSite.inGlobalScope || !isEligibleDefinition(defFinder, defSite)) {
      return ;
    }
    Node node = defSite.node;
    if(!isPrototypeMethodDefinition(node)) {
      return ;
    }
    for(com.google.javascript.rhino.Node ancestor = node.getParent(); ancestor != null; ancestor = ancestor.getParent()) {
      if(NodeUtil.isControlStructure(ancestor)) {
        return ;
      }
    }
    String newMethodName = getRewrittenMethodName(getMethodName(node));
    rewriteDefinition(node, newMethodName);
    rewriteCallSites(defFinder, defSite.definition, newMethodName);
  }
}