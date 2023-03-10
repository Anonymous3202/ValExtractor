package com.google.javascript.jscomp;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.NodeTraversal.ScopedCallback;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TokenStream;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

class MakeDeclaredNamesUnique implements NodeTraversal.ScopedCallback  {
  final public static String ARGUMENTS = "arguments";
  private Deque<Renamer> nameStack = new ArrayDeque<Renamer>();
  final private Renamer rootRenamer;
  MakeDeclaredNamesUnique() {
    this(new ContextualRenamer());
  }
  MakeDeclaredNamesUnique(Renamer renamer) {
    super();
    this.rootRenamer = renamer;
  }
  static CompilerPass getContextualRenameInverter(AbstractCompiler compiler) {
    return new ContextualRenameInverter(compiler);
  }
  private String getReplacementName(String oldName) {
    for (Renamer names : nameStack) {
      String newName = names.getReplacementName(oldName);
      if(newName != null) {
        return newName;
      }
    }
    return null;
  }
  @Override() public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()){
      case Token.FUNCTION:
      {
        Renamer renamer = nameStack.peek().forChildScope();
        String name = n.getFirstChild().getString();
        if(name != null && !name.isEmpty() && parent != null && !NodeUtil.isFunctionDeclaration(n)) {
          renamer.addDeclaredName(name);
        }
        nameStack.push(renamer);
      }
      break ;
      case Token.PARAM_LIST:
      {
        Renamer renamer = nameStack.peek().forChildScope();
        for(com.google.javascript.rhino.Node c = n.getFirstChild(); c != null; c = c.getNext()) {
          String name = c.getString();
          renamer.addDeclaredName(name);
        }
        Node functionBody = n.getNext();
        findDeclaredNames(functionBody, null, renamer);
        nameStack.push(renamer);
      }
      break ;
      case Token.CATCH:
      {
        Renamer renamer = nameStack.peek().forChildScope();
        String name = n.getFirstChild().getString();
        renamer.addDeclaredName(name);
        nameStack.push(renamer);
      }
      break ;
    }
    return true;
  }
  @Override() public void enterScope(NodeTraversal t) {
    Node declarationRoot = t.getScopeRoot();
    Renamer renamer;
    if(nameStack.isEmpty()) {
      Preconditions.checkState(!declarationRoot.isFunction() || !(rootRenamer instanceof ContextualRenamer));
      Preconditions.checkState(t.inGlobalScope());
      renamer = rootRenamer;
    }
    else {
      renamer = nameStack.peek().forChildScope();
    }
    if(!declarationRoot.isFunction()) {
      findDeclaredNames(declarationRoot, null, renamer);
    }
    nameStack.push(renamer);
  }
  @Override() public void exitScope(NodeTraversal t) {
    if(!t.inGlobalScope()) {
      nameStack.pop();
    }
  }
  private void findDeclaredNames(Node n, Node parent, Renamer renamer) {
    if(parent == null || !parent.isFunction() || n == parent.getFirstChild()) {
      if(NodeUtil.isVarDeclaration(n)) {
        renamer.addDeclaredName(n.getString());
      }
      else 
        if(NodeUtil.isFunctionDeclaration(n)) {
          Node var_234 = n.getFirstChild();
          Node nameNode = var_234;
          renamer.addDeclaredName(nameNode.getString());
        }
      for(com.google.javascript.rhino.Node c = n.getFirstChild(); c != null; c = c.getNext()) {
        findDeclaredNames(c, n, renamer);
      }
    }
  }
  @Override() public void visit(NodeTraversal t, Node n, Node parent) {
    switch (n.getType()){
      case Token.NAME:
      String newName = getReplacementName(n.getString());
      if(newName != null) {
        Renamer renamer = nameStack.peek();
        if(renamer.stripConstIfReplaced()) {
          n.removeProp(Node.IS_CONSTANT_NAME);
        }
        n.setString(newName);
        t.getCompiler().reportCodeChange();
      }
      break ;
      case Token.FUNCTION:
      nameStack.pop();
      nameStack.pop();
      break ;
      case Token.PARAM_LIST:
      break ;
      case Token.CATCH:
      nameStack.pop();
      break ;
    }
  }
  
  static class BoilerplateRenamer extends ContextualRenamer  {
    final private Supplier<String> uniqueIdSupplier;
    final private String idPrefix;
    BoilerplateRenamer(Supplier<String> uniqueIdSupplier, String idPrefix) {
      super();
      this.uniqueIdSupplier = uniqueIdSupplier;
      this.idPrefix = idPrefix;
    }
    @Override() public Renamer forChildScope() {
      return new InlineRenamer(uniqueIdSupplier, idPrefix, false);
    }
  }
  
  static class ContextualRenameInverter implements ScopedCallback, CompilerPass  {
    final private AbstractCompiler compiler;
    private Set<String> referencedNames = ImmutableSet.of();
    private Deque<Set<String>> referenceStack = new ArrayDeque<Set<String>>();
    private Map<String, List<Node>> nameMap = Maps.newHashMap();
    private ContextualRenameInverter(AbstractCompiler compiler) {
      super();
      this.compiler = compiler;
    }
    private String findReplacementName(String name) {
      String original = getOrginalName(name);
      String newName = original;
      int i = 0;
      while(!isValidName(newName)){
        newName = original + ContextualRenamer.UNIQUE_ID_SEPARATOR + String.valueOf(i++);
      }
      return newName;
    }
    public static String getOrginalName(String name) {
      int index = indexOfSeparator(name);
      return (index == -1) ? name : name.substring(0, index);
    }
    private boolean containsSeparator(String name) {
      return name.indexOf(ContextualRenamer.UNIQUE_ID_SEPARATOR) != -1;
    }
    private boolean isValidName(String name) {
      if(TokenStream.isJSIdentifier(name) && !referencedNames.contains(name) && !name.equals(ARGUMENTS)) {
        return true;
      }
      return false;
    }
    @Override() public boolean shouldTraverse(NodeTraversal t, Node n, Node parent) {
      return true;
    }
    private static int indexOfSeparator(String name) {
      return name.lastIndexOf(ContextualRenamer.UNIQUE_ID_SEPARATOR);
    }
    private void addCandidateNameReference(String name, Node n) {
      List<Node> nodes = nameMap.get(name);
      if(null == nodes) {
        nodes = Lists.newLinkedList();
        nameMap.put(name, nodes);
      }
      nodes.add(n);
    }
    @Override() public void enterScope(NodeTraversal t) {
      if(t.inGlobalScope()) {
        return ;
      }
      referenceStack.push(referencedNames);
      referencedNames = Sets.newHashSet();
    }
    @Override() public void exitScope(NodeTraversal t) {
      if(t.inGlobalScope()) {
        return ;
      }
      for(java.util.Iterator<com.google.javascript.jscomp.Scope.Var> it = t.getScope().getVars(); it.hasNext(); ) {
        Var v = it.next();
        handleScopeVar(v);
      }
      Set<String> current = referencedNames;
      referencedNames = referenceStack.pop();
      if(!referenceStack.isEmpty()) {
        referencedNames.addAll(current);
      }
    }
    void handleScopeVar(Var v) {
      String name = v.getName();
      if(containsSeparator(name) && !getOrginalName(name).isEmpty()) {
        String newName = findReplacementName(name);
        referencedNames.remove(name);
        referencedNames.add(newName);
        List<Node> references = nameMap.get(name);
        Preconditions.checkState(references != null);
        for (Node n : references) {
          Preconditions.checkState(n.isName());
          n.setString(newName);
        }
        compiler.reportCodeChange();
        nameMap.remove(name);
      }
    }
    @Override() public void process(Node externs, Node js) {
      NodeTraversal.traverse(compiler, js, this);
    }
    @Override() public void visit(NodeTraversal t, Node node, Node parent) {
      if(t.inGlobalScope()) {
        return ;
      }
      if(NodeUtil.isReferenceName(node)) {
        String name = node.getString();
        referencedNames.add(name);
        if(containsSeparator(name)) {
          addCandidateNameReference(name, node);
        }
      }
    }
  }
  
  static class ContextualRenamer implements Renamer  {
    final private Multiset<String> nameUsage;
    final private Map<String, String> declarations = Maps.newHashMap();
    final private boolean global;
    final static String UNIQUE_ID_SEPARATOR = "$$";
    ContextualRenamer() {
      super();
      this.global = true;
      nameUsage = HashMultiset.create();
    }
    private ContextualRenamer(Multiset<String> nameUsage) {
      super();
      this.global = false;
      this.nameUsage = nameUsage;
    }
    @Override() public Renamer forChildScope() {
      return new ContextualRenamer(nameUsage);
    }
    @Override() public String getReplacementName(String oldName) {
      return declarations.get(oldName);
    }
    private String getUniqueName(String name, int id) {
      return name + UNIQUE_ID_SEPARATOR + id;
    }
    @Override() public boolean stripConstIfReplaced() {
      return false;
    }
    private int incrementNameCount(String name) {
      return nameUsage.add(name, 1);
    }
    @Override() public void addDeclaredName(String name) {
      if(!name.equals(ARGUMENTS)) {
        if(global) {
          reserveName(name);
        }
        else {
          if(!declarations.containsKey(name)) {
            int id = incrementNameCount(name);
            String newName = null;
            if(id != 0) {
              newName = getUniqueName(name, id);
            }
            declarations.put(name, newName);
          }
        }
      }
    }
    private void reserveName(String name) {
      nameUsage.setCount(name, 0, 1);
    }
  }
  
  static class InlineRenamer implements Renamer  {
    final private Map<String, String> declarations = Maps.newHashMap();
    final private Supplier<String> uniqueIdSupplier;
    final private String idPrefix;
    final private boolean removeConstness;
    InlineRenamer(Supplier<String> uniqueIdSupplier, String idPrefix, boolean removeConstness) {
      super();
      this.uniqueIdSupplier = uniqueIdSupplier;
      Preconditions.checkArgument(!idPrefix.isEmpty());
      this.idPrefix = idPrefix;
      this.removeConstness = removeConstness;
    }
    @Override() public Renamer forChildScope() {
      return new InlineRenamer(uniqueIdSupplier, idPrefix, removeConstness);
    }
    @Override() public String getReplacementName(String oldName) {
      return declarations.get(oldName);
    }
    private String getUniqueName(String name) {
      if(name.isEmpty()) {
        return name;
      }
      if(name.indexOf(ContextualRenamer.UNIQUE_ID_SEPARATOR) != -1) {
        name = name.substring(0, name.lastIndexOf(ContextualRenamer.UNIQUE_ID_SEPARATOR));
      }
      return name + ContextualRenamer.UNIQUE_ID_SEPARATOR + idPrefix + uniqueIdSupplier.get();
    }
    @Override() public boolean stripConstIfReplaced() {
      return removeConstness;
    }
    @Override() public void addDeclaredName(String name) {
      Preconditions.checkState(!name.equals(ARGUMENTS));
      if(!declarations.containsKey(name)) {
        declarations.put(name, getUniqueName(name));
      }
    }
  }
  
  interface Renamer  {
    Renamer forChildScope();
    String getReplacementName(String oldName);
    boolean stripConstIfReplaced();
    void addDeclaredName(String name);
  }
  
  static class WhitelistedRenamer implements Renamer  {
    private Renamer delegate;
    private Set<String> whitelist;
    WhitelistedRenamer(Renamer delegate, Set<String> whitelist) {
      super();
      this.delegate = delegate;
      this.whitelist = whitelist;
    }
    @Override() public Renamer forChildScope() {
      return new WhitelistedRenamer(delegate.forChildScope(), whitelist);
    }
    @Override() public String getReplacementName(String oldName) {
      return whitelist.contains(oldName) ? delegate.getReplacementName(oldName) : null;
    }
    @Override() public boolean stripConstIfReplaced() {
      return delegate.stripConstIfReplaced();
    }
    @Override() public void addDeclaredName(String name) {
      if(whitelist.contains(name)) {
        delegate.addDeclaredName(name);
      }
    }
  }
}