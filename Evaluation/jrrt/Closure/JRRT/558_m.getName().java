package com.google.javascript.jscomp;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.deps.SortedDependencies;
import com.google.javascript.jscomp.deps.SortedDependencies.CircularDependencyException;
import com.google.javascript.jscomp.deps.SortedDependencies.MissingProvideException;
import com.google.javascript.jscomp.graph.LinkedDirectedGraph;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class JSModuleGraph  {
  private List<JSModule> modules;
  private List<List<JSModule>> modulesByDepth;
  private Map<JSModule, Set<JSModule>> dependencyMap = Maps.newHashMap();
  public JSModuleGraph(JSModule[] modulesInDepOrder) {
    this(ImmutableList.copyOf(modulesInDepOrder));
  }
  public JSModuleGraph(List<JSModule> modulesInDepOrder) {
    super();
    Preconditions.checkState(modulesInDepOrder.size() == Sets.newHashSet(modulesInDepOrder).size(), "Found duplicate modules");
    modules = ImmutableList.copyOf(modulesInDepOrder);
    modulesByDepth = Lists.newArrayList();
    for (JSModule module : modulesInDepOrder) {
      int depth = 0;
      for (JSModule dep : module.getDependencies()) {
        int depDepth = dep.getDepth();
        if(depDepth < 0) {
          throw new ModuleDependenceException(String.format("Modules not in dependency order: %s preceded %s", module.getName(), dep.getName()), module, dep);
        }
        depth = Math.max(depth, depDepth + 1);
      }
      module.setDepth(depth);
      if(depth == modulesByDepth.size()) {
        modulesByDepth.add(new ArrayList<JSModule>());
      }
      modulesByDepth.get(depth).add(module);
    }
  }
  Iterable<JSModule> getAllModules() {
    return modules;
  }
  JSModule getDeepestCommonDependency(JSModule m1, JSModule m2) {
    int m1Depth = m1.getDepth();
    int m2Depth = m2.getDepth();
    for(int depth = Math.min(m1Depth, m2Depth) - 1; depth >= 0; depth--) {
      List<JSModule> modulesAtDepth = modulesByDepth.get(depth);
      for(int i = modulesAtDepth.size() - 1; i >= 0; i--) {
        JSModule m = modulesAtDepth.get(i);
        if(dependsOn(m1, m) && dependsOn(m2, m)) {
          return m;
        }
      }
    }
    return null;
  }
  public JSModule getDeepestCommonDependencyInclusive(JSModule m1, JSModule m2) {
    if(m2 == m1 || dependsOn(m2, m1)) {
      return m1;
    }
    else 
      if(dependsOn(m1, m2)) {
        return m2;
      }
    return getDeepestCommonDependency(m1, m2);
  }
  public JSModule getDeepestCommonDependencyInclusive(Collection<JSModule> modules) {
    Iterator<JSModule> iter = modules.iterator();
    JSModule dep = iter.next();
    while(iter.hasNext()){
      dep = getDeepestCommonDependencyInclusive(dep, iter.next());
    }
    return dep;
  }
  JSModule getRootModule() {
    return Iterables.getOnlyElement(modulesByDepth.get(0));
  }
  JSONArray toJson() {
    JSONArray modules = new JSONArray();
    for (JSModule module : getAllModules()) {
      JSONObject node = new JSONObject();
      try {
        node.put("name", module.getName());
        JSONArray deps = new JSONArray();
        node.put("dependencies", deps);
        for (JSModule m : module.getDependencies()) {
          String var_558 = m.getName();
          deps.put(var_558);
        }
        JSONArray transitiveDeps = new JSONArray();
        node.put("transitive-dependencies", transitiveDeps);
        for (JSModule m : getTransitiveDepsDeepestFirst(module)) {
          transitiveDeps.put(m.getName());
        }
        JSONArray inputs = new JSONArray();
        node.put("inputs", inputs);
        for (CompilerInput input : module.getInputs()) {
          inputs.put(input.getSourceFile().getOriginalPath());
        }
        modules.put(node);
      }
      catch (JSONException e) {
        Throwables.propagate(e);
      }
    }
    return modules;
  }
  LinkedDirectedGraph<JSModule, String> toGraphvizGraph() {
    LinkedDirectedGraph<JSModule, String> graphViz = LinkedDirectedGraph.create();
    for (JSModule module : getAllModules()) {
      graphViz.createNode(module);
      for (JSModule dep : module.getDependencies()) {
        graphViz.createNode(dep);
        graphViz.connect(module, "->", dep);
      }
    }
    return graphViz;
  }
  public List<CompilerInput> manageDependencies(DependencyOptions depOptions, List<CompilerInput> inputs) throws CircularDependencyException, MissingProvideException {
    SortedDependencies<CompilerInput> sorter = new SortedDependencies<CompilerInput>(inputs);
    Set<CompilerInput> entryPointInputs = Sets.newLinkedHashSet();
    if(depOptions.shouldPruneDependencies()) {
      if(!depOptions.shouldDropMoochers()) {
        entryPointInputs.addAll(sorter.getInputsWithoutProvides());
      }
      for (String entryPoint : depOptions.getEntryPoints()) {
        entryPointInputs.add(sorter.getInputProviding(entryPoint));
      }
      CompilerInput baseJs = sorter.maybeGetInputProviding("goog");
      if(baseJs != null) {
        entryPointInputs.add(baseJs);
      }
    }
    else {
      entryPointInputs.addAll(inputs);
    }
    List<CompilerInput> absoluteOrder = sorter.getDependenciesOf(inputs, depOptions.shouldSortDependencies());
    ListMultimap<JSModule, CompilerInput> entryPointInputsPerModule = LinkedListMultimap.create();
    for (CompilerInput input : entryPointInputs) {
      JSModule module = input.getModule();
      Preconditions.checkNotNull(module);
      entryPointInputsPerModule.put(module, input);
    }
    for (JSModule module : getAllModules()) {
      module.removeAll();
    }
    for (JSModule module : entryPointInputsPerModule.keySet()) {
      List<CompilerInput> transitiveClosure = sorter.getDependenciesOf(entryPointInputsPerModule.get(module), depOptions.shouldSortDependencies());
      for (CompilerInput input : transitiveClosure) {
        JSModule oldModule = input.getModule();
        if(oldModule == null) {
          input.setModule(module);
        }
        else {
          input.setModule(null);
          input.setModule(getDeepestCommonDependencyInclusive(oldModule, module));
        }
      }
    }
    for (CompilerInput input : absoluteOrder) {
      JSModule module = input.getModule();
      if(module != null) {
        module.add(input);
      }
    }
    List<CompilerInput> result = Lists.newArrayList();
    for (JSModule module : getAllModules()) {
      result.addAll(module.getInputs());
    }
    return result;
  }
  public List<CompilerInput> manageDependencies(List<String> entryPoints, List<CompilerInput> inputs) throws CircularDependencyException, MissingProvideException {
    DependencyOptions depOptions = new DependencyOptions();
    depOptions.setDependencySorting(true);
    depOptions.setDependencyPruning(true);
    depOptions.setEntryPoints(entryPoints);
    return manageDependencies(depOptions, inputs);
  }
  Set<JSModule> getTransitiveDepsDeepestFirst(JSModule m) {
    Set<JSModule> deps = dependencyMap.get(m);
    if(deps != null) {
      return deps;
    }
    deps = new TreeSet<JSModule>(new InverseDepthComparator());
    addDeps(deps, m);
    dependencyMap.put(m, deps);
    return deps;
  }
  public boolean dependsOn(JSModule src, JSModule m) {
    Set<JSModule> deps = dependencyMap.get(src);
    if(deps == null) {
      deps = getTransitiveDepsDeepestFirst(src);
      dependencyMap.put(src, deps);
    }
    return deps.contains(m);
  }
  private int depthCompare(JSModule m1, JSModule m2) {
    if(m1 == m2) {
      return 0;
    }
    int d1 = m1.getDepth();
    int d2 = m2.getDepth();
    return d1 < d2 ? -1 : d2 == d1 ? m1.getName().compareTo(m2.getName()) : 1;
  }
  int getModuleCount() {
    return modules.size();
  }
  private void addDeps(Set<JSModule> deps, JSModule m) {
    for (JSModule dep : m.getDependencies()) {
      deps.add(dep);
      addDeps(deps, dep);
    }
  }
  public void coalesceDuplicateFiles() {
    Multimap<String, JSModule> fileRefs = LinkedHashMultimap.create();
    for (JSModule module : modules) {
      for (CompilerInput jsFile : module.getInputs()) {
        fileRefs.put(jsFile.getName(), module);
      }
    }
    for (String path : fileRefs.keySet()) {
      Collection<JSModule> refModules = fileRefs.get(path);
      if(refModules.size() > 1) {
        JSModule depModule = getDeepestCommonDependencyInclusive(refModules);
        CompilerInput file = refModules.iterator().next().getByName(path);
        for (JSModule module : refModules) {
          if(module != depModule) {
            module.removeByName(path);
          }
        }
        if(!refModules.contains(depModule)) {
          depModule.add(file);
        }
      }
    }
  }
  
  private class InverseDepthComparator implements Comparator<JSModule>  {
    @Override() public int compare(JSModule m1, JSModule m2) {
      return depthCompare(m2, m1);
    }
  }
  
  protected static class ModuleDependenceException extends IllegalArgumentException  {
    final private static long serialVersionUID = 1;
    final private JSModule module;
    final private JSModule dependentModule;
    protected ModuleDependenceException(String message, JSModule module, JSModule dependentModule) {
      super(message);
      this.module = module;
      this.dependentModule = dependentModule;
    }
    public JSModule getDependentModule() {
      return dependentModule;
    }
    public JSModule getModule() {
      return module;
    }
  }
}