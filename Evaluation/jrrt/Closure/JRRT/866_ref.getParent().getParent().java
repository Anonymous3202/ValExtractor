package com.google.javascript.jscomp;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.Deque;
import java.util.List;

class OptimizeArgumentsArray implements CompilerPass, ScopedCallback  {
  final private static String ARGUMENTS = "arguments";
  final private static String PARAMETER_PREFIX = "JSCompiler_OptimizeArgumentsArray_p";
  final private String paramPredix;
  private int uniqueId = 0;
  final private AbstractCompiler compiler;
  final private Deque<List<Node>> argumentsAccessStack = Lists.newLinkedList();
  private List<Node> currentArgumentsAccess = null;
  OptimizeArgumentsArray(AbstractCompiler compiler) {
    this(compiler, PARAMETER_PREFIX);
  }
  OptimizeArgumentsArray(AbstractCompiler compiler, String paramPrefix) {
    super();
    this.compiler = Preconditions.checkNotNull(compiler);
    this.paramPredix = Preconditions.checkNotNull(paramPrefix);
  }
  private String getNewName() {
    return paramPredix + uniqueId++;
  }
  @Override() public boolean shouldTraverse(NodeTraversal nodeTraversal, Node node, Node parent) {
    return true;
  }
  private boolean tryReplaceArguments(Scope scope) {
    Node parametersList = scope.getRootNode().getFirstChild().getNext();
    Preconditions.checkState(parametersList.isParamList());
    boolean changed = false;
    int numNamedParameter = parametersList.getChildCount();
    int highestIndex = numNamedParameter - 1;
    for (Node ref : currentArgumentsAccess) {
      Node getElem = ref.getParent();
      if(!getElem.isGetElem()) {
        return false;
      }
      Node index = ref.getNext();
      if(!index.isNumber()) {
        return false;
      }
      Node getElemParent = getElem.getParent();
      if(getElemParent.isCall() && getElemParent.getFirstChild() == getElem) {
        return false;
      }
      int value = (int)index.getDouble();
      if(value > highestIndex) {
        highestIndex = value;
      }
    }
    int numExtraArgs = highestIndex - numNamedParameter + 1;
    String[] argNames = new String[numExtraArgs];
    for(int i = 0; i < numExtraArgs; i++) {
      String name = getNewName();
      argNames[i] = name;
      parametersList.addChildrenToBack(IR.name(name));
      changed = true;
    }
    for (Node ref : currentArgumentsAccess) {
      Node index = ref.getNext();
      if(!index.isNumber()) {
        continue ;
      }
      int value = (int)index.getDouble();
      if(value >= numNamedParameter) {
        Node var_866 = ref.getParent().getParent();
        var_866.replaceChild(ref.getParent(), IR.name(argNames[value - numNamedParameter]));
      }
      else {
        Node name = parametersList.getFirstChild();
        for(int i = 0; i < value; i++) {
          name = name.getNext();
        }
        ref.getParent().getParent().replaceChild(ref.getParent(), IR.name(name.getString()));
      }
      changed = true;
    }
    return changed;
  }
  @Override() public void enterScope(NodeTraversal traversal) {
    Preconditions.checkNotNull(traversal);
    Node function = traversal.getScopeRoot();
    if(!function.isFunction()) {
      return ;
    }
    if(currentArgumentsAccess != null) {
      argumentsAccessStack.push(currentArgumentsAccess);
    }
    currentArgumentsAccess = Lists.newLinkedList();
  }
  @Override() public void exitScope(NodeTraversal traversal) {
    Preconditions.checkNotNull(traversal);
    if(currentArgumentsAccess == null) {
      return ;
    }
    if(tryReplaceArguments(traversal.getScope())) {
      traversal.getCompiler().reportCodeChange();
    }
    if(!argumentsAccessStack.isEmpty()) {
      currentArgumentsAccess = argumentsAccessStack.pop();
    }
    else {
      currentArgumentsAccess = null;
    }
  }
  @Override() public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, Preconditions.checkNotNull(root), this);
  }
  @Override() public void visit(NodeTraversal traversal, Node node, Node parent) {
    Preconditions.checkNotNull(traversal);
    Preconditions.checkNotNull(node);
    if(currentArgumentsAccess == null) {
      return ;
    }
    if(node.isName() && ARGUMENTS.equals(node.getString())) {
      currentArgumentsAccess.add(node);
    }
  }
}