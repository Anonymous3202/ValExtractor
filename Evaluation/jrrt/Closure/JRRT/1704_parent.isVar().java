package com.google.javascript.jscomp;
import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.EnumType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.ObjectType;
import javax.annotation.Nullable;

class InferJSDocInfo extends AbstractPostOrderCallback implements HotSwapCompilerPass  {
  final private AbstractCompiler compiler;
  @SuppressWarnings(value = {"unused", }) private boolean inExterns;
  InferJSDocInfo(AbstractCompiler compiler) {
    super();
    this.compiler = compiler;
  }
  private ObjectType dereferenceToObject(JSType type) {
    return ObjectType.cast(type == null ? null : type.dereference());
  }
  private void attachJSDocInfoToNominalTypeOrShape(ObjectType objType, JSDocInfo docInfo, @Nullable() String qName) {
    if(objType.isConstructor() || objType.isEnumType() || objType.isInterface()) {
      if(objType.hasReferenceName() && objType.getReferenceName().equals(qName)) {
        objType.setJSDocInfo(docInfo);
        if(objType.isConstructor() || objType.isInterface()) {
          JSType.toMaybeFunctionType(objType).getInstanceType().setJSDocInfo(docInfo);
        }
        else 
          if(objType instanceof EnumType) {
            ((EnumType)objType).getElementsType().setJSDocInfo(docInfo);
          }
      }
    }
    else 
      if(!objType.isNativeObjectType() && objType.isFunctionType()) {
        objType.setJSDocInfo(docInfo);
      }
  }
  @Override() public void hotSwapScript(Node root, Node originalRoot) {
    Preconditions.checkNotNull(root);
    Preconditions.checkState(root.isScript());
    inExterns = false;
    NodeTraversal.traverse(compiler, root, this);
  }
  @Override() public void process(Node externs, Node root) {
    if(externs != null) {
      inExterns = true;
      NodeTraversal.traverse(compiler, externs, this);
    }
    if(root != null) {
      inExterns = false;
      NodeTraversal.traverse(compiler, root, this);
    }
  }
  @Override() public void visit(NodeTraversal t, Node n, Node parent) {
    JSDocInfo docInfo;
    switch (n.getType()){
      case Token.NAME:
      if(parent == null) {
        return ;
      }
      boolean var_1704 = parent.isVar();
      if(!var_1704 && !NodeUtil.isFunctionDeclaration(parent) && !(parent.isAssign() && n == parent.getFirstChild())) {
        return ;
      }
      docInfo = n.getJSDocInfo();
      if(docInfo == null && !(parent.isVar() && !parent.hasOneChild())) {
        docInfo = parent.getJSDocInfo();
      }
      JSType varType = n.getJSType();
      if(varType == null && parent.isFunction()) {
        varType = parent.getJSType();
      }
      if(varType == null || docInfo == null) {
        return ;
      }
      ObjectType objType = dereferenceToObject(varType);
      if(objType == null || objType.getJSDocInfo() != null) {
        return ;
      }
      attachJSDocInfoToNominalTypeOrShape(objType, docInfo, n.getString());
      break ;
      case Token.GETPROP:
      if(parent.isExprResult() || (parent.isAssign() && parent.getFirstChild() == n)) {
        docInfo = n.getJSDocInfo();
        if(docInfo == null) {
          docInfo = parent.getJSDocInfo();
        }
        if(docInfo != null) {
          ObjectType lhsType = dereferenceToObject(n.getFirstChild().getJSType());
          if(lhsType != null) {
            String propName = n.getLastChild().getString();
            if(lhsType.hasOwnProperty(propName)) {
              lhsType.setPropertyJSDocInfo(propName, docInfo);
            }
            ObjectType propType = dereferenceToObject(lhsType.getPropertyType(propName));
            if(propType != null) {
              attachJSDocInfoToNominalTypeOrShape(propType, docInfo, n.getQualifiedName());
            }
          }
        }
      }
      break ;
    }
  }
}