package com.google.javascript.jscomp.deps;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
public class SortedDependencies<INPUT extends com.google.javascript.jscomp.deps.DependencyInfo>  {
  final private List<INPUT> inputs;
  final private List<INPUT> sortedList;
  final private List<INPUT> noProvides;
  final private Map<String, INPUT> provideMap = Maps.newHashMap();
  public SortedDependencies(List<INPUT> inputs) throws CircularDependencyException {
    super();
    this.inputs = Lists.newArrayList(inputs);
    noProvides = Lists.newArrayList();
    for (INPUT input : inputs) {
      Collection<String> currentProvides = input.getProvides();
      if(currentProvides.isEmpty()) {
        noProvides.add(input);
      }
      for (String provide : currentProvides) {
        provideMap.put(provide, input);
      }
    }
    final Multimap<INPUT, INPUT> deps = HashMultimap.create();
    for (INPUT input : inputs) {
      for (String req : input.getRequires()) {
        INPUT dep = provideMap.get(req);
        if(dep != null && dep != input) {
          deps.put(input, dep);
        }
      }
    }
    sortedList = topologicalStableSort(inputs, deps);
    if(sortedList.size() < inputs.size()) {
      List<INPUT> subGraph = Lists.newArrayList(inputs);
      subGraph.removeAll(sortedList);
      throw new CircularDependencyException(cycleToString(findCycle(subGraph, deps)));
    }
  }
  private INPUT findRequireInSubGraphOrFail(INPUT input, Set<INPUT> subGraph) {
    for (String symbol : input.getRequires()) {
      INPUT candidate = provideMap.get(symbol);
      if(subGraph.contains(candidate)) {
        return candidate;
      }
    }
    throw new IllegalStateException("no require found in subgraph");
  }
  public INPUT getInputProviding(String symbol) throws MissingProvideException {
    if(provideMap.containsKey(symbol)) {
      return provideMap.get(symbol);
    }
    throw new MissingProvideException(symbol);
  }
  public INPUT maybeGetInputProviding(String symbol) {
    return provideMap.get(symbol);
  }
  private List<INPUT> findCycle(INPUT current, Set<INPUT> subGraph, Multimap<INPUT, INPUT> deps, Set<INPUT> covered) {
    if(covered.add(current)) {
      List<INPUT> cycle = findCycle(findRequireInSubGraphOrFail(current, subGraph), subGraph, deps, covered);
      if(cycle.get(0) != cycle.get(cycle.size() - 1)) {
        cycle.add(current);
      }
      return cycle;
    }
    else {
      List<INPUT> cycle = Lists.<INPUT>newArrayList();
      cycle.add(current);
      return cycle;
    }
  }
  private List<INPUT> findCycle(List<INPUT> subGraph, Multimap<INPUT, INPUT> deps) {
    return findCycle(subGraph.get(0), Sets.<INPUT>newHashSet(subGraph), deps, Sets.<INPUT>newHashSet());
  }
  public List<INPUT> getDependenciesOf(List<INPUT> roots, boolean sorted) {
    Preconditions.checkArgument(inputs.containsAll(roots));
    Set<INPUT> included = Sets.newHashSet();
    Deque<INPUT> worklist = new ArrayDeque<INPUT>(roots);
    while(!worklist.isEmpty()){
      INPUT current = worklist.pop();
      if(included.add(current)) {
        for (String req : current.getRequires()) {
          INPUT dep = provideMap.get(req);
          if(dep != null) {
            worklist.add(dep);
          }
        }
      }
    }
    ImmutableList.Builder<INPUT> builder = ImmutableList.builder();
    for (INPUT current : (sorted ? sortedList : inputs)) {
      if(included.contains(current)) {
        builder.add(current);
      }
    }
    return builder.build();
  }
  public List<INPUT> getInputsWithoutProvides() {
    return Collections.<INPUT>unmodifiableList(noProvides);
  }
  public List<INPUT> getSortedDependenciesOf(List<INPUT> roots) {
    return getDependenciesOf(roots, true);
  }
  public List<INPUT> getSortedList() {
    return Collections.<INPUT>unmodifiableList(sortedList);
  }
  private static  <T extends java.lang.Object> List<T> topologicalStableSort(List<T> items, Multimap<T, T> deps) {
    if(items.size() == 0) {
      java.util.ArrayList<T> var_2155 = Lists.newArrayList();
      return var_2155;
    }
    final Map<T, Integer> originalIndex = Maps.newHashMap();
    for(int i = 0; i < items.size(); i++) {
      originalIndex.put(items.get(i), i);
    }
    PriorityQueue<T> inDegreeZero = new PriorityQueue<T>(items.size(), new Comparator<T>() {
        @Override() public int compare(T a, T b) {
          return originalIndex.get(a).intValue() - originalIndex.get(b).intValue();
        }
    });
    List<T> result = Lists.newArrayList();
    Multiset<T> inDegree = HashMultiset.create();
    Multimap<T, T> reverseDeps = ArrayListMultimap.create();
    Multimaps.invertFrom(deps, reverseDeps);
    for (T item : items) {
      Collection<T> itemDeps = deps.get(item);
      inDegree.add(item, itemDeps.size());
      if(itemDeps.isEmpty()) {
        inDegreeZero.add(item);
      }
    }
    while(!inDegreeZero.isEmpty()){
      T item = inDegreeZero.remove();
      result.add(item);
      for (T inWaiting : reverseDeps.get(item)) {
        inDegree.remove(inWaiting, 1);
        if(inDegree.count(inWaiting) == 0) {
          inDegreeZero.add(inWaiting);
        }
      }
    }
    return result;
  }
  private String cycleToString(List<INPUT> cycle) {
    List<String> symbols = Lists.newArrayList();
    for(int i = cycle.size() - 1; i >= 0; i--) {
      symbols.add(cycle.get(i).getProvides().iterator().next());
    }
    symbols.add(symbols.get(0));
    return Joiner.on(" -> ").join(symbols);
  }
  
  public static class CircularDependencyException extends Exception  {
    CircularDependencyException(String message) {
      super(message);
    }
  }
  
  public static class MissingProvideException extends Exception  {
    MissingProvideException(String provide) {
      super(provide);
    }
  }
}