package com.google.javascript.jscomp.deps;
import com.google.common.base.CharMatcher;
import com.google.common.collect.Lists;
import com.google.javascript.jscomp.ErrorManager;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsFileParser extends JsFileLineParser  {
  private static Logger logger = Logger.getLogger(JsFileParser.class.getName());
  final private static Pattern GOOG_PROVIDE_REQUIRE_PATTERN = Pattern.compile("(?:^|;)\\s*goog\\.(provide|require|addDependency)\\s*\\((.*?)\\)");
  final private static String BASE_JS_START = "var COMPILED = false;";
  private Matcher googMatcher = GOOG_PROVIDE_REQUIRE_PATTERN.matcher("");
  private List<String> provides;
  private List<String> requires;
  private boolean fileHasProvidesOrRequires;
  private boolean includeGoogBase = false;
  public JsFileParser(ErrorManager errorManager) {
    super(errorManager);
  }
  public DependencyInfo parseFile(String filePath, String closureRelativePath) throws IOException {
    return parseReader(filePath, closureRelativePath, new FileReader(filePath));
  }
  public DependencyInfo parseFile(String filePath, String closureRelativePath, String fileContents) {
    return parseReader(filePath, closureRelativePath, new StringReader(fileContents));
  }
  private DependencyInfo parseReader(String filePath, String closureRelativePath, Reader fileContents) {
    java.util.ArrayList<String> var_2151 = Lists.newArrayList();
    provides = var_2151;
    requires = Lists.newArrayList();
    fileHasProvidesOrRequires = false;
    logger.fine("Parsing Source: " + filePath);
    doParse(filePath, fileContents);
    DependencyInfo dependencyInfo = new SimpleDependencyInfo(closureRelativePath, filePath, provides, requires);
    logger.fine("DepInfo: " + dependencyInfo);
    return dependencyInfo;
  }
  public JsFileParser setIncludeGoogBase(boolean include) {
    includeGoogBase = include;
    return this;
  }
  @Override() protected boolean parseLine(String line) throws ParseException {
    boolean lineHasProvidesOrRequires = false;
    if(line.indexOf("provide") != -1 || line.indexOf("require") != -1 || line.indexOf("addDependency") != -1) {
      googMatcher.reset(line);
      while(googMatcher.find()){
        lineHasProvidesOrRequires = true;
        if(includeGoogBase && !fileHasProvidesOrRequires) {
          fileHasProvidesOrRequires = true;
          requires.add("goog");
        }
        char firstChar = googMatcher.group(1).charAt(0);
        boolean isProvide = firstChar == 'p';
        boolean isRequire = firstChar == 'r';
        if(isProvide || isRequire) {
          String arg = parseJsString(googMatcher.group(2));
          if(isRequire) {
            if(!"goog".equals(arg)) {
              requires.add(arg);
            }
          }
          else {
            provides.add(arg);
          }
        }
      }
    }
    else 
      if(includeGoogBase && line.startsWith(BASE_JS_START) && provides.isEmpty() && requires.isEmpty()) {
        provides.add("goog");
        return false;
      }
    return !shortcutMode || lineHasProvidesOrRequires || CharMatcher.WHITESPACE.matchesAllOf(line);
  }
}