package com.google.javascript.jscomp;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Supplier;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NodeUtil.Visitor;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

class FunctionArgumentInjector  {
  final static String THIS_MARKER = "this";
  private FunctionArgumentInjector() {
    super();
  }
  static LinkedHashMap<String, Node> getFunctionCallParameterMap(Node fnNode, Node callNode, Supplier<String> safeNameIdSupplier) {
    LinkedHashMap<String, Node> argMap = Maps.newLinkedHashMap();
    Node cArg = callNode.getFirstChild().getNext();
    if(cArg != null && NodeUtil.isFunctionObjectCall(callNode)) {
      argMap.put(THIS_MARKER, cArg);
      cArg = cArg.getNext();
    }
    else {
      Preconditions.checkState(!NodeUtil.isFunctionObjectApply(callNode));
      argMap.put(THIS_MARKER, NodeUtil.newUndefinedNode(callNode));
    }
    for (Node fnArg : NodeUtil.getFunctionParameters(fnNode).children()) {
      if(cArg != null) {
        argMap.put(fnArg.getString(), cArg);
        cArg = cArg.getNext();
      }
      else {
        Node srcLocation = callNode;
        argMap.put(fnArg.getString(), NodeUtil.newUndefinedNode(srcLocation));
      }
    }
    int anonArg = 0;
    while(cArg != null){
      String uniquePlaceholder = getUniqueAnonymousParameterName(safeNameIdSupplier);
      argMap.put(uniquePlaceholder, cArg);
      cArg = cArg.getNext();
    }
    return argMap;
  }
  static Node inject(AbstractCompiler compiler, Node node, Node parent, Map<String, Node> replacements) {
    return inject(compiler, node, parent, replacements, true);
  }
  static Node inject(AbstractCompiler compiler, Node node, Node parent, Map<String, Node> replacements, boolean replaceThis) {
    if(node.isName()) {
      Node replacementTemplate = replacements.get(node.getString());
      if(replacementTemplate != null) {
        Preconditions.checkState(!parent.isFunction() || !parent.isVar() || !parent.isCatch());
        Node var_819 = replacementTemplate.cloneTree();
        Node replacement = var_819;
        parent.replaceChild(node, replacement);
        return replacement;
      }
    }
    else 
      if(replaceThis && node.isThis()) {
        Node replacementTemplate = replacements.get(THIS_MARKER);
        Preconditions.checkNotNull(replacementTemplate);
        if(!replacementTemplate.isThis()) {
          Node replacement = replacementTemplate.cloneTree();
          parent.replaceChild(node, replacement);
          if(NodeUtil.mayHaveSideEffects(replacementTemplate, compiler)) {
            replacements.remove(THIS_MARKER);
          }
          return replacement;
        }
      }
      else 
        if(node.isFunction()) {
          replaceThis = false;
        }
    for(com.google.javascript.rhino.Node c = node.getFirstChild(); c != null; c = c.getNext()) {
      c = inject(compiler, c, node, replacements, replaceThis);
    }
    return node;
  }
  static Set<String> findModifiedParameters(Node fnNode) {
    Set<String> names = getFunctionParameterSet(fnNode);
    Set<String> unsafeNames = Sets.newHashSet();
    return findModifiedParameters(fnNode.getLastChild(), null, names, unsafeNames, false);
  }
  private static Set<String> findModifiedParameters(Node n, Node parent, Set<String> names, Set<String> unsafe, boolean inInnerFunction) {
    Preconditions.checkArgument(unsafe != null);
    if(n.isName()) {
      if(names.contains(n.getString())) {
        if(inInnerFunction || canNameValueChange(n, parent)) {
          unsafe.add(n.getString());
        }
      }
    }
    else 
      if(n.isFunction()) {
        inInnerFunction = true;
      }
    for (Node c : n.children()) {
      findModifiedParameters(c, n, names, unsafe, inInnerFunction);
    }
    return unsafe;
  }
  private static Set<String> findParametersReferencedAfterSideEffect(Set<String> parameters, Node root) {
    Set<String> locals = Sets.newHashSet(parameters);
    gatherLocalNames(root, locals);
    ReferencedAfterSideEffect collector = new ReferencedAfterSideEffect(parameters, locals);
    NodeUtil.visitPostOrder(root, collector, collector);
    return collector.getResults();
  }
  private static Set<String> getFunctionParameterSet(Node fnNode) {
    Set<String> set = Sets.newHashSet();
    for (Node n : NodeUtil.getFunctionParameters(fnNode).children()) {
      set.add(n.getString());
    }
    return set;
  }
  private static String getUniqueAnonymousParameterName(Supplier<String> safeNameIdSupplier) {
    return "JSCompiler_inline_anon_param_" + safeNameIdSupplier.get();
  }
  private static boolean canNameValueChange(Node n, Node parent) {
    int type = parent.getType();
    return (type == Token.VAR || type == Token.INC || type == Token.DEC || (NodeUtil.isAssignmentOp(parent) && parent.getFirstChild() == n));
  }
  private static void gatherLocalNames(Node n, Set<String> names) {
    if(n.isFunction()) {
      if(NodeUtil.isFunctionDeclaration(n)) {
        names.add(n.getFirstChild().getString());
      }
      return ;
    }
    else 
      if(n.isName()) {
        switch (n.getParent().getType()){
          case Token.VAR:
          case Token.CATCH:
          names.add(n.getString());
        }
      }
    for(com.google.javascript.rhino.Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      gatherLocalNames(c, names);
    }
  }
  static void maybeAddTempsForCallArguments(Node fnNode, Map<String, Node> argMap, Set<String> namesNeedingTemps, CodingConvention convention) {
    if(argMap.isEmpty()) {
      return ;
    }
    Preconditions.checkArgument(fnNode.isFunction());
    Node block = fnNode.getLastChild();
    Set<String> parameters = argMap.keySet();
    Set<String> namesAfterSideEffects = findParametersReferencedAfterSideEffect(parameters, block);
    for (Map.Entry<String, Node> entry : argMap.entrySet()) {
      String argName = entry.getKey();
      if(namesNeedingTemps.contains(argName)) {
        continue ;
      }
      Node cArg = entry.getValue();
      boolean safe = true;
      int references = NodeUtil.getNameReferenceCount(block, argName);
      if(NodeUtil.mayEffectMutableState(cArg) && references > 0) {
        safe = false;
      }
      else 
        if(NodeUtil.mayHaveSideEffects(cArg)) {
          safe = false;
        }
        else 
          if(NodeUtil.canBeSideEffected(cArg) && namesAfterSideEffects.contains(argName)) {
            safe = false;
          }
          else 
            if(references > 1) {
              switch (cArg.getType()){
                case Token.NAME:
                String name = cArg.getString();
                safe = !(convention.isExported(name));
                break ;
                case Token.THIS:
                safe = true;
                break ;
                case Token.STRING:
                safe = (cArg.getString().length() < 2);
                break ;
                default:
                safe = NodeUtil.isImmutableValue(cArg);
                break ;
              }
            }
      if(!safe) {
        namesNeedingTemps.add(argName);
      }
    }
  }
  
