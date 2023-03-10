package com.google.javascript.jscomp;
import static com.google.common.base.Preconditions.checkState;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.AbstractCompiler.LifeCycleStage;
import com.google.javascript.jscomp.ConcreteType.ConcreteFunctionType;
import com.google.javascript.jscomp.ConcreteType.ConcreteInstanceType;
import com.google.javascript.jscomp.ConcreteType.ConcreteUnionType;
import com.google.javascript.jscomp.ConcreteType.ConcreteUniqueType;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.TypeValidator.TypeMismatch;
import com.google.javascript.jscomp.graph.StandardUnionFind;
import com.google.javascript.jscomp.graph.UnionFind;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.StaticScope;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.logging.Logger;
class DisambiguateProperties<T extends java.lang.Object> implements CompilerPass  {
  final private static int MAX_INVALDIATION_WARNINGS_PER_PROPERTY = 10;
  final private static Logger logger = Logger.getLogger(DisambiguateProperties.class.getName());
  final private AbstractCompiler compiler;
  final private TypeSystem<T> typeSystem;
  private Multimap<Object, JSError> invalidationMap;
  final private Map<String, CheckLevel> propertiesToErrorFor;
  private Map<String, Property> properties = Maps.newHashMap();
  private DisambiguateProperties(AbstractCompiler compiler, TypeSystem<T> typeSystem, Map<String, CheckLevel> propertiesToErrorFor) {
    super();
    this.compiler = compiler;
    this.typeSystem = typeSystem;
    this.propertiesToErrorFor = propertiesToErrorFor;
    if(!this.propertiesToErrorFor.isEmpty()) {
      this.invalidationMap = LinkedHashMultimap.create();
    }
    else {
      this.invalidationMap = null;
    }
  }
  static DisambiguateProperties<ConcreteType> forConcreteTypeSystem(AbstractCompiler compiler, TightenTypes tt, Map<String, CheckLevel> propertiesToErrorFor) {
    return new DisambiguateProperties<ConcreteType>(compiler, new ConcreteTypeSystem(tt, compiler.getCodingConvention()), propertiesToErrorFor);
  }
  static DisambiguateProperties<JSType> forJSTypeSystem(AbstractCompiler compiler, Map<String, CheckLevel> propertiesToErrorFor) {
    return new DisambiguateProperties<JSType>(compiler, new JSTypeSystem(compiler), propertiesToErrorFor);
  }
  private Map<T, String> buildPropNames(UnionFind<T> types, String name) {
    Map<T, String> names = Maps.newHashMap();
    for (Set<T> set : types.allEquivalenceClasses()) {
      checkState(!set.isEmpty());
      String typeName = null;
      for (T type : set) {
        if(typeName == null || type.toString().compareTo(typeName) < 0) {
          typeName = type.toString();
        }
      }
      String newName;
      if("{...}".equals(typeName)) {
        newName = name;
      }
      else {
        newName = typeName.replaceAll("[^\\w$]", "_") + "$" + name;
      }
      for (T type : set) {
        names.put(type, newName);
      }
    }
    return names;
  }
  Multimap<String, Collection<T>> getRenamedTypesForTesting() {
    Multimap<String, Collection<T>> ret = HashMultimap.create();
    for (Map.Entry<String, Property> entry : properties.entrySet()) {
      Property prop = entry.getValue();
      if(!prop.skipRenaming) {
        for (Collection<T> c : prop.getTypes().allEquivalenceClasses()) {
          if(!c.isEmpty() && !prop.typesToSkip.contains(c.iterator().next())) {
            ret.put(entry.getKey(), c);
          }
        }
      }
    }
    return ret;
  }
  protected Property getProperty(String name) {
    if(!properties.containsKey(name)) {
      properties.put(name, new Property(name));
    }
    return properties.get(name);
  }
  T getTypeWithProperty(String field, T type) {
    return typeSystem.getTypeWithProperty(field, type);
  }
  private void addInvalidatingType(JSType type, JSError error) {
    type = type.restrictByNotNullOrUndefined();
    if(type.isUnionType()) {
      for (JSType alt : type.toMaybeUnionType().getAlternates()) {
        addInvalidatingType(alt, error);
      }
    }
    else 
      if(type.isEnumElementType()) {
        addInvalidatingType(type.toMaybeEnumElementType().getPrimitiveType(), error);
      }
      else {
        typeSystem.addInvalidatingType(type);
        recordInvalidationError(type, error);
        ObjectType objType = ObjectType.cast(type);
        if(objType != null && objType.getImplicitPrototype() != null) {
          typeSystem.addInvalidatingType(objType.getImplicitPrototype());
          recordInvalidationError(objType.getImplicitPrototype(), error);
        }
      }
  }
  @Override() public void process(Node externs, Node root) {
    Preconditions.checkState(compiler.getLifeCycleStage() == LifeCycleStage.NORMALIZED);
    for (TypeMismatch mis : compiler.getTypeValidator().getMismatches()) {
      addInvalidatingType(mis.typeA, mis.src);
      addInvalidatingType(mis.typeB, mis.src);
    }
    StaticScope<T> scope = typeSystem.getRootScope();
    NodeTraversal.traverse(compiler, externs, new FindExternProperties());
    NodeTraversal.traverse(compiler, root, new FindRenameableProperties());
    renameProperties();
  }
  private void recordInvalidationError(JSType t, JSError error) {
    if(!t.isObject()) {
      return ;
    }
    if(invalidationMap != null) {
      invalidationMap.put(t, error);
    }
  }
  void renameProperties() {
    int propsRenamed = 0;
    int propsSkipped = 0;
    int instancesRenamed = 0;
    int instancesSkipped = 0;
    int singleTypeProps = 0;
    for (Property prop : properties.values()) {
      if(prop.shouldRename()) {
        Map<T, String> propNames = buildPropNames(prop.getTypes(), prop.name);
        ++propsRenamed;
        prop.expandTypesToSkip();
        UnionFind<T> types = prop.getTypes();
        for (Node node : prop.renameNodes) {
          T rootType = prop.rootTypes.get(node);
          if(prop.shouldRename(rootType)) {
            String newName = propNames.get(rootType);
            node.setString(newName);
            compiler.reportCodeChange();
            ++instancesRenamed;
          }
          else {
            ++instancesSkipped;
          }
        }
      }
      else {
        if(prop.skipRenaming) {
          ++propsSkipped;
        }
        else {
          ++singleTypeProps;
        }
      }
    }
    logger.fine("Renamed " + instancesRenamed + " instances of " + propsRenamed + " properties.");
    logger.fine("Skipped renaming " + instancesSkipped + " invalidated " + "properties, " + propsSkipped + " instances of properties " + "that were skipped for specific types and " + singleTypeProps + " properties that were referenced from only one type.");
  }
  
