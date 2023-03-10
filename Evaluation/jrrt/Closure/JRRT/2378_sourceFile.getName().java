package com.google.javascript.jscomp.parsing;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.javascript.jscomp.parsing.Config.LanguageMode;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.head.CompilerEnvirons;
import com.google.javascript.rhino.head.Context;
import com.google.javascript.rhino.head.ErrorReporter;
import com.google.javascript.rhino.head.EvaluatorException;
import com.google.javascript.rhino.head.Parser;
import com.google.javascript.rhino.head.ast.AstRoot;
import com.google.javascript.rhino.jstype.StaticSourceFile;
import java.io.IOException;
import java.util.HashSet;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.logging.Logger;

public class ParserRunner  {
  final private static String configResource = "com.google.javascript.jscomp.parsing.ParserConfig";
  private static Set<String> annotationNames = null;
  private static Set<String> suppressionNames = null;
  private static Set<String> reservedVars = null;
  private ParserRunner() {
    super();
  }
  @Deprecated() public static Config createConfig(boolean isIdeMode) {
    return createConfig(isIdeMode, LanguageMode.ECMASCRIPT3, false);
  }
  public static Config createConfig(boolean isIdeMode, LanguageMode languageMode, boolean acceptConstKeyword) {
    return createConfig(isIdeMode, languageMode, acceptConstKeyword, null);
  }
  public static Config createConfig(boolean isIdeMode, LanguageMode languageMode, boolean acceptConstKeyword, Set<String> extraAnnotationNames) {
    initResourceConfig();
    Set<String> effectiveAnnotationNames;
    if(extraAnnotationNames == null) {
      effectiveAnnotationNames = annotationNames;
    }
    else {
      effectiveAnnotationNames = new HashSet<String>(annotationNames);
      effectiveAnnotationNames.addAll(extraAnnotationNames);
    }
    return new Config(effectiveAnnotationNames, suppressionNames, isIdeMode, languageMode, acceptConstKeyword);
  }
  public static ParseResult parse(StaticSourceFile sourceFile, String sourceString, Config config, ErrorReporter errorReporter, Logger logger) throws IOException {
    Context cx = Context.enter();
    cx.setErrorReporter(errorReporter);
    cx.setLanguageVersion(Context.VERSION_1_5);
    CompilerEnvirons compilerEnv = new CompilerEnvirons();
    compilerEnv.initFromContext(cx);
    compilerEnv.setRecordingComments(true);
    compilerEnv.setRecordingLocalJsDocComments(true);
    compilerEnv.setWarnTrailingComma(config.languageMode == LanguageMode.ECMASCRIPT3);
    boolean acceptEs5 = config.isIdeMode || config.languageMode != LanguageMode.ECMASCRIPT3;
    compilerEnv.setReservedKeywordAsIdentifier(acceptEs5);
    compilerEnv.setAllowMemberExprAsFunctionName(false);
    compilerEnv.setIdeMode(config.isIdeMode);
    compilerEnv.setRecoverFromErrors(config.isIdeMode);
    Parser p = new Parser(compilerEnv, errorReporter);
    AstRoot astRoot = null;
    try {
      String var_2378 = sourceFile.getName();
      astRoot = p.parse(sourceString, var_2378, 1);
    }
    catch (EvaluatorException e) {
      logger.info("Error parsing " + sourceFile.getName() + ": " + e.getMessage());
    }
    finally {
      Context.exit();
    }
    Node root = null;
    if(astRoot != null) {
      root = IRFactory.transformTree(astRoot, sourceFile, sourceString, config, errorReporter);
      root.setIsSyntheticBlock(true);
    }
    return new ParseResult(root, astRoot);
  }
  private static Set<String> extractList(String configProp) {
    String[] names = configProp.split(",");
    Set<String> trimmedNames = Sets.newHashSet();
    for (String name : names) {
      trimmedNames.add(name.trim());
    }
    return ImmutableSet.copyOf(trimmedNames);
  }
  public static Set<String> getReservedVars() {
    initResourceConfig();
    return reservedVars;
  }
  private static synchronized void initResourceConfig() {
    if(annotationNames != null) {
      return ;
    }
    ResourceBundle config = ResourceBundle.getBundle(configResource);
    annotationNames = extractList(config.getString("jsdoc.annotations"));
    suppressionNames = extractList(config.getString("jsdoc.suppressions"));
    reservedVars = extractList(config.getString("compiler.reserved.vars"));
  }
  
  public static class ParseResult  {
    final public Node ast;
    final public AstRoot oldAst;
    public ParseResult(Node ast, AstRoot oldAst) {
      super();
      this.ast = ast;
      this.oldAst = oldAst;
    }
  }
}