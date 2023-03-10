package com.google.javascript.jscomp;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.javascript.jscomp.graph.GraphvizGraph;
import com.google.javascript.jscomp.graph.LinkedDirectedGraph;
import com.google.javascript.rhino.Node;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

abstract public class PassConfig  {
  final CompilerOptions options;
  private MemoizedScopeCreator typedScopeCreator;
  private TypedScopeCreator internalScopeCreator;
  Scope topScope = null;
  public PassConfig(CompilerOptions options) {
    super();
    this.options = options;
  }
  GraphvizGraph getPassGraph() {
    LinkedDirectedGraph<String, String> graph = LinkedDirectedGraph.createWithoutAnnotations();
    Iterable<PassFactory> allPasses = Iterables.concat(getChecks(), getOptimizations());
    String lastPass = null;
    String loopStart = null;
    for (PassFactory pass : allPasses) {
      String var_1654 = pass.getName();
      String passName = var_1654;
      int i = 1;
      while(graph.hasNode(passName)){
        passName = pass.getName() + (i++);
      }
      graph.createNode(passName);
      if(loopStart == null && !pass.isOneTimePass()) {
        loopStart = passName;
      }
      else 
        if(loopStart != null && pass.isOneTimePass()) {
          graph.connect(lastPass, "loop", loopStart);
          loopStart = null;
        }
      if(lastPass != null) {
        graph.connect(lastPass, "", passName);
      }
      lastPass = passName;
    }
    return graph;
  }
  final InferJSDocInfo makeInferJsDocInfo(AbstractCompiler compiler) {
    return new InferJSDocInfo(compiler);
  }
  abstract protected List<PassFactory> getChecks();
  abstract protected List<PassFactory> getOptimizations();
  MemoizedScopeCreator getTypedScopeCreator() {
    return typedScopeCreator;
  }
  final PassConfig getBasePassConfig() {
    PassConfig current = this;
    while(current instanceof PassConfigDelegate){
      current = ((PassConfigDelegate)current).delegate;
    }
    return current;
  }
  Scope getTopScope() {
    return topScope;
  }
  abstract protected State getIntermediateState();
  final TypeCheck makeTypeCheck(AbstractCompiler compiler) {
    return new TypeCheck(compiler, compiler.getReverseAbstractInterpreter(), compiler.getTypeRegistry(), topScope, typedScopeCreator, options.reportMissingOverride, options.reportUnknownTypes).reportMissingProperties(options.enables(DiagnosticGroup.forType(TypeCheck.INEXISTENT_PROPERTY)));
  }
  final TypeInferencePass makeTypeInference(AbstractCompiler compiler) {
    return new TypeInferencePass(compiler, compiler.getReverseAbstractInterpreter(), topScope, typedScopeCreator);
  }
  private static int findPassIndexByName(List<PassFactory> factoryList, String name) {
    for(int i = 0; i < factoryList.size(); i++) {
      if(factoryList.get(i).getName().equals(name)) {
        return i;
      }
    }
    throw new IllegalArgumentException("No factory named \'" + name + "\' in the factory list");
  }
  final static void addPassFactoryBefore(List<PassFactory> factoryList, PassFactory factory, String passName) {
    factoryList.add(findPassIndexByName(factoryList, passName), factory);
  }
  void clearTypedScope() {
    internalScopeCreator = null;
    typedScopeCreator = null;
    topScope = null;
  }
  void patchGlobalTypedScope(AbstractCompiler compiler, Node scriptRoot) {
    Preconditions.checkNotNull(internalScopeCreator);
    internalScopeCreator.patchGlobalScope(topScope, scriptRoot);
  }
  void regenerateGlobalTypedScope(AbstractCompiler compiler, Node root) {
    internalScopeCreator = new TypedScopeCreator(compiler);
    typedScopeCreator = new MemoizedScopeCreator(internalScopeCreator);
    topScope = typedScopeCreator.createScope(root, null);
  }
  final static void replacePassFactory(List<PassFactory> factoryList, PassFactory factory) {
    factoryList.set(findPassIndexByName(factoryList, factory.getName()), factory);
  }
  abstract protected void setIntermediateState(State state);
  
  static class PassConfigDelegate extends PassConfig  {
    final private PassConfig delegate;
    PassConfigDelegate(PassConfig delegate) {
      super(delegate.options);
      this.delegate = delegate;
    }
    @Override() protected List<PassFactory> getChecks() {
      return delegate.getChecks();
    }
    @Override() protected List<PassFactory> getOptimizations() {
      return delegate.getOptimizations();
    }
    @Override() MemoizedScopeCreator getTypedScopeCreator() {
      return delegate.getTypedScopeCreator();
    }
    @Override() Scope getTopScope() {
      return delegate.getTopScope();
    }
    @Override() protected State getIntermediateState() {
      return delegate.getIntermediateState();
    }
    @Override() protected void setIntermediateState(State state) {
      delegate.setIntermediateState(state);
    }
  }
  
  public static class State implements Serializable  {
    final private static long serialVersionUID = 1L;
    final Map<String, Integer> cssNames;
    final Set<String> exportedNames;
    final CrossModuleMethodMotion.IdGenerator crossModuleIdGenerator;
    final VariableMap variableMap;
    final VariableMap propertyMap;
    final VariableMap anonymousFunctionNameMap;
    final VariableMap stringMap;
    final FunctionNames functionNames;
    final String idGeneratorMap;
    public State(Map<String, Integer> cssNames, Set<String> exportedNames, CrossModuleMethodMotion.IdGenerator crossModuleIdGenerator, VariableMap variableMap, VariableMap propertyMap, VariableMap anonymousFunctionNameMap, VariableMap stringMap, FunctionNames functionNames, String idGeneratorMap) {
      super();
      this.cssNames = cssNames;
      this.exportedNames = exportedNames;
      this.crossModuleIdGenerator = crossModuleIdGenerator;
      this.variableMap = variableMap;
      this.propertyMap = propertyMap;
      this.anonymousFunctionNameMap = anonymousFunctionNameMap;
      this.stringMap = stringMap;
      this.idGeneratorMap = idGeneratorMap;
      this.functionNames = functionNames;
    }
  }
}