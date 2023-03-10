package com.google.javascript.jscomp;
import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.Node;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.annotation.Nullable;

final class RenameVars implements CompilerPass  {
  final private AbstractCompiler compiler;
  final private ArrayList<Node> globalNameNodes = new ArrayList<Node>();
  final private ArrayList<Node> localNameNodes = new ArrayList<Node>();
  final private Map<Node, String> pseudoNameMap;
  final private Set<String> externNames = new HashSet<String>();
  final private Set<String> reservedNames;
  final private Map<String, String> renameMap = new HashMap<String, String>();
  final private VariableMap prevUsedRenameMap;
  final private String prefix;
  private int assignmentCount = 0;
  private StringBuilder assignmentLog;
  private Set<Var> localBleedingFunctions = Sets.newHashSet();
  private ArrayListMultimap<Scope, Var> localBleedingFunctionsPerScope = ArrayListMultimap.create();
  final private Map<String, Assignment> assignments = new HashMap<String, Assignment>();
  final private boolean localRenamingOnly;
  private boolean preserveFunctionExpressionNames;
  final private boolean shouldShadow;
  final private char[] reservedCharacters;
  final public static String LOCAL_VAR_PREFIX = "L ";
  final private static Comparator<Assignment> FREQUENCY_COMPARATOR = new Comparator<Assignment>() {
      @Override() public int compare(Assignment a1, Assignment a2) {
        if(a1.count != a2.count) {
          return a2.count - a1.count;
        }
        return ORDER_OF_OCCURRENCE_COMPARATOR.compare(a1, a2);
      }
  };
  final private static Comparator<Assignment> ORDER_OF_OCCURRENCE_COMPARATOR = new Comparator<Assignment>() {
      @Override() public int compare(Assignment a1, Assignment a2) {
        return a1.orderOfOccurrence - a2.orderOfOccurrence;
      }
  };
  RenameVars(AbstractCompiler compiler, String prefix, boolean localRenamingOnly, boolean preserveFunctionExpressionNames, boolean generatePseudoNames, boolean shouldShadow, VariableMap prevUsedRenameMap, @Nullable() char[] reservedCharacters, @Nullable() Set<String> reservedNames) {
    super();
    this.compiler = compiler;
    this.prefix = prefix == null ? "" : prefix;
    this.localRenamingOnly = localRenamingOnly;
    this.preserveFunctionExpressionNames = preserveFunctionExpressionNames;
    if(generatePseudoNames) {
      this.pseudoNameMap = Maps.newHashMap();
    }
    else {
      this.pseudoNameMap = null;
    }
    this.prevUsedRenameMap = prevUsedRenameMap;
    this.reservedCharacters = reservedCharacters;
    this.shouldShadow = shouldShadow;
    if(reservedNames == null) {
      this.reservedNames = Sets.newHashSet();
    }
    else {
      this.reservedNames = Sets.newHashSet(reservedNames);
    }
  }
  private String getNewGlobalName(Node n) {
    String oldName = n.getString();
    Assignment a = assignments.get(oldName);
    if(a.newName != null && !a.newName.equals(oldName)) {
      if(pseudoNameMap != null) {
        return pseudoNameMap.get(n);
      }
      return a.newName;
    }
    else {
      return null;
    }
  }
  private String getNewLocalName(Node n) {
    String oldTempName = n.getString();
    Assignment a = assignments.get(oldTempName);
    if(!a.newName.equals(oldTempName)) {
      if(pseudoNameMap != null) {
        return pseudoNameMap.get(n);
      }
      return a.newName;
    }
    return null;
  }
  VariableMap getVariableMap() {
    return new VariableMap(ImmutableMap.copyOf(renameMap));
  }
  private boolean okToRenameVar(String name, boolean isLocal) {
    return !compiler.getCodingConvention().isExported(name, isLocal);
  }
  private int getLocalVarIndex(Var v) {
    int num = v.index;
    Scope s = v.scope.getParent();
    if(s == null) {
      throw new IllegalArgumentException("Var is not local");
    }
    boolean isBleedingIntoScope = s.getParent() != null && localBleedingFunctions.contains(v);
    while(s.getParent() != null){
      if(isBleedingIntoScope) {
        num += localBleedingFunctionsPerScope.get(s).indexOf(v) + 1;
        isBleedingIntoScope = false;
      }
      else {
        num += localBleedingFunctionsPerScope.get(s).size();
      }
      num += s.getVarCount();
      s = s.getParent();
    }
    return num;
  }
  private void assignNames(Set<Assignment> varsToRename) {
    NameGenerator globalNameGenerator = new NameGenerator(reservedNames, prefix, reservedCharacters);
    NameGenerator localNameGenerator = prefix.isEmpty() ? globalNameGenerator : new NameGenerator(reservedNames, "", reservedCharacters);
    List<Assignment> pendingAssignments = new ArrayList<Assignment>();
    List<String> generatedNamesForAssignments = new ArrayList<String>();
    for (Assignment a : varsToRename) {
      if(a.newName != null) {
        continue ;
      }
      if(externNames.contains(a.oldName)) {
        continue ;
      }
      String newName;
      if(a.oldName.startsWith(LOCAL_VAR_PREFIX)) {
        newName = localNameGenerator.generateNextName();
        finalizeNameAssignment(a, newName);
      }
      else {
        newName = globalNameGenerator.generateNextName();
        pendingAssignments.add(a);
        generatedNamesForAssignments.add(newName);
      }
      reservedNames.add(newName);
    }
    int numPendingAssignments = generatedNamesForAssignments.size();
    for(int i = 0; i < numPendingAssignments; ) {
      SortedSet<Assignment> varsByOrderOfOccurrence = new TreeSet<Assignment>(ORDER_OF_OCCURRENCE_COMPARATOR);
      int len = generatedNamesForAssignments.get(i).length();
      for(int j = i; j < numPendingAssignments && generatedNamesForAssignments.get(j).length() == len; j++) {
        varsByOrderOfOccurrence.add(pendingAssignments.get(j));
      }
      for (Assignment a : varsByOrderOfOccurrence) {
        finalizeNameAssignment(a, generatedNamesForAssignments.get(i));
        ++i;
      }
    }
  }
  private void finalizeNameAssignment(Assignment a, String newName) {
    a.setNewName(newName);
    renameMap.put(a.oldName, newName);
    assignmentLog.append(a.oldName).append(" => ").append(newName).append('\n');
  }
  @Override() public void process(Node externs, Node root) {
    assignmentLog = new StringBuilder();
    NodeTraversal.traverse(compiler, externs, new ProcessVars(true));
    NodeTraversal.traverse(compiler, root, new ProcessVars(false));
    reservedNames.addAll(externNames);
    SortedSet<Assignment> varsByFrequency = new TreeSet<Assignment>(FREQUENCY_COMPARATOR);
    varsByFrequency.addAll(assignments.values());
    if(shouldShadow) {
      new ShadowVariables(compiler, assignments, varsByFrequency, pseudoNameMap).process(externs, root);
    }
    if(prevUsedRenameMap != null) {
      reusePreviouslyUsedVariableMap();
    }
    assignNames(varsByFrequency);
    boolean changed = false;
    for (Node n : globalNameNodes) {
      String newName = getNewGlobalName(n);
      if(newName != null) {
        n.setString(newName);
        changed = true;
      }
    }
    int count = 0;
    for (Node n : localNameNodes) {
      String newName = getNewLocalName(n);
      if(newName != null) {
        n.setString(newName);
        changed = true;
      }
      count++;
    }
    if(changed) {
      compiler.reportCodeChange();
    }
    compiler.addToDebugLog("JS var assignments:\n" + assignmentLog);
    assignmentLog = null;
  }
  private void recordPseudoName(Node n) {
    pseudoNameMap.put(n, '$' + n.getString() + "$$");
  }
  private void reusePreviouslyUsedVariableMap() {
    for (Assignment a : assignments.values()) {
      String var_632 = a.oldName;
      String prevNewName = prevUsedRenameMap.lookupNewName(var_632);
      if(prevNewName == null || reservedNames.contains(prevNewName)) {
        continue ;
      }
      if(a.oldName.startsWith(LOCAL_VAR_PREFIX) || (!externNames.contains(a.oldName) && prevNewName.startsWith(prefix))) {
        reservedNames.add(prevNewName);
        finalizeNameAssignment(a, prevNewName);
      }
    }
  }
  
