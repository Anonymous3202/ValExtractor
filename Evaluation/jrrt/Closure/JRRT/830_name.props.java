package com.google.javascript.jscomp;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.GlobalNamespace.Name;
import com.google.javascript.jscomp.GlobalNamespace.Ref;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import java.util.Set;

class CheckGlobalNames implements CompilerPass  {
  final private AbstractCompiler compiler;
  final private CodingConvention convention;
  final private CheckLevel level;
  private GlobalNamespace namespace = null;
  final private Set<String> objectPrototypeProps = Sets.newHashSet();
  final private Set<String> functionPrototypeProps = Sets.newHashSet();
  final static DiagnosticType UNDEFINED_NAME_WARNING = DiagnosticType.warning("JSC_UNDEFINED_NAME", "{0} is never defined");
  final static DiagnosticType NAME_DEFINED_LATE_WARNING = DiagnosticType.warning("JSC_NAME_DEFINED_LATE", "{0} defined before its owner. {1} is defined at {2}:{3}");
  final static DiagnosticType STRICT_MODULE_DEP_QNAME = DiagnosticType.disabled("JSC_STRICT_MODULE_DEP_QNAME", "module {0} cannot reference {2}, defined in " + "module {1}");
  CheckGlobalNames(AbstractCompiler compiler, CheckLevel level) {
    super();
    this.compiler = compiler;
    this.convention = compiler.getCodingConvention();
    this.level = level;
  }
  CheckGlobalNames injectNamespace(GlobalNamespace namespace) {
    Preconditions.checkArgument(namespace.hasExternsRoot());
    this.namespace = namespace;
    return this;
  }
  private boolean isTypedef(Ref ref) {
    Node parent = ref.node.getParent();
    if(parent.isExprResult()) {
      JSDocInfo info = ref.node.getJSDocInfo();
      if(info != null && info.hasTypedefType()) {
        return true;
      }
    }
    return false;
  }
  private boolean propertyMustBeInitializedByFullName(Name name) {
    if(name.parent == null) {
      return false;
    }
    boolean parentIsAliased = false;
    if(name.parent.aliasingGets > 0) {
      for (Ref ref : name.parent.getRefs()) {
        if(ref.type == Ref.Type.ALIASING_GET) {
          Node aliaser = ref.getNode().getParent();
          boolean isKnownAlias = aliaser.isCall() && (convention.getClassesDefinedByCall(aliaser) != null || convention.getSingletonGetterClassName(aliaser) != null);
          if(!isKnownAlias) {
            parentIsAliased = true;
          }
        }
      }
    }
    if(parentIsAliased) {
      return false;
    }
    if(objectPrototypeProps.contains(name.getBaseName())) {
      return false;
    }
    if(name.parent.type == Name.Type.OBJECTLIT) {
      return true;
    }
    if(name.parent.type == Name.Type.FUNCTION && name.parent.isDeclaredType() && !functionPrototypeProps.contains(name.getBaseName())) {
      return true;
    }
    return false;
  }
  private void checkDescendantNames(Name name, boolean nameIsDefined) {
    java.util.List<Name> var_830 = name.props;
    if(var_830 != null) {
      for (Name prop : name.props) {
        boolean propIsDefined = false;
        if(nameIsDefined) {
          propIsDefined = (!propertyMustBeInitializedByFullName(prop) || prop.globalSets + prop.localSets > 0);
        }
        validateName(prop, propIsDefined);
        checkDescendantNames(prop, propIsDefined);
      }
    }
  }
  private void findPrototypeProps(String type, Set<String> props) {
    Name slot = namespace.getSlot(type);
    if(slot != null) {
      for (Ref ref : slot.getRefs()) {
        if(ref.type == Ref.Type.PROTOTYPE_GET) {
          Node fullName = ref.getNode().getParent().getParent();
          if(fullName.isGetProp()) {
            props.add(fullName.getLastChild().getString());
          }
        }
      }
    }
  }
  @Override() public void process(Node externs, Node root) {
    if(namespace == null) {
      namespace = new GlobalNamespace(compiler, externs, root);
    }
    Preconditions.checkState(namespace.hasExternsRoot());
    findPrototypeProps("Object", objectPrototypeProps);
    findPrototypeProps("Function", functionPrototypeProps);
    objectPrototypeProps.addAll(convention.getIndirectlyDeclaredProperties());
    for (Name name : namespace.getNameForest()) {
      if(name.inExterns) {
        continue ;
      }
      checkDescendantNames(name, name.globalSets + name.localSets > 0);
    }
  }
  private void reportBadModuleReference(Name name, Ref ref) {
    compiler.report(JSError.make(ref.source.getName(), ref.node, STRICT_MODULE_DEP_QNAME, ref.getModule().getName(), name.getDeclaration().getModule().getName(), name.getFullName()));
  }
  private void reportRefToUndefinedName(Name name, Ref ref) {
    while(name.parent != null && name.parent.globalSets + name.parent.localSets == 0){
      name = name.parent;
    }
    compiler.report(JSError.make(ref.getSourceName(), ref.node, level, UNDEFINED_NAME_WARNING, name.getFullName()));
  }
  private void validateName(Name name, boolean isDefined) {
    Ref declaration = name.getDeclaration();
    Name parent = name.parent;
    JSModuleGraph moduleGraph = compiler.getModuleGraph();
    for (Ref ref : name.getRefs()) {
      boolean isGlobalExpr = ref.getNode().getParent().isExprResult();
      if(!isDefined && !isTypedef(ref)) {
        if(!isGlobalExpr) {
          reportRefToUndefinedName(name, ref);
        }
      }
      else 
        if(declaration != null && ref.getModule() != declaration.getModule() && !moduleGraph.dependsOn(ref.getModule(), declaration.getModule())) {
          reportBadModuleReference(name, ref);
        }
        else {
          if(ref.scope.isGlobal()) {
            boolean isPrototypeGet = (ref.type == Ref.Type.PROTOTYPE_GET);
            Name owner = isPrototypeGet ? name : parent;
            boolean singleGlobalParentDecl = owner != null && owner.getDeclaration() != null && owner.localSets == 0;
            if(singleGlobalParentDecl && owner.getDeclaration().preOrderIndex > ref.preOrderIndex) {
              String refName = isPrototypeGet ? name.getFullName() + ".prototype" : name.getFullName();
              compiler.report(JSError.make(ref.source.getName(), ref.node, NAME_DEFINED_LATE_WARNING, refName, owner.getFullName(), owner.getDeclaration().source.getName(), String.valueOf(owner.getDeclaration().node.getLineno())));
            }
          }
        }
    }
  }
}