package com.google.javascript.jscomp;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Supplier;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.Files;
import com.google.javascript.jscomp.CompilerOptions.TweakProcessing;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.TokenStream;
import com.google.protobuf.CodedOutputStream;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import javax.annotation.Nullable;
abstract class AbstractCommandLineRunner<A extends com.google.javascript.jscomp.Compiler, B extends com.google.javascript.jscomp.CompilerOptions>  {
  final static DiagnosticType OUTPUT_SAME_AS_INPUT_ERROR = DiagnosticType.error("JSC_OUTPUT_SAME_AS_INPUT_ERROR", "Bad output file (already listed as input file): {0}");
  final private CommandLineConfig config;
  private Appendable jsOutput;
  final private PrintStream err;
  private A compiler;
  private Charset inputCharset;
  private Charset outputCharset2;
  private String legacyOutputCharset;
  private boolean testMode = false;
  private Supplier<List<SourceFile>> externsSupplierForTesting = null;
  private Supplier<List<SourceFile>> inputsSupplierForTesting = null;
  private Supplier<List<JSModule>> modulesSupplierForTesting = null;
  private Function<Integer, Boolean> exitCodeReceiverForTesting = null;
  private Map<String, String> rootRelativePathsMap = null;
  private Map<String, String> parsedModuleWrappers = null;
  final private static int NUM_RUNS_TO_DETERMINE_OPTIMAL_ORDER = 100;
  final private static String OUTPUT_MARKER = "%output%";
  final private static String OUTPUT_MARKER_JS_STRING = "%output|jsstring%";
  final private RunTimeStats runTimeStats = new RunTimeStats();
  AbstractCommandLineRunner() {
    this(System.out, System.err);
  }
  AbstractCommandLineRunner(PrintStream out, PrintStream err) {
    super();
    this.config = new CommandLineConfig();
    this.jsOutput = Preconditions.checkNotNull(out);
    this.err = Preconditions.checkNotNull(err);
  }
  abstract protected A createCompiler();
  final protected A getCompiler() {
    return compiler;
  }
  abstract protected B createOptions();
  private Charset getInputCharset() throws FlagUsageException {
    if(!config.charset.isEmpty()) {
      if(!Charset.isSupported(config.charset)) {
        throw new FlagUsageException(config.charset + " is not a valid charset name.");
      }
      return Charset.forName(config.charset);
    }
    return Charsets.UTF_8;
  }
  private Charset getOutputCharset2() throws FlagUsageException {
    if(!config.charset.isEmpty()) {
      String var_433 = config.charset;
      if(!Charset.isSupported(var_433)) {
        throw new FlagUsageException(config.charset + " is not a valid charset name.");
      }
      return Charset.forName(config.charset);
    }
    return Charsets.UTF_8;
  }
  protected CommandLineConfig getCommandLineConfig() {
    return config;
  }
  static DependencyOptions createDependencyOptions(boolean manageClosureDependencies, boolean onlyClosureDependencies, boolean processCommonJSModules, List<String> closureEntryPoints) throws FlagUsageException {
    if(onlyClosureDependencies) {
      if(closureEntryPoints.isEmpty()) {
        throw new FlagUsageException("When only_closure_dependencies is " + "on, you must specify at least one closure_entry_point");
      }
      return new DependencyOptions().setDependencyPruning(true).setDependencySorting(true).setMoocherDropping(true).setEntryPoints(closureEntryPoints);
    }
    else 
      if(processCommonJSModules) {
        return new DependencyOptions().setDependencyPruning(false).setDependencySorting(true).setMoocherDropping(false).setEntryPoints(closureEntryPoints);
      }
      else 
        if(manageClosureDependencies || closureEntryPoints.size() > 0) {
          return new DependencyOptions().setDependencyPruning(true).setDependencySorting(true).setMoocherDropping(false).setEntryPoints(closureEntryPoints);
        }
    return null;
  }
  protected DiagnosticGroups getDiagnosticGroups() {
    if(compiler == null) {
      return new DiagnosticGroups();
    }
    return compiler.getDiagnosticGroups();
  }
  Function<String, String> getJavascriptEscaper() {
    throw new UnsupportedOperationException("SourceCodeEscapers is not in the standard release of Guava yet :(");
  }
  List<JSModule> createJsModules(List<String> specs, List<String> jsFiles) throws FlagUsageException, IOException {
    if(isInTestMode()) {
      return modulesSupplierForTesting.get();
    }
    Preconditions.checkState(specs != null);
    Preconditions.checkState(!specs.isEmpty());
    Preconditions.checkState(jsFiles != null);
    final int totalNumJsFiles = jsFiles.size();
    int nextJsFileIndex = 0;
    Map<String, JSModule> modulesByName = Maps.newLinkedHashMap();
    for (String spec : specs) {
      String[] parts = spec.split(":");
      if(parts.length < 2 || parts.length > 4) {
        throw new FlagUsageException("Expected 2-4 colon-delimited parts in " + "module spec: " + spec);
      }
      String name = parts[0];
      checkModuleName(name);
      if(modulesByName.containsKey(name)) {
        throw new FlagUsageException("Duplicate module name: " + name);
      }
      JSModule module = new JSModule(name);
      int numJsFiles = -1;
      try {
        numJsFiles = Integer.parseInt(parts[1]);
      }
      catch (NumberFormatException ignored) {
        numJsFiles = -1;
      }
      if(numJsFiles < 0) {
        throw new FlagUsageException("Invalid JS file count \'" + parts[1] + "\' for module: " + name);
      }
      if(nextJsFileIndex + numJsFiles > totalNumJsFiles) {
        throw new FlagUsageException("Not enough JS files specified. Expected " + (nextJsFileIndex + numJsFiles - totalNumJsFiles) + " more in module:" + name);
      }
      List<String> moduleJsFiles = jsFiles.subList(nextJsFileIndex, nextJsFileIndex + numJsFiles);
      for (SourceFile input : createInputs(moduleJsFiles, false)) {
        module.add(input);
      }
      nextJsFileIndex += numJsFiles;
      if(parts.length > 2) {
        String depList = parts[2];
        if(depList.length() > 0) {
          String[] deps = depList.split(",");
          for (String dep : deps) {
            JSModule other = modulesByName.get(dep);
            if(other == null) {
              throw new FlagUsageException("Module \'" + name + "\' depends on unknown module \'" + dep + "\'. Be sure to list modules in dependency order.");
            }
            module.addDependency(other);
          }
        }
      }
      modulesByName.put(name, module);
    }
    if(nextJsFileIndex < totalNumJsFiles) {
      throw new FlagUsageException("Too many JS files specified. Expected " + nextJsFileIndex + " but found " + totalNumJsFiles);
    }
    return Lists.newArrayList(modulesByName.values());
  }
  private List<SourceFile> createExternInputs(List<String> files) throws FlagUsageException, IOException {
    if(files.isEmpty()) {
      return ImmutableList.of(SourceFile.fromCode("/dev/null", ""));
    }
    try {
      return createInputs(files, false);
    }
    catch (FlagUsageException e) {
      throw new FlagUsageException("Bad --externs flag. " + e.getMessage());
    }
  }
  protected List<SourceFile> createExterns() throws FlagUsageException, IOException {
    return isInTestMode() ? externsSupplierForTesting.get() : createExternInputs(config.externs);
  }
  protected List<SourceFile> createInputs(List<String> files, boolean allowStdIn) throws FlagUsageException, IOException {
    List<SourceFile> inputs = new ArrayList<SourceFile>(files.size());
    boolean usingStdin = false;
    for (String filename : files) {
      if(!"-".equals(filename)) {
        SourceFile newFile = SourceFile.fromFile(filename, inputCharset);
        inputs.add(newFile);
      }
      else {
        if(!allowStdIn) {
          throw new FlagUsageException("Can\'t specify stdin.");
        }
        if(usingStdin) {
          throw new FlagUsageException("Can\'t specify stdin twice.");
        }
        if(!config.outputManifests.isEmpty()) {
          throw new FlagUsageException("Manifest files cannot be generated " + "when the input is from stdin.");
        }
        if(!config.outputBundles.isEmpty()) {
          throw new FlagUsageException("Bundle files cannot be generated " + "when the input is from stdin.");
        }
        inputs.add(SourceFile.fromInputStream("stdin", System.in));
        usingStdin = true;
      }
    }
    return inputs;
  }
  private List<SourceFile> createSourceInputs(List<String> files) throws FlagUsageException, IOException {
    if(isInTestMode()) {
      return inputsSupplierForTesting.get();
    }
    if(files.isEmpty()) {
      files = Collections.singletonList("-");
    }
    try {
      return createInputs(files, true);
    }
    catch (FlagUsageException e) {
      throw new FlagUsageException("Bad --js flag. " + e.getMessage());
    }
  }
  private Map<String, String> constructRootRelativePathsMap() {
    Map<String, String> rootRelativePathsMap = Maps.newLinkedHashMap();
    for (String mapString : config.manifestMaps) {
      int colonIndex = mapString.indexOf(':');
      Preconditions.checkState(colonIndex > 0);
      String execPath = mapString.substring(0, colonIndex);
      String rootRelativePath = mapString.substring(colonIndex + 1);
      Preconditions.checkState(rootRelativePath.indexOf(':') == -1);
      rootRelativePathsMap.put(execPath, rootRelativePath);
    }
    return rootRelativePathsMap;
  }
  static Map<String, String> parseModuleWrappers(List<String> specs, List<JSModule> modules) throws FlagUsageException {
    Preconditions.checkState(specs != null);
    Map<String, String> wrappers = Maps.newHashMapWithExpectedSize(modules.size());
    for (JSModule m : modules) {
      wrappers.put(m.getName(), "");
    }
    for (String spec : specs) {
      int pos = spec.indexOf(':');
      if(pos == -1) {
        throw new FlagUsageException("Expected module wrapper to have " + "<name>:<wrapper> format: " + spec);
      }
      String name = spec.substring(0, pos);
      if(!wrappers.containsKey(name)) {
        throw new FlagUsageException("Unknown module: \'" + name + "\'");
      }
      String wrapper = spec.substring(pos + 1);
      if(!wrapper.contains("%s")) {
        throw new FlagUsageException("No %s placeholder in module wrapper: \'" + wrapper + "\'");
      }
      wrappers.put(name, wrapper);
    }
    return wrappers;
  }
  protected OutputStream filenameToOutputStream(String fileName) throws IOException {
    if(fileName == null) {
      return null;
    }
    return new FileOutputStream(fileName);
  }
  protected PrintStream getErrorPrintStream() {
    return err;
  }
  private String expandCommandLinePath(String path, JSModule forModule) {
    String sub;
    if(forModule != null) {
      sub = config.moduleOutputPathPrefix + forModule.getName() + ".js";
    }
    else 
      if(!config.module.isEmpty()) {
        sub = config.moduleOutputPathPrefix;
      }
      else {
        sub = config.jsOutputFile;
      }
    return path.replace("%outname%", sub);
  }
  @VisibleForTesting() String expandSourceMapPath(B options, JSModule forModule) {
    if(Strings.isNullOrEmpty(options.sourceMapOutputPath)) {
      return null;
    }
    return expandCommandLinePath(options.sourceMapOutputPath, forModule);
  }
  private String getLegacyOutputCharset() throws FlagUsageException {
    if(!config.charset.isEmpty()) {
      if(!Charset.isSupported(config.charset)) {
        throw new FlagUsageException(config.charset + " is not a valid charset name.");
      }
      return config.charset;
    }
    return "US-ASCII";
  }
  private String getMapPath(String outputFile) {
    String basePath = "";
    if(outputFile.equals("")) {
      if(!config.moduleOutputPathPrefix.equals("")) {
        basePath = config.moduleOutputPathPrefix;
      }
      else {
        basePath = "jscompiler";
      }
    }
    else {
      File file = new File(outputFile);
      String outputFileName = file.getName();
      if(outputFileName.endsWith(".js")) {
        outputFileName = outputFileName.substring(0, outputFileName.length() - 3);
      }
      basePath = file.getParent() + File.separatorChar + outputFileName;
    }
    return basePath;
  }
  private String getModuleOutputFileName(JSModule m) {
    return config.moduleOutputPathPrefix + m.getName() + ".js";
  }
  private Writer fileNameToLegacyOutputWriter(String fileName) throws IOException {
    if(fileName == null) {
      return null;
    }
    if(testMode) {
      return new StringWriter();
    }
    return streamToLegacyOutputWriter(filenameToOutputStream(fileName));
  }
  private Writer fileNameToOutputWriter2(String fileName) throws IOException {
    if(fileName == null) {
      return null;
    }
    if(testMode) {
      return new StringWriter();
    }
    return streamToOutputWriter2(filenameToOutputStream(fileName));
  }
  private Writer openExternExportsStream(B options, String path) throws IOException {
    if(options.externExportsPath == null) {
      return null;
    }
    String exPath = options.externExportsPath;
    if(!exPath.contains(File.separator)) {
      File outputFile = new File(path);
      exPath = outputFile.getParent() + File.separatorChar + exPath;
    }
    return fileNameToOutputWriter2(exPath);
  }
  private Writer streamToLegacyOutputWriter(OutputStream stream) throws IOException {
    if(legacyOutputCharset == null) {
      return new BufferedWriter(new OutputStreamWriter(stream));
    }
    else {
      return new BufferedWriter(new OutputStreamWriter(stream, legacyOutputCharset));
    }
  }
  private Writer streamToOutputWriter2(OutputStream stream) {
    if(outputCharset2 == null) {
      return new BufferedWriter(new OutputStreamWriter(stream));
    }
    else {
      return new BufferedWriter(new OutputStreamWriter(stream, outputCharset2));
    }
  }
  protected boolean isInTestMode() {
    return testMode;
  }
  private boolean shouldGenerateMapPerModule(B options) {
    return options.sourceMapOutputPath != null && options.sourceMapOutputPath.contains("%outname%");
  }
  private boolean shouldGenerateOutputPerModule(String output) {
    return !config.module.isEmpty() && output != null && output.contains("%outname%");
  }
  protected int doRun() throws FlagUsageException, IOException {
    Compiler.setLoggingLevel(Level.parse(config.loggingLevel));
    List<SourceFile> externs = createExterns();
    compiler = createCompiler();
    B options = createOptions();
    List<JSModule> modules = null;
    Result result = null;
    setRunOptions(options);
    boolean writeOutputToFile = !config.jsOutputFile.isEmpty();
    List<String> outputFileNames = Lists.newArrayList();
    if(writeOutputToFile) {
      outputFileNames.add(config.jsOutputFile);
      jsOutput = fileNameToLegacyOutputWriter(config.jsOutputFile);
    }
    else 
      if(jsOutput instanceof OutputStream) {
        jsOutput = streamToLegacyOutputWriter((OutputStream)jsOutput);
      }
    List<String> jsFiles = config.js;
    List<String> moduleSpecs = config.module;
    boolean createCommonJsModules = false;
    if(options.processCommonJSModules) {
      if(moduleSpecs.size() == 1 && "auto".equals(moduleSpecs.get(0))) {
        createCommonJsModules = true;
        moduleSpecs.remove(0);
      }
    }
    if(!moduleSpecs.isEmpty()) {
      modules = createJsModules(moduleSpecs, jsFiles);
      for (JSModule m : modules) {
        outputFileNames.add(getModuleOutputFileName(m));
      }
      if(config.skipNormalOutputs) {
        compiler.initModules(externs, modules, options);
      }
      else {
        result = compiler.compileModules(externs, modules, options);
      }
    }
    else {
      List<SourceFile> inputs = createSourceInputs(jsFiles);
      if(config.skipNormalOutputs) {
        compiler.init(externs, inputs, options);
      }
      else {
        result = compiler.compile(externs, inputs, options);
      }
    }
    if(createCommonJsModules) {
      modules = Lists.newArrayList(compiler.getDegenerateModuleGraph().getAllModules());
      for (JSModule m : modules) {
        outputFileNames.add(getModuleOutputFileName(m));
      }
    }
    for (String outputFileName : outputFileNames) {
      if(compiler.getSourceFileByName(outputFileName) != null) {
        compiler.report(JSError.make(OUTPUT_SAME_AS_INPUT_ERROR, outputFileName));
        return 1;
      }
    }
    int errCode = processResults(result, modules, options);
    if(jsOutput instanceof Flushable) {
      ((Flushable)jsOutput).flush();
    }
    return errCode;
  }
  int processResults(Result result, List<JSModule> modules, B options) throws FlagUsageException, IOException {
    if(config.computePhaseOrdering) {
      return 0;
    }
    if(config.printPassGraph) {
      if(compiler.getRoot() == null) {
        return 1;
      }
      else {
        jsOutput.append(DotFormatter.toDot(compiler.getPassConfig().getPassGraph()));
        jsOutput.append('\n');
        return 0;
      }
    }
    if(config.printAst) {
      if(compiler.getRoot() == null) {
        return 1;
      }
      else {
        ControlFlowGraph<Node> cfg = compiler.computeCFG();
        DotFormatter.appendDot(compiler.getRoot().getLastChild(), cfg, jsOutput);
        jsOutput.append('\n');
        return 0;
      }
    }
    if(config.printTree) {
      if(compiler.getRoot() == null) {
        jsOutput.append("Code contains errors; no tree was generated.\n");
        return 1;
      }
      else {
        compiler.getRoot().appendStringTree(jsOutput);
        jsOutput.append("\n");
        return 0;
      }
    }
    rootRelativePathsMap = constructRootRelativePathsMap();
    if(config.skipNormalOutputs) {
      outputManifest();
      outputBundle();
      outputModuleGraphJson();
      return 0;
    }
    else 
      if(result.success) {
        outputModuleGraphJson();
        if(modules == null) {
          outputSingleBinary();
          outputSourceMap(options, config.jsOutputFile);
        }
        else {
          outputModuleBinaryAndSourceMaps(modules, options);
        }
        if(options.externExportsPath != null) {
          Writer eeOut = openExternExportsStream(options, config.jsOutputFile);
          eeOut.append(result.externExport);
          eeOut.close();
        }
        outputNameMaps(options);
        outputManifest();
        outputBundle();
      }
    return Math.min(result.errors.length, 0x7f);
  }
  protected void checkModuleName(String name) throws FlagUsageException {
    if(!TokenStream.isJSIdentifier(name)) {
      throw new FlagUsageException("Invalid module name: \'" + name + "\'");
    }
  }
  @VisibleForTesting() static void createDefineOrTweakReplacements(List<String> definitions, CompilerOptions options, boolean tweaks) {
    for (String override : definitions) {
      String[] assignment = override.split("=", 2);
      String defName = assignment[0];
      if(defName.length() > 0) {
        String defValue = assignment.length == 1 ? "true" : assignment[1];
        boolean isTrue = defValue.equals("true");
        boolean isFalse = defValue.equals("false");
        if(isTrue || isFalse) {
          if(tweaks) {
            options.setTweakToBooleanLiteral(defName, isTrue);
          }
          else {
            options.setDefineToBooleanLiteral(defName, isTrue);
          }
          continue ;
        }
        else 
          if(defValue.length() > 1 && ((defValue.charAt(0) == '\'' && defValue.charAt(defValue.length() - 1) == '\'') || (defValue.charAt(0) == '\"' && defValue.charAt(defValue.length() - 1) == '\"'))) {
            String maybeStringVal = defValue.substring(1, defValue.length() - 1);
            if(maybeStringVal.indexOf(defValue.charAt(0)) == -1) {
              if(tweaks) {
                options.setTweakToStringLiteral(defName, maybeStringVal);
              }
              else {
                options.setDefineToStringLiteral(defName, maybeStringVal);
              }
              continue ;
            }
          }
          else {
            try {
              double value = Double.parseDouble(defValue);
              if(tweaks) {
                options.setTweakToDoubleLiteral(defName, value);
              }
              else {
                options.setDefineToDoubleLiteral(defName, value);
              }
              continue ;
            }
            catch (NumberFormatException e) {
            }
          }
      }
      if(tweaks) {
        throw new RuntimeException("--tweak flag syntax invalid: " + override);
      }
      throw new RuntimeException("--define flag syntax invalid: " + override);
    }
  }
  @VisibleForTesting() void enableTestMode(Supplier<List<SourceFile>> externsSupplier, Supplier<List<SourceFile>> inputsSupplier, Supplier<List<JSModule>> modulesSupplier, Function<Integer, Boolean> exitCodeReceiver) {
    Preconditions.checkArgument(inputsSupplier == null ^ modulesSupplier == null);
    testMode = true;
    this.externsSupplierForTesting = externsSupplier;
    this.inputsSupplierForTesting = inputsSupplier;
    this.modulesSupplierForTesting = modulesSupplier;
    this.exitCodeReceiverForTesting = exitCodeReceiver;
  }
  @Deprecated() protected void initOptionsFromFlags(CompilerOptions options) {
  }
  private static void maybeCreateDirsForPath(String pathPrefix) {
    if(pathPrefix.length() > 0) {
      String dirName = pathPrefix.charAt(pathPrefix.length() - 1) == File.separatorChar ? pathPrefix.substring(0, pathPrefix.length() - 1) : new File(pathPrefix).getParent();
      if(dirName != null) {
        new File(dirName).mkdirs();
      }
    }
  }
  private void outputBundle() throws IOException {
    outputManifestOrBundle(config.outputBundles, false);
  }
  private void outputManifest() throws IOException {
    outputManifestOrBundle(config.outputManifests, true);
  }
  private void outputManifestOrBundle(List<String> outputFiles, boolean isManifest) throws IOException {
    if(outputFiles.isEmpty()) {
      return ;
    }
    for (String output : outputFiles) {
      if(output.isEmpty()) {
        continue ;
      }
      if(shouldGenerateOutputPerModule(output)) {
        JSModuleGraph graph = compiler.getDegenerateModuleGraph();
        Iterable<JSModule> modules = graph.getAllModules();
        for (JSModule module : modules) {
          Writer out = fileNameToOutputWriter2(expandCommandLinePath(output, module));
          if(isManifest) {
            printManifestTo(module.getInputs(), out);
          }
          else {
            printBundleTo(module.getInputs(), out);
          }
          out.close();
        }
      }
      else {
        Writer out = fileNameToOutputWriter2(expandCommandLinePath(output, null));
        if(config.module.isEmpty()) {
          if(isManifest) {
            printManifestTo(compiler.getInputsInOrder(), out);
          }
          else {
            printBundleTo(compiler.getInputsInOrder(), out);
          }
        }
        else {
          printModuleGraphManifestOrBundleTo(compiler.getDegenerateModuleGraph(), out, isManifest);
        }
        out.close();
      }
    }
  }
  private void outputModuleBinaryAndSourceMaps(List<JSModule> modules, B options) throws FlagUsageException, IOException {
    parsedModuleWrappers = parseModuleWrappers(config.moduleWrapper, modules);
    maybeCreateDirsForPath(config.moduleOutputPathPrefix);
    Writer mapOut = null;
    if(!shouldGenerateMapPerModule(options)) {
      mapOut = fileNameToOutputWriter2(expandSourceMapPath(options, null));
    }
    for (JSModule m : modules) {
      if(shouldGenerateMapPerModule(options)) {
        mapOut = fileNameToOutputWriter2(expandSourceMapPath(options, m));
      }
      Writer writer = fileNameToLegacyOutputWriter(getModuleOutputFileName(m));
      if(options.sourceMapOutputPath != null) {
        compiler.getSourceMap().reset();
      }
      writeModuleOutput(writer, m);
      if(options.sourceMapOutputPath != null) {
        compiler.getSourceMap().appendTo(mapOut, m.getName());
      }
      writer.close();
      if(shouldGenerateMapPerModule(options) && mapOut != null) {
        mapOut.close();
        mapOut = null;
      }
    }
    if(mapOut != null) {
      mapOut.close();
    }
  }
  private void outputModuleGraphJson() throws IOException {
    if(config.outputModuleDependencies != null && config.outputModuleDependencies != "") {
      Writer out = fileNameToOutputWriter2(config.outputModuleDependencies);
      printModuleGraphJsonTo(compiler.getDegenerateModuleGraph(), out);
      out.close();
    }
  }
  private void outputNameMaps(B options) throws FlagUsageException, IOException {
    String propertyMapOutputPath = null;
    String variableMapOutputPath = null;
    String functionInformationMapOutputPath = null;
    if(config.createNameMapFiles) {
      String basePath = getMapPath(config.jsOutputFile);
      propertyMapOutputPath = basePath + "_props_map.out";
      variableMapOutputPath = basePath + "_vars_map.out";
      functionInformationMapOutputPath = basePath + "_functions_map.out";
    }
    if(!config.variableMapOutputFile.equals("")) {
      if(variableMapOutputPath != null) {
        throw new FlagUsageException("The flags variable_map_output_file and " + "create_name_map_files cannot both be used simultaniously.");
      }
      variableMapOutputPath = config.variableMapOutputFile;
    }
    if(!config.propertyMapOutputFile.equals("")) {
      if(propertyMapOutputPath != null) {
        throw new FlagUsageException("The flags property_map_output_file and " + "create_name_map_files cannot both be used simultaniously.");
      }
      propertyMapOutputPath = config.propertyMapOutputFile;
    }
    if(variableMapOutputPath != null) {
      if(compiler.getVariableMap() != null) {
        compiler.getVariableMap().save(variableMapOutputPath);
      }
    }
    if(propertyMapOutputPath != null) {
      if(compiler.getPropertyMap() != null) {
        compiler.getPropertyMap().save(propertyMapOutputPath);
      }
    }
    if(functionInformationMapOutputPath != null) {
      if(compiler.getFunctionalInformationMap() != null) {
        OutputStream file = filenameToOutputStream(functionInformationMapOutputPath);
        CodedOutputStream outputStream = CodedOutputStream.newInstance(file);
        compiler.getFunctionalInformationMap().writeTo(outputStream);
        outputStream.flush();
        file.flush();
        file.close();
      }
    }
  }
  void outputSingleBinary() throws IOException {
    Function<String, String> escaper = null;
    String marker = OUTPUT_MARKER;
    if(config.outputWrapper.contains(OUTPUT_MARKER_JS_STRING)) {
      marker = OUTPUT_MARKER_JS_STRING;
      escaper = getJavascriptEscaper();
    }
    writeOutput(jsOutput, compiler, compiler.toSource(), config.outputWrapper, marker, escaper);
  }
  private void outputSourceMap(B options, String associatedName) throws IOException {
    if(Strings.isNullOrEmpty(options.sourceMapOutputPath)) {
      return ;
    }
    String outName = expandSourceMapPath(options, null);
    Writer out = fileNameToOutputWriter2(outName);
    compiler.getSourceMap().appendTo(out, associatedName);
    out.close();
  }
  private void printBundleTo(Iterable<CompilerInput> inputs, Appendable out) throws IOException {
    for (CompilerInput input : inputs) {
      if(input.getName().equals(Compiler.createFillFileName(Compiler.SINGLETON_MODULE_NAME))) {
        Preconditions.checkState(1 == Iterables.size(inputs));
        return ;
      }
      String rootRelativePath = rootRelativePathsMap.get(input.getName());
      String displayName = rootRelativePath != null ? rootRelativePath : input.getName();
      File file = new File(input.getName());
      out.append("//");
      out.append(displayName);
      out.append("\n");
      Files.copy(file, inputCharset, out);
      out.append("\n");
    }
  }
  private void printManifestTo(Iterable<CompilerInput> inputs, Appendable out) throws IOException {
    for (CompilerInput input : inputs) {
      String rootRelativePath = rootRelativePathsMap.get(input.getName());
      String displayName = rootRelativePath != null ? rootRelativePath : input.getName();
      out.append(displayName);
      out.append("\n");
    }
  }
  @VisibleForTesting() void printModuleGraphJsonTo(JSModuleGraph graph, Appendable out) throws IOException {
    out.append(compiler.getDegenerateModuleGraph().toJson().toString());
  }
  @VisibleForTesting() void printModuleGraphManifestOrBundleTo(JSModuleGraph graph, Appendable out, boolean isManifest) throws IOException {
    Joiner commas = Joiner.on(",");
    boolean requiresNewline = false;
    for (JSModule module : graph.getAllModules()) {
      if(requiresNewline) {
        out.append("\n");
      }
      if(isManifest) {
        String dependencies = commas.join(module.getSortedDependencyNames());
        out.append(String.format("{%s%s}\n", module.getName(), dependencies.isEmpty() ? "" : ":" + dependencies));
        printManifestTo(module.getInputs(), out);
      }
      else {
        printBundleTo(module.getInputs(), out);
      }
      requiresNewline = true;
    }
  }
  final public void run() {
    int result = 0;
    int runs = 1;
    if(config.computePhaseOrdering) {
      runs = NUM_RUNS_TO_DETERMINE_OPTIMAL_ORDER;
      PhaseOptimizer.randomizeLoops();
    }
    try {
      for(int i = 0; i < runs && result == 0; i++) {
        runTimeStats.recordStartRun();
        result = doRun();
        runTimeStats.recordEndRun();
      }
    }
    catch (AbstractCommandLineRunner.FlagUsageException e) {
      System.err.println(e.getMessage());
      result = -1;
    }
    catch (Throwable t) {
      t.printStackTrace();
      result = -2;
    }
    if(config.computePhaseOrdering) {
      runTimeStats.outputBestPhaseOrdering();
    }
    try {
      if(jsOutput instanceof Closeable) {
        ((Closeable)jsOutput).close();
      }
    }
    catch (IOException e) {
      throw Throwables.propagate(e);
    }
    if(testMode) {
      exitCodeReceiverForTesting.apply(result);
    }
    else {
      System.exit(result);
    }
  }
  protected void setRunOptions(CompilerOptions options) throws FlagUsageException, IOException {
    DiagnosticGroups diagnosticGroups = getDiagnosticGroups();
    if(config.warningGuards != null) {
      for (WarningGuardSpec.Entry entry : config.warningGuards.entries) {
        diagnosticGroups.setWarningLevel(options, entry.groupName, entry.level);
      }
    }
    if(!config.warningsWhitelistFile.isEmpty()) {
      options.addWarningsGuard(WhitelistWarningsGuard.fromFile(new File(config.warningsWhitelistFile)));
    }
    createDefineOrTweakReplacements(config.define, options, false);
    options.setTweakProcessing(config.tweakProcessing);
    createDefineOrTweakReplacements(config.tweak, options, true);
    DependencyOptions depOptions = createDependencyOptions(config.manageClosureDependencies, config.onlyClosureDependencies, config.processCommonJSModules, config.closureEntryPoints);
    if(depOptions != null) {
      options.setDependencyOptions(depOptions);
    }
    options.devMode = config.jscompDevMode;
    options.setCodingConvention(config.codingConvention);
    options.setSummaryDetailLevel(config.summaryDetailLevel);
    options.setTrustedStrings(true);
    legacyOutputCharset = options.outputCharset = getLegacyOutputCharset();
    outputCharset2 = getOutputCharset2();
    inputCharset = getInputCharset();
    if(config.jsOutputFile.length() > 0) {
      if(config.skipNormalOutputs) {
        throw new FlagUsageException("skip_normal_outputs and js_output_file" + " cannot be used together.");
      }
    }
    if(config.skipNormalOutputs && config.printAst) {
      throw new FlagUsageException("skip_normal_outputs and print_ast cannot" + " be used together.");
    }
    if(config.skipNormalOutputs && config.printTree) {
      throw new FlagUsageException("skip_normal_outputs and print_tree cannot" + " be used together.");
    }
    if(config.createSourceMap.length() > 0) {
      options.sourceMapOutputPath = config.createSourceMap;
    }
    options.sourceMapDetailLevel = config.sourceMapDetailLevel;
    options.sourceMapFormat = config.sourceMapFormat;
    if(!config.variableMapInputFile.equals("")) {
      options.inputVariableMap = VariableMap.load(config.variableMapInputFile);
    }
    if(!config.propertyMapInputFile.equals("")) {
      options.inputPropertyMap = VariableMap.load(config.propertyMapInputFile);
    }
    if(config.languageIn.length() > 0) {
      CompilerOptions.LanguageMode languageMode = CompilerOptions.LanguageMode.fromString(config.languageIn);
      if(languageMode != null) {
        options.setLanguageIn(languageMode);
      }
      else {
        throw new FlagUsageException("Unknown language `" + config.languageIn + "\' specified.");
      }
    }
    if(!config.outputManifests.isEmpty()) {
      Set<String> uniqueNames = Sets.newHashSet();
      for (String filename : config.outputManifests) {
        if(!uniqueNames.add(filename)) {
          throw new FlagUsageException("output_manifest flags specify " + "duplicate file names: " + filename);
        }
      }
    }
    if(!config.outputBundles.isEmpty()) {
      Set<String> uniqueNames = Sets.newHashSet();
      for (String filename : config.outputBundles) {
        if(!uniqueNames.add(filename)) {
          throw new FlagUsageException("output_bundle flags specify " + "duplicate file names: " + filename);
        }
      }
    }
    options.acceptConstKeyword = config.acceptConstKeyword;
    options.transformAMDToCJSModules = config.transformAMDToCJSModules;
    options.processCommonJSModules = config.processCommonJSModules;
    options.commonJSModulePathPrefix = config.commonJSModulePathPrefix;
  }
  @VisibleForTesting() void writeModuleOutput(Appendable out, JSModule m) throws FlagUsageException, IOException {
    if(parsedModuleWrappers == null) {
      parsedModuleWrappers = parseModuleWrappers(config.moduleWrapper, Lists.newArrayList(compiler.getDegenerateModuleGraph().getAllModules()));
    }
    String fileName = getModuleOutputFileName(m);
    String baseName = new File(fileName).getName();
    writeOutput(out, compiler, compiler.toSource(m), parsedModuleWrappers.get(m.getName()).replace("%basename%", baseName), "%s", null);
  }
  static void writeOutput(Appendable out, Compiler compiler, String code, String wrapper, String codePlaceholder, @Nullable() Function<String, String> escaper) throws IOException {
    int pos = wrapper.indexOf(codePlaceholder);
    if(pos != -1) {
      String prefix = "";
      if(pos > 0) {
        prefix = wrapper.substring(0, pos);
        out.append(prefix);
      }
      out.append(escaper == null ? code : escaper.apply(code));
      int suffixStart = pos + codePlaceholder.length();
      if(suffixStart != wrapper.length()) {
        out.append(wrapper.substring(suffixStart));
      }
      out.append('\n');
      if(compiler != null && compiler.getSourceMap() != null) {
        compiler.getSourceMap().setWrapperPrefix(prefix);
      }
    }
    else {
      out.append(code);
      out.append('\n');
    }
  }
  