  class Assignment  {
    final String oldName;
    final int orderOfOccurrence;
    String newName;
    int count;
    Assignment(String name) {
      super();
      this.oldName = name;
      this.newName = null;
      this.count = 0;
      this.orderOfOccurrence = assignmentCount++;
    }
    void setNewName(String newName) {
      Preconditions.checkState(this.newName == null);
      this.newName = newName;
    }
  }
  
  class ProcessVars extends AbstractPostOrderCallback implements ScopedCallback  {
    final private boolean isExternsPass_;
    ProcessVars(boolean isExterns) {
      super();
      isExternsPass_ = isExterns;
    }
    @Override() public void enterScope(NodeTraversal t) {
      if(t.inGlobalScope()) 
        return ;
      Iterator<Var> it = t.getScope().getVars();
      while(it.hasNext()){
        Var current = it.next();
        if(current.isBleedingFunction()) {
          localBleedingFunctions.add(current);
          localBleedingFunctionsPerScope.put(t.getScope().getParent(), current);
        }
      }
    }
    @Override() public void exitScope(NodeTraversal t) {
    }
    void incCount(String name) {
      Assignment s = assignments.get(name);
      if(s == null) {
        s = new Assignment(name);
        assignments.put(name, s);
      }
      s.count++;
    }
    @Override() public void visit(NodeTraversal t, Node n, Node parent) {
      if(!n.isName()) {
        return ;
      }
      String name = n.getString();
      if(name.length() == 0) {
        return ;
      }
      Scope.Var var = t.getScope().getVar(name);
      boolean local = (var != null) && var.isLocal() && (!var.scope.getParent().isGlobal() || !var.isBleedingFunction());
      if(!local && localRenamingOnly) {
        reservedNames.add(name);
        return ;
      }
      if(preserveFunctionExpressionNames && var != null && NodeUtil.isFunctionExpression(var.getParentNode())) {
        reservedNames.add(name);
        return ;
      }
      if(!okToRenameVar(name, local)) {
        if(local) {
          String newName = MakeDeclaredNamesUnique.ContextualRenameInverter.getOrginalName(name);
          if(!newName.equals(name)) {
            n.setString(newName);
          }
        }
        return ;
      }
      if(isExternsPass_) {
        if(!local) {
          externNames.add(name);
        }
        return ;
      }
      if(pseudoNameMap != null) {
        recordPseudoName(n);
      }
      if(local) {
        String tempName = LOCAL_VAR_PREFIX + getLocalVarIndex(var);
        incCount(tempName);
        localNameNodes.add(n);
        n.setString(tempName);
      }
      else 
        if(var != null) {
          incCount(name);
          globalNameNodes.add(n);
        }
    }
  }
}