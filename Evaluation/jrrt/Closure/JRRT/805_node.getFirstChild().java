package com.google.javascript.jscomp;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.DefinitionsRemover.Definition;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.Collection;
import java.util.List;

class OptimizeReturns implements OptimizeCalls.CallGraphCompilerPass, CompilerPass  {
  private AbstractCompiler compiler;
  OptimizeReturns(AbstractCompiler compiler) {
    super();
    this.compiler = compiler;
  }
  private boolean callResultsMaybeUsed(SimpleDefinitionFinder defFinder, DefinitionSite definitionSite) {
    Definition definition = definitionSite.definition;
    Node rValue = definition.getRValue();
    if(rValue == null || !rValue.isFunction()) {
      return true;
    }
    if(!SimpleDefinitionFinder.isSimpleFunctionDeclaration(rValue)) {
      return true;
    }
    if(!defFinder.canModifyDefinition(definition)) {
      return true;
    }
    Collection<UseSite> useSites = defFinder.getUseSites(definition);
    for (UseSite site : useSites) {
      Node useNodeParent = site.node.getParent();
      if(isCall(site)) {
        Node callNode = useNodeParent;
        Preconditions.checkState(callNode.isCall());
        if(NodeUtil.isExpressionResultUsed(callNode)) {
          return true;
        }
      }
      else {
        if(!useNodeParent.isVar()) {
          return true;
        }
      }
    }
    return false;
  }
  private static boolean isCall(UseSite site) {
    Node node = site.node;
    Node parent = node.getParent();
    return (parent.getFirstChild() == node) && parent.isCall();
  }
  @Override() @VisibleForTesting() public void process(Node externs, Node root) {
    SimpleDefinitionFinder defFinder = new SimpleDefinitionFinder(compiler);
    defFinder.process(externs, root);
    process(externs, root, defFinder);
  }
  @Override() public void process(Node externs, Node root, SimpleDefinitionFinder definitions) {
    List<Node> toOptimize = Lists.newArrayList();
    for (DefinitionSite defSite : definitions.getDefinitionSites()) {
      if(!defSite.inExterns && !callResultsMaybeUsed(definitions, defSite)) {
        toOptimize.add(defSite.definition.getRValue());
      }
    }
    for (Node node : toOptimize) {
      rewriteReturns(definitions, node);
    }
  }
  private void rewriteReturns(final SimpleDefinitionFinder defFinder, Node fnNode) {
    Preconditions.checkState(fnNode.isFunction());
    NodeUtil.visitPostOrder(fnNode.getLastChild(), new NodeUtil.Visitor() {
        @Override() public void visit(Node node) {
          if(node.isReturn() && node.hasOneChild()) {
            Node var_805 = node.getFirstChild();
            boolean keepValue = NodeUtil.mayHaveSideEffects(var_805, compiler);
            if(!keepValue) {
              defFinder.removeReferences(node.getFirstChild());
            }
            Node result = node.removeFirstChild();
            if(keepValue) {
              node.getParent().addChildBefore(IR.exprResult(result).srcref(result), node);
            }
            compiler.reportCodeChange();
          }
        }
    }, new NodeUtil.MatchShallowStatement());
  }
}