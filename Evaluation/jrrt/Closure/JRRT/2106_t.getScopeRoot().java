package com.google.javascript.jscomp;
import com.google.common.base.Predicate;
import com.google.javascript.jscomp.ControlFlowGraph.Branch;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.graph.DiGraph.DiGraphEdge;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.TernaryValue;

class CheckMissingReturn implements ScopedCallback  {
  final static DiagnosticType MISSING_RETURN_STATEMENT = DiagnosticType.warning("JSC_MISSING_RETURN_STATEMENT", "Missing return statement. Function expected to return {0}.");
  final private AbstractCompiler compiler;
  final private CheckLevel level;
  final private static Predicate<Node> IS_RETURN = new Predicate<Node>() {
      @Override() public boolean apply(Node input) {
        return input != null && input.isReturn();
      }
  };
  final private static Predicate<DiGraphEdge<Node, ControlFlowGraph.Branch>> GOES_THROUGH_TRUE_CONDITION_PREDICATE = new Predicate<DiGraphEdge<Node, ControlFlowGraph.Branch>>() {
      @Override() public boolean apply(DiGraphEdge<Node, ControlFlowGraph.Branch> input) {
        Branch branch = input.getValue();
        if(branch == Branch.ON_EX) {
          return false;
        }
        else 
          if(branch.isConditional()) {
            Node condition = NodeUtil.getConditionExpression(input.getSource().getValue());
            if(condition != null) {
              TernaryValue val = NodeUtil.getImpureBooleanValue(condition);
              if(val != TernaryValue.UNKNOWN) {
                return val.toBoolean(true) == (Branch.ON_TRUE == branch);
              }
            }
          }
        return true;
      }
  };
  CheckMissingReturn(AbstractCompiler compiler, CheckLevel level) {
    super();
    this.compiler = compiler;
    this.level = level;
  }
  private JSType explicitReturnExpected(Node scope) {
    FunctionType scopeType = JSType.toMaybeFunctionType(scope.getJSType());
    if(scopeType == null) {
      return null;
    }
    if(isEmptyFunction(scope)) {
      return null;
    }
    JSType returnType = scopeType.getReturnType();
    if(returnType == null) {
      return null;
    }
    if(!isVoidOrUnknown(returnType)) {
      return returnType;
    }
    return null;
  }
  private static boolean fastAllPathsReturnCheck(ControlFlowGraph<Node> cfg) {
    for (DiGraphEdge<Node, Branch> s : cfg.getImplicitReturn().getInEdges()) {
      if(!s.getSource().getValue().isReturn()) {
        return false;
      }
    }
    return true;
  }
  private static boolean isEmptyFunction(Node function) {
    return function.getChildCount() == 3 && !function.getFirstChild().getNext().getNext().hasChildren();
  }
  private boolean isVoidOrUnknown(JSType returnType) {
    final JSType voidType = compiler.getTypeRegistry().getNativeType(JSTypeNative.VOID_TYPE);
    return voidType.isSubtype(returnType);
  }
  @Override() public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n, Node parent) {
    return true;
  }
  @Override() public void enterScope(NodeTraversal t) {
    Node var_2106 = t.getScopeRoot();
    JSType returnType = explicitReturnExpected(var_2106);
    if(returnType == null) {
      return ;
    }
    if(fastAllPathsReturnCheck(t.getControlFlowGraph())) {
      return ;
    }
    CheckPathsBetweenNodes<Node, ControlFlowGraph.Branch> test = new CheckPathsBetweenNodes<Node, ControlFlowGraph.Branch>(t.getControlFlowGraph(), t.getControlFlowGraph().getEntry(), t.getControlFlowGraph().getImplicitReturn(), IS_RETURN, GOES_THROUGH_TRUE_CONDITION_PREDICATE);
    if(!test.allPathsSatisfyPredicate()) {
      compiler.report(t.makeError(t.getScopeRoot(), level, MISSING_RETURN_STATEMENT, returnType.toString()));
    }
  }
  @Override() public void exitScope(NodeTraversal t) {
  }
  @Override() public void visit(NodeTraversal t, Node n, Node parent) {
  }
}