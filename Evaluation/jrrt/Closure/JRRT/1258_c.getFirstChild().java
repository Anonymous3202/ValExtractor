package com.google.javascript.jscomp;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.AbstractCompiler.LifeCycleStage;
import com.google.javascript.jscomp.MakeDeclaredNamesUnique.BoilerplateRenamer;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.Map;
import java.util.Set;

class Normalize implements CompilerPass  {
  final private AbstractCompiler compiler;
  final private boolean assertOnChange;
  final private static boolean CONVERT_WHILE_TO_FOR = true;
  final static boolean MAKE_LOCAL_NAMES_UNIQUE = true;
  final public static DiagnosticType CATCH_BLOCK_VAR_ERROR = DiagnosticType.error("JSC_CATCH_BLOCK_VAR_ERROR", "The use of scope variable {0} is not allowed within a catch block " + "with a catch exception of the same name.");
  Normalize(AbstractCompiler compiler, boolean assertOnChange) {
    super();
    this.compiler = compiler;
    this.assertOnChange = assertOnChange;
  }
  static Node parseAndNormalizeSyntheticCode(AbstractCompiler compiler, String code, String prefix) {
    Node js = compiler.parseSyntheticCode(code);
    NodeTraversal.traverse(compiler, js, new Normalize.NormalizeStatements(compiler, false));
    NodeTraversal.traverse(compiler, js, new MakeDeclaredNamesUnique(new BoilerplateRenamer(compiler.getUniqueNameIdSupplier(), prefix)));
    return js;
  }
  static Node parseAndNormalizeTestCode(AbstractCompiler compiler, String code, String prefix) {
    Node js = compiler.parseTestCode(code);
    NodeTraversal.traverse(compiler, js, new Normalize.NormalizeStatements(compiler, false));
    NodeTraversal.traverse(compiler, js, new MakeDeclaredNamesUnique());
    return js;
  }
  @Override() public void process(Node externs, Node root) {
    new NodeTraversal(compiler, new NormalizeStatements(compiler, assertOnChange)).traverseRoots(externs, root);
    if(MAKE_LOCAL_NAMES_UNIQUE) {
      MakeDeclaredNamesUnique renamer = new MakeDeclaredNamesUnique();
      NodeTraversal t = new NodeTraversal(compiler, renamer);
      t.traverseRoots(externs, root);
    }
    removeDuplicateDeclarations(externs, root);
    new PropagateConstantAnnotationsOverVars(compiler, assertOnChange).process(externs, root);
    FindExposeAnnotations findExposeAnnotations = new FindExposeAnnotations();
    NodeTraversal.traverse(compiler, root, findExposeAnnotations);
    if(!findExposeAnnotations.exposedProperties.isEmpty()) {
      NodeTraversal.traverse(compiler, root, new RewriteExposedProperties(findExposeAnnotations.exposedProperties));
    }
    if(!compiler.getLifeCycleStage().isNormalized()) {
      compiler.setLifeCycleStage(LifeCycleStage.NORMALIZED);
    }
  }
  private void removeDuplicateDeclarations(Node externs, Node root) {
    Callback tickler = new ScopeTicklingCallback();
    ScopeCreator scopeCreator = new SyntacticScopeCreator(compiler, new DuplicateDeclarationHandler());
    NodeTraversal t = new NodeTraversal(compiler, tickler, scopeCreator);
    t.traverseRoots(externs, root);
  }
  private void reportCodeChange(String changeDescription) {
    if(assertOnChange) {
      throw new IllegalStateException("Normalize constraints violated:\n" + changeDescription);
    }
    compiler.reportCodeChange();
  }
  
