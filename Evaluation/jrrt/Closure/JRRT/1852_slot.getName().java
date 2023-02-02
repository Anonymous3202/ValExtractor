package com.google.javascript.jscomp;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.jscomp.type.FlowScope;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.SimpleSlot;
import com.google.javascript.rhino.jstype.StaticScope;
import com.google.javascript.rhino.jstype.StaticSlot;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

class LinkedFlowScope implements FlowScope  {
  final private FlatFlowScopeCache cache;
  final private LinkedFlowScope parent;
  private int depth;
  final static int MAX_DEPTH = 250;
  private FlatFlowScopeCache flattened;
  private boolean frozen = false;
  private LinkedFlowSlot lastSlot;
  LinkedFlowScope(FlatFlowScopeCache cache) {
    this(cache, null);
  }
  private LinkedFlowScope(FlatFlowScopeCache cache, LinkedFlowScope directParent) {
    super();
    this.cache = cache;
    if(directParent == null) {
      this.lastSlot = null;
      this.depth = 0;
      this.parent = cache.linkedEquivalent;
    }
    else {
      this.lastSlot = directParent.lastSlot;
      this.depth = directParent.depth + 1;
      this.parent = directParent;
    }
  }
  LinkedFlowScope(LinkedFlowScope directParent) {
    this(directParent.cache, directParent);
  }
  @Override() public FlowScope createChildFlowScope() {
    frozen = true;
    if(depth > MAX_DEPTH) {
      if(flattened == null) {
        flattened = new FlatFlowScopeCache(this);
      }
      return new LinkedFlowScope(flattened);
    }
    return new LinkedFlowScope(this);
  }
  @Override() public JSType getTypeOfThis() {
    return cache.functionScope.getTypeOfThis();
  }
  public static LinkedFlowScope createEntryLattice(Scope scope) {
    return new LinkedFlowScope(new FlatFlowScopeCache(scope));
  }
  @Override() public LinkedFlowScope optimize() {
    LinkedFlowScope current;
    for(current = this; current.parent != null && current.lastSlot == current.parent.lastSlot; current = current.parent) {
    }
    return current;
  }
  private Map<String, StaticSlot<JSType>> allFlowSlots() {
    Map<String, StaticSlot<JSType>> slots = Maps.newHashMap();
    for(com.google.javascript.jscomp.LinkedFlowScope.LinkedFlowSlot slot = lastSlot; slot != null; slot = slot.parent) {
      String var_1852 = slot.getName();
      if(!slots.containsKey(var_1852)) {
        slots.put(slot.getName(), slot);
      }
    }
    for (Map.Entry<String, StaticSlot<JSType>> symbolEntry : cache.symbols.entrySet()) {
      if(!slots.containsKey(symbolEntry.getKey())) {
        slots.put(symbolEntry.getKey(), symbolEntry.getValue());
      }
    }
    return slots;
  }
  @Override() public Node getRootNode() {
    return getFunctionScope().getRootNode();
  }
  private Scope getFunctionScope() {
    return cache.functionScope;
  }
  @Override() public StaticScope<JSType> getParentScope() {
    return getFunctionScope().getParentScope();
  }
  @Override() public StaticSlot<JSType> findUniqueRefinedSlot(FlowScope blindScope) {
    StaticSlot<JSType> result = null;
    for(com.google.javascript.jscomp.LinkedFlowScope currentScope = this; currentScope != blindScope; currentScope = currentScope.parent) {
      for(com.google.javascript.jscomp.LinkedFlowScope.LinkedFlowSlot currentSlot = currentScope.lastSlot; currentSlot != null && (currentScope.parent == null || currentScope.parent.lastSlot != currentSlot); currentSlot = currentSlot.parent) {
        if(result == null) {
          result = currentSlot;
        }
        else 
          if(!currentSlot.getName().equals(result.getName())) {
            return null;
          }
      }
    }
    return result;
  }
  @Override() public StaticSlot<JSType> getOwnSlot(String name) {
    throw new UnsupportedOperationException();
  }
  @Override() public StaticSlot<JSType> getSlot(String name) {
    if(cache.dirtySymbols.contains(name)) {
      for(com.google.javascript.jscomp.LinkedFlowScope.LinkedFlowSlot slot = lastSlot; slot != null; slot = slot.parent) {
        if(slot.getName().equals(name)) {
          return slot;
        }
      }
    }
    return cache.getSlot(name);
  }
  private boolean diffSlots(StaticSlot<JSType> slotA, StaticSlot<JSType> slotB) {
    boolean aIsNull = slotA == null || slotA.getType() == null;
    boolean bIsNull = slotB == null || slotB.getType() == null;
    if(aIsNull && bIsNull) {
      return false;
    }
    else 
      if(aIsNull ^ bIsNull) {
        return true;
      }
    return slotA.getType().differsFrom(slotB.getType());
  }
  @Override() public boolean equals(Object other) {
    if(other instanceof LinkedFlowScope) {
      LinkedFlowScope that = (LinkedFlowScope)other;
      if(this.optimize() == that.optimize()) {
        return true;
      }
      if(this.getFunctionScope() != that.getFunctionScope()) {
        return false;
      }
      if(cache == that.cache) {
        for (String name : cache.dirtySymbols) {
          if(diffSlots(getSlot(name), that.getSlot(name))) {
            return false;
          }
        }
        return true;
      }
      Map<String, StaticSlot<JSType>> myFlowSlots = allFlowSlots();
      Map<String, StaticSlot<JSType>> otherFlowSlots = that.allFlowSlots();
      for (StaticSlot<JSType> slot : myFlowSlots.values()) {
        if(diffSlots(slot, otherFlowSlots.get(slot.getName()))) {
          return false;
        }
        otherFlowSlots.remove(slot.getName());
      }
      for (StaticSlot<JSType> slot : otherFlowSlots.values()) {
        if(diffSlots(slot, myFlowSlots.get(slot.getName()))) {
          return false;
        }
      }
      return true;
    }
    return false;
  }
  private boolean flowsFromBottom() {
    return getFunctionScope().isBottom();
  }
  @Override() public void completeScope(StaticScope<JSType> staticScope) {
    Scope scope = (Scope)staticScope;
    for(java.util.Iterator<com.google.javascript.jscomp.Scope.Var> it = scope.getVars(); it.hasNext(); ) {
      Var var = it.next();
      if(var.isTypeInferred()) {
        JSType type = var.getType();
        if(type == null || type.isUnknownType()) {
          JSType flowType = getSlot(var.getName()).getType();
          var.setType(flowType);
        }
      }
    }
  }
  @Override() public void inferQualifiedSlot(Node node, String symbol, JSType bottomType, JSType inferredType) {
    Scope functionScope = getFunctionScope();
    if(functionScope.isLocal()) {
      if(functionScope.getVar(symbol) == null && !functionScope.isBottom()) {
        functionScope.declare(symbol, node, bottomType, null);
      }
      inferSlotType(symbol, inferredType);
    }
  }
  @Override() public void inferSlotType(String symbol, JSType type) {
    Preconditions.checkState(!frozen);
    lastSlot = new LinkedFlowSlot(symbol, type, lastSlot);
    depth++;
    cache.dirtySymbols.add(symbol);
  }
  
