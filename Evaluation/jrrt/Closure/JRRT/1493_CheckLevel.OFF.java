package com.google.javascript.jscomp;
import com.google.javascript.jscomp.CompilerOptions.Reach;
public enum CompilationLevel {
  WHITESPACE_ONLY(),

  SIMPLE_OPTIMIZATIONS(),

  ADVANCED_OPTIMIZATIONS(),

;
private CompilationLevel() {
}
  public void setOptionsForCompilationLevel(CompilerOptions options) {
    switch (this){
      case WHITESPACE_ONLY:
      applyBasicCompilationOptions(options);
      break ;
      case SIMPLE_OPTIMIZATIONS:
      applySafeCompilationOptions(options);
      break ;
      case ADVANCED_OPTIMIZATIONS:
      applyFullCompilationOptions(options);
      break ;
      default:
      throw new RuntimeException("Unknown compilation level.");
    }
  }
  public void setDebugOptionsForCompilationLevel(CompilerOptions options) {
    options.anonymousFunctionNaming = AnonymousFunctionNamingPolicy.UNMAPPED;
    options.generatePseudoNames = true;
    options.removeClosureAsserts = false;
    options.shadowVariables = false;
  }
  private static void applyBasicCompilationOptions(CompilerOptions options) {
    options.skipAllCompilerPasses();
    options.setWarningLevel(DiagnosticGroups.NON_STANDARD_JSDOC, CheckLevel.OFF);
  }
  private static void applySafeCompilationOptions(CompilerOptions options) {
    options.replaceIdGenerators = false;
    options.dependencyOptions.setDependencySorting(true);
    options.closurePass = true;
    options.setRenamingPolicy(VariableRenamingPolicy.LOCAL, PropertyRenamingPolicy.OFF);
    options.shadowVariables = true;
    options.setInlineVariables(Reach.LOCAL_ONLY);
    options.flowSensitiveInlineVariables = true;
    options.setInlineFunctions(Reach.LOCAL_ONLY);
    CheckLevel var_1493 = CheckLevel.OFF;
    options.checkGlobalThisLevel = var_1493;
    options.foldConstants = true;
    options.coalesceVariableNames = true;
    options.deadAssignmentElimination = true;
    options.collapseVariableDeclarations = true;
    options.convertToDottedProperties = true;
    options.labelRenaming = true;
    options.removeDeadCode = true;
    options.optimizeArgumentsArray = true;
    options.setRemoveUnusedVariables(Reach.LOCAL_ONLY);
    options.collapseObjectLiterals = true;
    options.protectHiddenSideEffects = true;
    options.setWarningLevel(DiagnosticGroups.NON_STANDARD_JSDOC, CheckLevel.OFF);
  }
  private static void applyFullCompilationOptions(CompilerOptions options) {
    options.dependencyOptions.setDependencySorting(true);
    options.closurePass = true;
    options.foldConstants = true;
    options.coalesceVariableNames = true;
    options.deadAssignmentElimination = true;
    options.extractPrototypeMemberDeclarations = true;
    options.collapseVariableDeclarations = true;
    options.convertToDottedProperties = true;
    options.rewriteFunctionExpressions = true;
    options.labelRenaming = true;
    options.removeDeadCode = true;
    options.optimizeArgumentsArray = true;
    options.collapseObjectLiterals = true;
    options.protectHiddenSideEffects = true;
    options.removeClosureAsserts = true;
    options.aliasKeywords = true;
    options.reserveRawExports = true;
    options.setRenamingPolicy(VariableRenamingPolicy.ALL, PropertyRenamingPolicy.ALL_UNQUOTED);
    options.shadowVariables = true;
    options.removeUnusedPrototypeProperties = true;
    options.removeUnusedPrototypePropertiesInExterns = true;
    options.collapseAnonymousFunctions = true;
    options.collapseProperties = true;
    options.checkGlobalThisLevel = CheckLevel.WARNING;
    options.rewriteFunctionExpressions = true;
    options.smartNameRemoval = true;
    options.inlineConstantVars = true;
    options.setInlineFunctions(Reach.ALL);
    options.inlineGetters = true;
    options.setInlineVariables(Reach.ALL);
    options.flowSensitiveInlineVariables = true;
    options.computeFunctionSideEffects = true;
    options.setRemoveUnusedVariables(Reach.ALL);
    options.crossModuleCodeMotion = true;
    options.crossModuleMethodMotion = true;
    options.devirtualizePrototypeMethods = true;
    options.optimizeParameters = true;
    options.optimizeReturns = true;
    options.optimizeCalls = true;
    options.setWarningLevel(DiagnosticGroups.NON_STANDARD_JSDOC, CheckLevel.WARNING);
  }
  public void setTypeBasedOptimizationOptions(CompilerOptions options) {
    switch (this){
      case ADVANCED_OPTIMIZATIONS:
      options.inferTypes = true;
      options.disambiguateProperties = true;
      options.ambiguateProperties = true;
      options.inlineProperties = true;
      options.removeUnusedClassProperties = true;
      break ;
      case SIMPLE_OPTIMIZATIONS:
      break ;
      case WHITESPACE_ONLY:
      break ;
    }
  }
}