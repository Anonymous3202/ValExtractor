package com.google.javascript.jscomp;
import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.JSDocInfo.Visibility;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.StaticSourceFile;

class CheckAccessControls implements ScopedCallback, HotSwapCompilerPass  {
  final static DiagnosticType DEPRECATED_NAME = DiagnosticType.disabled("JSC_DEPRECATED_VAR", "Variable {0} has been deprecated.");
  final static DiagnosticType DEPRECATED_NAME_REASON = DiagnosticType.disabled("JSC_DEPRECATED_VAR_REASON", "Variable {0} has been deprecated: {1}");
  final static DiagnosticType DEPRECATED_PROP = DiagnosticType.disabled("JSC_DEPRECATED_PROP", "Property {0} of type {1} has been deprecated.");
  final static DiagnosticType DEPRECATED_PROP_REASON = DiagnosticType.disabled("JSC_DEPRECATED_PROP_REASON", "Property {0} of type {1} has been deprecated: {2}");
  final static DiagnosticType DEPRECATED_CLASS = DiagnosticType.disabled("JSC_DEPRECATED_CLASS", "Class {0} has been deprecated.");
  final static DiagnosticType DEPRECATED_CLASS_REASON = DiagnosticType.disabled("JSC_DEPRECATED_CLASS_REASON", "Class {0} has been deprecated: {1}");
  final static DiagnosticType BAD_PRIVATE_GLOBAL_ACCESS = DiagnosticType.disabled("JSC_BAD_PRIVATE_GLOBAL_ACCESS", "Access to private variable {0} not allowed outside file {1}.");
  final static DiagnosticType BAD_PRIVATE_PROPERTY_ACCESS = DiagnosticType.disabled("JSC_BAD_PRIVATE_PROPERTY_ACCESS", "Access to private property {0} of {1} not allowed here.");
  final static DiagnosticType BAD_PROTECTED_PROPERTY_ACCESS = DiagnosticType.disabled("JSC_BAD_PROTECTED_PROPERTY_ACCESS", "Access to protected property {0} of {1} not allowed here.");
  final static DiagnosticType PRIVATE_OVERRIDE = DiagnosticType.disabled("JSC_PRIVATE_OVERRIDE", "Overriding private property of {0}.");
  final static DiagnosticType EXTEND_FINAL_CLASS = DiagnosticType.error("JSC_EXTEND_FINAL_CLASS", "{0} is not allowed to extend final class {1}.");
  final static DiagnosticType VISIBILITY_MISMATCH = DiagnosticType.disabled("JSC_VISIBILITY_MISMATCH", "Overriding {0} property of {1} with {2} property.");
  final static DiagnosticType CONST_PROPERTY_REASSIGNED_VALUE = DiagnosticType.warning("JSC_CONSTANT_PROPERTY_REASSIGNED_VALUE", "constant property {0} assigned a value more than once");
  final static DiagnosticType CONST_PROPERTY_DELETED = DiagnosticType.warning("JSC_CONSTANT_PROPERTY_DELETED", "constant property {0} cannot be deleted");
  final private AbstractCompiler compiler;
  final private TypeValidator validator;
  private int deprecatedDepth = 0;
  private int methodDepth = 0;
  private JSType currentClass = null;
  final private Multimap<String, String> initializedConstantProperties;
  CheckAccessControls(AbstractCompiler compiler) {
    super();
    this.compiler = compiler;
    this.validator = compiler.getTypeValidator();
    this.initializedConstantProperties = HashMultimap.create();
  }
  private static JSType dereference(JSType type) {
    return type == null ? null : type.dereference();
  }
  private JSType getClassOfMethod(Node n, Node parent) {
    if(parent.isAssign()) {
      Node lValue = parent.getFirstChild();
      if(NodeUtil.isGet(lValue)) {
        JSType lValueType = lValue.getJSType();
        if(lValueType != null && lValueType.isNominalConstructor()) {
          return (lValueType.toMaybeFunctionType()).getInstanceType();
        }
        else {
          return normalizeClassType(lValue.getFirstChild().getJSType());
        }
      }
      else {
        return normalizeClassType(lValue.getJSType());
      }
    }
    else 
      if(NodeUtil.isFunctionDeclaration(n) || parent.isName()) {
        return normalizeClassType(n.getJSType());
      }
    return null;
  }
  private JSType getFinalParentClass(JSType type) {
    if(type != null) {
      ObjectType iproto = ObjectType.cast(type).getImplicitPrototype();
      while(iproto != null && iproto.getConstructor() == null){
        iproto = iproto.getImplicitPrototype();
      }
      if(iproto != null) {
        Node source = iproto.getConstructor().getSource();
        JSDocInfo jsDoc = source != null ? NodeUtil.getBestJSDocInfo(source) : null;
        if(jsDoc != null && jsDoc.isConstant()) {
          return iproto;
        }
      }
    }
    return null;
  }
  private JSType normalizeClassType(JSType type) {
    if(type == null || type.isUnknownType()) {
      return type;
    }
    else 
      if(type.isNominalConstructor()) {
        return (type.toMaybeFunctionType()).getInstanceType();
      }
      else 
        if(type.isFunctionPrototypeType()) {
          FunctionType owner = ((ObjectType)type).getOwnerFunction();
          if(owner.isConstructor()) {
            return owner.getInstanceType();
          }
        }
    return type;
  }
  private static String getPropertyDeprecationInfo(ObjectType type, String prop) {
    JSDocInfo info = type.getOwnPropertyJSDocInfo(prop);
    if(info != null && info.isDeprecated()) {
      if(info.getDeprecationReason() != null) {
        return info.getDeprecationReason();
      }
      return "";
    }
    ObjectType implicitProto = type.getImplicitPrototype();
    if(implicitProto != null) {
      return getPropertyDeprecationInfo(implicitProto, prop);
    }
    return null;
  }
  private static String getTypeDeprecationInfo(JSType type) {
    if(type == null) {
      return null;
    }
    JSDocInfo info = type.getJSDocInfo();
    if(info != null && info.isDeprecated()) {
      if(info.getDeprecationReason() != null) {
        return info.getDeprecationReason();
      }
      return "";
    }
    ObjectType objType = ObjectType.cast(type);
    if(objType != null) {
      ObjectType implicitProto = objType.getImplicitPrototype();
      if(implicitProto != null) {
        return getTypeDeprecationInfo(implicitProto);
      }
    }
    return null;
  }
  private boolean canAccessDeprecatedTypes(NodeTraversal t) {
    Node scopeRoot = t.getScopeRoot();
    Node scopeRootParent = scopeRoot.getParent();
    return (deprecatedDepth > 0) || (getTypeDeprecationInfo(t.getScope().getTypeOfThis()) != null) || (scopeRootParent != null && scopeRootParent.isAssign() && getTypeDeprecationInfo(getClassOfMethod(scopeRoot, scopeRootParent)) != null);
  }
  private static boolean isDeprecatedFunction(Node n, Node parent) {
    if(n.isFunction()) {
      JSType type = n.getJSType();
      if(type != null) {
        return getTypeDeprecationInfo(type) != null;
      }
    }
    return false;
  }
  private static boolean isPropertyDeclaredConstant(ObjectType objectType, String prop) {
    for(; objectType != null && objectType.hasReferenceName(); objectType = objectType.getImplicitPrototype()) {
      JSDocInfo docInfo = objectType.getOwnPropertyJSDocInfo(prop);
      if(docInfo != null && docInfo.isConstant()) {
        return true;
      }
    }
    return false;
  }
  private static boolean isValidPrivateConstructorAccess(Node parent) {
    return !parent.isNew();
  }
  private boolean shouldEmitDeprecationWarning(NodeTraversal t, Node n, Node parent) {
    if(t.inGlobalScope()) {
      if(!((parent.isCall() && parent.getFirstChild() == n) || n.isNew())) {
        return false;
      }
    }
    if(n.isGetProp() && n == parent.getFirstChild() && NodeUtil.isAssignmentOp(parent)) {
      return false;
    }
    return !canAccessDeprecatedTypes(t);
  }
  @Override() public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    return true;
  }
  private void checkConstantProperty(NodeTraversal t, Node getprop) {
    Node parent = getprop.getParent();
    boolean isDelete = parent.isDelProp();
    if(!(NodeUtil.isAssignmentOp(parent) && parent.getFirstChild() == getprop) && !parent.isInc() && !parent.isDec() && !isDelete) {
      return ;
    }
    ObjectType objectType = ObjectType.cast(dereference(getprop.getFirstChild().getJSType()));
    String propertyName = getprop.getLastChild().getString();
    boolean isConstant = isPropertyDeclaredConstant(objectType, propertyName);
    if(isConstant) {
      if(isDelete) {
        compiler.report(t.makeError(getprop, CONST_PROPERTY_DELETED, propertyName));
        return ;
      }
      ObjectType oType = objectType;
      while(oType != null){
        if(oType.hasReferenceName()) {
          if(initializedConstantProperties.containsEntry(oType.getReferenceName(), propertyName)) {
            compiler.report(t.makeError(getprop, CONST_PROPERTY_REASSIGNED_VALUE, propertyName));
            break ;
          }
        }
        oType = oType.getImplicitPrototype();
      }
      Preconditions.checkState(objectType.hasReferenceName());
      initializedConstantProperties.put(objectType.getReferenceName(), propertyName);
      if(objectType.isInstanceType()) {
        ObjectType prototype = objectType.getImplicitPrototype();
        if(prototype != null) {
          if(prototype.hasProperty(propertyName) && prototype.hasReferenceName()) {
            initializedConstantProperties.put(prototype.getReferenceName(), propertyName);
          }
        }
      }
    }
  }
  private void checkConstructorDeprecation(NodeTraversal t, Node n, Node parent) {
    JSType type = n.getJSType();
    if(type != null) {
      String deprecationInfo = getTypeDeprecationInfo(type);
      if(deprecationInfo != null && shouldEmitDeprecationWarning(t, n, parent)) {
        if(!deprecationInfo.isEmpty()) {
          compiler.report(t.makeError(n, DEPRECATED_CLASS_REASON, type.toString(), deprecationInfo));
        }
        else {
          compiler.report(t.makeError(n, DEPRECATED_CLASS, type.toString()));
        }
      }
    }
  }
  private void checkFinalClassOverrides(NodeTraversal t, Node fn, Node parent) {
    JSType type = fn.getJSType().toMaybeFunctionType();
    if(type != null && type.isConstructor()) {
      JSType finalParentClass = getFinalParentClass(getClassOfMethod(fn, parent));
      if(finalParentClass != null) {
        compiler.report(t.makeError(fn, EXTEND_FINAL_CLASS, type.getDisplayName(), finalParentClass.getDisplayName()));
      }
    }
  }
  private void checkNameDeprecation(NodeTraversal t, Node n, Node parent) {
    if(parent.isFunction() || parent.isVar() || parent.isNew()) {
      return ;
    }
    Scope.Var var = t.getScope().getVar(n.getString());
    JSDocInfo docInfo = var == null ? null : var.getJSDocInfo();
    if(docInfo != null && docInfo.isDeprecated() && shouldEmitDeprecationWarning(t, n, parent)) {
      String var_914 = docInfo.getDeprecationReason();
      if(var_914 != null) {
        compiler.report(t.makeError(n, DEPRECATED_NAME_REASON, n.getString(), docInfo.getDeprecationReason()));
      }
      else {
        compiler.report(t.makeError(n, DEPRECATED_NAME, n.getString()));
      }
    }
  }
  private void checkNameVisibility(NodeTraversal t, Node name, Node parent) {
    Var var = t.getScope().getVar(name.getString());
    if(var != null) {
      JSDocInfo docInfo = var.getJSDocInfo();
      if(docInfo != null) {
        Visibility visibility = docInfo.getVisibility();
        if(visibility == Visibility.PRIVATE) {
          StaticSourceFile varSrc = var.getSourceFile();
          StaticSourceFile refSrc = name.getStaticSourceFile();
          if(varSrc != null && refSrc != null && !varSrc.getName().equals(refSrc.getName())) {
            if(docInfo.isConstructor() && isValidPrivateConstructorAccess(parent)) {
              return ;
            }
            compiler.report(t.makeError(name, BAD_PRIVATE_GLOBAL_ACCESS, name.getString(), varSrc.getName()));
          }
        }
      }
    }
  }
  private void checkPropertyDeprecation(NodeTraversal t, Node n, Node parent) {
    if(parent.isNew()) {
      return ;
    }
    ObjectType objectType = ObjectType.cast(dereference(n.getFirstChild().getJSType()));
    String propertyName = n.getLastChild().getString();
    if(objectType != null) {
      String deprecationInfo = getPropertyDeprecationInfo(objectType, propertyName);
      if(deprecationInfo != null && shouldEmitDeprecationWarning(t, n, parent)) {
        if(!deprecationInfo.isEmpty()) {
          compiler.report(t.makeError(n, DEPRECATED_PROP_REASON, propertyName, validator.getReadableJSTypeName(n.getFirstChild(), true), deprecationInfo));
        }
        else {
          compiler.report(t.makeError(n, DEPRECATED_PROP, propertyName, validator.getReadableJSTypeName(n.getFirstChild(), true)));
        }
      }
    }
  }
  private void checkPropertyVisibility(NodeTraversal t, Node getprop, Node parent) {
    ObjectType objectType = ObjectType.cast(dereference(getprop.getFirstChild().getJSType()));
    String propertyName = getprop.getLastChild().getString();
    if(objectType != null) {
      boolean isOverride = parent.getJSDocInfo() != null && parent.isAssign() && parent.getFirstChild() == getprop;
      if(isOverride) {
        objectType = objectType.getImplicitPrototype();
      }
      JSDocInfo docInfo = null;
      for(; objectType != null; objectType = objectType.getImplicitPrototype()) {
        docInfo = objectType.getOwnPropertyJSDocInfo(propertyName);
        if(docInfo != null && docInfo.getVisibility() != Visibility.INHERITED) {
          break ;
        }
      }
      if(objectType == null) {
        return ;
      }
      String referenceSource = getprop.getSourceFileName();
      String definingSource = docInfo.getSourceName();
      boolean sameInput = referenceSource != null && referenceSource.equals(definingSource);
      Visibility visibility = docInfo.getVisibility();
      JSType ownerType = normalizeClassType(objectType);
      if(isOverride) {
        JSDocInfo overridingInfo = parent.getJSDocInfo();
        Visibility overridingVisibility = overridingInfo == null ? Visibility.INHERITED : overridingInfo.getVisibility();
        if(visibility == Visibility.PRIVATE && !sameInput) {
          compiler.report(t.makeError(getprop, PRIVATE_OVERRIDE, objectType.toString()));
        }
        else 
          if(overridingVisibility != Visibility.INHERITED && overridingVisibility != visibility) {
            compiler.report(t.makeError(getprop, VISIBILITY_MISMATCH, visibility.name(), objectType.toString(), overridingVisibility.name()));
          }
      }
      else {
        if(sameInput) {
          return ;
        }
        else 
          if(visibility == Visibility.PRIVATE && (currentClass == null || !ownerType.isEquivalentTo(currentClass))) {
            if(docInfo.isConstructor() && isValidPrivateConstructorAccess(parent)) {
              return ;
            }
            compiler.report(t.makeError(getprop, BAD_PRIVATE_PROPERTY_ACCESS, propertyName, validator.getReadableJSTypeName(getprop.getFirstChild(), true)));
          }
          else 
            if(visibility == Visibility.PROTECTED) {
              if(currentClass == null || !currentClass.isSubtype(ownerType)) {
                compiler.report(t.makeError(getprop, BAD_PROTECTED_PROPERTY_ACCESS, propertyName, validator.getReadableJSTypeName(getprop.getFirstChild(), true)));
              }
            }
      }
    }
  }
  @Override() public void enterScope(NodeTraversal t) {
    if(!t.inGlobalScope()) {
      Node n = t.getScopeRoot();
      Node parent = n.getParent();
      if(isDeprecatedFunction(n, parent)) {
        deprecatedDepth++;
      }
      if(methodDepth == 0) {
        currentClass = getClassOfMethod(n, parent);
      }
      methodDepth++;
    }
  }
  @Override() public void exitScope(NodeTraversal t) {
    if(!t.inGlobalScope()) {
      Node n = t.getScopeRoot();
      Node parent = n.getParent();
      if(isDeprecatedFunction(n, parent)) {
        deprecatedDepth--;
      }
      methodDepth--;
      if(methodDepth == 0) {
        currentClass = null;
      }
    }
  }
  @Override() public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    NodeTraversal.traverse(compiler, scriptRoot, this);
  }
  @Override() public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, this);
  }
  @Override() public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()){
      case Token.NAME:
      checkNameDeprecation(t, n, parent);
      checkNameVisibility(t, n, parent);
      break ;
      case Token.GETPROP:
      checkPropertyDeprecation(t, n, parent);
      checkPropertyVisibility(t, n, parent);
      checkConstantProperty(t, n);
      break ;
      case Token.NEW:
      checkConstructorDeprecation(t, n, parent);
      break ;
      case Token.FUNCTION:
      checkFinalClassOverrides(t, n, parent);
      break ;
    }
  }
}