  private static class ReferencedAfterSideEffect implements Visitor, Predicate<Node>  {
    final private Set<String> parameters;
    final private Set<String> locals;
    private boolean sideEffectSeen = false;
    private Set<String> parametersReferenced = Sets.newHashSet();
    private int loopsEntered = 0;
    ReferencedAfterSideEffect(Set<String> parameters, Set<String> locals) {
      super();
      this.parameters = parameters;
      this.locals = locals;
    }
    Set<String> getResults() {
      return parametersReferenced;
    }
    @Override() public boolean apply(Node node) {
      if(NodeUtil.isLoopStructure(node)) {
        loopsEntered++;
      }
      return !(sideEffectSeen && parameters.size() == parametersReferenced.size());
    }
    private boolean hasNonLocalSideEffect(Node n) {
      boolean sideEffect = false;
      int type = n.getType();
      if(NodeUtil.isAssignmentOp(n) || type == Token.INC || type == Token.DEC) {
        Node lhs = n.getFirstChild();
        if(!isLocalName(lhs)) {
          sideEffect = true;
        }
      }
      else 
        if(type == Token.CALL) {
          sideEffect = NodeUtil.functionCallHasSideEffects(n);
        }
        else 
          if(type == Token.NEW) {
            sideEffect = NodeUtil.constructorCallHasSideEffects(n);
          }
          else 
            if(type == Token.DELPROP) {
              sideEffect = true;
            }
      return sideEffect;
    }
    boolean inLoop() {
      return loopsEntered != 0;
    }
    private boolean isLocalName(Node node) {
      if(node.isName()) {
        String name = node.getString();
        return locals.contains(name);
      }
      return false;
    }
    @Override() public void visit(Node n) {
      if(NodeUtil.isLoopStructure(n)) {
        loopsEntered--;
        if(!inLoop() && !sideEffectSeen) {
          parametersReferenced.clear();
        }
      }
      if(!sideEffectSeen) {
        if(hasNonLocalSideEffect(n)) {
          sideEffectSeen = true;
        }
      }
      if(inLoop() || sideEffectSeen) {
        if(n.isName()) {
          String name = n.getString();
          if(parameters.contains(name)) {
            parametersReferenced.add(name);
          }
        }
        else 
          if(n.isThis()) {
            parametersReferenced.add(THIS_MARKER);
          }
      }
    }
  }
}