package com.google.javascript.jscomp;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.RenameVars.Assignment;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.Node;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedSet;

class ShadowVariables implements CompilerPass  {
  final private Multimap<Node, String> scopeUpRefMap = HashMultimap.create();
  final private Multimap<Var, Node> varToNameUsage = HashMultimap.create();
  final private AbstractCompiler compiler;
  final private SortedSet<Assignment> varsByFrequency;
  final private Map<String, Assignment> assignments;
  final private Map<Node, String> oldPseudoNameMap;
  final private Map<Node, String> deltaPseudoNameMap;
  ShadowVariables(AbstractCompiler compiler, Map<String, Assignment> assignments, SortedSet<Assignment> varsByFrequency, Map<Node, String> pseudoNameMap) {
    super();
    this.compiler = compiler;
    this.assignments = assignments;
    this.varsByFrequency = varsByFrequency;
    this.oldPseudoNameMap = pseudoNameMap;
    this.deltaPseudoNameMap = Maps.newLinkedHashMap();
  }
  @Override() public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, new GatherReferenceInfo());
    NodeTraversal.traverse(compiler, root, new DoShadowVariables());
    if(oldPseudoNameMap != null) {
      oldPseudoNameMap.putAll(deltaPseudoNameMap);
    }
  }
  
  private class DoShadowVariables extends AbstractPostOrderCallback implements ScopedCallback  {
    private Assignment findBestShadow(Scope curScope, Var candidate) {
      for (Assignment assignment : varsByFrequency) {
        if(assignment.oldName.startsWith(RenameVars.LOCAL_VAR_PREFIX)) {
          if(!scopeUpRefMap.get(curScope.getRootNode()).contains(assignment.oldName)) {
            if(curScope.isDeclared(assignment.oldName, true)) {
              return assignment;
            }
          }
        }
      }
      return null;
    }
    private void doShadow(Assignment original, Assignment toShadow, Var var) {
      Scope s = var.getScope();
      Collection<Node> references = varToNameUsage.get(var);
      varsByFrequency.remove(original);
      varsByFrequency.remove(toShadow);
      original.count -= references.size();
      toShadow.count += references.size();
      varsByFrequency.add(original);
      varsByFrequency.add(toShadow);
      Var shadowed = s.getVar(toShadow.oldName);
      if(shadowed != null) {
        for(com.google.javascript.jscomp.Scope curScope = s; curScope != shadowed.scope; curScope = curScope.getParent()) {
          scopeUpRefMap.put(curScope.getRootNode(), toShadow.oldName);
        }
      }
      for (Node n : references) {
        n.setString(toShadow.oldName);
        Node cur = n;
        while(cur != s.getRootNode()){
          cur = cur.getParent();
          if(cur.isFunction()) {
            scopeUpRefMap.put(cur, toShadow.oldName);
          }
        }
      }
    }
    @Override() public void enterScope(NodeTraversal t) {
      Scope s = t.getScope();
      if(!s.isLocal()) {
        return ;
      }
      if(s.getParent().isGlobal()) {
        return ;
      }
      for(java.util.Iterator<com.google.javascript.jscomp.Scope.Var> vars = s.getVars(); vars.hasNext(); ) {
        Var var = vars.next();
        if(var.isBleedingFunction()) {
          continue ;
        }
        if(compiler.getCodingConvention().isExported(var.name, s.isLocal())) {
          continue ;
        }
        Assignment bestShadow = findBestShadow(s, var);
        if(bestShadow == null) {
          continue ;
        }
        Assignment localAssignment = assignments.get(var.getName());
        if(bestShadow.count < localAssignment.count) {
          continue ;
        }
        doShadow(localAssignment, bestShadow, var);
        if(oldPseudoNameMap != null) {
          String targetPseudoName = oldPseudoNameMap.get(s.getVar(bestShadow.oldName).nameNode);
          for (Node use : varToNameUsage.get(var)) {
            deltaPseudoNameMap.put(use, targetPseudoName);
          }
        }
      }
    }
    @Override() public void exitScope(NodeTraversal t) {
    }
    @Override() public void visit(NodeTraversal t, Node n, Node parent) {
    }
  }
  
  private class GatherReferenceInfo extends AbstractPostOrderCallback  {
    @Override() public void visit(NodeTraversal t, Node n, Node parent) {
      if(!NodeUtil.isReferenceName(n)) {
        return ;
      }
      if(t.inGlobalScope()) {
        return ;
      }
      Scope var_536 = t.getScope();
      Var var = var_536.getVar(n.getString());
      if(var == null) {
        return ;
      }
      if(var.getScope().isGlobal()) {
        return ;
      }
      if(var.getScope() != t.getScope()) {
        for(com.google.javascript.jscomp.Scope s = t.getScope(); s != var.getScope() && s.isLocal(); s = s.getParent()) {
          scopeUpRefMap.put(s.getRootNode(), var.name);
        }
      }
      if(var.getScope() == t.getScope()) {
        scopeUpRefMap.put(t.getScopeRoot(), var.name);
      }
      varToNameUsage.put(var, n);
    }
  }
}