  abstract private class AbstractScopingCallback implements ScopedCallback  {
    final protected Stack<StaticScope<T>> scopes = new Stack<StaticScope<T>>();
    protected StaticScope<T> getScope() {
      return scopes.peek();
    }
    @Override() public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      return true;
    }
    @Override() public void enterScope(NodeTraversal t) {
      if(t.inGlobalScope()) {
        scopes.push(typeSystem.getRootScope());
      }
      else {
        scopes.push(typeSystem.getFunctionScope(t.getScopeRoot()));
      }
    }
    @Override() public void exitScope(NodeTraversal t) {
      scopes.pop();
    }
  }
  
  private static class ConcreteTypeSystem implements TypeSystem<ConcreteType>  {
    final private TightenTypes tt;
    private int nextUniqueId;
    private CodingConvention codingConvention;
    final private Set<JSType> invalidatingTypes = Sets.newHashSet();
    final private static JSTypeNative[] nativeTypes = new JSTypeNative[]{ JSTypeNative.BOOLEAN_OBJECT_TYPE, JSTypeNative.NUMBER_OBJECT_TYPE, JSTypeNative.STRING_OBJECT_TYPE } ;
    public ConcreteTypeSystem(TightenTypes tt, CodingConvention convention) {
      super();
      this.tt = tt;
      this.codingConvention = convention;
    }
    @Override() public ConcreteType getInstanceFromPrototype(ConcreteType type) {
      if(type.isInstance()) {
        ConcreteInstanceType instanceType = (ConcreteInstanceType)type;
        if(instanceType.isFunctionPrototype()) {
          return instanceType.getConstructorType().getInstanceType();
        }
      }
      return null;
    }
    @Override() public ConcreteType getType(StaticScope<ConcreteType> scope, Node node, String prop) {
      if(scope != null) {
        ConcreteType c = tt.inferConcreteType((TightenTypes.ConcreteScope)scope, node);
        return maybeAddAutoboxes(c, node, prop);
      }
      else {
        return null;
      }
    }
    @Override() public ConcreteType getTypeWithProperty(String field, ConcreteType type) {
      if(type.isInstance()) {
        ConcreteInstanceType instanceType = (ConcreteInstanceType)type;
        return instanceType.getInstanceTypeWithProperty(field);
      }
      else 
        if(type.isFunction()) {
          if("prototype".equals(field) || codingConvention.isSuperClassReference(field)) {
            return type;
          }
        }
        else 
          if(type.isNone()) {
            return new ConcreteUniqueType(++nextUniqueId);
          }
          else 
            if(type.isUnion()) {
              for (ConcreteType t : ((ConcreteUnionType)type).getAlternatives()) {
                ConcreteType ret = getTypeWithProperty(field, t);
                if(ret != null) {
                  return ret;
                }
              }
            }
      return null;
    }
    private ConcreteType maybeAddAutoboxes(ConcreteType cType, Node node, String prop) {
      JSType jsType = node.getJSType();
      if(jsType == null) {
        return cType;
      }
      else 
        if(jsType.isUnknownType()) {
          for (JSTypeNative nativeType : nativeTypes) {
            ConcreteType concrete = tt.getConcreteInstance(tt.getTypeRegistry().getNativeObjectType(nativeType));
            if(concrete != null && !concrete.getPropertyType(prop).isNone()) {
              cType = cType.unionWith(concrete);
            }
          }
          return cType;
        }
      return maybeAddAutoboxes(cType, jsType, prop);
    }
    private ConcreteType maybeAddAutoboxes(ConcreteType cType, JSType jsType, String prop) {
      jsType = jsType.restrictByNotNullOrUndefined();
      if(jsType.isUnionType()) {
        for (JSType alt : jsType.toMaybeUnionType().getAlternates()) {
          cType = maybeAddAutoboxes(cType, alt, prop);
        }
        return cType;
      }
      else 
        if(jsType.isEnumElementType()) {
          return maybeAddAutoboxes(cType, jsType.toMaybeEnumElementType().getPrimitiveType(), prop);
        }
      JSType var_889 = jsType.autoboxesTo();
      if(var_889 != null) {
        JSType autoboxed = jsType.autoboxesTo();
        return cType.unionWith(tt.getConcreteInstance((ObjectType)autoboxed));
      }
      else 
        if(jsType.unboxesTo() != null) {
          return cType.unionWith(tt.getConcreteInstance((ObjectType)jsType));
        }
      return cType;
    }
    @Override() public ConcreteType restrictByNotNullOrUndefined(ConcreteType type) {
      return type;
    }
    @Override() public ImmutableSet<ConcreteType> getTypesToSkipForType(ConcreteType type) {
      return ImmutableSet.of(type);
    }
    @Override() public Iterable<ConcreteType> getTypeAlternatives(ConcreteType type) {
      if(type.isUnion()) {
        return ((ConcreteUnionType)type).getAlternatives();
      }
      else {
        return null;
      }
    }
    @Override() public StaticScope<ConcreteType> getFunctionScope(Node decl) {
      ConcreteFunctionType func = tt.getConcreteFunction(decl);
      return (func != null) ? func.getScope() : (StaticScope<ConcreteType>)null;
    }
    @Override() public StaticScope<ConcreteType> getRootScope() {
      return tt.getTopScope();
    }
    @Override() public boolean isInvalidatingType(ConcreteType type) {
      return (type == null) || type.isAll() || type.isFunction() || (type.isInstance() && invalidatingTypes.contains(type.toInstance().instanceType));
    }
    @Override() public boolean isTypeToSkip(ConcreteType type) {
      return type.isInstance() && !(type.toInstance().isFunctionPrototype() || type.toInstance().instanceType.isInstanceType());
    }
    @Override() public void addInvalidatingType(JSType type) {
      checkState(!type.isUnionType());
      invalidatingTypes.add(type);
    }
    @Override() public void recordInterfaces(ConcreteType type, ConcreteType relatedType, DisambiguateProperties<ConcreteType>.Property p) {
    }
  }
  
  private class FindExternProperties extends AbstractScopingCallback  {
    @Override() public void visit(NodeTraversal t, Node n, Node parent) {
      if(n.isGetProp()) {
        String field = n.getLastChild().getString();
        T type = typeSystem.getType(getScope(), n.getFirstChild(), field);
        Property prop = getProperty(field);
        if(typeSystem.isInvalidatingType(type)) {
          prop.invalidate();
        }
        else {
          prop.addTypeToSkip(type);
          if((type = typeSystem.getInstanceFromPrototype(type)) != null) {
            prop.getTypes().add(type);
            prop.typesToSkip.add(type);
          }
        }
      }
    }
  }
  
  private class FindRenameableProperties extends AbstractScopingCallback  {
    private T processProperty(NodeTraversal t, Property prop, T type, T relatedType) {
      type = typeSystem.restrictByNotNullOrUndefined(type);
      if(prop.skipRenaming || typeSystem.isInvalidatingType(type)) {
        return null;
      }
      Iterable<T> alternatives = typeSystem.getTypeAlternatives(type);
      if(alternatives != null) {
        T firstType = relatedType;
        for (T subType : alternatives) {
          T lastType = processProperty(t, prop, subType, firstType);
          if(lastType != null) {
            firstType = firstType == null ? lastType : firstType;
          }
        }
        return firstType;
      }
      else {
        T topType = typeSystem.getTypeWithProperty(prop.name, type);
        if(typeSystem.isInvalidatingType(topType)) {
          return null;
        }
        prop.addType(type, topType, relatedType);
        return topType;
      }
    }
    private void handleGetProp(NodeTraversal t, Node n) {
      String name = n.getLastChild().getString();
      T type = typeSystem.getType(getScope(), n.getFirstChild(), name);
      Property prop = getProperty(name);
      if(!prop.scheduleRenaming(n.getLastChild(), processProperty(t, prop, type, null))) {
        if(propertiesToErrorFor.containsKey(name)) {
          String suggestion = "";
          if(type instanceof JSType) {
            JSType jsType = (JSType)type;
            if(jsType.isAllType() || jsType.isUnknownType()) {
              if(n.getFirstChild().isThis()) {
                suggestion = "The \"this\" object is unknown in the function," + "consider using @this";
              }
              else {
                String qName = n.getFirstChild().getQualifiedName();
                suggestion = "Consider casting " + qName + " if you know it\'s type.";
              }
            }
            else {
              List<String> errors = Lists.newArrayList();
              printErrorLocations(errors, jsType);
              if(!errors.isEmpty()) {
                suggestion = "Consider fixing errors for the following types:\n";
                suggestion += Joiner.on("\n").join(errors);
              }
            }
          }
          compiler.report(JSError.make(t.getSourceName(), n, propertiesToErrorFor.get(name), Warnings.INVALIDATION, name, (type == null ? "null" : type.toString()), n.toString(), suggestion));
        }
      }
    }
    private void handleObjectLit(NodeTraversal t, Node n) {
      Node child = n.getFirstChild();
      while(child != null){
        String name = child.getString();
        T type = typeSystem.getType(getScope(), n, name);
        Property prop = getProperty(name);
        if(!prop.scheduleRenaming(child, processProperty(t, prop, type, null))) {
          if(propertiesToErrorFor.containsKey(name)) {
            compiler.report(JSError.make(t.getSourceName(), child, propertiesToErrorFor.get(name), Warnings.INVALIDATION, name, (type == null ? "null" : type.toString()), n.toString(), ""));
          }
        }
        child = child.getNext();
      }
    }
    private void printErrorLocations(List<String> errors, JSType t) {
      if(!t.isObject() || t.isAllType()) {
        return ;
      }
      if(t.isUnionType()) {
        for (JSType alt : t.toMaybeUnionType().getAlternates()) {
          printErrorLocations(errors, alt);
        }
        return ;
      }
      for (JSError error : invalidationMap.get(t)) {
        if(errors.size() > MAX_INVALDIATION_WARNINGS_PER_PROPERTY) {
          return ;
        }
        errors.add(t.toString() + " at " + error.sourceName + ":" + error.lineNumber);
      }
    }
    @Override() public void visit(NodeTraversal t, Node n, Node parent) {
      if(n.isGetProp()) {
        handleGetProp(t, n);
      }
      else 
        if(n.isObjectLit()) {
          handleObjectLit(t, n);
        }
    }
  }
  
  private static class JSTypeSystem implements TypeSystem<JSType>  {
    final private Set<JSType> invalidatingTypes;
    private JSTypeRegistry registry;
    public JSTypeSystem(AbstractCompiler compiler) {
      super();
      registry = compiler.getTypeRegistry();
      invalidatingTypes = Sets.newHashSet(registry.getNativeType(JSTypeNative.ALL_TYPE), registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE), registry.getNativeType(JSTypeNative.NO_TYPE), registry.getNativeType(JSTypeNative.FUNCTION_PROTOTYPE), registry.getNativeType(JSTypeNative.FUNCTION_INSTANCE_TYPE), registry.getNativeType(JSTypeNative.OBJECT_PROTOTYPE), registry.getNativeType(JSTypeNative.TOP_LEVEL_PROTOTYPE), registry.getNativeType(JSTypeNative.UNKNOWN_TYPE));
    }
    @Override() public ImmutableSet<JSType> getTypesToSkipForType(JSType type) {
      type = type.restrictByNotNullOrUndefined();
      if(type.isUnionType()) {
        Set<JSType> types = Sets.newHashSet(type);
        for (JSType alt : type.toMaybeUnionType().getAlternates()) {
          types.addAll(getTypesToSkipForTypeNonUnion(type));
        }
        return ImmutableSet.copyOf(types);
      }
      else 
        if(type.isEnumElementType()) {
          return getTypesToSkipForType(type.toMaybeEnumElementType().getPrimitiveType());
        }
      return ImmutableSet.copyOf(getTypesToSkipForTypeNonUnion(type));
    }
    @Override() public Iterable<JSType> getTypeAlternatives(JSType type) {
      if(type.isUnionType()) {
        return type.toMaybeUnionType().getAlternates();
      }
      else {
        ObjectType objType = type.toObjectType();
        if(objType != null && objType.getConstructor() != null && objType.getConstructor().isInterface()) {
          List<JSType> list = Lists.newArrayList();
          for (FunctionType impl : registry.getDirectImplementors(objType)) {
            list.add(impl.getInstanceType());
          }
          return list;
        }
        else {
          return null;
        }
      }
    }
    @Override() public JSType getInstanceFromPrototype(JSType type) {
      if(type.isFunctionPrototypeType()) {
        ObjectType prototype = (ObjectType)type;
        FunctionType owner = prototype.getOwnerFunction();
        if(owner.isConstructor() || owner.isInterface()) {
          return prototype.getOwnerFunction().getInstanceType();
        }
      }
      return null;
    }
    @Override() public JSType getType(StaticScope<JSType> scope, Node node, String prop) {
      if(node.getJSType() == null) {
        return registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);
      }
      return node.getJSType();
    }
    @Override() public JSType restrictByNotNullOrUndefined(JSType type) {
      return type.restrictByNotNullOrUndefined();
    }
    @Override() public ObjectType getTypeWithProperty(String field, JSType type) {
      if(type == null) {
        return null;
      }
      if(type.isEnumElementType()) {
        return getTypeWithProperty(field, type.toMaybeEnumElementType().getPrimitiveType());
      }
      if(!(type instanceof ObjectType)) {
        if(type.autoboxesTo() != null) {
          type = type.autoboxesTo();
        }
        else {
          return null;
        }
      }
      if("prototype".equals(field)) {
        return null;
      }
      ObjectType foundType = null;
      ObjectType objType = ObjectType.cast(type);
      if(objType != null && objType.getConstructor() != null && objType.getConstructor().isInterface()) {
        ObjectType topInterface = FunctionType.getTopDefiningInterface(objType, field);
        if(topInterface != null && topInterface.getConstructor() != null) {
          foundType = topInterface.getConstructor().getPrototype();
        }
      }
      else {
        while(objType != null && objType.getImplicitPrototype() != objType){
          if(objType.hasOwnProperty(field)) {
            foundType = objType;
          }
          objType = objType.getImplicitPrototype();
        }
      }
      if(foundType == null) {
        ObjectType maybeType = ObjectType.cast(registry.getGreatestSubtypeWithProperty(type, field));
        if(maybeType != null && maybeType.hasOwnProperty(field)) {
          foundType = maybeType;
        }
      }
      return foundType;
    }
    private Set<JSType> getTypesToSkipForTypeNonUnion(JSType type) {
      Set<JSType> types = Sets.newHashSet();
      JSType skipType = type;
      while(skipType != null){
        types.add(skipType);
        ObjectType objSkipType = skipType.toObjectType();
        if(objSkipType != null) {
          skipType = objSkipType.getImplicitPrototype();
        }
        else {
          break ;
        }
      }
      return types;
    }
    @Override() public StaticScope<JSType> getFunctionScope(Node node) {
      return null;
    }
    @Override() public StaticScope<JSType> getRootScope() {
      return null;
    }
    @Override() public boolean isInvalidatingType(JSType type) {
      if(type == null || invalidatingTypes.contains(type) || type.isUnknownType()) {
        return true;
      }
      ObjectType objType = ObjectType.cast(type);
      return objType != null && !objType.hasReferenceName();
    }
    @Override() public boolean isTypeToSkip(JSType type) {
      return type.isEnumType() || (type.autoboxesTo() != null);
    }
    @Override() public void addInvalidatingType(JSType type) {
      checkState(!type.isUnionType());
      invalidatingTypes.add(type);
    }
    @Override() public void recordInterfaces(JSType type, JSType relatedType, DisambiguateProperties<JSType>.Property p) {
      ObjectType objType = ObjectType.cast(type);
      if(objType != null) {
        FunctionType constructor;
        if(objType.isFunctionType()) {
          constructor = objType.toMaybeFunctionType();
        }
        else 
          if(objType.isFunctionPrototypeType()) {
            constructor = objType.getOwnerFunction();
          }
          else {
            constructor = objType.getConstructor();
          }
        while(constructor != null){
          for (ObjectType itype : constructor.getImplementedInterfaces()) {
            JSType top = getTypeWithProperty(p.name, itype);
            if(top != null) {
              p.addType(itype, top, relatedType);
            }
            else {
              recordInterfaces(itype, relatedType, p);
            }
            if(p.skipRenaming) 
              return ;
          }
          if(constructor.isInterface() || constructor.isConstructor()) {
            constructor = constructor.getSuperClassConstructor();
          }
          else {
            constructor = null;
          }
        }
      }
    }
  }
  
  private class Property  {
    final String name;
    private UnionFind<T> types;
    Set<T> typesToSkip = Sets.newHashSet();
    boolean skipRenaming;
    Set<Node> renameNodes = Sets.newHashSet();
    final Map<Node, T> rootTypes = Maps.newHashMap();
    Property(String name) {
      super();
      this.name = name;
    }
    UnionFind<T> getTypes() {
      if(types == null) {
        types = new StandardUnionFind<T>();
      }
      return types;
    }
    boolean addType(T type, T top, T relatedType) {
      checkState(!skipRenaming, "Attempt to record skipped property: %s", name);
      if(typeSystem.isInvalidatingType(top)) {
        invalidate();
        return false;
      }
      else {
        if(typeSystem.isTypeToSkip(top)) {
          addTypeToSkip(top);
        }
        if(relatedType == null) {
          getTypes().add(top);
        }
        else {
          getTypes().union(top, relatedType);
        }
        typeSystem.recordInterfaces(type, top, this);
        return true;
      }
    }
    boolean invalidate() {
      boolean changed = !skipRenaming;
      skipRenaming = true;
      types = null;
      return changed;
    }
    boolean scheduleRenaming(Node node, T type) {
      if(!skipRenaming) {
        if(typeSystem.isInvalidatingType(type)) {
          invalidate();
          return false;
        }
        renameNodes.add(node);
        rootTypes.put(node, type);
      }
      return true;
    }
    boolean shouldRename() {
      return !skipRenaming && types != null && types.allEquivalenceClasses().size() > 1;
    }
    boolean shouldRename(T type) {
      return !skipRenaming && !typesToSkip.contains(type);
    }
    void addTypeToSkip(T type) {
      for (T skipType : typeSystem.getTypesToSkipForType(type)) {
        typesToSkip.add(skipType);
        getTypes().union(skipType, type);
      }
    }
    void expandTypesToSkip() {
      if(shouldRename()) {
        int count = 0;
        while(true){
          checkState(++count < 10, "Stuck in loop expanding types to skip.");
          Set<T> rootTypesToSkip = Sets.newHashSet();
          for (T subType : typesToSkip) {
            rootTypesToSkip.add(types.find(subType));
          }
          typesToSkip.addAll(rootTypesToSkip);
          Set<T> newTypesToSkip = Sets.newHashSet();
          Set<T> allTypes = types.elements();
          int originalTypesSize = allTypes.size();
          for (T subType : allTypes) {
            if(!typesToSkip.contains(subType) && typesToSkip.contains(types.find(subType))) {
              newTypesToSkip.add(subType);
            }
          }
          for (T newType : newTypesToSkip) {
            addTypeToSkip(newType);
          }
          if(types.elements().size() == originalTypesSize) {
            break ;
          }
        }
      }
    }
  }
  private interface TypeSystem<T extends java.lang.Object>  {
    ImmutableSet<T> getTypesToSkipForType(T type);
    Iterable<T> getTypeAlternatives(T type);
    StaticScope<T> getFunctionScope(Node node);
    StaticScope<T> getRootScope();
    T getInstanceFromPrototype(T type);
    T getType(StaticScope<T> scope, Node node, String prop);
    T getTypeWithProperty(String field, T type);
    T restrictByNotNullOrUndefined(T type);
    boolean isInvalidatingType(T type);
    boolean isTypeToSkip(T type);
    void addInvalidatingType(JSType type);
    void recordInterfaces(T type, T relatedType, DisambiguateProperties<T>.Property p);
  }
  
  static class Warnings  {
    final static DiagnosticType INVALIDATION = DiagnosticType.disabled("JSC_INVALIDATION", "Property disambiguator skipping all instances of property {0} " + "because of type {1} node {2}. {3}");
  }
}