  final private class DuplicateDeclarationHandler implements SyntacticScopeCreator.RedeclarationHandler  {
    private Set<Var> hasOkDuplicateDeclaration = Sets.newHashSet();
    @Override() public void onRedeclaration(Scope s, String name, Node n, CompilerInput input) {
      Preconditions.checkState(n.isName());
      Node parent = n.getParent();
      Var v = s.getVar(name);
      if(v != null && s.isGlobal()) {
        if(v.isExtern() && !input.isExtern()) {
          if(hasOkDuplicateDeclaration.add(v)) {
            return ;
          }
        }
      }
      if(v != null && v.getParentNode().isCatch()) {
        name = MakeDeclaredNamesUnique.ContextualRenameInverter.getOrginalName(name);
        compiler.report(JSError.make(input.getName(), n, CATCH_BLOCK_VAR_ERROR, name));
      }
      else 
        if(v != null && parent.isFunction()) {
          if(v.getParentNode().isVar()) {
            s.undeclare(v);
            s.declare(name, n, n.getJSType(), v.input);
            replaceVarWithAssignment(v.getNameNode(), v.getParentNode(), v.getParentNode().getParent());
          }
        }
        else 
          if(parent.isVar()) {
            Preconditions.checkState(parent.hasOneChild());
            replaceVarWithAssignment(n, parent, parent.getParent());
          }
    }
    private void replaceVarWithAssignment(Node n, Node parent, Node gramps) {
      if(n.hasChildren()) {
        parent.removeChild(n);
        Node value = n.getFirstChild();
        n.removeChild(value);
        Node replacement = IR.assign(n, value);
        replacement.copyInformationFrom(parent);
        gramps.replaceChild(parent, NodeUtil.newExpr(replacement));
      }
      else {
        if(NodeUtil.isStatementBlock(gramps)) {
          gramps.removeChild(parent);
        }
        else 
          if(gramps.isFor()) {
            parent.removeChild(n);
            gramps.replaceChild(parent, n);
          }
          else {
            Preconditions.checkState(gramps.isLabel());
            throw new IllegalStateException("Unexpected LABEL");
          }
      }
      reportCodeChange("Duplicate VAR declaration");
    }
  }
  
  private static class FindExposeAnnotations extends AbstractPostOrderCallback  {
    final private Set<String> exposedProperties = Sets.newHashSet();
    private boolean isMarkedExpose(Node n) {
      JSDocInfo info = n.getJSDocInfo();
      return info != null && info.isExpose();
    }
    @Override() public void visit(NodeTraversal t, Node n, Node parent) {
      if(NodeUtil.isExprAssign(n)) {
        Node assign = n.getFirstChild();
        Node lhs = assign.getFirstChild();
        if(lhs.isGetProp() && isMarkedExpose(assign)) {
          exposedProperties.add(lhs.getLastChild().getString());
        }
      }
      else 
        if(n.isStringKey() && isMarkedExpose(n)) {
          exposedProperties.add(n.getString());
        }
    }
  }
  
