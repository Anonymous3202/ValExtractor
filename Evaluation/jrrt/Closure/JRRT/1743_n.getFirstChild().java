package com.google.javascript.jscomp;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.jscomp.NodeTraversal.Callback;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import java.util.List;
import java.util.Set;

abstract class MethodCompilerPass implements CompilerPass  {
  final Set<String> externMethods = Sets.newHashSet();
  final Set<String> externMethodsWithoutSignatures = Sets.newHashSet();
  final Set<String> nonMethodProperties = Sets.newHashSet();
  final Multimap<String, Node> methodDefinitions = LinkedHashMultimap.create();
  final AbstractCompiler compiler;
  MethodCompilerPass(AbstractCompiler compiler) {
    super();
    this.compiler = compiler;
  }
  abstract Callback getActingCallback();
  abstract SignatureStore getSignatureStore();
  private void addPossibleSignature(String name, Node node, NodeTraversal t) {
    if(node.isFunction()) {
      addSignature(name, node, t.getSourceName());
    }
    else {
      nonMethodProperties.add(name);
    }
  }
  private void addSignature(String name, Node function, String fnSourceName) {
    if(externMethodsWithoutSignatures.contains(name)) {
      return ;
    }
    getSignatureStore().addSignature(name, function, fnSourceName);
    methodDefinitions.put(name, function);
  }
  @Override() public void process(Node externs, Node root) {
    externMethods.clear();
    externMethodsWithoutSignatures.clear();
    getSignatureStore().reset();
    methodDefinitions.clear();
    if(externs != null) {
      NodeTraversal.traverse(compiler, externs, new GetExternMethods());
    }
    List<Node> externsAndJs = Lists.newArrayList(externs, root);
    NodeTraversal.traverseRoots(compiler, Lists.newArrayList(externs, root), new GatherSignatures());
    NodeTraversal.traverseRoots(compiler, externsAndJs, getActingCallback());
  }
  
  private class GatherSignatures extends AbstractPostOrderCallback  {
    private void processPrototypeParent(NodeTraversal t, Node n) {
      switch (n.getType()){
        case Token.GETPROP:
        case Token.GETELEM:
        Node dest = n.getFirstChild().getNext();
        Node parent = n.getParent().getParent();
        if(dest.isString() && parent.isAssign()) {
          Node assignee = parent.getFirstChild().getNext();
          addPossibleSignature(dest.getString(), assignee, t);
        }
        break ;
      }
    }
    @Override() public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()){
        case Token.GETPROP:
        case Token.GETELEM:
        Node dest = n.getFirstChild().getNext();
        if(dest.isString()) {
          if(dest.getString().equals("prototype")) {
            processPrototypeParent(t, parent);
          }
          else {
            if(parent.isAssign() && parent.getFirstChild() == n) {
              addPossibleSignature(dest.getString(), n.getNext(), t);
            }
          }
        }
        break ;
        case Token.OBJECTLIT:
        for(com.google.javascript.rhino.Node key = n.getFirstChild(); key != null; key = key.getNext()) {
          switch (key.getType()){
            case Token.STRING_KEY:
            addPossibleSignature(key.getString(), key.getFirstChild(), t);
            break ;
            case Token.SETTER_DEF:
            case Token.GETTER_DEF:
            nonMethodProperties.add(key.getString());
            break ;
            default:
            throw new IllegalStateException("unexpect OBJECTLIT key: " + key);
          }
        }
        break ;
      }
    }
  }
  
  private class GetExternMethods extends AbstractPostOrderCallback  {
    @Override() public void visit(NodeTraversal t, Node n, Node parent) {
      switch (n.getType()){
        case Token.GETPROP:
        case Token.GETELEM:
        {
          Node var_1743 = n.getFirstChild();
          Node dest = var_1743.getNext();
          if(!dest.isString()) {
            return ;
          }
          String name = dest.getString();
          if(parent.isAssign() && parent.getFirstChild() == n && n.getNext().isFunction()) {
            addSignature(name, n.getNext(), t.getSourceName());
          }
          else {
            getSignatureStore().removeSignature(name);
            externMethodsWithoutSignatures.add(name);
          }
          externMethods.add(name);
        }
        break ;
        case Token.OBJECTLIT:
        {
          for(com.google.javascript.rhino.Node key = n.getFirstChild(); key != null; key = key.getNext()) {
            Node value = key.getFirstChild();
            String name = key.getString();
            if(key.isStringKey() && value.isFunction()) {
              addSignature(name, value, t.getSourceName());
            }
            else {
              getSignatureStore().removeSignature(name);
              externMethodsWithoutSignatures.add(name);
            }
            externMethods.add(name);
          }
        }
        break ;
      }
    }
  }
  
  interface SignatureStore  {
    public void addSignature(String functionName, Node functionNode, String sourceFile);
    public void removeSignature(String functionName);
    public void reset();
  }
}