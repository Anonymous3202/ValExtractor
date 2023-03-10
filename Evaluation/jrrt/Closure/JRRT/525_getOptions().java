package com.google.javascript.jscomp;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.io.CharStreams;
import com.google.javascript.jscomp.CompilerOptions.DevMode;
import com.google.javascript.jscomp.CompilerOptions.LanguageMode;
import com.google.javascript.jscomp.ReferenceCollectingCallback.ReferenceCollection;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.jscomp.deps.SortedDependencies;
import com.google.javascript.jscomp.deps.SortedDependencies.CircularDependencyException;
import com.google.javascript.jscomp.deps.SortedDependencies.MissingProvideException;
import com.google.javascript.jscomp.parsing.Config;
import com.google.javascript.jscomp.parsing.ParserRunner;
import com.google.javascript.jscomp.type.ChainableReverseAbstractInterpreter;
import com.google.javascript.jscomp.type.ClosureReverseAbstractInterpreter;
import com.google.javascript.jscomp.type.ReverseAbstractInterpreter;
import com.google.javascript.jscomp.type.SemanticReverseAbstractInterpreter;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.head.ErrorReporter;
import com.google.javascript.rhino.head.ast.AstRoot;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;

public class Compiler extends AbstractCompiler  {
  final static String SINGLETON_MODULE_NAME = "[singleton]";
  final static DiagnosticType MODULE_DEPENDENCY_ERROR = DiagnosticType.error("JSC_MODULE_DEPENDENCY_ERROR", "Bad dependency: {0} -> {1}. " + "Modules must be listed in dependency order.");
  final static DiagnosticType MISSING_ENTRY_ERROR = DiagnosticType.error("JSC_MISSING_ENTRY_ERROR", "required entry point \"{0}\" never provided");
  final private static String CONFIG_RESOURCE = "com.google.javascript.jscomp.parsing.ParserConfig";
  CompilerOptions options = null;
  private PassConfig passes = null;
  private List<CompilerInput> externs;
  private List<JSModule> modules;
  private JSModuleGraph moduleGraph;
  private List<CompilerInput> inputs;
  private ErrorManager errorManager;
  private WarningsGuard warningsGuard;
  final private Map<String, Node> injectedLibraries = Maps.newLinkedHashMap();
  Node externsRoot;
  Node jsRoot;
  Node externAndJsRoot;
  private Map<InputId, CompilerInput> inputsById;
  private SourceMap sourceMap;
  private String externExports = null;
  private int uniqueNameId = 0;
  private boolean hasRegExpGlobalReferences = true;
  private FunctionInformationMap functionInformationMap;
  final private StringBuilder debugLog = new StringBuilder();
  CodingConvention defaultCodingConvention = new ClosureCodingConvention();
  private JSTypeRegistry typeRegistry;
  private Config parserConfig = null;
  private ReverseAbstractInterpreter abstractInterpreter;
  private TypeValidator typeValidator;
  public PerformanceTracker tracker;
  final private com.google.javascript.rhino.ErrorReporter oldErrorReporter = RhinoErrorReporter.forOldRhino(this);
  final private ErrorReporter defaultErrorReporter = RhinoErrorReporter.forNewRhino(this);
  final public static DiagnosticType OPTIMIZE_LOOP_ERROR = DiagnosticType.error("JSC_OPTIMIZE_LOOP_ERROR", "Exceeded max number of optimization iterations: {0}");
  final public static DiagnosticType MOTION_ITERATIONS_ERROR = DiagnosticType.error("JSC_OPTIMIZE_LOOP_ERROR", "Exceeded max number of code motion iterations: {0}");
  final private static long COMPILER_STACK_SIZE = (1 << 21);
  final private static ExecutorService compilerExecutor = Executors.newCachedThreadPool(new ThreadFactory() {
      @Override() public Thread newThread(Runnable r) {
        return new Thread(null, r, "jscompiler", COMPILER_STACK_SIZE);
      }
  });
  private Thread compilerThread = null;
  private boolean useThreads = true;
  final private static Logger logger = Logger.getLogger("com.google.javascript.jscomp");
  final private PrintStream outStream;
  private GlobalVarReferenceMap globalRefMap = null;
  private volatile double progress = 0.0D;
  private String lastPassName;
  final private static DiagnosticType EMPTY_MODULE_LIST_ERROR = DiagnosticType.error("JSC_EMPTY_MODULE_LIST_ERROR", "At least one module must be provided");
  final private static DiagnosticType EMPTY_ROOT_MODULE_ERROR = DiagnosticType.error("JSC_EMPTY_ROOT_MODULE_ERROR", "Root module \'{0}\' must contain at least one source code input");
  final static DiagnosticType DUPLICATE_INPUT = DiagnosticType.error("JSC_DUPLICATE_INPUT", "Duplicate input: {0}");
  final static DiagnosticType DUPLICATE_EXTERN_INPUT = DiagnosticType.error("JSC_DUPLICATE_EXTERN_INPUT", "Duplicate extern input: {0}");
  final private PassFactory sanityCheck = new PassFactory("sanityCheck", false) {
      @Override() protected CompilerPass create(AbstractCompiler compiler) {
        return new SanityCheck(compiler);
      }
  };
  private Tracer currentTracer = null;
  private String currentPassName = null;
  private int syntheticCodeId = 0;
  final protected CodeChangeHandler.RecentChange recentChange = new CodeChangeHandler.RecentChange();
  final private List<CodeChangeHandler> codeChangeHandlers = Lists.<CodeChangeHandler>newArrayList();
  final static String SYNTHETIC_EXTERNS = "{SyntheticVarsDeclar}";
  private CompilerInput synthesizedExternsInput = null;
  public Compiler() {
    this((PrintStream)null);
  }
  public Compiler(ErrorManager errorManager) {
    this();
    setErrorManager(errorManager);
  }
  public Compiler(PrintStream stream) {
    super();
    addChangeHandler(recentChange);
    outStream = stream;
  }
  @Override() public AstRoot getOldParseTreeByName(String sourceName) {
    return null;
  }
  @Override() public CheckLevel getErrorLevel(JSError error) {
    Preconditions.checkNotNull(options);
    return warningsGuard.level(error);
  }
  @Override() public CodingConvention getCodingConvention() {
    CodingConvention convention = options.getCodingConvention();
    convention = convention != null ? convention : defaultCodingConvention;
    return convention;
  }
  @Override() public CompilerInput getInput(InputId id) {
    return inputsById.get(id);
  }
  @Override() CompilerInput getSynthesizedExternsInput() {
    if(synthesizedExternsInput == null) {
      synthesizedExternsInput = newExternInput(SYNTHETIC_EXTERNS);
    }
    return synthesizedExternsInput;
  }
  @Override() public CompilerInput newExternInput(String name) {
    SourceAst ast = new SyntheticAst(name);
    if(inputsById.containsKey(ast.getInputId())) {
      throw new IllegalArgumentException("Conflicting externs name: " + name);
    }
    CompilerInput input = new CompilerInput(ast, true);
    putCompilerInput(input.getInputId(), input);
    externsRoot.addChildToFront(ast.getAstRoot(this));
    externs.add(0, input);
    return input;
  }
  private CompilerInput putCompilerInput(InputId id, CompilerInput input) {
    input.setCompiler(this);
    return inputsById.put(id, input);
  }
  CompilerOptions getOptions() {
    return options;
  }
  protected CompilerOptions newCompilerOptions() {
    return new CompilerOptions();
  }
  @Override() Config getParserConfig() {
    if(parserConfig == null) {
      Config.LanguageMode mode;
      switch (options.getLanguageIn()){
        case ECMASCRIPT3:
        mode = Config.LanguageMode.ECMASCRIPT3;
        break ;
        case ECMASCRIPT5:
        mode = Config.LanguageMode.ECMASCRIPT5;
        break ;
        case ECMASCRIPT5_STRICT:
        mode = Config.LanguageMode.ECMASCRIPT5_STRICT;
        break ;
        default:
        throw new IllegalStateException("unexpected language mode");
      }
      parserConfig = ParserRunner.createConfig(isIdeMode(), mode, acceptConstKeyword(), options.extraAnnotationNames);
    }
    return parserConfig;
  }
  ControlFlowGraph<Node> computeCFG() {
    logger.fine("Computing Control Flow Graph");
    Tracer tracer = newTracer("computeCFG");
    ControlFlowAnalysis cfa = new ControlFlowAnalysis(this, true, false);
    process(cfa);
    stopTracer(tracer, "computeCFG");
    return cfa.getCfg();
  }
  @Override() CssRenamingMap getCssRenamingMap() {
    return options.cssRenamingMap;
  }
  @SuppressWarnings(value = {"unchecked", }) DefaultPassConfig ensureDefaultPassConfig() {
    PassConfig passes = getPassConfig().getBasePassConfig();
    Preconditions.checkState(passes instanceof DefaultPassConfig, "PassConfigs must eventually delegate to the DefaultPassConfig");
    return (DefaultPassConfig)passes;
  }
  protected DiagnosticGroups getDiagnosticGroups() {
    return new DiagnosticGroups();
  }
  @Override() public ErrorManager getErrorManager() {
    if(options == null) {
      initOptions(newCompilerOptions());
    }
    return errorManager;
  }
  @Override() ErrorReporter getDefaultErrorReporter() {
    return defaultErrorReporter;
  }
  FunctionInformationMap getFunctionalInformationMap() {
    return functionInformationMap;
  }
  @Override() GlobalVarReferenceMap getGlobalVarReferences() {
    return globalRefMap;
  }
  public IntermediateState getState() {
    IntermediateState state = new IntermediateState();
    state.externsRoot = externsRoot;
    state.jsRoot = jsRoot;
    state.externs = externs;
    state.inputs = inputs;
    state.modules = modules;
    state.passConfigState = getPassConfig().getIntermediateState();
    state.typeRegistry = typeRegistry;
    state.lifeCycleStage = getLifeCycleStage();
    state.injectedLibraries = Maps.newLinkedHashMap(injectedLibraries);
    return state;
  }
  public JSError[] getErrors() {
    return errorManager.getErrors();
  }
  public JSError[] getMessages() {
    return getErrors();
  }
  public JSError[] getWarnings() {
    return errorManager.getWarnings();
  }
  JSModuleGraph getDegenerateModuleGraph() {
    return moduleGraph == null ? new JSModuleGraph(modules) : moduleGraph;
  }
  @Override() JSModuleGraph getModuleGraph() {
    return moduleGraph;
  }
  @Override() public JSTypeRegistry getTypeRegistry() {
    if(typeRegistry == null) {
      typeRegistry = new JSTypeRegistry(oldErrorReporter, options.looseTypes);
    }
    return typeRegistry;
  }
  public LanguageMode languageMode() {
    return options.getLanguageIn();
  }
  private static List<CompilerInput> getAllInputsFromModules(List<JSModule> modules) {
    List<CompilerInput> inputs = Lists.newArrayList();
    Map<String, JSModule> inputMap = Maps.newHashMap();
    for (JSModule module : modules) {
      for (CompilerInput input : module.getInputs()) {
        String inputName = input.getName();
        inputs.add(input);
        inputMap.put(inputName, module);
      }
    }
    return inputs;
  }
  @VisibleForTesting() List<CompilerInput> getExternsForTesting() {
    return externs;
  }
  List<CompilerInput> getExternsInOrder() {
    return Collections.<CompilerInput>unmodifiableList(externs);
  }
  @VisibleForTesting() List<CompilerInput> getInputsForTesting() {
    return inputs;
  }
  @Override() List<CompilerInput> getInputsInOrder() {
    return Collections.<CompilerInput>unmodifiableList(inputs);
  }
  private  <T extends com.google.javascript.jscomp.SourceFile> List<CompilerInput> makeCompilerInput(List<T> files, boolean isExtern) {
    List<CompilerInput> inputs = Lists.newArrayList();
    for (T file : files) {
      inputs.add(new CompilerInput(file, isExtern));
    }
    return inputs;
  }
  public Map<InputId, CompilerInput> getInputsById() {
    return Collections.unmodifiableMap(inputsById);
  }
  @Override() public MemoizedScopeCreator getTypedScopeCreator() {
    return getPassConfig().getTypedScopeCreator();
  }
  private MessageFormatter createMessageFormatter() {
    boolean colorize = options.shouldColorizeErrorOutput();
    return options.errorFormat.toFormatter(this, colorize);
  }
  @Override() Node ensureLibraryInjected(String resourceName) {
    if(injectedLibraries.containsKey(resourceName)) {
      return null;
    }
    boolean isBase = "base".equals(resourceName);
    if(!isBase) {
      ensureLibraryInjected("base");
    }
    Node firstChild = loadLibraryCode(resourceName).removeChildren();
    Node lastChild = firstChild.getLastSibling();
    Node parent = getNodeForCodeInsertion(null);
    if(isBase) {
      parent.addChildrenToFront(firstChild);
    }
    else {
      parent.addChildrenAfter(firstChild, injectedLibraries.get("base"));
    }
    reportCodeChange();
    injectedLibraries.put(resourceName, lastChild);
    return lastChild;
  }
  @Override() Node getNodeForCodeInsertion(JSModule module) {
    if(module == null) {
      if(inputs.isEmpty()) {
        throw new IllegalStateException("No inputs");
      }
      return inputs.get(0).getAstRoot(this);
    }
    List<CompilerInput> moduleInputs = module.getInputs();
    if(moduleInputs.size() > 0) {
      return moduleInputs.get(0).getAstRoot(this);
    }
    throw new IllegalStateException("Root module has no inputs");
  }
  @Override() public Node getRoot() {
    return externAndJsRoot;
  }
  @VisibleForTesting() Node loadLibraryCode(String resourceName) {
    String originalCode;
    try {
      originalCode = CharStreams.toString(new InputStreamReader(Compiler.class.getResourceAsStream(String.format("js/%s.js", resourceName)), Charsets.UTF_8));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    return Normalize.parseAndNormalizeSyntheticCode(this, originalCode, String.format("jscomp_%s_", resourceName));
  }
  public Node parse(SourceFile file) {
    initCompilerOptionsIfTesting();
    addToDebugLog("Parsing: " + file.getName());
    return new JsAst(file).getAstRoot(this);
  }
  Node parseInputs() {
    boolean devMode = options.devMode != DevMode.OFF;
    if(externsRoot != null) {
      externsRoot.detachChildren();
    }
    if(jsRoot != null) {
      jsRoot.detachChildren();
    }
    jsRoot = IR.block();
    jsRoot.setIsSyntheticBlock(true);
    externsRoot = IR.block();
    externsRoot.setIsSyntheticBlock(true);
    externAndJsRoot = IR.block(externsRoot, jsRoot);
    externAndJsRoot.setIsSyntheticBlock(true);
    if(options.tracer.isOn()) {
      tracker = new PerformanceTracker(jsRoot, options.tracer);
      addChangeHandler(tracker.getCodeChangeHandler());
    }
    Tracer tracer = newTracer("parseInputs");
    try {
      for (CompilerInput input : externs) {
        Node n = input.getAstRoot(this);
        if(hasErrors()) {
          return null;
        }
        externsRoot.addChildToBack(n);
      }
      if(options.transformAMDToCJSModules || options.processCommonJSModules) {
        processAMDAndCommonJSModules();
      }
      hoistExterns(externsRoot);
      boolean staleInputs = false;
      if(options.dependencyOptions.needsManagement()) {
        for (CompilerInput input : inputs) {
          for (String provide : input.getProvides()) {
            getTypeRegistry().forwardDeclareType(provide);
          }
        }
        try {
          inputs = (moduleGraph == null ? new JSModuleGraph(modules) : moduleGraph).manageDependencies(options.dependencyOptions, inputs);
          staleInputs = true;
        }
        catch (CircularDependencyException e) {
          report(JSError.make(JSModule.CIRCULAR_DEPENDENCY_ERROR, e.getMessage()));
          if(hasErrors()) {
            return null;
          }
        }
        catch (MissingProvideException e) {
          report(JSError.make(MISSING_ENTRY_ERROR, e.getMessage()));
          if(hasErrors()) {
            return null;
          }
        }
      }
      hoistNoCompileFiles();
      if(staleInputs) {
        repartitionInputs();
      }
      for (CompilerInput input : inputs) {
        Node n = input.getAstRoot(this);
        if(n == null) {
          continue ;
        }
        if(devMode) {
          runSanityCheck();
          if(hasErrors()) {
            return null;
          }
        }
        if(options.sourceMapOutputPath != null || options.nameReferenceReportPath != null) {
          SourceInformationAnnotator sia = new SourceInformationAnnotator(input.getName(), options.devMode != DevMode.OFF);
          NodeTraversal.traverse(this, n, sia);
        }
        jsRoot.addChildToBack(n);
      }
      if(hasErrors()) {
        return null;
      }
      return externAndJsRoot;
    }
    finally {
      stopTracer(tracer, "parseInputs");
    }
  }
  @Override() Node parseSyntheticCode(String js) {
    CompilerInput input = new CompilerInput(SourceFile.fromCode(" [synthetic:" + (++syntheticCodeId) + "] ", js));
    putCompilerInput(input.getInputId(), input);
    return input.getAstRoot(this);
  }
  @Override() Node parseSyntheticCode(String fileName, String js) {
    initCompilerOptionsIfTesting();
    return parse(SourceFile.fromCode(fileName, js));
  }
  @Override() Node parseTestCode(String js) {
    initCompilerOptionsIfTesting();
    CompilerInput input = new CompilerInput(SourceFile.fromCode("[testcode]", js));
    if(inputsById == null) {
      inputsById = Maps.newHashMap();
    }
    putCompilerInput(input.getInputId(), input);
    return input.getAstRoot(this);
  }
  PassConfig createPassConfigInternal() {
    return new DefaultPassConfig(options);
  }
  private PassConfig getCleanupPassConfig() {
    return new CleanupPasses(getOptions());
  }
  PassConfig getPassConfig() {
    if(passes == null) {
      passes = createPassConfigInternal();
    }
    return passes;
  }
  @Override() public Region getSourceRegion(String sourceName, int lineNumber) {
    if(lineNumber < 1) {
      return null;
    }
    SourceFile input = getSourceFileByName(sourceName);
    if(input != null) {
      return input.getRegion(lineNumber);
    }
    return null;
  }
  private Result compile() {
    return runInCompilerThread(new Callable<Result>() {
        @Override() public Result call() throws Exception {
          compileInternal();
          return getResult();
        }
    });
  }
  @Deprecated() public Result compile(JSSourceFile extern, JSModule[] modules, CompilerOptions options) {
    return compileModules(Lists.newArrayList(extern), Lists.newArrayList(modules), options);
  }
  @Deprecated() public Result compile(JSSourceFile[] externs, JSModule[] modules, CompilerOptions options) {
    return compileModules(Lists.<SourceFile>newArrayList(externs), Lists.<JSModule>newArrayList(modules), options);
  }
  @Deprecated() public Result compile(JSSourceFile[] externs, JSSourceFile[] inputs, CompilerOptions options) {
    return compile(Lists.<SourceFile>newArrayList(externs), Lists.<SourceFile>newArrayList(inputs), options);
  }
  @Deprecated() public Result compile(SourceFile extern, JSSourceFile[] input, CompilerOptions options) {
    return compile(Lists.newArrayList(extern), Lists.newArrayList(input), options);
  }
  public Result compile(SourceFile extern, SourceFile input, CompilerOptions options) {
    return compile(Lists.newArrayList(extern), Lists.newArrayList(input), options);
  }
  public  <T1 extends com.google.javascript.jscomp.SourceFile, T2 extends com.google.javascript.jscomp.SourceFile> Result compile(List<T1> externs, List<T2> inputs, CompilerOptions options) {
    Preconditions.checkState(jsRoot == null);
    try {
      init(externs, inputs, options);
      if(hasErrors()) {
        return getResult();
      }
      return compile();
    }
    finally {
      Tracer t = newTracer("generateReport");
      errorManager.generateReport();
      stopTracer(t, "generateReport");
    }
  }
  public  <T extends com.google.javascript.jscomp.SourceFile> Result compileModules(List<T> externs, List<JSModule> modules, CompilerOptions options) {
    Preconditions.checkState(jsRoot == null);
    try {
      initModules(externs, modules, options);
      if(hasErrors()) {
        return getResult();
      }
      return compile();
    }
    finally {
      Tracer t = newTracer("generateReport");
      errorManager.generateReport();
      stopTracer(t, "generateReport");
    }
  }
  public Result getResult() {
    PassConfig.State state = getPassConfig().getIntermediateState();
    return new Result(getErrors(), getWarnings(), debugLog.toString(), state.variableMap, state.propertyMap, state.anonymousFunctionNameMap, state.stringMap, functionInformationMap, sourceMap, externExports, state.cssNames, state.idGeneratorMap);
  }
  @Override() public ReverseAbstractInterpreter getReverseAbstractInterpreter() {
    if(abstractInterpreter == null) {
      ChainableReverseAbstractInterpreter interpreter = new SemanticReverseAbstractInterpreter(getCodingConvention(), getTypeRegistry());
      if(options.closurePass) {
        interpreter = new ClosureReverseAbstractInterpreter(getCodingConvention(), getTypeRegistry()).append(interpreter).getFirst();
      }
      abstractInterpreter = interpreter;
    }
    return abstractInterpreter;
  }
  @Override() public Scope getTopScope() {
    return getPassConfig().getTopScope();
  }
  @Override() SourceFile getSourceFileByName(String sourceName) {
    if(sourceName != null) {
      CompilerInput input = inputsById.get(new InputId(sourceName));
      if(input != null) {
        return input.getSourceFile();
      }
    }
    return null;
  }
  public SourceMap getSourceMap() {
    return sourceMap;
  }
  static String createFillFileName(String moduleName) {
    return "[" + moduleName + "]";
  }
  public String getAstDotGraph() throws IOException {
    if(jsRoot != null) {
      ControlFlowAnalysis cfa = new ControlFlowAnalysis(this, true, false);
      cfa.process(null, jsRoot);
      return DotFormatter.toDot(jsRoot, cfa.getCfg());
    }
    else {
      return "";
    }
  }
  @Override() String getLastPassName() {
    return lastPassName;
  }
  public static String getReleaseDate() {
    ResourceBundle config = ResourceBundle.getBundle(CONFIG_RESOURCE);
    return config.getString("compiler.date");
  }
  public static String getReleaseVersion() {
    ResourceBundle config = ResourceBundle.getBundle(CONFIG_RESOURCE);
    return config.getString("compiler.version");
  }
  @Override() public String getSourceLine(String sourceName, int lineNumber) {
    if(lineNumber < 1) {
      return null;
    }
    SourceFile input = getSourceFileByName(sourceName);
    if(input != null) {
      return input.getLine(lineNumber);
    }
    return null;
  }
  public String toSource() {
    return runInCompilerThread(new Callable<String>() {
        @Override() public String call() throws Exception {
          Tracer tracer = newTracer("toSource");
          try {
            CodeBuilder cb = new CodeBuilder();
            if(jsRoot != null) {
              int i = 0;
              for(com.google.javascript.rhino.Node scriptNode = jsRoot.getFirstChild(); scriptNode != null; scriptNode = scriptNode.getNext()) {
                toSource(cb, i++, scriptNode);
              }
            }
            return cb.toString();
          }
          finally {
            stopTracer(tracer, "toSource");
          }
        }
    });
  }
  public String toSource(final JSModule module) {
    return runInCompilerThread(new Callable<String>() {
        @Override() public String call() throws Exception {
          List<CompilerInput> inputs = module.getInputs();
          int numInputs = inputs.size();
          if(numInputs == 0) {
            return "";
          }
          CodeBuilder cb = new CodeBuilder();
          for(int i = 0; i < numInputs; i++) {
            Node scriptNode = inputs.get(i).getAstRoot(Compiler.this);
            if(scriptNode == null) {
              throw new IllegalArgumentException("Bad module: " + module.getName());
            }
            toSource(cb, i, scriptNode);
          }
          return cb.toString();
        }
    });
  }
  @Override() String toSource(Node n) {
    initCompilerOptionsIfTesting();
    return toSource(n, null, true);
  }
  private String toSource(Node n, SourceMap sourceMap, boolean firstOutput) {
    CodePrinter.Builder builder = new CodePrinter.Builder(n);
    builder.setCompilerOptions(options);
    builder.setSourceMap(sourceMap);
    builder.setTagAsStrict(firstOutput && options.getLanguageOut() == LanguageMode.ECMASCRIPT5_STRICT);
    return builder.build();
  }
  public String[] toSourceArray() {
    return runInCompilerThread(new Callable<String[]>() {
        @Override() public String[] call() throws Exception {
          Tracer tracer = newTracer("toSourceArray");
          try {
            int numInputs = inputs.size();
            String[] sources = new String[numInputs];
            CodeBuilder cb = new CodeBuilder();
            for(int i = 0; i < numInputs; i++) {
              Node scriptNode = inputs.get(i).getAstRoot(Compiler.this);
              cb.reset();
              toSource(cb, i, scriptNode);
              sources[i] = cb.toString();
            }
            return sources;
          }
          finally {
            stopTracer(tracer, "toSourceArray");
          }
        }
    });
  }
  public String[] toSourceArray(final JSModule module) {
    return runInCompilerThread(new Callable<String[]>() {
        @Override() public String[] call() throws Exception {
          List<CompilerInput> inputs = module.getInputs();
          int numInputs = inputs.size();
          if(numInputs == 0) {
            return new String[0];
          }
          String[] sources = new String[numInputs];
          CodeBuilder cb = new CodeBuilder();
          for(int i = 0; i < numInputs; i++) {
            Node scriptNode = inputs.get(i).getAstRoot(Compiler.this);
            if(scriptNode == null) {
              throw new IllegalArgumentException("Bad module input: " + inputs.get(i).getName());
            }
            cb.reset();
            toSource(cb, i, scriptNode);
            sources[i] = cb.toString();
          }
          return sources;
        }
    });
  }
  @Override() Supplier<String> getUniqueNameIdSupplier() {
    final Compiler self = this;
    return new Supplier<String>() {
        @Override() public String get() {
          return String.valueOf(self.nextUniqueNameId());
        }
    };
  }
  public SymbolTable buildKnownSymbolTable() {
    SymbolTable symbolTable = new SymbolTable(getTypeRegistry());
    MemoizedScopeCreator typedScopeCreator = getTypedScopeCreator();
    if(typedScopeCreator != null) {
      symbolTable.addScopes(typedScopeCreator.getAllMemoizedScopes());
      symbolTable.addSymbolsFrom(typedScopeCreator);
    }
    else {
      symbolTable.findScopes(this, externsRoot, jsRoot);
    }
    GlobalNamespace globalNamespace = ensureDefaultPassConfig().getGlobalNamespace();
    if(globalNamespace != null) {
      symbolTable.addSymbolsFrom(globalNamespace);
    }
    ReferenceCollectingCallback refCollector = new ReferenceCollectingCallback(this, ReferenceCollectingCallback.DO_NOTHING_BEHAVIOR);
    NodeTraversal.traverse(this, getRoot(), refCollector);
    symbolTable.addSymbolsFrom(refCollector);
    PreprocessorSymbolTable preprocessorSymbolTable = ensureDefaultPassConfig().getPreprocessorSymbolTable();
    if(preprocessorSymbolTable != null) {
      symbolTable.addSymbolsFrom(preprocessorSymbolTable);
    }
    symbolTable.fillNamespaceReferences();
    symbolTable.fillPropertyScopes();
    symbolTable.fillThisReferences(this, externsRoot, jsRoot);
    symbolTable.fillPropertySymbols(this, externsRoot, jsRoot);
    symbolTable.fillJSDocInfo(this, externsRoot, jsRoot);
    return symbolTable;
  }
  @SuppressWarnings(value = {"unchecked", })  <T extends java.lang.Object> T runInCompilerThread(final Callable<T> callable) {
    final boolean dumpTraceReport = options != null && options.tracer.isOn();
    T result = null;
    final Throwable[] exception = new Throwable[1];
    Callable<T> bootCompilerThread = new Callable<T>() {
        @Override() public T call() {
          try {
            compilerThread = Thread.currentThread();
            if(dumpTraceReport) {
              Tracer.initCurrentThreadTrace();
            }
            return callable.call();
          }
          catch (Throwable e) {
            exception[0] = e;
          }
          finally {
            compilerThread = null;
            if(dumpTraceReport) {
              Tracer.logAndClearCurrentThreadTrace();
              tracker.outputTracerReport(outStream == null ? System.out : outStream);
            }
          }
          return null;
        }
    };
    Preconditions.checkState(compilerThread == null || compilerThread == Thread.currentThread(), "Please do not share the Compiler across threads");
    if(useThreads && compilerThread == null) {
      try {
        result = compilerExecutor.submit(bootCompilerThread).get();
      }
      catch (InterruptedException e) {
        throw Throwables.propagate(e);
      }
      catch (ExecutionException e) {
        throw Throwables.propagate(e);
      }
    }
    else {
      try {
        result = callable.call();
      }
      catch (Exception e) {
        exception[0] = e;
      }
    }
    if(exception[0] != null) {
      throw new RuntimeException(exception[0]);
    }
    return result;
  }
  Tracer newTracer(String passName) {
    String comment = passName + (recentChange.hasCodeChanged() ? " on recently changed AST" : "");
    if(options.tracer.isOn()) {
      tracker.recordPassStart(passName);
    }
    return new Tracer("Compiler", comment);
  }
  @Override() TypeValidator getTypeValidator() {
    if(typeValidator == null) {
      typeValidator = new TypeValidator(this);
    }
    return typeValidator;
  }
  VariableMap getPropertyMap() {
    return getPassConfig().getIntermediateState().propertyMap;
  }
  VariableMap getVariableMap() {
    return getPassConfig().getIntermediateState().variableMap;
  }
  @Override() public boolean acceptConstKeyword() {
    return options.acceptConstKeyword;
  }
  @Override() public boolean acceptEcmaScript5() {
    switch (options.getLanguageIn()){
      case ECMASCRIPT5:
      case ECMASCRIPT5_STRICT:
      return true;
      case ECMASCRIPT3:
      return false;
    }
    throw new IllegalStateException("unexpected language mode");
  }
  boolean addNewSourceAst(JsAst ast) {
    CompilerInput oldInput = getInput(ast.getInputId());
    if(oldInput != null) {
      throw new IllegalStateException("Input already exists: " + ast.getInputId().getIdName());
    }
    Node newRoot = ast.getAstRoot(this);
    if(newRoot == null) {
      return false;
    }
    getRoot().getLastChild().addChildToBack(newRoot);
    CompilerInput newInput = new CompilerInput(ast);
    if(moduleGraph == null && !modules.isEmpty()) {
      modules.get(0).add(newInput);
    }
    putCompilerInput(ast.getInputId(), newInput);
    return true;
  }
  @Override() boolean areNodesEqualForInlining(Node n1, Node n2) {
    if(options.ambiguateProperties || options.disambiguateProperties) {
      return n1.isEquivalentToTyped(n2);
    }
    else {
      return n1.isEquivalentTo(n2);
    }
  }
  public boolean hasErrors() {
    return hasHaltingErrors();
  }
  @Override() boolean hasHaltingErrors() {
    return !isIdeMode() && getErrorCount() > 0;
  }
  @Override() boolean hasRegExpGlobalReferences() {
    return hasRegExpGlobalReferences;
  }
  @Override() public boolean isIdeMode() {
    return options.ideMode;
  }
  boolean isInliningForbidden() {
    return options.propertyRenaming == PropertyRenamingPolicy.HEURISTIC || options.propertyRenaming == PropertyRenamingPolicy.AGGRESSIVE_HEURISTIC;
  }
  @Override() public boolean isTypeCheckingEnabled() {
    return options.checkTypes;
  }
  boolean precheck() {
    return true;
  }
  boolean replaceIncrementalSourceAst(JsAst ast) {
    CompilerInput oldInput = getInput(ast.getInputId());
    Preconditions.checkNotNull(oldInput, "No input to replace: %s", ast.getInputId().getIdName());
    Node newRoot = ast.getAstRoot(this);
    if(newRoot == null) {
      return false;
    }
    Node oldRoot = oldInput.getAstRoot(this);
    if(oldRoot != null) {
      oldRoot.getParent().replaceChild(oldRoot, newRoot);
    }
    else {
      getRoot().getLastChild().addChildToBack(newRoot);
    }
    CompilerInput newInput = new CompilerInput(ast);
    putCompilerInput(ast.getInputId(), newInput);
    JSModule module = oldInput.getModule();
    if(module != null) {
      module.addAfter(newInput, oldInput);
      module.remove(oldInput);
    }
    Preconditions.checkState(newInput.getInputId().equals(oldInput.getInputId()));
    InputId inputIdOnAst = newInput.getAstRoot(this).getInputId();
    Preconditions.checkState(newInput.getInputId().equals(inputIdOnAst));
    inputs.remove(oldInput);
    return true;
  }
  @Override() public double getProgress() {
    return progress;
  }
  public int getErrorCount() {
    return errorManager.getErrorCount();
  }
  public int getWarningCount() {
    return errorManager.getWarningCount();
  }
  private int nextUniqueNameId() {
    return uniqueNameId++;
  }
  @Override() void addChangeHandler(CodeChangeHandler handler) {
    codeChangeHandlers.add(handler);
  }
  void addIncrementalSourceAst(JsAst ast) {
    InputId id = ast.getInputId();
    Preconditions.checkState(getInput(id) == null, "Duplicate input %s", id.getIdName());
    putCompilerInput(id, new CompilerInput(ast));
  }
  public void addNewScript(JsAst ast) {
    if(!addNewSourceAst(ast)) {
      return ;
    }
    Node emptyScript = new Node(Token.SCRIPT);
    InputId inputId = ast.getInputId();
    emptyScript.setInputId(inputId);
    emptyScript.setStaticSourceFile(SourceFile.fromCode(inputId.getIdName(), ""));
    processNewScript(ast, emptyScript);
  }
  @Override() void addToDebugLog(String str) {
    debugLog.append(str);
    debugLog.append('\n');
    logger.fine(str);
  }
  public void check() {
    runCustomPasses(CustomPassExecutionTime.BEFORE_CHECKS);
    PhaseOptimizer phaseOptimizer = new PhaseOptimizer(this, tracker, new PhaseOptimizer.ProgressRange(getProgress(), 1.0D));
    if(options.devMode == DevMode.EVERY_PASS) {
      phaseOptimizer.setSanityCheck(sanityCheck);
    }
    phaseOptimizer.consume(getPassConfig().getChecks());
    phaseOptimizer.process(externsRoot, jsRoot);
    if(hasErrors()) {
      return ;
    }
    if(options.nameAnonymousFunctionsOnly) {
      return ;
    }
    if(options.removeTryCatchFinally) {
      removeTryCatchFinally();
    }
    if(options.getTweakProcessing().shouldStrip() || !options.stripTypes.isEmpty() || !options.stripNameSuffixes.isEmpty() || !options.stripTypePrefixes.isEmpty() || !options.stripNamePrefixes.isEmpty()) {
      stripCode(options.stripTypes, options.stripNameSuffixes, options.stripTypePrefixes, options.stripNamePrefixes);
    }
    runCustomPasses(CustomPassExecutionTime.BEFORE_OPTIMIZATIONS);
  }
  private void checkFirstModule(List<JSModule> modules) {
    if(modules.isEmpty()) {
      report(JSError.make(EMPTY_MODULE_LIST_ERROR));
    }
    else 
      if(modules.get(0).getInputs().isEmpty() && modules.size() > 1) {
        report(JSError.make(EMPTY_ROOT_MODULE_ERROR, modules.get(0).getName()));
      }
  }
  private void compileInternal() {
    setProgress(0.0D, null);
    parse();
    setProgress(0.15D, "parse");
    if(hasErrors()) {
      return ;
    }
    if(!precheck()) {
      return ;
    }
    if(options.nameAnonymousFunctionsOnly) {
      check();
      return ;
    }
    if(!options.skipAllPasses) {
      check();
      if(hasErrors()) {
        return ;
      }
      if(options.isExternExportsEnabled() || options.externExportsPath != null) {
        externExports();
      }
      if(!options.ideMode) {
        optimize();
      }
    }
    if(options.recordFunctionInformation) {
      recordFunctionInformation();
    }
    if(options.devMode == DevMode.START_AND_END) {
      runSanityCheck();
    }
    setProgress(1.0D, "recordFunctionInformation");
  }
  public void disableThreads() {
    useThreads = false;
  }
  void endPass() {
    Preconditions.checkState(currentTracer != null, "Tracer should not be null at the end of a pass.");
    stopTracer(currentTracer, currentPassName);
    String passToCheck = currentPassName;
    currentPassName = null;
    currentTracer = null;
    maybeSanityCheck();
  }
  private void externExports() {
    logger.fine("Creating extern file for exports");
    startPass("externExports");
    ExternExportsPass pass = new ExternExportsPass(this);
    process(pass);
    externExports = pass.getGeneratedExterns();
    endPass();
  }
  private static void fillEmptyModules(List<JSModule> modules) {
    for (JSModule module : modules) {
      if(module.getInputs().isEmpty()) {
        module.add(SourceFile.fromCode(createFillFileName(module.getName()), ""));
      }
    }
  }
  private void hoistExterns(Node externsRoot) {
    boolean staleInputs = false;
    for (CompilerInput input : inputs) {
      if(options.dependencyOptions.needsManagement()) {
        if(!input.getProvides().isEmpty() || !input.getRequires().isEmpty()) {
          continue ;
        }
      }
      Node n = input.getAstRoot(this);
      if(n == null) {
        continue ;
      }
      JSDocInfo info = n.getJSDocInfo();
      if(info != null && info.isExterns()) {
        externsRoot.addChildToBack(n);
        input.setIsExtern(true);
        input.getModule().remove(input);
        externs.add(input);
        staleInputs = true;
      }
    }
    if(staleInputs) {
      repartitionInputs();
    }
  }
  private void hoistNoCompileFiles() {
    boolean staleInputs = false;
    for (CompilerInput input : inputs) {
      Node n = input.getAstRoot(this);
      if(n == null) {
        continue ;
      }
      JSDocInfo info = n.getJSDocInfo();
      if(info != null && info.isNoCompile()) {
        input.getModule().remove(input);
        staleInputs = true;
      }
    }
    if(staleInputs) {
      repartitionInputs();
    }
  }
  @Deprecated() public void init(JSSourceFile[] externs, JSModule[] modules, CompilerOptions options) {
    initModules(Lists.<SourceFile>newArrayList(externs), Lists.<JSModule>newArrayList(modules), options);
  }
  @Deprecated() public void init(JSSourceFile[] externs, JSSourceFile[] inputs, CompilerOptions options) {
    init(Lists.<JSSourceFile>newArrayList(externs), Lists.<JSSourceFile>newArrayList(inputs), options);
  }
  public  <T1 extends com.google.javascript.jscomp.SourceFile, T2 extends com.google.javascript.jscomp.SourceFile> void init(List<T1> externs, List<T2> inputs, CompilerOptions options) {
    JSModule module = new JSModule(SINGLETON_MODULE_NAME);
    for (SourceFile input : inputs) {
      module.add(input);
    }
    initModules(externs, Lists.newArrayList(module), options);
  }
  private void initBasedOnOptions() {
    if(options.sourceMapOutputPath != null) {
      sourceMap = options.sourceMapFormat.getInstance();
      sourceMap.setPrefixMappings(options.sourceMapLocationMappings);
    }
  }
  void initCompilerOptionsIfTesting() {
    if(options == null) {
      initOptions(newCompilerOptions());
    }
  }
  void initInputsByIdMap() {
    inputsById = new HashMap<InputId, CompilerInput>();
    for (CompilerInput input : externs) {
      InputId id = input.getInputId();
      CompilerInput previous = putCompilerInput(id, input);
      if(previous != null) {
        report(JSError.make(DUPLICATE_EXTERN_INPUT, input.getName()));
      }
    }
    for (CompilerInput input : inputs) {
      InputId id = input.getInputId();
      CompilerInput previous = putCompilerInput(id, input);
      if(previous != null) {
        report(JSError.make(DUPLICATE_INPUT, input.getName()));
      }
    }
  }
  public  <T extends com.google.javascript.jscomp.SourceFile> void initModules(List<T> externs, List<JSModule> modules, CompilerOptions options) {
    initOptions(options);
    checkFirstModule(modules);
    fillEmptyModules(modules);
    this.externs = makeCompilerInput(externs, true);
    this.modules = modules;
    if(modules.size() > 1) {
      try {
        this.moduleGraph = new JSModuleGraph(modules);
      }
      catch (JSModuleGraph.ModuleDependenceException e) {
        report(JSError.make(MODULE_DEPENDENCY_ERROR, e.getModule().getName(), e.getDependentModule().getName()));
        return ;
      }
    }
    else {
      this.moduleGraph = null;
    }
    this.inputs = getAllInputsFromModules(modules);
    initBasedOnOptions();
    initInputsByIdMap();
  }
  public void initOptions(CompilerOptions options) {
    this.options = options;
    if(errorManager == null) {
      if(outStream == null) {
        setErrorManager(new LoggerErrorManager(createMessageFormatter(), logger));
      }
      else {
        PrintStreamErrorManager printer = new PrintStreamErrorManager(createMessageFormatter(), outStream);
        printer.setSummaryDetailLevel(options.summaryDetailLevel);
        setErrorManager(printer);
      }
    }
    if(options.enables(DiagnosticGroups.CHECK_TYPES)) {
      options.checkTypes = true;
    }
    else 
      if(options.disables(DiagnosticGroups.CHECK_TYPES)) {
        options.checkTypes = false;
      }
      else 
        if(!options.checkTypes) {
          options.setWarningLevel(DiagnosticGroup.forType(RhinoErrorReporter.TYPE_PARSE_ERROR), CheckLevel.OFF);
        }
    if(options.checkGlobalThisLevel.isOn() && !options.disables(DiagnosticGroups.GLOBAL_THIS)) {
      options.setWarningLevel(DiagnosticGroups.GLOBAL_THIS, options.checkGlobalThisLevel);
    }
    if(options.getLanguageIn() == LanguageMode.ECMASCRIPT5_STRICT) {
      options.setWarningLevel(DiagnosticGroups.ES5_STRICT, CheckLevel.ERROR);
    }
    List<WarningsGuard> guards = Lists.newArrayList();
    guards.add(new SuppressDocWarningsGuard(getDiagnosticGroups().getRegisteredGroups()));
    guards.add(options.getWarningsGuard());
    ComposeWarningsGuard composedGuards = new ComposeWarningsGuard(guards);
    if(!options.checkSymbols && !composedGuards.enables(DiagnosticGroups.CHECK_VARIABLES)) {
      composedGuards.addGuard(new DiagnosticGroupWarningsGuard(DiagnosticGroups.CHECK_VARIABLES, CheckLevel.OFF));
    }
    this.warningsGuard = composedGuards;
  }
  private void maybeSanityCheck() {
    if(options.devMode == DevMode.EVERY_PASS) {
      runSanityCheck();
    }
  }
  public void normalize() {
    logger.fine("Normalizing");
    startPass("normalize");
    process(new Normalize(this, false));
    endPass();
  }
  public void optimize() {
    normalize();
    PhaseOptimizer phaseOptimizer = new PhaseOptimizer(this, tracker, null);
    if(options.devMode == DevMode.EVERY_PASS) {
      phaseOptimizer.setSanityCheck(sanityCheck);
    }
    phaseOptimizer.consume(getPassConfig().getOptimizations());
    phaseOptimizer.process(externsRoot, jsRoot);
  }
  public void parse() {
    parseInputs();
  }
  @Override() void prepareAst(Node root) {
    CompilerPass pass = new PrepareAst(this);
    pass.process(null, root);
  }
  @Override() void process(CompilerPass p) {
    p.process(externsRoot, jsRoot);
  }
  void processAMDAndCommonJSModules() {
    Map<String, JSModule> modulesByName = Maps.newLinkedHashMap();
    Map<CompilerInput, JSModule> modulesByInput = Maps.newLinkedHashMap();
    for (CompilerInput input : inputs) {
      input.setCompiler(this);
      Node root = input.getAstRoot(this);
      if(root == null) {
        continue ;
      }
      if(options.transformAMDToCJSModules) {
        new TransformAMDToCJSModule(this).process(null, root);
      }
      if(options.processCommonJSModules) {
        ProcessCommonJSModules cjs = new ProcessCommonJSModules(this, options.commonJSModulePathPrefix);
        cjs.process(null, root);
        JSModule m = cjs.getModule();
        if(m != null) {
          modulesByName.put(m.getName(), m);
          modulesByInput.put(input, m);
        }
      }
    }
    if(options.processCommonJSModules) {
      List<JSModule> modules = Lists.newArrayList(modulesByName.values());
      if(!modules.isEmpty()) {
        this.modules = modules;
        this.moduleGraph = new JSModuleGraph(this.modules);
      }
      for (JSModule module : modules) {
        for (CompilerInput input : module.getInputs()) {
          for (String require : input.getRequires()) {
            JSModule dependency = modulesByName.get(require);
            if(dependency == null) {
              report(JSError.make(MISSING_ENTRY_ERROR, require));
            }
            else {
              module.addDependency(dependency);
            }
          }
        }
      }
      try {
        modules = Lists.newArrayList();
        for (CompilerInput input : this.moduleGraph.manageDependencies(options.dependencyOptions, inputs)) {
          modules.add(modulesByInput.get(input));
        }
        JSModule root = new JSModule("root");
        for (JSModule m : modules) {
          m.addDependency(root);
        }
        modules.add(0, root);
        SortedDependencies<JSModule> sorter = new SortedDependencies<JSModule>(modules);
        modules = sorter.getDependenciesOf(modules, true);
        this.modules = modules;
        this.moduleGraph = new JSModuleGraph(modules);
      }
      catch (Exception e) {
        Throwables.propagate(e);
      }
    }
  }
  public void processDefines() {
    (new DefaultPassConfig(options)).processDefines.create(this).process(externsRoot, jsRoot);
  }
  private void processNewScript(JsAst ast, Node originalRoot) {
    Node js = ast.getAstRoot(this);
    Preconditions.checkNotNull(js);
    runHotSwap(originalRoot, js, this.getCleanupPassConfig());
    runHotSwapPass(null, null, ensureDefaultPassConfig().garbageCollectChecks);
    this.getTypeRegistry().clearNamedTypes();
    this.removeSyntheticVarsInput();
    runHotSwap(originalRoot, js, this.ensureDefaultPassConfig());
  }
  public void rebuildInputsFromModules() {
    inputs = getAllInputsFromModules(modules);
    initInputsByIdMap();
  }
  void recordFunctionInformation() {
    logger.fine("Recording function information");
    startPass("recordFunctionInformation");
    RecordFunctionInformation recordFunctionInfoPass = new RecordFunctionInformation(this, getPassConfig().getIntermediateState().functionNames);
    process(recordFunctionInfoPass);
    functionInformationMap = recordFunctionInfoPass.getMap();
    endPass();
  }
  @Override() void removeChangeHandler(CodeChangeHandler handler) {
    codeChangeHandlers.remove(handler);
  }
  protected void removeExternInput(InputId id) {
    CompilerInput input = getInput(id);
    if(input == null) {
      return ;
    }
    Preconditions.checkState(input.isExtern(), "Not an extern input: %s", input.getName());
    inputsById.remove(id);
    externs.remove(input);
    Node root = input.getAstRoot(this);
    if(root != null) {
      root.detachFromParent();
    }
  }
  private void removeSyntheticVarsInput() {
    String sourceName = Compiler.SYNTHETIC_EXTERNS;
    removeExternInput(new InputId(sourceName));
  }
  void removeTryCatchFinally() {
    logger.fine("Remove try/catch/finally");
    startPass("removeTryCatchFinally");
    RemoveTryCatch r = new RemoveTryCatch(this);
    process(r);
    endPass();
  }
  private void repartitionInputs() {
    fillEmptyModules(modules);
    rebuildInputsFromModules();
  }
  public void replaceScript(JsAst ast) {
    CompilerInput input = this.getInput(ast.getInputId());
    if(!replaceIncrementalSourceAst(ast)) {
      return ;
    }
    Node originalRoot = input.getAstRoot(this);
    processNewScript(ast, originalRoot);
  }
  @Override() public void report(JSError error) {
    CheckLevel level = error.getDefaultLevel();
    if(warningsGuard != null) {
      CheckLevel newLevel = warningsGuard.level(error);
      if(newLevel != null) {
        level = newLevel;
      }
    }
    if(level.isOn()) {
      CompilerOptions var_525 = getOptions();
      if(var_525.errorHandler != null) {
        getOptions().errorHandler.report(level, error);
      }
      errorManager.report(level, error);
    }
  }
  @Override() public void reportCodeChange() {
    for (CodeChangeHandler handler : codeChangeHandlers) {
      handler.reportChange();
    }
  }
  @VisibleForTesting() void resetUniqueNameId() {
    uniqueNameId = 0;
  }
  private void runCustomPasses(CustomPassExecutionTime executionTime) {
    if(options.customPasses != null) {
      Tracer t = newTracer("runCustomPasses");
      try {
        for (CompilerPass p : options.customPasses.get(executionTime)) {
          process(p);
        }
      }
      finally {
        stopTracer(t, "runCustomPasses");
      }
    }
  }
  private void runHotSwap(Node originalRoot, Node js, PassConfig passConfig) {
    for (PassFactory passFactory : passConfig.getChecks()) {
      runHotSwapPass(originalRoot, js, passFactory);
    }
  }
  private void runHotSwapPass(Node originalRoot, Node js, PassFactory passFactory) {
    HotSwapCompilerPass pass = passFactory.getHotSwapPass(this);
    if(pass != null) {
      logger.info("Performing HotSwap for pass " + passFactory.getName());
      pass.hotSwapScript(js, originalRoot);
    }
  }
  private void runSanityCheck() {
    sanityCheck.create(this).process(externsRoot, jsRoot);
  }
  @Override() void setCssRenamingMap(CssRenamingMap map) {
    options.cssRenamingMap = map;
  }
  public void setErrorManager(ErrorManager errorManager) {
    Preconditions.checkNotNull(errorManager, "the error manager cannot be null");
    this.errorManager = errorManager;
  }
  @Override() void setHasRegExpGlobalReferences(boolean references) {
    hasRegExpGlobalReferences = references;
  }
  public static void setLoggingLevel(Level level) {
    logger.setLevel(level);
  }
  @Override() public void setOldParseTree(String sourceName, AstRoot oldAst) {
  }
  public void setPassConfig(PassConfig passes) {
    Preconditions.checkNotNull(passes);
    if(this.passes != null) {
      throw new IllegalStateException("this.passes has already been assigned");
    }
    this.passes = passes;
  }
  @Override() void setProgress(double newProgress, String passName) {
    this.lastPassName = passName;
    if(newProgress > 1.0D) {
      progress = 1.0D;
    }
    else {
      progress = newProgress;
    }
  }
  public void setState(IntermediateState state) {
    externsRoot = state.externsRoot;
    jsRoot = state.jsRoot;
    externs = state.externs;
    inputs = state.inputs;
    modules = state.modules;
    passes = createPassConfigInternal();
    getPassConfig().setIntermediateState(state.passConfigState);
    typeRegistry = state.typeRegistry;
    setLifeCycleStage(state.lifeCycleStage);
    injectedLibraries.clear();
    injectedLibraries.putAll(state.injectedLibraries);
  }
  void startPass(String passName) {
    Preconditions.checkState(currentTracer == null);
    currentPassName = passName;
    currentTracer = newTracer(passName);
  }
  void stopTracer(Tracer t, String passName) {
    long result = t.stop();
    if(options.tracer.isOn()) {
      tracker.recordPassStop(passName, result);
    }
  }
  void stripCode(Set<String> stripTypes, Set<String> stripNameSuffixes, Set<String> stripTypePrefixes, Set<String> stripNamePrefixes) {
    logger.fine("Strip code");
    startPass("stripCode");
    StripCode r = new StripCode(this, stripTypes, stripNameSuffixes, stripTypePrefixes, stripNamePrefixes);
    if(options.getTweakProcessing().shouldStrip()) {
      r.enableTweakStripping();
    }
    process(r);
    endPass();
  }
  @Override() void throwInternalError(String message, Exception cause) {
    String finalMessage = "INTERNAL COMPILER ERROR.\n" + "Please report this problem.\n" + message;
    RuntimeException e = new RuntimeException(finalMessage, cause);
    if(cause != null) {
      e.setStackTrace(cause.getStackTrace());
    }
    throw e;
  }
  public void toSource(final CodeBuilder cb, final int inputSeqNum, final Node root) {
    runInCompilerThread(new Callable<Void>() {
        @Override() public Void call() throws Exception {
          if(options.printInputDelimiter) {
            if((cb.getLength() > 0) && !cb.endsWith("\n")) {
              cb.append("\n");
            }
            Preconditions.checkState(root.isScript());
            String delimiter = options.inputDelimiter;
            String inputName = root.getInputId().getIdName();
            String sourceName = root.getSourceFileName();
            Preconditions.checkState(sourceName != null);
            Preconditions.checkState(!sourceName.isEmpty());
            delimiter = delimiter.replaceAll("%name%", Matcher.quoteReplacement(inputName)).replaceAll("%num%", String.valueOf(inputSeqNum));
            cb.append(delimiter).append("\n");
          }
          if(root.getJSDocInfo() != null && root.getJSDocInfo().getLicense() != null) {
            cb.append("/*\n").append(root.getJSDocInfo().getLicense()).append("*/\n");
          }
          if(options.sourceMapOutputPath != null) {
            sourceMap.setStartingPosition(cb.getLineIndex(), cb.getColumnIndex());
          }
          String code = toSource(root, sourceMap, inputSeqNum == 0);
          if(!code.isEmpty()) {
            cb.append(code);
            int length = code.length();
            char lastChar = code.charAt(length - 1);
            char secondLastChar = length >= 2 ? code.charAt(length - 2) : '\u0000';
            boolean hasSemiColon = lastChar == ';' || (lastChar == '\n' && secondLastChar == ';');
            if(!hasSemiColon) {
              cb.append(";");
            }
          }
          return null;
        }
    });
  }
  @Override() void updateGlobalVarReferences(Map<Var, ReferenceCollection> refMapPatch, Node collectionRoot) {
    Preconditions.checkState(collectionRoot.isScript() || collectionRoot.isBlock());
    if(globalRefMap == null) {
      globalRefMap = new GlobalVarReferenceMap(getInputsInOrder(), getExternsInOrder());
    }
    globalRefMap.updateGlobalVarReferences(refMapPatch, collectionRoot);
  }
  
  public static class CodeBuilder  {
    final private StringBuilder sb = new StringBuilder();
    private int lineCount = 0;
    private int colCount = 0;
    CodeBuilder append(String str) {
      sb.append(str);
      int index = -1;
      int lastIndex = index;
      while((index = str.indexOf('\n', index + 1)) >= 0){
        ++lineCount;
        lastIndex = index;
      }
      if(lastIndex == -1) {
        colCount += str.length();
      }
      else {
        colCount = str.length() - (lastIndex + 1);
      }
      return this;
    }
    @Override() public String toString() {
      return sb.toString();
    }
    boolean endsWith(String suffix) {
      return (sb.length() > suffix.length()) && suffix.equals(sb.substring(sb.length() - suffix.length()));
    }
    int getColumnIndex() {
      return colCount;
    }
    public int getLength() {
      return sb.length();
    }
    int getLineIndex() {
      return lineCount;
    }
    void reset() {
      sb.setLength(0);
    }
  }
  
  public static class IntermediateState implements Serializable  {
    final private static long serialVersionUID = 1L;
    Node externsRoot;
    private Node jsRoot;
    private List<CompilerInput> externs;
    private List<CompilerInput> inputs;
    private List<JSModule> modules;
    private PassConfig.State passConfigState;
    private JSTypeRegistry typeRegistry;
    private AbstractCompiler.LifeCycleStage lifeCycleStage;
    private Map<String, Node> injectedLibraries;
    private IntermediateState() {
      super();
    }
  }
}