  static class CommandLineConfig  {
    private boolean printTree = false;
    private boolean computePhaseOrdering = false;
    private boolean printAst = false;
    private boolean printPassGraph = false;
    private CompilerOptions.DevMode jscompDevMode = CompilerOptions.DevMode.OFF;
    private String loggingLevel = Level.WARNING.getName();
    final private List<String> externs = Lists.newArrayList();
    final private List<String> js = Lists.newArrayList();
    private String jsOutputFile = "";
    final private List<String> module = Lists.newArrayList();
    private String variableMapInputFile = "";
    private String propertyMapInputFile = "";
    private String variableMapOutputFile = "";
    private boolean createNameMapFiles = false;
    private String propertyMapOutputFile = "";
    private CodingConvention codingConvention = CodingConventions.getDefault();
    private int summaryDetailLevel = 1;
    private String outputWrapper = "";
    final private List<String> moduleWrapper = Lists.newArrayList();
    private String moduleOutputPathPrefix = "";
    private String createSourceMap = "";
    private SourceMap.DetailLevel sourceMapDetailLevel = SourceMap.DetailLevel.ALL;
    private SourceMap.Format sourceMapFormat = SourceMap.Format.DEFAULT;
    private WarningGuardSpec warningGuards = null;
    final private List<String> define = Lists.newArrayList();
    final private List<String> tweak = Lists.newArrayList();
    private TweakProcessing tweakProcessing = TweakProcessing.OFF;
    private String charset = "";
    private boolean manageClosureDependencies = false;
    private boolean onlyClosureDependencies = false;
    private List<String> closureEntryPoints = ImmutableList.of();
    private List<String> outputManifests = ImmutableList.of();
    private String outputModuleDependencies = null;
    private List<String> outputBundles = ImmutableList.of();
    private boolean acceptConstKeyword = false;
    private String languageIn = "";
    private boolean skipNormalOutputs = false;
    private List<String> manifestMaps = ImmutableList.of();
    private boolean transformAMDToCJSModules = false;
    private boolean processCommonJSModules = false;
    private String commonJSModulePathPrefix = ProcessCommonJSModules.DEFAULT_FILENAME_PREFIX;
    private String warningsWhitelistFile = "";
    CommandLineConfig setAcceptConstKeyword(boolean acceptConstKeyword) {
      this.acceptConstKeyword = acceptConstKeyword;
      return this;
    }
    CommandLineConfig setCharset(String charset) {
      this.charset = charset;
      return this;
    }
    CommandLineConfig setClosureEntryPoints(List<String> entryPoints) {
      Preconditions.checkNotNull(entryPoints);
      this.closureEntryPoints = entryPoints;
      return this;
    }
    CommandLineConfig setCodingConvention(CodingConvention codingConvention) {
      this.codingConvention = codingConvention;
      return this;
    }
    CommandLineConfig setCommonJSModulePathPrefix(String commonJSModulePathPrefix) {
      this.commonJSModulePathPrefix = commonJSModulePathPrefix;
      return this;
    }
    CommandLineConfig setComputePhaseOrdering(boolean computePhaseOrdering) {
      this.computePhaseOrdering = computePhaseOrdering;
      return this;
    }
    CommandLineConfig setCreateNameMapFiles(boolean createNameMapFiles) {
      this.createNameMapFiles = createNameMapFiles;
      return this;
    }
    CommandLineConfig setCreateSourceMap(String createSourceMap) {
      this.createSourceMap = createSourceMap;
      return this;
    }
    CommandLineConfig setDefine(List<String> define) {
      this.define.clear();
      this.define.addAll(define);
      return this;
    }
    CommandLineConfig setExterns(List<String> externs) {
      this.externs.clear();
      this.externs.addAll(externs);
      return this;
    }
    CommandLineConfig setJs(List<String> js) {
      this.js.clear();
      this.js.addAll(js);
      return this;
    }
    CommandLineConfig setJsOutputFile(String jsOutputFile) {
      this.jsOutputFile = jsOutputFile;
      return this;
    }
    CommandLineConfig setJscompDevMode(CompilerOptions.DevMode jscompDevMode) {
      this.jscompDevMode = jscompDevMode;
      return this;
    }
    CommandLineConfig setLanguageIn(String languageIn) {
      this.languageIn = languageIn;
      return this;
    }
    CommandLineConfig setLoggingLevel(String loggingLevel) {
      this.loggingLevel = loggingLevel;
      return this;
    }
    CommandLineConfig setManageClosureDependencies(boolean newVal) {
      this.manageClosureDependencies = newVal;
      return this;
    }
    CommandLineConfig setManifestMaps(List<String> manifestMaps) {
      this.manifestMaps = manifestMaps;
      return this;
    }
    CommandLineConfig setModule(List<String> module) {
      this.module.clear();
      this.module.addAll(module);
      return this;
    }
    CommandLineConfig setModuleOutputPathPrefix(String moduleOutputPathPrefix) {
      this.moduleOutputPathPrefix = moduleOutputPathPrefix;
      return this;
    }
    CommandLineConfig setModuleWrapper(List<String> moduleWrapper) {
      this.moduleWrapper.clear();
      this.moduleWrapper.addAll(moduleWrapper);
      return this;
    }
    CommandLineConfig setOnlyClosureDependencies(boolean newVal) {
      this.onlyClosureDependencies = newVal;
      return this;
    }
    CommandLineConfig setOutputBundle(List<String> outputBundles) {
      this.outputBundles = outputBundles;
      return this;
    }
    CommandLineConfig setOutputManifest(List<String> outputManifests) {
      this.outputManifests = Lists.newArrayList();
      for (String manifestName : outputManifests) {
        if(!manifestName.isEmpty()) {
          this.outputManifests.add(manifestName);
        }
      }
      this.outputManifests = ImmutableList.copyOf(this.outputManifests);
      return this;
    }
    CommandLineConfig setOutputModuleDependencies(String outputModuleDependencies) {
      this.outputModuleDependencies = outputModuleDependencies;
      return this;
    }
    CommandLineConfig setOutputWrapper(String outputWrapper) {
      this.outputWrapper = outputWrapper;
      return this;
    }
    CommandLineConfig setPrintAst(boolean printAst) {
      this.printAst = printAst;
      return this;
    }
    CommandLineConfig setPrintPassGraph(boolean printPassGraph) {
      this.printPassGraph = printPassGraph;
      return this;
    }
    CommandLineConfig setPrintTree(boolean printTree) {
      this.printTree = printTree;
      return this;
    }
    CommandLineConfig setProcessCommonJSModules(boolean processCommonJSModules) {
      this.processCommonJSModules = processCommonJSModules;
      return this;
    }
    CommandLineConfig setPropertyMapInputFile(String propertyMapInputFile) {
      this.propertyMapInputFile = propertyMapInputFile;
      return this;
    }
    CommandLineConfig setPropertyMapOutputFile(String propertyMapOutputFile) {
      this.propertyMapOutputFile = propertyMapOutputFile;
      return this;
    }
    CommandLineConfig setSkipNormalOutputs(boolean skipNormalOutputs) {
      this.skipNormalOutputs = skipNormalOutputs;
      return this;
    }
    CommandLineConfig setSourceMapDetailLevel(SourceMap.DetailLevel level) {
      this.sourceMapDetailLevel = level;
      return this;
    }
    CommandLineConfig setSourceMapFormat(SourceMap.Format format) {
      this.sourceMapFormat = format;
      return this;
    }
    CommandLineConfig setSummaryDetailLevel(int summaryDetailLevel) {
      this.summaryDetailLevel = summaryDetailLevel;
      return this;
    }
    CommandLineConfig setTransformAMDToCJSModules(boolean transformAMDToCJSModules) {
      this.transformAMDToCJSModules = transformAMDToCJSModules;
      return this;
    }
    CommandLineConfig setTweak(List<String> tweak) {
      this.tweak.clear();
      this.tweak.addAll(tweak);
      return this;
    }
    CommandLineConfig setTweakProcessing(TweakProcessing tweakProcessing) {
      this.tweakProcessing = tweakProcessing;
      return this;
    }
    CommandLineConfig setVariableMapInputFile(String variableMapInputFile) {
      this.variableMapInputFile = variableMapInputFile;
      return this;
    }
    CommandLineConfig setVariableMapOutputFile(String variableMapOutputFile) {
      this.variableMapOutputFile = variableMapOutputFile;
      return this;
    }
    CommandLineConfig setWarningGuardSpec(WarningGuardSpec spec) {
      this.warningGuards = spec;
      return this;
    }
    CommandLineConfig setWarningsWhitelistFile(String fileName) {
      this.warningsWhitelistFile = fileName;
      return this;
    }
  }
  