  private static class FlatFlowScopeCache  {
    final private Scope functionScope;
    final private LinkedFlowScope linkedEquivalent;
    private Map<String, StaticSlot<JSType>> symbols = Maps.newHashMap();
    final Set<String> dirtySymbols = Sets.newHashSet();
    FlatFlowScopeCache(LinkedFlowScope directParent) {
      super();
      FlatFlowScopeCache cache = directParent.cache;
      functionScope = cache.functionScope;
      symbols = directParent.allFlowSlots();
      linkedEquivalent = directParent;
    }
    FlatFlowScopeCache(LinkedFlowScope joinedScopeA, LinkedFlowScope joinedScopeB) {
      super();
      linkedEquivalent = null;
      functionScope = joinedScopeA.flowsFromBottom() ? joinedScopeB.getFunctionScope() : joinedScopeA.getFunctionScope();
      Map<String, StaticSlot<JSType>> slotsA = joinedScopeA.allFlowSlots();
      Map<String, StaticSlot<JSType>> slotsB = joinedScopeB.allFlowSlots();
      symbols = slotsA;
      Set<String> symbolNames = Sets.newHashSet(symbols.keySet());
      symbolNames.addAll(slotsB.keySet());
      for (String name : symbolNames) {
        StaticSlot<JSType> slotA = slotsA.get(name);
        StaticSlot<JSType> slotB = slotsB.get(name);
        JSType joinedType = null;
        if(slotB == null || slotB.getType() == null) {
          StaticSlot<JSType> fnSlot = joinedScopeB.getFunctionScope().getSlot(name);
          JSType fnSlotType = fnSlot == null ? null : fnSlot.getType();
          if(fnSlotType == null) {
          }
          else {
            joinedType = slotA.getType().getLeastSupertype(fnSlotType);
          }
        }
        else 
          if(slotA == null || slotA.getType() == null) {
            StaticSlot<JSType> fnSlot = joinedScopeA.getFunctionScope().getSlot(name);
            JSType fnSlotType = fnSlot == null ? null : fnSlot.getType();
            if(fnSlotType == null) {
              symbols.put(name, slotB);
            }
            else {
              joinedType = slotB.getType().getLeastSupertype(fnSlotType);
            }
          }
          else {
            joinedType = slotA.getType().getLeastSupertype(slotB.getType());
          }
        if(joinedType != null) {
          symbols.put(name, new SimpleSlot(name, joinedType, true));
        }
      }
    }
    FlatFlowScopeCache(Scope functionScope) {
      super();
      this.functionScope = functionScope;
      symbols = ImmutableMap.of();
      linkedEquivalent = null;
    }
    public StaticSlot<JSType> getSlot(String name) {
      if(symbols.containsKey(name)) {
        return symbols.get(name);
      }
      else {
        return functionScope.getSlot(name);
      }
    }
  }
  
  static class FlowScopeJoinOp extends JoinOp.BinaryJoinOp<FlowScope>  {
    @SuppressWarnings(value = {"unchecked", }) @Override() public FlowScope apply(FlowScope a, FlowScope b) {
      LinkedFlowScope linkedA = (LinkedFlowScope)a;
      LinkedFlowScope linkedB = (LinkedFlowScope)b;
      linkedA.frozen = true;
      linkedB.frozen = true;
      if(linkedA.optimize() == linkedB.optimize()) {
        return linkedA.createChildFlowScope();
      }
      return new LinkedFlowScope(new FlatFlowScopeCache(linkedA, linkedB));
    }
  }
  
  private static class LinkedFlowSlot extends SimpleSlot  {
    final LinkedFlowSlot parent;
    LinkedFlowSlot(String name, JSType type, LinkedFlowSlot parent) {
      super(name, type, true);
      this.parent = parent;
    }
  }
}