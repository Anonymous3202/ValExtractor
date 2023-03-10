package com.google.javascript.jscomp;
import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.*;

class ConstCheck extends AbstractPostOrderCallback implements CompilerPass  {
  final static DiagnosticType CONST_REASSIGNED_VALUE_ERROR = DiagnosticType.error("JSC_CONSTANT_REASSIGNED_VALUE_ERROR", "constant {0} assigned a value more than once");
  final private AbstractCompiler compiler;
  final private Set<Scope.Var> initializedConstants;
  public ConstCheck(AbstractCompiler compiler) {
    super();
    this.compiler = compiler;
    this.initializedConstants = new HashSet<Scope.Var>();
  }
  private boolean isConstant(Scope.Var var) {
    return var != null && var.isConst();
  }
  @Override() public void process(Node externs, Node root) {
    Preconditions.checkState(compiler.getLifeCycleStage().isNormalized());
    NodeTraversal.traverse(compiler, root, this);
  }
  void reportError(NodeTraversal t, Node n, String name) {
    compiler.report(t.makeError(n, CONST_REASSIGNED_VALUE_ERROR, name));
  }
  @Override() public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()){
      case Token.NAME:
      if(parent != null && parent.isVar() && n.hasChildren()) {
        String name = n.getString();
        Scope.Var var = t.getScope().getVar(name);
        if(isConstant(var)) {
          if(initializedConstants.contains(var)) {
            reportError(t, n, name);
          }
          else {
            initializedConstants.add(var);
          }
        }
      }
      break ;
      case Token.ASSIGN:
      case Token.ASSIGN_BITOR:
      case Token.ASSIGN_BITXOR:
      case Token.ASSIGN_BITAND:
      case Token.ASSIGN_LSH:
      case Token.ASSIGN_RSH:
      case Token.ASSIGN_URSH:
      case Token.ASSIGN_ADD:
      case Token.ASSIGN_SUB:
      case Token.ASSIGN_MUL:
      case Token.ASSIGN_DIV:
      case Token.ASSIGN_MOD:
      {
        Node lhs = n.getFirstChild();
        boolean var_1126 = lhs.isName();
        if(var_1126) {
          String name = lhs.getString();
          Scope.Var var = t.getScope().getVar(name);
          if(isConstant(var)) {
            if(initializedConstants.contains(var)) {
              reportError(t, n, name);
            }
            else {
              initializedConstants.add(var);
            }
          }
        }
        break ;
      }
      case Token.INC:
      case Token.DEC:
      {
        Node lhs = n.getFirstChild();
        if(lhs.isName()) {
          String name = lhs.getString();
          Scope.Var var = t.getScope().getVar(name);
          if(isConstant(var)) {
            reportError(t, n, name);
          }
        }
        break ;
      }
    }
  }
}