  public static class FlagUsageException extends Exception  {
    final private static long serialVersionUID = 1L;
    public FlagUsageException(String message) {
      super(message);
    }
  }
  
  private class RunTimeStats  {
    private long bestRunTime = Long.MAX_VALUE;
    private long worstRunTime = Long.MIN_VALUE;
    private long lastStartTime = 0;
    private List<List<String>> loopedPassesInBestRun = null;
    private void outputBestPhaseOrdering() {
      try {
        jsOutput.append("Best time: " + bestRunTime + "\n");
        jsOutput.append("Worst time: " + worstRunTime + "\n");
        int i = 1;
        for (List<String> loop : loopedPassesInBestRun) {
          jsOutput.append("\nLoop " + i + ":\n" + Joiner.on("\n").join(loop) + "\n");
          i++;
        }
      }
      catch (IOException e) {
        throw new RuntimeException("unexpected exception", e);
      }
    }
    private void recordEndRun() {
      long endTime = System.currentTimeMillis();
      long length = endTime - lastStartTime;
      worstRunTime = Math.max(length, worstRunTime);
      if(length < bestRunTime) {
        loopedPassesInBestRun = PhaseOptimizer.getLoopsRun();
        bestRunTime = length;
      }
    }
    private void recordStartRun() {
      lastStartTime = System.currentTimeMillis();
      PhaseOptimizer.clearLoopsRun();
    }
  }
  
  protected static class WarningGuardSpec  {
    final private List<Entry> entries = Lists.newArrayList();
    protected void add(CheckLevel level, String groupName) {
      entries.add(new Entry(level, groupName));
    }
    protected void clear() {
      entries.clear();
    }
    
    private static class Entry  {
      final private CheckLevel level;
      final private String groupName;
      private Entry(CheckLevel level, String groupName) {
        super();
        this.level = level;
        this.groupName = groupName;
      }
    }
  }
}