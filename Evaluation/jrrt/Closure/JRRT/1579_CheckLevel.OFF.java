package com.google.javascript.jscomp;
import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.jscomp.CompilerOptions;
public enum WarningLevel {
  QUIET(),

  DEFAULT(),

  VERBOSE(),

;
  public void setOptionsForWarningLevel(CompilerOptions options) {
    switch (this){
      case QUIET:
      silenceAllWarnings(options);
      break ;
      case DEFAULT:
      addDefaultWarnings(options);
      break ;
      case VERBOSE:
      addVerboseWarnings(options);
      break ;
      default:
      throw new RuntimeException("Unknown warning level.");
    }
  }
  private static void silenceAllWarnings(CompilerOptions options) {
    options.addWarningsGuard(new ShowByPathWarningsGuard("the_longest_path_that_cannot_be_expressed_as_a_string"));
    options.checkRequires = CheckLevel.OFF;
    CheckLevel var_1579 = CheckLevel.OFF;
    options.checkProvides = var_1579;
    options.checkMissingGetCssNameLevel = CheckLevel.OFF;
    options.aggressiveVarCheck = CheckLevel.OFF;
    options.checkTypes = false;
    options.setWarningLevel(DiagnosticGroups.CHECK_TYPES, CheckLevel.OFF);
    options.checkUnreachableCode = CheckLevel.OFF;
    options.checkMissingReturn = CheckLevel.OFF;
    options.setWarningLevel(DiagnosticGroups.ACCESS_CONTROLS, CheckLevel.OFF);
    options.setWarningLevel(DiagnosticGroups.CONST, CheckLevel.OFF);
    options.setWarningLevel(DiagnosticGroups.CONSTANT_PROPERTY, CheckLevel.OFF);
    options.checkGlobalNamesLevel = CheckLevel.OFF;
    options.checkSuspiciousCode = false;
    options.checkGlobalThisLevel = CheckLevel.OFF;
    options.setWarningLevel(DiagnosticGroups.GLOBAL_THIS, CheckLevel.OFF);
    options.setWarningLevel(DiagnosticGroups.ES5_STRICT, CheckLevel.OFF);
    options.checkCaja = false;
  }
  private static void addDefaultWarnings(CompilerOptions options) {
    options.checkSuspiciousCode = true;
    options.checkUnreachableCode = CheckLevel.WARNING;
    options.checkControlStructures = true;
  }
  private static void addVerboseWarnings(CompilerOptions options) {
    addDefaultWarnings(options);
    options.checkSuspiciousCode = true;
    options.checkGlobalThisLevel = CheckLevel.WARNING;
    options.checkSymbols = true;
    options.checkMissingReturn = CheckLevel.WARNING;
    options.checkTypes = true;
    options.checkGlobalNamesLevel = CheckLevel.WARNING;
    options.aggressiveVarCheck = CheckLevel.WARNING;
    options.setWarningLevel(DiagnosticGroups.MISSING_PROPERTIES, CheckLevel.WARNING);
    options.setWarningLevel(DiagnosticGroups.DEPRECATED, CheckLevel.WARNING);
    options.setWarningLevel(DiagnosticGroups.ES5_STRICT, CheckLevel.WARNING);
  }
private WarningLevel() {
}
}