  static class NormalizeStatements implements Callback  {
    final private AbstractCompiler compiler;
    final private boolean assertOnChange;
    NormalizeStatements(AbstractCompiler compiler, boolean assertOnChange) {
      super();
      this.compiler = compiler;
      this.assertOnChange = assertOnChange;
    }
    private Node addToFront(Node parent, Node newChild, Node after) {
      if(after == null) {
        parent.addChildToFront(newChild);
      }
      else {
        parent.addChildAfter(newChild, after);
      }
      return newChild;
    }
    @Override() public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      doStatementNormalizations(t, n, parent);
      return true;
    }
    private void annotateConstantsByConvention(Node n, Node parent) {
      Preconditions.checkState(n.isName() || n.isString() || n.isStringKey() || n.isGetterDef() || n.isSetterDef());
      boolean isObjLitKey = NodeUtil.isObjectLitKey(n, parent);
      boolean isProperty = isObjLitKey || (parent.isGetProp() && parent.getLastChild() == n);
      if(n.isName() || isProperty) {
        boolean isMarkedConstant = n.getBooleanProp(Node.IS_CONSTANT_NAME);
        if(!isMarkedConstant && NodeUtil.isConstantByConvention(compiler.getCodingConvention(), n, parent)) {
          if(assertOnChange) {
            String name = n.getString();
            throw new IllegalStateException("Unexpected const change.\n" + "  name: " + name + "\n" + "  parent:" + n.getParent().toStringTree());
          }
          n.putBooleanProp(Node.IS_CONSTANT_NAME, true);
        }
      }
    }
    private void doStatementNormalizations(NodeTraversal t, Node n, Node parent) {
      if(n.isLabel()) {
        normalizeLabels(n);
      }
      if(NodeUtil.isStatementBlock(n) || n.isLabel()) {
        extractForInitializer(n, null, null);
      }
      if(NodeUtil.isStatementBlock(n)) {
        splitVarDeclarations(n);
      }
      if(n.isFunction()) {
        moveNamedFunctions(n.getLastChild());
      }
    }
    private void extractForInitializer(Node n, Node before, Node beforeParent) {
      for(com.google.javascript.rhino.Node next, c = n.getFirstChild(); c != null; c = next) {
        next = c.getNext();
        Node insertBefore = (before == null) ? c : before;
        Node insertBeforeParent = (before == null) ? n : beforeParent;
        switch (c.getType()){
          case Token.LABEL:
          extractForInitializer(c, insertBefore, insertBeforeParent);
          break ;
          case Token.FOR:
          if(NodeUtil.isForIn(c)) {
            Node first = c.getFirstChild();
            if(first.isVar()) {
              Node newStatement = first;
              Node name = newStatement.getFirstChild().cloneNode();
              first.getParent().replaceChild(first, name);
              insertBeforeParent.addChildBefore(newStatement, insertBefore);
              reportCodeChange("FOR-IN var declaration");
            }
          }
          else {
            Node var_1258 = c.getFirstChild();
            if(!var_1258.isEmpty()) {
              Node init = c.getFirstChild();
              Node empty = IR.empty();
              empty.copyInformationFrom(c);
              c.replaceChild(init, empty);
              Node newStatement;
              if(init.isVar()) {
                newStatement = init;
              }
              else {
                newStatement = NodeUtil.newExpr(init);
              }
              insertBeforeParent.addChildBefore(newStatement, insertBefore);
              reportCodeChange("FOR initializer");
            }
          }
          break ;
        }
      }
    }
    private void moveNamedFunctions(Node functionBody) {
      Preconditions.checkState(functionBody.getParent().isFunction());
      Node previous = null;
      Node current = functionBody.getFirstChild();
      while(current != null && NodeUtil.isFunctionDeclaration(current)){
        previous = current;
        current = current.getNext();
      }
      Node insertAfter = previous;
      while(current != null){
        Node next = current.getNext();
        if(NodeUtil.isFunctionDeclaration(current)) {
          Preconditions.checkNotNull(previous);
          functionBody.removeChildAfter(previous);
          insertAfter = addToFront(functionBody, current, insertAfter);
          reportCodeChange("Move function declaration not at top of function");
        }
        else {
          previous = current;
        }
        current = next;
      }
    }
    private void normalizeFunctionDeclaration(Node n) {
      Preconditions.checkState(n.isFunction());
      if(!NodeUtil.isFunctionExpression(n) && !NodeUtil.isHoistedFunctionDeclaration(n)) {
        rewriteFunctionDeclaration(n);
      }
    }
    private void normalizeLabels(Node n) {
      Preconditions.checkArgument(n.isLabel());
      Node last = n.getLastChild();
      switch (last.getType()){
        case Token.LABEL:
        case Token.BLOCK:
        case Token.FOR:
        case Token.WHILE:
        case Token.DO:
        return ;
        default:
        Node block = IR.block();
        block.copyInformationFrom(last);
        n.replaceChild(last, block);
        block.addChildToFront(last);
        reportCodeChange("LABEL normalization");
        return ;
      }
    }
    private void reportCodeChange(String changeDescription) {
      if(assertOnChange) {
        throw new IllegalStateException("Normalize constraints violated:\n" + changeDescription);
      }
      compiler.reportCodeChange();
    }
    private void rewriteFunctionDeclaration(Node n) {
      Node oldNameNode = n.getFirstChild();
      Node fnNameNode = oldNameNode.cloneNode();
      Node var = IR.var(fnNameNode).srcref(n);
      oldNameNode.setString("");
      Node parent = n.getParent();
      parent.replaceChild(n, var);
      fnNameNode.addChildToFront(n);
      reportCodeChange("Function declaration");
    }
    private void splitVarDeclarations(Node n) {
      for(com.google.javascript.rhino.Node next, c = n.getFirstChild(); c != null; c = next) {
        next = c.getNext();
        if(c.isVar()) {
          if(assertOnChange && !c.hasChildren()) {
            throw new IllegalStateException("Empty VAR node.");
          }
          while(c.getFirstChild() != c.getLastChild()){
            Node name = c.getFirstChild();
            c.removeChild(name);
            Node newVar = IR.var(name).srcref(n);
            n.addChildBefore(newVar, c);
            reportCodeChange("VAR with multiple children");
          }
        }
      }
    }
    @Override() public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()){
        case Token.WHILE:
        if(CONVERT_WHILE_TO_FOR) {
          Node expr = n.getFirstChild();
          n.setType(Token.FOR);
          Node empty = IR.empty();
          empty.copyInformationFrom(n);
          n.addChildBefore(empty, expr);
          n.addChildAfter(empty.cloneNode(), expr);
          reportCodeChange("WHILE node");
        }
        break ;
        case Token.FUNCTION:
        normalizeFunctionDeclaration(n);
        break ;
        case Token.NAME:
        case Token.STRING:
        case Token.STRING_KEY:
        case Token.GETTER_DEF:
        case Token.SETTER_DEF:
        if(!compiler.getLifeCycleStage().isNormalizedObfuscated()) {
          annotateConstantsByConvention(n, parent);
        }
        break ;
        case Token.CAST:
        parent.replaceChild(n, n.removeFirstChild());
        break ;
      }
    }
  }
  
  static class PropagateConstantAnnotationsOverVars extends AbstractPostOrderCallback implements CompilerPass  {
    final private AbstractCompiler compiler;
    final private boolean assertOnChange;
    PropagateConstantAnnotationsOverVars(AbstractCompiler compiler, boolean forbidChanges) {
      super();
      this.compiler = compiler;
      this.assertOnChange = forbidChanges;
    }
    @Override() public void process(Node externs, Node root) {
      new NodeTraversal(compiler, this).traverseRoots(externs, root);
    }
    @Override() public void visit(NodeTraversal t, Node n, Node parent) {
      if(n.isName()) {
        if(n.getString().isEmpty()) {
          return ;
        }
        JSDocInfo info = null;
        Var var = t.getScope().getVar(n.getString());
        if(var != null) {
          info = var.getJSDocInfo();
        }
        boolean shouldBeConstant = (info != null && info.isConstant()) || NodeUtil.isConstantByConvention(compiler.getCodingConvention(), n, parent);
        boolean isMarkedConstant = n.getBooleanProp(Node.IS_CONSTANT_NAME);
        if(shouldBeConstant && !isMarkedConstant) {
          if(assertOnChange) {
            String name = n.getString();
            throw new IllegalStateException("Unexpected const change.\n" + "  name: " + name + "\n" + "  parent:" + n.getParent().toStringTree());
          }
          n.putBooleanProp(Node.IS_CONSTANT_NAME, true);
        }
      }
    }
  }
  
  private class RewriteExposedProperties extends AbstractPostOrderCallback  {
    final private Set<String> exposedProperties;
    RewriteExposedProperties(Set<String> exposedProperties) {
      super();
      this.exposedProperties = exposedProperties;
    }
    @Override() public void visit(NodeTraversal t, Node n, Node parent) {
      if(n.isGetProp()) {
        String propName = n.getLastChild().getString();
        if(exposedProperties.contains(propName)) {
          Node obj = n.removeFirstChild();
          Node prop = n.removeFirstChild();
          n.getParent().replaceChild(n, IR.getelem(obj, prop));
          compiler.reportCodeChange();
        }
      }
      else 
        if(n.isStringKey()) {
          String propName = n.getString();
          if(exposedProperties.contains(propName)) {
            n.setQuotedString();
            compiler.reportCodeChange();
          }
        }
    }
  }
  
  final private class ScopeTicklingCallback implements NodeTraversal.ScopedCallback  {
    @Override() public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
      return true;
    }
    @Override() public void enterScope(NodeTraversal t) {
      t.getScope();
    }
    @Override() public void exitScope(NodeTraversal t) {
    }
    @Override() public void visit(NodeTraversal t, Node n, Node parent) {
    }
  }
  
  static class VerifyConstants extends AbstractPostOrderCallback implements CompilerPass  {
    final private AbstractCompiler compiler;
    final private boolean checkUserDeclarations;
    private Map<String, Boolean> constantMap = Maps.newHashMap();
    VerifyConstants(AbstractCompiler compiler, boolean checkUserDeclarations) {
      super();
      this.compiler = compiler;
      this.checkUserDeclarations = checkUserDeclarations;
    }
    @Override() public void process(Node externs, Node root) {
      Node externsAndJs = root.getParent();
      Preconditions.checkState(externsAndJs != null);
      Preconditions.checkState(externsAndJs.hasChild(externs));
      NodeTraversal.traverseRoots(compiler, Lists.newArrayList(externs, root), this);
    }
    @Override() public void visit(NodeTraversal t, Node n, Node parent) {
      if(n.isName()) {
        String name = n.getString();
        if(n.getString().isEmpty()) {
          return ;
        }
        boolean isConst = n.getBooleanProp(Node.IS_CONSTANT_NAME);
        if(checkUserDeclarations) {
          boolean expectedConst = false;
          CodingConvention convention = compiler.getCodingConvention();
          if(NodeUtil.isConstantName(n) || NodeUtil.isConstantByConvention(convention, n, parent)) {
            expectedConst = true;
          }
          else {
            expectedConst = false;
            JSDocInfo info = null;
            Var var = t.getScope().getVar(n.getString());
            if(var != null) {
              info = var.getJSDocInfo();
            }
            if(info != null && info.isConstant()) {
              expectedConst = true;
            }
            else {
              expectedConst = false;
            }
          }
          if(expectedConst) {
            Preconditions.checkState(expectedConst == isConst, "The name %s is not annotated as constant.", name);
          }
          else {
            Preconditions.checkState(expectedConst == isConst, "The name %s should not be annotated as constant.", name);
          }
        }
        Boolean value = constantMap.get(name);
        if(value == null) {
          constantMap.put(name, isConst);
        }
        else {
          Preconditions.checkState(value.booleanValue() == isConst, "The name %s is not consistently annotated as constant.", name);
        }
      }
    }
  }
}