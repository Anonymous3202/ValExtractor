package com.google.javascript.jscomp;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.List;
import java.util.Set;

class CheckRequiresForConstructors implements HotSwapCompilerPass  {
  final private AbstractCompiler compiler;
  final private CodingConvention codingConvention;
  final private CheckLevel level;
  final static DiagnosticType MISSING_REQUIRE_WARNING = DiagnosticType.disabled("JSC_MISSING_REQUIRE_WARNING", "\'\'{0}\'\' used but not goog.require\'\'d");
  CheckRequiresForConstructors(AbstractCompiler compiler, CheckLevel level) {
    super();
    this.compiler = compiler;
    this.codingConvention = compiler.getCodingConvention();
    this.level = level;
  }
  private static String getOutermostClassName(String className) {
    for (String part : className.split("\\.")) {
      if(isClassName(part)) {
        return className.substring(0, className.indexOf(part) + part.length());
      }
    }
    return null;
  }
  private static boolean isClassName(String name) {
    return (name != null && name.length() > 1 && Character.isUpperCase(name.charAt(0)) && !name.equals(name.toUpperCase()));
  }
  @Override() public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    Callback callback = new CheckRequiresForConstructorsCallback();
    new NodeTraversal(compiler, callback).traverseWithScope(scriptRoot, SyntacticScopeCreator.generateUntypedTopScope(compiler));
  }
  @Override() public void process(Node externs, Node root) {
    Callback callback = new CheckRequiresForConstructorsCallback();
    new NodeTraversal(compiler, callback).traverseRoots(externs, root);
  }
  
  private class CheckRequiresForConstructorsCallback implements Callback  {
    final private List<String> constructors = Lists.newArrayList();
    final private List<String> requires = Lists.newArrayList();
    final private List<Node> newNodes = Lists.newArrayList();
    @Override() public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      return parent == null || !parent.isScript() || !t.getInput().isExtern();
    }
    @Override() public void visit(NodeTraversal t, Node n, Node parent) {
      JSDocInfo info;
      switch (n.getType()){
        case Token.ASSIGN:
        info = (JSDocInfo)n.getProp(Node.JSDOC_INFO_PROP);
        if(info != null && info.isConstructor()) {
          Node var_843 = n.getFirstChild();
          String qualifiedName = var_843.getQualifiedName();
          constructors.add(qualifiedName);
        }
        break ;
        case Token.FUNCTION:
        if(NodeUtil.isFunctionExpression(n)) {
          if(parent.isName()) {
            String functionName = parent.getString();
            info = (JSDocInfo)parent.getProp(Node.JSDOC_INFO_PROP);
            if(info != null && info.isConstructor()) {
              constructors.add(functionName);
            }
            else {
              Node gramps = parent.getParent();
              Preconditions.checkState(gramps != null && gramps.isVar());
              info = (JSDocInfo)gramps.getProp(Node.JSDOC_INFO_PROP);
              if(info != null && info.isConstructor()) {
                constructors.add(functionName);
              }
            }
          }
        }
        else {
          info = (JSDocInfo)n.getProp(Node.JSDOC_INFO_PROP);
          if(info != null && info.isConstructor()) {
            String functionName = n.getFirstChild().getString();
            constructors.add(functionName);
          }
        }
        break ;
        case Token.CALL:
        visitCallNode(n, parent);
        break ;
        case Token.SCRIPT:
        visitScriptNode(t);
        break ;
        case Token.NEW:
        visitNewNode(t, n);
      }
    }
    private void visitCallNode(Node n, Node parent) {
      String required = codingConvention.extractClassNameIfRequire(n, parent);
      if(required != null) {
        requires.add(required);
      }
    }
    private void visitNewNode(NodeTraversal t, Node n) {
      Node qNameNode = n.getFirstChild();
      if(!qNameNode.isQualifiedName()) {
        return ;
      }
      Node nameNode = qNameNode;
      for(; nameNode.hasChildren(); nameNode = nameNode.getFirstChild()) {
      }
      if(!nameNode.isName()) {
        return ;
      }
      String name = nameNode.getString();
      Scope.Var var = t.getScope().getVar(name);
      if(var == null || var.isLocal() || var.isExtern()) {
        return ;
      }
      newNodes.add(n);
    }
    private void visitScriptNode(NodeTraversal t) {
      Set<String> classNames = Sets.newHashSet();
      for (Node node : newNodes) {
        String className = node.getFirstChild().getQualifiedName();
        String outermostClassName = getOutermostClassName(className);
        boolean notProvidedByConstructors = (constructors == null || !constructors.contains(className));
        boolean notProvidedByRequires = (requires == null || (!requires.contains(className) && !requires.contains(outermostClassName)));
        if(notProvidedByConstructors && notProvidedByRequires && !classNames.contains(className)) {
          compiler.report(t.makeError(node, level, MISSING_REQUIRE_WARNING, className));
          classNames.add(className);
        }
      }
      this.newNodes.clear();
      this.requires.clear();
      this.constructors.clear();
    }
  }
}