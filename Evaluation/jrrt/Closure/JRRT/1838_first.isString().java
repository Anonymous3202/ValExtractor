package com.google.javascript.jscomp;
import static com.google.javascript.rhino.jstype.JSTypeNative.STRING_TYPE;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

class ReplaceCssNames implements CompilerPass  {
  final static String GET_CSS_NAME_FUNCTION = "goog.getCssName";
  final static DiagnosticType INVALID_NUM_ARGUMENTS_ERROR = DiagnosticType.error("JSC_GETCSSNAME_NUM_ARGS", "goog.getCssName called with \"{0}\" arguments, expected 1 or 2.");
  final static DiagnosticType STRING_LITERAL_EXPECTED_ERROR = DiagnosticType.error("JSC_GETCSSNAME_STRING_LITERAL_EXPECTED", "goog.getCssName called with invalid argument, string literal " + "expected.  Was \"{0}\".");
  final static DiagnosticType UNEXPECTED_STRING_LITERAL_ERROR = DiagnosticType.error("JSC_GETCSSNAME_UNEXPECTED_STRING_LITERAL", "goog.getCssName called with invalid arguments, string literal " + "passed as first of two arguments.  Did you mean " + "goog.getCssName(\"{0}-{1}\")?");
  final static DiagnosticType UNKNOWN_SYMBOL_WARNING = DiagnosticType.warning("JSC_GETCSSNAME_UNKNOWN_CSS_SYMBOL", "goog.getCssName called with unrecognized symbol \"{0}\" in class " + "\"{1}\".");
  final private AbstractCompiler compiler;
  final private Map<String, Integer> cssNames;
  private CssRenamingMap symbolMap;
  final private Set<String> whitelist;
  final private JSType nativeStringType;
  ReplaceCssNames(AbstractCompiler compiler, @Nullable() Map<String, Integer> cssNames, @Nullable() Set<String> whitelist) {
    super();
    this.compiler = compiler;
    this.cssNames = cssNames;
    this.whitelist = whitelist;
    this.nativeStringType = compiler.getTypeRegistry().getNativeType(STRING_TYPE);
  }
  @VisibleForTesting() protected CssRenamingMap getCssRenamingMap() {
    return compiler.getCssRenamingMap();
  }
  @Override() public void process(Node externs, Node root) {
    symbolMap = getCssRenamingMap();
    NodeTraversal.traverse(compiler, root, new Traversal());
  }
  
  private class Traversal extends AbstractPostOrderCallback  {
    private void processStringNode(NodeTraversal t, Node n) {
      String name = n.getString();
      if(whitelist != null && whitelist.contains(name)) {
        return ;
      }
      String[] parts = name.split("-");
      if(symbolMap != null) {
        String replacement = null;
        switch (symbolMap.getStyle()){
          case BY_WHOLE:
          replacement = symbolMap.get(name);
          if(replacement == null) {
            compiler.report(t.makeError(n, UNKNOWN_SYMBOL_WARNING, name, name));
            return ;
          }
          break ;
          case BY_PART:
          String[] replaced = new String[parts.length];
          for(int i = 0; i < parts.length; i++) {
            String part = symbolMap.get(parts[i]);
            if(part == null) {
              compiler.report(t.makeError(n, UNKNOWN_SYMBOL_WARNING, parts[i], name));
              return ;
            }
            replaced[i] = part;
          }
          replacement = Joiner.on("-").join(replaced);
          break ;
          default:
          throw new IllegalStateException("Unknown replacement style: " + symbolMap.getStyle());
        }
        n.setString(replacement);
      }
      if(cssNames != null) {
        for(int i = 0; i < parts.length; i++) {
          Integer count = cssNames.get(parts[i]);
          if(count == null) {
            count = Integer.valueOf(0);
          }
          cssNames.put(parts[i], count.intValue() + 1);
        }
      }
    }
    @Override() public void visit(NodeTraversal t, Node n, Node parent) {
      if(n.isCall() && GET_CSS_NAME_FUNCTION.equals(n.getFirstChild().getQualifiedName())) {
        int count = n.getChildCount();
        Node first = n.getFirstChild().getNext();
        switch (count){
          case 2:
          boolean var_1838 = first.isString();
          if(var_1838) {
            processStringNode(t, first);
            n.removeChild(first);
            parent.replaceChild(n, first);
            compiler.reportCodeChange();
          }
          else {
            compiler.report(t.makeError(n, STRING_LITERAL_EXPECTED_ERROR, Token.name(first.getType())));
          }
          break ;
          case 3:
          Node second = first.getNext();
          if(!second.isString()) {
            compiler.report(t.makeError(n, STRING_LITERAL_EXPECTED_ERROR, Token.name(second.getType())));
          }
          else 
            if(first.isString()) {
              compiler.report(t.makeError(n, UNEXPECTED_STRING_LITERAL_ERROR, first.getString(), second.getString()));
            }
            else {
              processStringNode(t, second);
              n.removeChild(first);
              Node replacement = IR.add(first, IR.string("-" + second.getString()).copyInformationFrom(second)).copyInformationFrom(n);
              replacement.setJSType(nativeStringType);
              parent.replaceChild(n, replacement);
              compiler.reportCodeChange();
            }
          break ;
          default:
          compiler.report(t.makeError(n, INVALID_NUM_ARGUMENTS_ERROR, String.valueOf(count)));
        }
      }
    }
  }
}