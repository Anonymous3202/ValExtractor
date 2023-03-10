package com.google.javascript.jscomp;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.javascript.jscomp.ReferenceCollectingCallback.Reference;
import com.google.javascript.jscomp.ReferenceCollectingCallback.ReferenceCollection;
import com.google.javascript.jscomp.ReferenceCollectingCallback.ReferenceMap;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

class GlobalVarReferenceMap implements ReferenceMap  {
  private Map<String, ReferenceCollection> refMap = null;
  final private Map<InputId, Integer> inputOrder;
  GlobalVarReferenceMap(List<CompilerInput> inputs, List<CompilerInput> externs) {
    super();
    inputOrder = Maps.newHashMap();
    int ind = 0;
    for (CompilerInput extern : externs) {
      inputOrder.put(extern.getInputId(), ind);
      ind++;
    }
    for (CompilerInput input : inputs) {
      inputOrder.put(input.getInputId(), ind);
      ind++;
    }
  }
  @Override() public ReferenceCollection getReferences(Var var) {
    if(!var.isGlobal()) {
      return null;
    }
    return refMap.get(var.getName());
  }
  private SourceRefRange findSourceRefRange(List<Reference> refList, InputId inputId) {
    Preconditions.checkNotNull(inputId);
    int lastBefore = -1;
    int firstAfter = refList.size();
    int index = 0;
    Preconditions.checkState(inputOrder.containsKey(inputId), inputId.getIdName());
    int sourceInputOrder = inputOrder.get(inputId);
    for (Reference ref : refList) {
      InputId var_656 = ref.getInputId();
      Preconditions.checkNotNull(var_656);
      int order = inputOrder.get(ref.getInputId());
      if(order < sourceInputOrder) {
        lastBefore = index;
      }
      else 
        if(order > sourceInputOrder) {
          firstAfter = index;
          break ;
        }
      index++;
    }
    return new SourceRefRange(refList, lastBefore, firstAfter);
  }
  private void removeScriptReferences(InputId inputId) {
    Preconditions.checkNotNull(inputId);
    if(!inputOrder.containsKey(inputId)) {
      return ;
    }
    for (ReferenceCollection collection : refMap.values()) {
      if(collection == null) {
        continue ;
      }
      List<Reference> oldRefs = collection.references;
      SourceRefRange range = findSourceRefRange(oldRefs, inputId);
      List<Reference> newRefs = Lists.newArrayList(range.refsBefore());
      newRefs.addAll(range.refsAfter());
      collection.references = newRefs;
    }
  }
  private void replaceReferences(String varName, InputId inputId, ReferenceCollection newSourceCollection) {
    ReferenceCollection combined = new ReferenceCollection();
    List<Reference> combinedRefs = combined.references;
    ReferenceCollection oldCollection = refMap.get(varName);
    refMap.put(varName, combined);
    if(oldCollection == null) {
      combinedRefs.addAll(newSourceCollection.references);
      return ;
    }
    SourceRefRange range = findSourceRefRange(oldCollection.references, inputId);
    combinedRefs.addAll(range.refsBefore());
    combinedRefs.addAll(newSourceCollection.references);
    combinedRefs.addAll(range.refsAfter());
  }
  private void resetGlobalVarReferences(Map<Var, ReferenceCollection> globalRefMap) {
    refMap = Maps.newHashMap();
    for (Entry<Var, ReferenceCollection> entry : globalRefMap.entrySet()) {
      Var var = entry.getKey();
      if(var.isGlobal()) {
        refMap.put(var.getName(), entry.getValue());
      }
    }
  }
  void updateGlobalVarReferences(Map<Var, ReferenceCollection> refMapPatch, Node root) {
    if(refMap == null || !root.isScript()) {
      resetGlobalVarReferences(refMapPatch);
      return ;
    }
    InputId inputId = root.getInputId();
    Preconditions.checkNotNull(inputId);
    removeScriptReferences(inputId);
    for (Entry<Var, ReferenceCollection> entry : refMapPatch.entrySet()) {
      Var var = entry.getKey();
      if(var.isGlobal()) {
        replaceReferences(var.getName(), inputId, entry.getValue());
      }
    }
  }
  public void updateReferencesWithGlobalScope(Scope globalScope) {
    for (ReferenceCollection collection : refMap.values()) {
      List<Reference> newRefs = Lists.newArrayListWithCapacity(collection.references.size());
      for (Reference ref : collection.references) {
        if(ref.getScope() != globalScope) {
          newRefs.add(ref.cloneWithNewScope(globalScope));
        }
        else {
          newRefs.add(ref);
        }
      }
      collection.references = newRefs;
    }
  }
  
  static class GlobalVarRefCleanupPass implements HotSwapCompilerPass  {
    final private AbstractCompiler compiler;
    public GlobalVarRefCleanupPass(AbstractCompiler compiler) {
      super();
      this.compiler = compiler;
    }
    @Override() public void hotSwapScript(Node scriptRoot, Node originalRoot) {
      GlobalVarReferenceMap refMap = compiler.getGlobalVarReferences();
      if(refMap != null) {
        refMap.updateReferencesWithGlobalScope(compiler.getTopScope());
      }
    }
    @Override() public void process(Node externs, Node root) {
    }
  }
  
  private static class SourceRefRange  {
    final private int lastBefore;
    final private int firstAfter;
    final private List<Reference> refList;
    SourceRefRange(List<Reference> refList, int lastBefore, int firstAfter) {
      super();
      this.lastBefore = Math.max(lastBefore, -1);
      this.firstAfter = Math.min(firstAfter, refList.size());
      this.refList = refList;
    }
    List<Reference> refsAfter() {
      return refList.subList(firstAfter, refList.size());
    }
    List<Reference> refsBefore() {
      return refList.subList(0, lastBefore + 1);
    }
  }
}