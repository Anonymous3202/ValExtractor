package com.google.javascript.jscomp;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.Set;

class StrictModeCheck extends AbstractPostOrderCallback implements CompilerPass  {
  final static DiagnosticType UNKNOWN_VARIABLE = DiagnosticType.warning("JSC_UNKNOWN_VARIABLE", "unknown variable {0}");
  final static DiagnosticType EVAL_USE = DiagnosticType.error("JSC_EVAL_USE", "\"eval\" cannot be used in Caja");
  final static DiagnosticType EVAL_DECLARATION = DiagnosticType.warning("JSC_EVAL_DECLARATION", "\"eval\" cannot be redeclared in ES5 strict mode");
  final static DiagnosticType EVAL_ASSIGNMENT = DiagnosticType.warning("JSC_EVAL_ASSIGNMENT", "the \"eval\" object cannot be reassigned in ES5 strict mode");
  final static DiagnosticType ARGUMENTS_DECLARATION = DiagnosticType.warning("JSC_ARGUMENTS_DECLARATION", "\"arguments\" cannot be redeclared in ES5 strict mode");
  final static DiagnosticType ARGUMENTS_ASSIGNMENT = DiagnosticType.warning("JSC_ARGUMENTS_ASSIGNMENT", "the \"arguments\" object cannot be reassigned in ES5 strict mode");
  final static DiagnosticType DELETE_VARIABLE = DiagnosticType.warning("JSC_DELETE_VARIABLE", "variables, functions, and arguments cannot be deleted in " + "ES5 strict mode");
  final static DiagnosticType ILLEGAL_NAME = DiagnosticType.error("JSC_ILLEGAL_NAME", "identifiers ending in \'__\' cannot be used in Caja");
  final static DiagnosticType DUPLICATE_OBJECT_KEY = DiagnosticType.warning("JSC_DUPLICATE_OBJECT_KEY", "object literals cannot contain duplicate keys in ES5 strict mode");
  final static DiagnosticType BAD_FUNCTION_DECLARATION = DiagnosticType.error("JSC_BAD_FUNCTION_DECLARATION", "functions can only be declared at top level or immediately within " + "another function in ES5 strict mode");
  final private AbstractCompiler compiler;
  final private boolean noVarCheck;
  final private boolean noCajaChecks;
  StrictModeCheck(AbstractCompiler compiler) {
    this(compiler, false, false);
  }
  StrictModeCheck(AbstractCompiler compiler, boolean noVarCheck, boolean noCajaChecks) {
    super();
    this.compiler = compiler;
    this.noVarCheck = noVarCheck;
    this.noCajaChecks = noCajaChecks;
  }
  private static boolean isDeclaration(Node n) {
    Node var_209 = n.getParent();
    switch (var_209.getType()){
      case Token.VAR:
      case Token.FUNCTION:
      case Token.CATCH:
      return true;
      case Token.PARAM_LIST:
      return n.getParent().getParent().isFunction();
      default:
      return false;
    }
  }
  private void checkAssignment(NodeTraversal t, Node n) {
    if(n.getFirstChild().isName()) {
      if("arguments".equals(n.getFirstChild().getString())) {
        t.report(n, ARGUMENTS_ASSIGNMENT);
      }
      else 
        if("eval".equals(n.getFirstChild().getString())) {
          if(noCajaChecks) {
            t.report(n, EVAL_ASSIGNMENT);
          }
        }
    }
  }
  private void checkDelete(NodeTraversal t, Node n) {
    if(n.getFirstChild().isName()) {
      Var v = t.getScope().getVar(n.getFirstChild().getString());
      if(v != null) {
        t.report(n, DELETE_VARIABLE);
      }
    }
  }
  private void checkFunctionUse(NodeTraversal t, Node n) {
    if(NodeUtil.isFunctionDeclaration(n) && !NodeUtil.isHoistedFunctionDeclaration(n)) {
      t.report(n, BAD_FUNCTION_DECLARATION);
    }
  }
  private void checkLabel(NodeTraversal t, Node n) {
    if(n.getFirstChild().getString().endsWith("__")) {
      if(!noCajaChecks) {
        t.report(n.getFirstChild(), ILLEGAL_NAME);
      }
    }
  }
  private void checkNameUse(NodeTraversal t, Node n) {
    Var v = t.getScope().getVar(n.getString());
    if(v == null) {
      if(!noVarCheck) {
        t.report(n, UNKNOWN_VARIABLE, n.getString());
      }
    }
    if(!noCajaChecks) {
      if("eval".equals(n.getString())) {
        t.report(n, EVAL_USE);
      }
      else 
        if(n.getString().endsWith("__")) {
          t.report(n, ILLEGAL_NAME);
        }
    }
  }
  private void checkObjectLiteral(NodeTraversal t, Node n) {
    Set<String> getters = Sets.newHashSet();
    Set<String> setters = Sets.newHashSet();
    for(com.google.javascript.rhino.Node key = n.getFirstChild(); key != null; key = key.getNext()) {
      if(!noCajaChecks && key.getString().endsWith("__")) {
        t.report(key, ILLEGAL_NAME);
      }
      if(!key.isSetterDef()) {
        if(getters.contains(key.getString())) {
          t.report(key, DUPLICATE_OBJECT_KEY);
        }
        else {
          getters.add(key.getString());
        }
      }
      if(!key.isGetterDef()) {
        if(setters.contains(key.getString())) {
          t.report(key, DUPLICATE_OBJECT_KEY);
        }
        else {
          setters.add(key.getString());
        }
      }
    }
  }
  @Override() public void process(Node externs, Node root) {
    NodeTraversal.traverseRoots(compiler, Lists.newArrayList(externs, root), this);
    NodeTraversal.traverse(compiler, root, new NonExternChecks());
  }
  @Override() public void visit(NodeTraversal t, Node n, Node parent) {
    if(n.isFunction()) {
      checkFunctionUse(t, n);
    }
    else 
      if(n.isName()) {
        if(!isDeclaration(n)) {
          checkNameUse(t, n);
        }
      }
      else 
        if(n.isAssign()) {
          checkAssignment(t, n);
        }
        else 
          if(n.isDelProp()) {
            checkDelete(t, n);
          }
          else 
            if(n.isObjectLit()) {
              checkObjectLiteral(t, n);
            }
            else 
              if(n.isLabel()) {
                checkLabel(t, n);
              }
  }
  
  private class NonExternChecks extends AbstractPostOrderCallback  {
    private void checkDeclaration(NodeTraversal t, Node n) {
      if("eval".equals(n.getString())) {
        t.report(n, EVAL_DECLARATION);
      }
      else 
        if("arguments".equals(n.getString())) {
          t.report(n, ARGUMENTS_DECLARATION);
        }
        else 
          if(n.getString().endsWith("__")) {
            if(!noCajaChecks) {
              t.report(n, ILLEGAL_NAME);
            }
          }
    }
    private void checkProperty(NodeTraversal t, Node n) {
      if(n.getLastChild().getString().endsWith("__")) {
        if(!noCajaChecks) {
          t.report(n.getLastChild(), ILLEGAL_NAME);
        }
      }
    }
    @Override() public void visit(NodeTraversal t, Node n, Node parent) {
      if((n.isName()) && isDeclaration(n)) {
        checkDeclaration(t, n);
      }
      else 
        if(n.isGetProp()) {
          checkProperty(t, n);
        }
    }
  }
}