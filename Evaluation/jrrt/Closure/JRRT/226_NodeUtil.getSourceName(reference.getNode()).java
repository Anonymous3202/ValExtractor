package com.google.javascript.jscomp;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.ReferenceCollectingCallback.BasicBlock;
import com.google.javascript.jscomp.ReferenceCollectingCallback.Behavior;
import com.google.javascript.jscomp.ReferenceCollectingCallback.Reference;
import com.google.javascript.jscomp.ReferenceCollectingCallback.ReferenceMap;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.Node;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

class VariableReferenceCheck implements HotSwapCompilerPass  {
  final static DiagnosticType UNDECLARED_REFERENCE = DiagnosticType.warning("JSC_REFERENCE_BEFORE_DECLARE", "Variable referenced before declaration: {0}");
  final static DiagnosticType REDECLARED_VARIABLE = DiagnosticType.warning("JSC_REDECLARED_VARIABLE", "Redeclared variable: {0}");
  final static DiagnosticType AMBIGUOUS_FUNCTION_DECL = DiagnosticType.disabled("AMBIGUOUS_FUNCTION_DECL", "Ambiguous use of a named function: {0}.");
  final private AbstractCompiler compiler;
  final private CheckLevel checkLevel;
  final private Set<BasicBlock> blocksWithDeclarations = Sets.newHashSet();
  public VariableReferenceCheck(AbstractCompiler compiler, CheckLevel checkLevel) {
    super();
    this.compiler = compiler;
    this.checkLevel = checkLevel;
  }
  @Override() public void hotSwapScript(Node scriptRoot, Node originalRoot) {
    ReferenceCollectingCallback callback = new ReferenceCollectingCallback(compiler, new ReferenceCheckingBehavior());
    callback.hotSwapScript(scriptRoot, originalRoot);
  }
  @Override() public void process(Node externs, Node root) {
    ReferenceCollectingCallback callback = new ReferenceCollectingCallback(compiler, new ReferenceCheckingBehavior());
    callback.process(externs, root);
  }
  
  private class ReferenceCheckingBehavior implements Behavior  {
    @Override() public void afterExitScope(NodeTraversal t, ReferenceMap referenceMap) {
      for(java.util.Iterator<com.google.javascript.jscomp.Scope.Var> it = t.getScope().getVars(); it.hasNext(); ) {
        Var v = it.next();
        checkVar(t, v, referenceMap.getReferences(v).references);
      }
    }
    private void checkVar(NodeTraversal t, Var v, List<Reference> references) {
      blocksWithDeclarations.clear();
      boolean isDeclaredInScope = false;
      boolean isUnhoistedNamedFunction = false;
      Reference hoistedFn = null;
      for (Reference reference : references) {
        if(reference.isHoistedFunction()) {
          blocksWithDeclarations.add(reference.getBasicBlock());
          isDeclaredInScope = true;
          hoistedFn = reference;
          break ;
        }
        else 
          if(NodeUtil.isFunctionDeclaration(reference.getNode().getParent())) {
            isUnhoistedNamedFunction = true;
          }
      }
      for (Reference reference : references) {
        if(reference == hoistedFn) {
          continue ;
        }
        BasicBlock basicBlock = reference.getBasicBlock();
        boolean isDeclaration = reference.isDeclaration();
        boolean allowDupe = SyntacticScopeCreator.hasDuplicateDeclarationSuppression(reference.getNode(), v);
        if(isDeclaration && !allowDupe) {
          for (BasicBlock declaredBlock : blocksWithDeclarations) {
            if(declaredBlock.provablyExecutesBefore(basicBlock)) {
              String var_226 = NodeUtil.getSourceName(reference.getNode());
              String filename = var_226;
              compiler.report(JSError.make(filename, reference.getNode(), checkLevel, REDECLARED_VARIABLE, v.name));
              break ;
            }
          }
        }
        if(isUnhoistedNamedFunction && !isDeclaration && isDeclaredInScope) {
          for (BasicBlock declaredBlock : blocksWithDeclarations) {
            if(!declaredBlock.provablyExecutesBefore(basicBlock)) {
              String filename = NodeUtil.getSourceName(reference.getNode());
              compiler.report(JSError.make(filename, reference.getNode(), AMBIGUOUS_FUNCTION_DECL, v.name));
              break ;
            }
          }
        }
        if(!isDeclaration && !isDeclaredInScope) {
          if(!reference.getNode().isFromExterns()) {
            Node grandparent = reference.getGrandparent();
            if(grandparent.isName() && grandparent.getString() == v.name) {
              continue ;
            }
            if(reference.getScope() == v.scope) {
              String filename = NodeUtil.getSourceName(reference.getNode());
              compiler.report(JSError.make(filename, reference.getNode(), checkLevel, UNDECLARED_REFERENCE, v.name));
            }
          }
        }
        if(isDeclaration) {
          blocksWithDeclarations.add(basicBlock);
          isDeclaredInScope = true;
        }
      }
    }
  }
}