package com.google.javascript.jscomp;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.TokenStream;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.StaticSourceFile;
import com.google.javascript.rhino.jstype.TernaryValue;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

final public class NodeUtil  {
  final static long MAX_POSITIVE_INTEGER_NUMBER = (long)Math.pow(2, 53);
  final static String JSC_PROPERTY_NAME_FN = "JSCompiler_renameProperty";
  final static char LARGEST_BASIC_LATIN = 0x7f;
  final private static Set<String> CONSTRUCTORS_WITHOUT_SIDE_EFFECTS = new HashSet<String>(Arrays.asList("Array", "Date", "Error", "Object", "RegExp", "XMLHttpRequest"));
  final private static Set<String> BUILTIN_FUNCTIONS_WITHOUT_SIDEEFFECTS = ImmutableSet.of("Object", "Array", "String", "Number", "Boolean", "RegExp", "Error");
  final private static Set<String> OBJECT_METHODS_WITHOUT_SIDEEFFECTS = ImmutableSet.of("toString", "valueOf");
  final private static Set<String> REGEXP_METHODS = ImmutableSet.of("test", "exec");
  final private static Set<String> STRING_REGEXP_METHODS = ImmutableSet.of("match", "replace", "search", "split");
  final static Predicate<Node> IMMUTABLE_PREDICATE = new Predicate<Node>() {
      @Override() public boolean apply(Node n) {
        return isImmutableValue(n);
      }
  };
  final static NumbericResultPredicate NUMBERIC_RESULT_PREDICATE = new NumbericResultPredicate();
  final static BooleanResultPredicate BOOLEAN_RESULT_PREDICATE = new BooleanResultPredicate();
  final static MayBeStringResultPredicate MAY_BE_STRING_PREDICATE = new MayBeStringResultPredicate();
  final static Predicate<Node> MATCH_NOT_FUNCTION = new MatchNotFunction();
  private NodeUtil() {
    super();
  }
  static Collection<Node> getVarsDeclaredInBranch(Node root) {
    VarCollector collector = new VarCollector();
    visitPreOrder(root, collector, MATCH_NOT_FUNCTION);
    return collector.vars.values();
  }
  static Double getNumberValue(Node n) {
    switch (n.getType()){
      case Token.TRUE:
      return 1.0D;
      case Token.FALSE:
      case Token.NULL:
      return 0.0D;
      case Token.NUMBER:
      return n.getDouble();
      case Token.VOID:
      if(mayHaveSideEffects(n.getFirstChild())) {
        return null;
      }
      else {
        double var_2039 = Double.NaN;
        return var_2039;
      }
      case Token.NAME:
      String name = n.getString();
      if(name.equals("undefined")) {
        return Double.NaN;
      }
      if(name.equals("NaN")) {
        return Double.NaN;
      }
      if(name.equals("Infinity")) {
        return Double.POSITIVE_INFINITY;
      }
      return null;
      case Token.NEG:
      if(n.getChildCount() == 1 && n.getFirstChild().isName() && n.getFirstChild().getString().equals("Infinity")) {
        return Double.NEGATIVE_INFINITY;
      }
      return null;
      case Token.NOT:
      TernaryValue child = getPureBooleanValue(n.getFirstChild());
      if(child != TernaryValue.UNKNOWN) {
        return child.toBoolean(true) ? 0.0D : 1.0D;
      }
      break ;
      case Token.STRING:
      return getStringNumberValue(n.getString());
      case Token.ARRAYLIT:
      case Token.OBJECTLIT:
      String value = getStringValue(n);
      return value != null ? getStringNumberValue(value) : null;
    }
    return null;
  }
  static Double getStringNumberValue(String rawJsString) {
    if(rawJsString.contains("\u000b")) {
      return null;
    }
    String s = trimJsWhiteSpace(rawJsString);
    if(s.length() == 0) {
      return 0.0D;
    }
    if(s.length() > 2 && s.charAt(0) == '0' && (s.charAt(1) == 'x' || s.charAt(1) == 'X')) {
      try {
        return Double.valueOf(Integer.parseInt(s.substring(2), 16));
      }
      catch (NumberFormatException e) {
        return Double.NaN;
      }
    }
    if(s.length() > 3 && (s.charAt(0) == '-' || s.charAt(0) == '+') && s.charAt(1) == '0' && (s.charAt(2) == 'x' || s.charAt(2) == 'X')) {
      return null;
    }
    if(s.equals("infinity") || s.equals("-infinity") || s.equals("+infinity")) {
      return null;
    }
    try {
      return Double.parseDouble(s);
    }
    catch (NumberFormatException e) {
      return Double.NaN;
    }
  }
  public static InputId getInputId(Node n) {
    while(n != null && !n.isScript()){
      n = n.getParent();
    }
    return (n != null && n.isScript()) ? n.getInputId() : null;
  }
  static JSDocInfo getBestJSDocInfo(Node n) {
    JSDocInfo info = n.getJSDocInfo();
    if(info == null) {
      Node parent = n.getParent();
      if(parent == null) {
        return null;
      }
      if(parent.isName()) {
        return getBestJSDocInfo(parent);
      }
      else 
        if(parent.isAssign()) {
          return parent.getJSDocInfo();
        }
        else 
          if(isObjectLitKey(parent, parent.getParent())) {
            return parent.getJSDocInfo();
          }
          else 
            if(parent.isFunction()) {
              return parent.getJSDocInfo();
            }
            else 
              if(parent.isVar() && parent.hasOneChild()) {
                return parent.getJSDocInfo();
              }
              else 
                if((parent.isHook() && parent.getFirstChild() != n) || parent.isOr() || parent.isAnd() || (parent.isComma() && parent.getFirstChild() != n)) {
                  return getBestJSDocInfo(parent);
                }
                else 
                  if(parent.isCast()) {
                    return parent.getJSDocInfo();
                  }
    }
    return info;
  }
  public static JSDocInfo getFunctionJSDocInfo(Node n) {
    Preconditions.checkState(n.isFunction());
    JSDocInfo fnInfo = n.getJSDocInfo();
    if(fnInfo == null && NodeUtil.isFunctionExpression(n)) {
      Node parent = n.getParent();
      if(parent.isAssign()) {
        fnInfo = parent.getJSDocInfo();
      }
      else 
        if(parent.isName()) {
          fnInfo = parent.getParent().getJSDocInfo();
        }
    }
    return fnInfo;
  }
  static JSType getObjectLitKeyTypeFromValueType(Node key, JSType valueType) {
    if(valueType != null) {
      switch (key.getType()){
        case Token.GETTER_DEF:
        if(valueType.isFunctionType()) {
          FunctionType fntype = valueType.toMaybeFunctionType();
          valueType = fntype.getReturnType();
        }
        else {
          return null;
        }
        break ;
        case Token.SETTER_DEF:
        if(valueType.isFunctionType()) {
          FunctionType fntype = valueType.toMaybeFunctionType();
          Node param = fntype.getParametersNode().getFirstChild();
          valueType = param.getJSType();
        }
        else {
          return null;
        }
        break ;
      }
    }
    return valueType;
  }
  static Node booleanNode(boolean value) {
    return value ? IR.trueNode() : IR.falseNode();
  }
  private static Node getAddingRoot(Node n) {
    Node addingRoot = null;
    Node ancestor = n;
    while(null != (ancestor = ancestor.getParent())){
      int type = ancestor.getType();
      if(type == Token.SCRIPT) {
        addingRoot = ancestor;
        break ;
      }
      else 
        if(type == Token.FUNCTION) {
          addingRoot = ancestor.getLastChild();
          break ;
        }
    }
    Preconditions.checkState(addingRoot.isBlock() || addingRoot.isScript());
    Preconditions.checkState(addingRoot.getFirstChild() == null || !addingRoot.getFirstChild().isScript());
    return addingRoot;
  }
  static Node getArgumentForCallOrNew(Node call, int index) {
    Preconditions.checkState(isCallOrNew(call));
    return getNthSibling(call.getFirstChild().getNext(), index);
  }
  static Node getArgumentForFunction(Node function, int index) {
    Preconditions.checkState(function.isFunction());
    return getNthSibling(function.getFirstChild().getNext().getFirstChild(), index);
  }
  static Node getAssignedValue(Node n) {
    Preconditions.checkState(n.isName());
    Node parent = n.getParent();
    if(parent.isVar()) {
      return n.getFirstChild();
    }
    else 
      if(parent.isAssign() && parent.getFirstChild() == n) {
        return n.getNext();
      }
      else {
        return null;
      }
  }
  static Node getBestLValue(Node n) {
    Node parent = n.getParent();
    boolean isFunctionDeclaration = isFunctionDeclaration(n);
    if(isFunctionDeclaration) {
      return n.getFirstChild();
    }
    else 
      if(parent.isName()) {
        return parent;
      }
      else 
        if(parent.isAssign()) {
          return parent.getFirstChild();
        }
        else 
          if(isObjectLitKey(parent, parent.getParent())) {
            return parent;
          }
          else 
            if((parent.isHook() && parent.getFirstChild() != n) || parent.isOr() || parent.isAnd() || (parent.isComma() && parent.getFirstChild() != n)) {
              return getBestLValue(parent);
            }
            else 
              if(parent.isCast()) {
                return getBestLValue(parent);
              }
    return null;
  }
  static Node getBestLValueOwner(@Nullable() Node lValue) {
    if(lValue == null || lValue.getParent() == null) {
      return null;
    }
    if(isObjectLitKey(lValue, lValue.getParent())) {
      return getBestLValue(lValue.getParent());
    }
    else 
      if(isGet(lValue)) {
        return lValue.getFirstChild();
      }
    return null;
  }
  static Node getCatchBlock(Node n) {
    Preconditions.checkArgument(n.isTry());
    return n.getFirstChild().getNext();
  }
  static Node getConditionExpression(Node n) {
    switch (n.getType()){
      case Token.IF:
      case Token.WHILE:
      return n.getFirstChild();
      case Token.DO:
      return n.getLastChild();
      case Token.FOR:
      switch (n.getChildCount()){
        case 3:
        return null;
        case 4:
        return n.getFirstChild().getNext();
      }
      throw new IllegalArgumentException("malformed \'for\' statement " + n);
      case Token.CASE:
      return null;
    }
    throw new IllegalArgumentException(n + " does not have a condition.");
  }
  static Node getFunctionBody(Node fn) {
    Preconditions.checkArgument(fn.isFunction());
    return fn.getLastChild();
  }
  public static Node getFunctionParameters(Node fnNode) {
    Preconditions.checkArgument(fnNode.isFunction());
    return fnNode.getFirstChild().getNext();
  }
  static Node getLoopCodeBlock(Node n) {
    switch (n.getType()){
      case Token.FOR:
      case Token.WHILE:
      return n.getLastChild();
      case Token.DO:
      return n.getFirstChild();
      default:
      return null;
    }
  }
  private static Node getNthSibling(Node first, int index) {
    Node sibling = first;
    while(index != 0 && sibling != null){
      sibling = sibling.getNext();
      index--;
    }
    return sibling;
  }
  static Node getPrototypeClassName(Node qName) {
    Node cur = qName;
    while(cur.isGetProp()){
      if(cur.getLastChild().getString().equals("prototype")) {
        return cur.getFirstChild();
      }
      else {
        cur = cur.getFirstChild();
      }
    }
    return null;
  }
  static Node getRValueOfLValue(Node n) {
    Node parent = n.getParent();
    switch (parent.getType()){
      case Token.ASSIGN:
      return n.getNext();
      case Token.VAR:
      return n.getFirstChild();
      case Token.FUNCTION:
      return parent;
    }
    return null;
  }
  static Node getRootOfQualifiedName(Node qName) {
    for(com.google.javascript.rhino.Node current = qName; true; current = current.getFirstChild()) {
      if(current.isName() || current.isThis()) {
        return current;
      }
      Preconditions.checkState(current.isGetProp());
    }
  }
  static Node newCallNode(Node callTarget, Node ... parameters) {
    boolean isFreeCall = !isGet(callTarget);
    Node call = IR.call(callTarget);
    call.putBooleanProp(Node.FREE_CALL, isFreeCall);
    for (Node parameter : parameters) {
      call.addChildToBack(parameter);
    }
    return call;
  }
  static Node newExpr(Node child) {
    return IR.exprResult(child).srcref(child);
  }
  private static Node newName(CodingConvention convention, String name) {
    Node nameNode = IR.name(name);
    if(convention.isConstant(name)) {
      nameNode.putBooleanProp(Node.IS_CONSTANT_NAME, true);
    }
    return nameNode;
  }
  static Node newName(CodingConvention convention, String name, Node srcref) {
    return newName(convention, name).srcref(srcref);
  }
  static Node newName(CodingConvention convention, String name, Node basisNode, String originalName) {
    Node nameNode = newName(convention, name, basisNode);
    nameNode.putProp(Node.ORIGINALNAME_PROP, originalName);
    return nameNode;
  }
  public static Node newQualifiedNameNode(CodingConvention convention, String name) {
    int endPos = name.indexOf('.');
    if(endPos == -1) {
      return newName(convention, name);
    }
    Node node = newName(convention, name.substring(0, endPos));
    int startPos;
    do {
      startPos = endPos + 1;
      endPos = name.indexOf('.', startPos);
      String part = (endPos == -1 ? name.substring(startPos) : name.substring(startPos, endPos));
      Node propNode = IR.string(part);
      if(convention.isConstantKey(part)) {
        propNode.putBooleanProp(Node.IS_CONSTANT_NAME, true);
      }
      node = IR.getprop(node, propNode);
    }while(endPos != -1);
    return node;
  }
  static Node newQualifiedNameNode(CodingConvention convention, String name, Node basisNode, String originalName) {
    Node node = newQualifiedNameNode(convention, name);
    setDebugInformation(node, basisNode, originalName);
    return node;
  }
  static Node newUndefinedNode(Node srcReferenceNode) {
    Node node = IR.voidNode(IR.number(0));
    if(srcReferenceNode != null) {
      node.copyInformationFromForTree(srcReferenceNode);
    }
    return node;
  }
  static Node newVarNode(String name, Node value) {
    Node nodeName = IR.name(name);
    if(value != null) {
      Preconditions.checkState(value.getNext() == null);
      nodeName.addChildToBack(value);
      nodeName.srcref(value);
    }
    Node var = IR.var(nodeName).srcref(nodeName);
    return var;
  }
  static Node numberNode(double value, Node srcref) {
    Node result;
    if(Double.isNaN(value)) {
      result = IR.name("NaN");
    }
    else 
      if(value == Double.POSITIVE_INFINITY) {
        result = IR.name("Infinity");
      }
      else 
        if(value == Double.NEGATIVE_INFINITY) {
          result = IR.neg(IR.name("Infinity"));
        }
        else {
          result = IR.number(value);
        }
    if(srcref != null) {
      result.srcrefTree(srcref);
    }
    return result;
  }
  public static StaticSourceFile getSourceFile(Node n) {
    StaticSourceFile sourceName = null;
    while(sourceName == null && n != null){
      sourceName = n.getStaticSourceFile();
      n = n.getParent();
    }
    return sourceName;
  }
  static String arrayToString(Node literal) {
    Node first = literal.getFirstChild();
    StringBuilder result = new StringBuilder();
    int nextSlot = 0;
    int nextSkipSlot = 0;
    for(com.google.javascript.rhino.Node n = first; n != null; n = n.getNext()) {
      String childValue = getArrayElementStringValue(n);
      if(childValue == null) {
        return null;
      }
      if(n != first) {
        result.append(',');
      }
      result.append(childValue);
      nextSlot++;
    }
    return result.toString();
  }
  static String getArrayElementStringValue(Node n) {
    return (NodeUtil.isNullOrUndefined(n) || n.isEmpty()) ? "" : getStringValue(n);
  }
  static String getBestLValueName(@Nullable() Node lValue) {
    if(lValue == null || lValue.getParent() == null) {
      return null;
    }
    if(isObjectLitKey(lValue, lValue.getParent())) {
      Node owner = getBestLValue(lValue.getParent());
      if(owner != null) {
        String ownerName = getBestLValueName(owner);
        if(ownerName != null) {
          return ownerName + "." + getObjectLitKeyName(lValue);
        }
      }
      return null;
    }
    return lValue.getQualifiedName();
  }
  static String getFunctionName(Node n) {
    Preconditions.checkState(n.isFunction());
    Node parent = n.getParent();
    switch (parent.getType()){
      case Token.NAME:
      return parent.getQualifiedName();
      case Token.ASSIGN:
      return parent.getFirstChild().getQualifiedName();
      default:
      String name = n.getFirstChild().getQualifiedName();
      return name;
    }
  }
  public static String getNearestFunctionName(Node n) {
    if(!n.isFunction()) {
      return null;
    }
    String name = getFunctionName(n);
    if(name != null) {
      return name;
    }
    Node parent = n.getParent();
    switch (parent.getType()){
      case Token.SETTER_DEF:
      case Token.GETTER_DEF:
      case Token.STRING_KEY:
      return parent.getString();
      case Token.NUMBER:
      return getStringValue(parent);
    }
    return null;
  }
  static String getObjectLitKeyName(Node key) {
    switch (key.getType()){
      case Token.STRING_KEY:
      case Token.GETTER_DEF:
      case Token.SETTER_DEF:
      return key.getString();
    }
    throw new IllegalStateException("Unexpected node type: " + key);
  }
  static String getPrototypePropertyName(Node qName) {
    String qNameStr = qName.getQualifiedName();
    int prototypeIdx = qNameStr.lastIndexOf(".prototype.");
    int memberIndex = prototypeIdx + ".prototype".length() + 1;
    return qNameStr.substring(memberIndex);
  }
  public static String getSourceName(Node n) {
    String sourceName = null;
    while(sourceName == null && n != null){
      sourceName = n.getSourceFileName();
      n = n.getParent();
    }
    return sourceName;
  }
  static String getStringValue(Node n) {
    switch (n.getType()){
      case Token.STRING:
      case Token.STRING_KEY:
      return n.getString();
      case Token.NAME:
      String name = n.getString();
      if("undefined".equals(name) || "Infinity".equals(name) || "NaN".equals(name)) {
        return name;
      }
      break ;
      case Token.NUMBER:
      return getStringValue(n.getDouble());
      case Token.FALSE:
      return "false";
      case Token.TRUE:
      return "true";
      case Token.NULL:
      return "null";
      case Token.VOID:
      return "undefined";
      case Token.NOT:
      TernaryValue child = getPureBooleanValue(n.getFirstChild());
      if(child != TernaryValue.UNKNOWN) {
        return child.toBoolean(true) ? "false" : "true";
      }
      break ;
      case Token.ARRAYLIT:
      return arrayToString(n);
      case Token.OBJECTLIT:
      return "[object Object]";
    }
    return null;
  }
  static String getStringValue(double value) {
    long longValue = (long)value;
    if(longValue == value) {
      return Long.toString(longValue);
    }
    else {
      return Double.toString(value);
    }
  }
  static String opToStr(int operator) {
    switch (operator){
      case Token.BITOR:
      return "|";
      case Token.OR:
      return "||";
      case Token.BITXOR:
      return "^";
      case Token.AND:
      return "&&";
      case Token.BITAND:
      return "&";
      case Token.SHEQ:
      return "===";
      case Token.EQ:
      return "==";
      case Token.NOT:
      return "!";
      case Token.NE:
      return "!=";
      case Token.SHNE:
      return "!==";
      case Token.LSH:
      return "<<";
      case Token.IN:
      return "in";
      case Token.LE:
      return "<=";
      case Token.LT:
      return "<";
      case Token.URSH:
      return ">>>";
      case Token.RSH:
      return ">>";
      case Token.GE:
      return ">=";
      case Token.GT:
      return ">";
      case Token.MUL:
      return "*";
      case Token.DIV:
      return "/";
      case Token.MOD:
      return "%";
      case Token.BITNOT:
      return "~";
      case Token.ADD:
      return "+";
      case Token.SUB:
      return "-";
      case Token.POS:
      return "+";
      case Token.NEG:
      return "-";
      case Token.ASSIGN:
      return "=";
      case Token.ASSIGN_BITOR:
      return "|=";
      case Token.ASSIGN_BITXOR:
      return "^=";
      case Token.ASSIGN_BITAND:
      return "&=";
      case Token.ASSIGN_LSH:
      return "<<=";
      case Token.ASSIGN_RSH:
      return ">>=";
      case Token.ASSIGN_URSH:
      return ">>>=";
      case Token.ASSIGN_ADD:
      return "+=";
      case Token.ASSIGN_SUB:
      return "-=";
      case Token.ASSIGN_MUL:
      return "*=";
      case Token.ASSIGN_DIV:
      return "/=";
      case Token.ASSIGN_MOD:
      return "%=";
      case Token.VOID:
      return "void";
      case Token.TYPEOF:
      return "typeof";
      case Token.INSTANCEOF:
      return "instanceof";
      default:
      return null;
    }
  }
  static String opToStrNoFail(int operator) {
    String res = opToStr(operator);
    if(res == null) {
      throw new Error("Unknown op " + operator + ": " + Token.name(operator));
    }
    return res;
  }
  static String trimJsWhiteSpace(String s) {
    int start = 0;
    int end = s.length();
    while(end > 0 && isStrWhiteSpaceChar(s.charAt(end - 1)) == TernaryValue.TRUE){
      end--;
    }
    while(start < end && isStrWhiteSpaceChar(s.charAt(start)) == TernaryValue.TRUE){
      start++;
    }
    return s.substring(start, end);
  }
  static TernaryValue getImpureBooleanValue(Node n) {
    switch (n.getType()){
      case Token.ASSIGN:
      case Token.COMMA:
      return getImpureBooleanValue(n.getLastChild());
      case Token.NOT:
      TernaryValue value = getImpureBooleanValue(n.getLastChild());
      return value.not();
      case Token.AND:
      {
        TernaryValue lhs = getImpureBooleanValue(n.getFirstChild());
        TernaryValue rhs = getImpureBooleanValue(n.getLastChild());
        return lhs.and(rhs);
      }
      case Token.OR:
      {
        TernaryValue lhs = getImpureBooleanValue(n.getFirstChild());
        TernaryValue rhs = getImpureBooleanValue(n.getLastChild());
        return lhs.or(rhs);
      }
      case Token.HOOK:
      {
        TernaryValue trueValue = getImpureBooleanValue(n.getFirstChild().getNext());
        TernaryValue falseValue = getImpureBooleanValue(n.getLastChild());
        if(trueValue.equals(falseValue)) {
          return trueValue;
        }
        else {
          return TernaryValue.UNKNOWN;
        }
      }
      case Token.ARRAYLIT:
      case Token.OBJECTLIT:
      return TernaryValue.TRUE;
      case Token.VOID:
      return TernaryValue.FALSE;
      default:
      return getPureBooleanValue(n);
    }
  }
  static TernaryValue getPureBooleanValue(Node n) {
    switch (n.getType()){
      case Token.STRING:
      return TernaryValue.forBoolean(n.getString().length() > 0);
      case Token.NUMBER:
      return TernaryValue.forBoolean(n.getDouble() != 0);
      case Token.NOT:
      return getPureBooleanValue(n.getLastChild()).not();
      case Token.NULL:
      case Token.FALSE:
      return TernaryValue.FALSE;
      case Token.VOID:
      if(!mayHaveSideEffects(n.getFirstChild())) {
        return TernaryValue.FALSE;
      }
      break ;
      case Token.NAME:
      String name = n.getString();
      if("undefined".equals(name) || "NaN".equals(name)) {
        return TernaryValue.FALSE;
      }
      else 
        if("Infinity".equals(name)) {
          return TernaryValue.TRUE;
        }
      break ;
      case Token.TRUE:
      case Token.REGEXP:
      return TernaryValue.TRUE;
      case Token.ARRAYLIT:
      case Token.OBJECTLIT:
      if(!mayHaveSideEffects(n)) {
        return TernaryValue.TRUE;
      }
      break ;
    }
    return TernaryValue.UNKNOWN;
  }
  public static TernaryValue isStrWhiteSpaceChar(int c) {
    switch (c){
      case '\u000b':
      return TernaryValue.UNKNOWN;
      case ' ':
      case '\n':
      case '\r':
      case '\t':
      case '\u00a0':
      case '\f':
      case '\u2028':
      case '\u2029':
      case '\ufeff':
      return TernaryValue.TRUE;
      default:
      return (Character.getType(c) == Character.SPACE_SEPARATOR) ? TernaryValue.TRUE : TernaryValue.FALSE;
    }
  }
  static boolean allResultsMatch(Node n, Predicate<Node> p) {
    switch (n.getType()){
      case Token.CAST:
      return allResultsMatch(n.getFirstChild(), p);
      case Token.ASSIGN:
      case Token.COMMA:
      return allResultsMatch(n.getLastChild(), p);
      case Token.AND:
      case Token.OR:
      return allResultsMatch(n.getFirstChild(), p) && allResultsMatch(n.getLastChild(), p);
      case Token.HOOK:
      return allResultsMatch(n.getFirstChild().getNext(), p) && allResultsMatch(n.getLastChild(), p);
      default:
      return p.apply(n);
    }
  }
  static boolean anyResultsMatch(Node n, Predicate<Node> p) {
    switch (n.getType()){
      case Token.CAST:
      return anyResultsMatch(n.getFirstChild(), p);
      case Token.ASSIGN:
      case Token.COMMA:
      return anyResultsMatch(n.getLastChild(), p);
      case Token.AND:
      case Token.OR:
      return anyResultsMatch(n.getFirstChild(), p) || anyResultsMatch(n.getLastChild(), p);
      case Token.HOOK:
      return anyResultsMatch(n.getFirstChild().getNext(), p) || anyResultsMatch(n.getLastChild(), p);
      default:
      return p.apply(n);
    }
  }
  static boolean callHasLocalResult(Node n) {
    Preconditions.checkState(n.isCall());
    return (n.getSideEffectFlags() & Node.FLAG_LOCAL_RESULTS) > 0;
  }
  static boolean canBeSideEffected(Node n) {
    Set<String> emptySet = Collections.emptySet();
    return canBeSideEffected(n, emptySet);
  }
  static boolean canBeSideEffected(Node n, Set<String> knownConstants) {
    switch (n.getType()){
      case Token.CALL:
      case Token.NEW:
      return true;
      case Token.NAME:
      return !isConstantName(n) && !knownConstants.contains(n.getString());
      case Token.GETPROP:
      case Token.GETELEM:
      return true;
      case Token.FUNCTION:
      Preconditions.checkState(isFunctionExpression(n));
      return false;
    }
    for(com.google.javascript.rhino.Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      if(canBeSideEffected(c, knownConstants)) {
        return true;
      }
    }
    return false;
  }
  private static boolean checkForStateChangeHelper(Node n, boolean checkForNewObjects, AbstractCompiler compiler) {
    switch (n.getType()){
      case Token.CAST:
      case Token.AND:
      case Token.BLOCK:
      case Token.EXPR_RESULT:
      case Token.HOOK:
      case Token.IF:
      case Token.IN:
      case Token.PARAM_LIST:
      case Token.NUMBER:
      case Token.OR:
      case Token.THIS:
      case Token.TRUE:
      case Token.FALSE:
      case Token.NULL:
      case Token.STRING:
      case Token.STRING_KEY:
      case Token.SWITCH:
      case Token.TRY:
      case Token.EMPTY:
      break ;
      case Token.THROW:
      return true;
      case Token.OBJECTLIT:
      if(checkForNewObjects) {
        return true;
      }
      for(com.google.javascript.rhino.Node c = n.getFirstChild(); c != null; c = c.getNext()) {
        if(checkForStateChangeHelper(c.getFirstChild(), checkForNewObjects, compiler)) {
          return true;
        }
      }
      return false;
      case Token.ARRAYLIT:
      case Token.REGEXP:
      if(checkForNewObjects) {
        return true;
      }
      break ;
      case Token.VAR:
      case Token.NAME:
      if(n.getFirstChild() != null) {
        return true;
      }
      break ;
      case Token.FUNCTION:
      return checkForNewObjects || !isFunctionExpression(n);
      case Token.NEW:
      if(checkForNewObjects) {
        return true;
      }
      if(!constructorCallHasSideEffects(n)) {
        break ;
      }
      return true;
      case Token.CALL:
      if(!functionCallHasSideEffects(n, compiler)) {
        break ;
      }
      return true;
      default:
      if(isSimpleOperator(n)) {
        break ;
      }
      if(isAssignmentOp(n)) {
        Node assignTarget = n.getFirstChild();
        if(assignTarget.isName()) {
          return true;
        }
        if(checkForStateChangeHelper(n.getFirstChild(), checkForNewObjects, compiler) || checkForStateChangeHelper(n.getLastChild(), checkForNewObjects, compiler)) {
          return true;
        }
        if(isGet(assignTarget)) {
          Node current = assignTarget.getFirstChild();
          if(evaluatesToLocalValue(current)) {
            return false;
          }
          while(isGet(current)){
            current = current.getFirstChild();
          }
          return !isLiteralValue(current, true);
        }
        else {
          return !isLiteralValue(assignTarget, true);
        }
      }
      return true;
    }
    for(com.google.javascript.rhino.Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      if(checkForStateChangeHelper(c, checkForNewObjects, compiler)) {
        return true;
      }
    }
    return false;
  }
  static boolean constructorCallHasSideEffects(Node callNode) {
    return constructorCallHasSideEffects(callNode, null);
  }
  static boolean constructorCallHasSideEffects(Node callNode, AbstractCompiler compiler) {
    if(!callNode.isNew()) {
      throw new IllegalStateException("Expected NEW node, got " + Token.name(callNode.getType()));
    }
    if(callNode.isNoSideEffectsCall()) {
      return false;
    }
    Node nameNode = callNode.getFirstChild();
    if(nameNode.isName() && CONSTRUCTORS_WITHOUT_SIDE_EFFECTS.contains(nameNode.getString())) {
      return false;
    }
    return true;
  }
  static boolean containsFunction(Node n) {
    return containsType(n, Token.FUNCTION);
  }
  static boolean containsType(Node node, int type) {
    return containsType(node, type, Predicates.<Node>alwaysTrue());
  }
  static boolean containsType(Node node, int type, Predicate<Node> traverseChildrenPred) {
    return has(node, new MatchNodeType(type), traverseChildrenPred);
  }
  static boolean evaluatesToLocalValue(Node value) {
    return evaluatesToLocalValue(value, Predicates.<Node>alwaysFalse());
  }
  static boolean evaluatesToLocalValue(Node value, Predicate<Node> locals) {
    switch (value.getType()){
      case Token.CAST:
      return evaluatesToLocalValue(value.getFirstChild(), locals);
      case Token.ASSIGN:
      return NodeUtil.isImmutableValue(value.getLastChild()) || (locals.apply(value) && evaluatesToLocalValue(value.getLastChild(), locals));
      case Token.COMMA:
      return evaluatesToLocalValue(value.getLastChild(), locals);
      case Token.AND:
      case Token.OR:
      return evaluatesToLocalValue(value.getFirstChild(), locals) && evaluatesToLocalValue(value.getLastChild(), locals);
      case Token.HOOK:
      return evaluatesToLocalValue(value.getFirstChild().getNext(), locals) && evaluatesToLocalValue(value.getLastChild(), locals);
      case Token.INC:
      case Token.DEC:
      if(value.getBooleanProp(Node.INCRDECR_PROP)) {
        return evaluatesToLocalValue(value.getFirstChild(), locals);
      }
      else {
        return true;
      }
      case Token.THIS:
      return locals.apply(value);
      case Token.NAME:
      return isImmutableValue(value) || locals.apply(value);
      case Token.GETELEM:
      case Token.GETPROP:
      return locals.apply(value);
      case Token.CALL:
      return callHasLocalResult(value) || isToStringMethodCall(value) || locals.apply(value);
      case Token.NEW:
      return newHasLocalResult(value) || locals.apply(value);
      case Token.FUNCTION:
      case Token.REGEXP:
      case Token.ARRAYLIT:
      case Token.OBJECTLIT:
      return true;
      case Token.DELPROP:
      case Token.IN:
      return true;
      default:
      if(isAssignmentOp(value) || isSimpleOperator(value) || isImmutableValue(value)) {
        return true;
      }
      throw new IllegalStateException("Unexpected expression node" + value + "\n parent:" + value.getParent());
    }
  }
  static boolean functionCallHasSideEffects(Node callNode) {
    return functionCallHasSideEffects(callNode, null);
  }
  static boolean functionCallHasSideEffects(Node callNode, @Nullable() AbstractCompiler compiler) {
    if(!callNode.isCall()) {
      throw new IllegalStateException("Expected CALL node, got " + Token.name(callNode.getType()));
    }
    if(callNode.isNoSideEffectsCall()) {
      return false;
    }
    Node nameNode = callNode.getFirstChild();
    if(nameNode.isName()) {
      String name = nameNode.getString();
      if(BUILTIN_FUNCTIONS_WITHOUT_SIDEEFFECTS.contains(name)) {
        return false;
      }
    }
    else 
      if(nameNode.isGetProp()) {
        if(callNode.hasOneChild() && OBJECT_METHODS_WITHOUT_SIDEEFFECTS.contains(nameNode.getLastChild().getString())) {
          return false;
        }
        if(callNode.isOnlyModifiesThisCall() && evaluatesToLocalValue(nameNode.getFirstChild())) {
          return false;
        }
        if(nameNode.getFirstChild().isName()) {
          if("Math.floor".equals(nameNode.getQualifiedName())) {
            return false;
          }
        }
        if(compiler != null && !compiler.hasRegExpGlobalReferences()) {
          if(nameNode.getFirstChild().isRegExp() && REGEXP_METHODS.contains(nameNode.getLastChild().getString())) {
            return false;
          }
          else 
            if(nameNode.getFirstChild().isString() && STRING_REGEXP_METHODS.contains(nameNode.getLastChild().getString())) {
              Node param = nameNode.getNext();
              if(param != null && (param.isString() || param.isRegExp())) {
                return false;
              }
            }
        }
      }
    return true;
  }
  static boolean has(Node node, Predicate<Node> pred, Predicate<Node> traverseChildrenPred) {
    if(pred.apply(node)) {
      return true;
    }
    if(!traverseChildrenPred.apply(node)) {
      return false;
    }
    for(com.google.javascript.rhino.Node c = node.getFirstChild(); c != null; c = c.getNext()) {
      if(has(c, pred, traverseChildrenPred)) {
        return true;
      }
    }
    return false;
  }
  static boolean hasCatchHandler(Node n) {
    Preconditions.checkArgument(n.isBlock());
    return n.hasChildren() && n.getFirstChild().isCatch();
  }
  static boolean hasFinally(Node n) {
    Preconditions.checkArgument(n.isTry());
    return n.getChildCount() == 3;
  }
  static boolean isAssignmentOp(Node n) {
    switch (n.getType()){
      case Token.ASSIGN:
      case Token.ASSIGN_BITOR:
      case Token.ASSIGN_BITXOR:
      case Token.ASSIGN_BITAND:
      case Token.ASSIGN_LSH:
      case Token.ASSIGN_RSH:
      case Token.ASSIGN_URSH:
      case Token.ASSIGN_ADD:
      case Token.ASSIGN_SUB:
      case Token.ASSIGN_MUL:
      case Token.ASSIGN_DIV:
      case Token.ASSIGN_MOD:
      return true;
    }
    return false;
  }
  static boolean isAssociative(int type) {
    switch (type){
      case Token.MUL:
      case Token.AND:
      case Token.OR:
      case Token.BITOR:
      case Token.BITXOR:
      case Token.BITAND:
      return true;
      default:
      return false;
    }
  }
  static boolean isBleedingFunctionName(Node n) {
    return n.isName() && !n.getString().isEmpty() && isFunctionExpression(n.getParent());
  }
  static boolean isBooleanResult(Node n) {
    return allResultsMatch(n, BOOLEAN_RESULT_PREDICATE);
  }
  static boolean isBooleanResultHelper(Node n) {
    switch (n.getType()){
      case Token.TRUE:
      case Token.FALSE:
      case Token.EQ:
      case Token.NE:
      case Token.SHEQ:
      case Token.SHNE:
      case Token.LT:
      case Token.GT:
      case Token.LE:
      case Token.GE:
      case Token.IN:
      case Token.INSTANCEOF:
      case Token.NOT:
      case Token.DELPROP:
      return true;
      default:
      return false;
    }
  }
  static boolean isCallOrNew(Node node) {
    return node.isCall() || node.isNew();
  }
  static boolean isCallOrNewTarget(Node target) {
    Node parent = target.getParent();
    return parent != null && NodeUtil.isCallOrNew(parent) && parent.getFirstChild() == target;
  }
  static boolean isCommutative(int type) {
    switch (type){
      case Token.MUL:
      case Token.BITOR:
      case Token.BITXOR:
      case Token.BITAND:
      return true;
      default:
      return false;
    }
  }
  static boolean isConstantByConvention(CodingConvention convention, Node node, Node parent) {
    String name = node.getString();
    if(parent.isGetProp() && node == parent.getLastChild()) {
      return convention.isConstantKey(name);
    }
    else 
      if(isObjectLitKey(node, parent)) {
        return convention.isConstantKey(name);
      }
      else {
        return convention.isConstant(name);
      }
  }
  static boolean isConstantName(Node node) {
    return node.getBooleanProp(Node.IS_CONSTANT_NAME);
  }
  static boolean isControlStructure(Node n) {
    switch (n.getType()){
      case Token.FOR:
      case Token.DO:
      case Token.WHILE:
      case Token.WITH:
      case Token.IF:
      case Token.LABEL:
      case Token.TRY:
      case Token.CATCH:
      case Token.SWITCH:
      case Token.CASE:
      case Token.DEFAULT_CASE:
      return true;
      default:
      return false;
    }
  }
  static boolean isControlStructureCodeBlock(Node parent, Node n) {
    switch (parent.getType()){
      case Token.FOR:
      case Token.WHILE:
      case Token.LABEL:
      case Token.WITH:
      return parent.getLastChild() == n;
      case Token.DO:
      return parent.getFirstChild() == n;
      case Token.IF:
      return parent.getFirstChild() != n;
      case Token.TRY:
      return parent.getFirstChild() == n || parent.getLastChild() == n;
      case Token.CATCH:
      return parent.getLastChild() == n;
      case Token.SWITCH:
      case Token.CASE:
      return parent.getFirstChild() != n;
      case Token.DEFAULT_CASE:
      return true;
      default:
      Preconditions.checkState(isControlStructure(parent));
      return false;
    }
  }
  static boolean isEmptyBlock(Node block) {
    if(!block.isBlock()) {
      return false;
    }
    for(com.google.javascript.rhino.Node n = block.getFirstChild(); n != null; n = n.getNext()) {
      if(!n.isEmpty()) {
        return false;
      }
    }
    return true;
  }
  static boolean isEmptyFunctionExpression(Node node) {
    return isFunctionExpression(node) && isEmptyBlock(node.getLastChild());
  }
  static boolean isExecutedExactlyOnce(Node n) {
    inspect:
      do {
        Node parent = n.getParent();
        switch (parent.getType()){
          case Token.IF:
          case Token.HOOK:
          case Token.AND:
          case Token.OR:
          if(parent.getFirstChild() != n) {
            return false;
          }
          continue inspect;
          case Token.FOR:
          if(NodeUtil.isForIn(parent)) {
            if(parent.getChildAtIndex(1) != n) {
              return false;
            }
          }
          else {
            if(parent.getFirstChild() != n) {
              return false;
            }
          }
          continue inspect;
          case Token.WHILE:
          case Token.DO:
          return false;
          case Token.TRY:
          if(!hasFinally(parent) || parent.getLastChild() != n) {
            return false;
          }
          continue inspect;
          case Token.CASE:
          case Token.DEFAULT_CASE:
          return false;
          case Token.SCRIPT:
          case Token.FUNCTION:
          break inspect;
        }
      }while((n = n.getParent()) != null);
    return true;
  }
  static boolean isExprAssign(Node n) {
    return n.isExprResult() && n.getFirstChild().isAssign();
  }
  static boolean isExprCall(Node n) {
    return n.isExprResult() && n.getFirstChild().isCall();
  }
  static boolean isExpressionResultUsed(Node expr) {
    Node parent = expr.getParent();
    switch (parent.getType()){
      case Token.BLOCK:
      case Token.EXPR_RESULT:
      return false;
      case Token.CAST:
      return isExpressionResultUsed(parent);
      case Token.HOOK:
      case Token.AND:
      case Token.OR:
      return (expr == parent.getFirstChild()) ? true : isExpressionResultUsed(parent);
      case Token.COMMA:
      Node gramps = parent.getParent();
      if(gramps.isCall() && parent == gramps.getFirstChild()) {
        if(expr == parent.getFirstChild() && parent.getChildCount() == 2 && expr.getNext().isName() && "eval".equals(expr.getNext().getString())) {
          return true;
        }
      }
      return (expr == parent.getFirstChild()) ? false : isExpressionResultUsed(parent);
      case Token.FOR:
      if(!NodeUtil.isForIn(parent)) {
        return (parent.getChildAtIndex(1) == expr);
      }
      break ;
    }
    return true;
  }
  static boolean isForIn(Node n) {
    return n.isFor() && n.getChildCount() == 3;
  }
  static boolean isFunctionDeclaration(Node n) {
    return n.isFunction() && isStatement(n);
  }
  static boolean isFunctionExpression(Node n) {
    return n.isFunction() && !isStatement(n);
  }
  static boolean isFunctionObjectApply(Node callNode) {
    return isObjectCallMethod(callNode, "apply");
  }
  static boolean isFunctionObjectCall(Node callNode) {
    return isObjectCallMethod(callNode, "call");
  }
  static boolean isGet(Node n) {
    return n.isGetProp() || n.isGetElem();
  }
  static boolean isGetOrSetKey(Node node) {
    switch (node.getType()){
      case Token.GETTER_DEF:
      case Token.SETTER_DEF:
      return true;
    }
    return false;
  }
  static boolean isHoistedFunctionDeclaration(Node n) {
    return isFunctionDeclaration(n) && (n.getParent().isScript() || n.getParent().getParent().isFunction());
  }
  static boolean isImmutableResult(Node n) {
    return allResultsMatch(n, IMMUTABLE_PREDICATE);
  }
  static boolean isImmutableValue(Node n) {
    switch (n.getType()){
      case Token.STRING:
      case Token.NUMBER:
      case Token.NULL:
      case Token.TRUE:
      case Token.FALSE:
      return true;
      case Token.CAST:
      case Token.NOT:
      return isImmutableValue(n.getFirstChild());
      case Token.VOID:
      case Token.NEG:
      return isImmutableValue(n.getFirstChild());
      case Token.NAME:
      String name = n.getString();
      return "undefined".equals(name) || "Infinity".equals(name) || "NaN".equals(name);
    }
    return false;
  }
  public static boolean isLValue(Node n) {
    Preconditions.checkArgument(n.isName() || n.isGetProp() || n.isGetElem());
    Node parent = n.getParent();
    if(parent == null) {
      return false;
    }
    return (NodeUtil.isAssignmentOp(parent) && parent.getFirstChild() == n) || (NodeUtil.isForIn(parent) && parent.getFirstChild() == n) || parent.isVar() || (parent.isFunction() && parent.getFirstChild() == n) || parent.isDec() || parent.isInc() || parent.isParamList() || parent.isCatch();
  }
  static boolean isLatin(String s) {
    int len = s.length();
    for(int index = 0; index < len; index++) {
      char c = s.charAt(index);
      if(c > LARGEST_BASIC_LATIN) {
        return false;
      }
    }
    return true;
  }
  static boolean isLiteralValue(Node n, boolean includeFunctions) {
    switch (n.getType()){
      case Token.CAST:
      return isLiteralValue(n.getFirstChild(), includeFunctions);
      case Token.ARRAYLIT:
      for(com.google.javascript.rhino.Node child = n.getFirstChild(); child != null; child = child.getNext()) {
        if((!child.isEmpty()) && !isLiteralValue(child, includeFunctions)) {
          return false;
        }
      }
      return true;
      case Token.REGEXP:
      for(com.google.javascript.rhino.Node child = n.getFirstChild(); child != null; child = child.getNext()) {
        if(!isLiteralValue(child, includeFunctions)) {
          return false;
        }
      }
      return true;
      case Token.OBJECTLIT:
      for(com.google.javascript.rhino.Node child = n.getFirstChild(); child != null; child = child.getNext()) {
        if(!isLiteralValue(child.getFirstChild(), includeFunctions)) {
          return false;
        }
      }
      return true;
      case Token.FUNCTION:
      return includeFunctions && !NodeUtil.isFunctionDeclaration(n);
      default:
      return isImmutableValue(n);
    }
  }
  static boolean isLoopStructure(Node n) {
    switch (n.getType()){
      case Token.FOR:
      case Token.DO:
      case Token.WHILE:
      return true;
      default:
      return false;
    }
  }
  static boolean isNaN(Node n) {
    if((n.isName() && n.getString().equals("NaN")) || (n.getType() == Token.DIV && n.getFirstChild().isNumber() && n.getFirstChild().getDouble() == 0 && n.getLastChild().isNumber() && n.getLastChild().getDouble() == 0)) {
      return true;
    }
    return false;
  }
  static boolean isNameReferenced(Node node, String name) {
    return isNameReferenced(node, name, Predicates.<Node>alwaysTrue());
  }
  static boolean isNameReferenced(Node node, String name, Predicate<Node> traverseChildrenPred) {
    return has(node, new MatchNameNode(name), traverseChildrenPred);
  }
  static boolean isNullOrUndefined(Node n) {
    return n.isNull() || isUndefined(n);
  }
  static boolean isNumericResult(Node n) {
    return allResultsMatch(n, NUMBERIC_RESULT_PREDICATE);
  }
  static boolean isNumericResultHelper(Node n) {
    switch (n.getType()){
      case Token.ADD:
      return !mayBeString(n.getFirstChild()) && !mayBeString(n.getLastChild());
      case Token.BITNOT:
      case Token.BITOR:
      case Token.BITXOR:
      case Token.BITAND:
      case Token.LSH:
      case Token.RSH:
      case Token.URSH:
      case Token.SUB:
      case Token.MUL:
      case Token.MOD:
      case Token.DIV:
      case Token.INC:
      case Token.DEC:
      case Token.POS:
      case Token.NEG:
      case Token.NUMBER:
      return true;
      case Token.NAME:
      String name = n.getString();
      if(name.equals("NaN")) {
        return true;
      }
      if(name.equals("Infinity")) {
        return true;
      }
      return false;
      default:
      return false;
    }
  }
  static boolean isObjectCallMethod(Node callNode, String methodName) {
    if(callNode.isCall()) {
      Node functionIndentifyingExpression = callNode.getFirstChild();
      if(isGet(functionIndentifyingExpression)) {
        Node last = functionIndentifyingExpression.getLastChild();
        if(last != null && last.isString()) {
          String propName = last.getString();
          return (propName.equals(methodName));
        }
      }
    }
    return false;
  }
  static boolean isObjectLitKey(Node node, Node parent) {
    switch (node.getType()){
      case Token.STRING_KEY:
      case Token.GETTER_DEF:
      case Token.SETTER_DEF:
      return true;
    }
    return false;
  }
  static boolean isPrototypeProperty(Node n) {
    String lhsString = n.getQualifiedName();
    if(lhsString == null) {
      return false;
    }
    int prototypeIdx = lhsString.indexOf(".prototype.");
    return prototypeIdx != -1;
  }
  static boolean isPrototypePropertyDeclaration(Node n) {
    if(!isExprAssign(n)) {
      return false;
    }
    return isPrototypeProperty(n.getFirstChild().getFirstChild());
  }
  static boolean isReferenceName(Node n) {
    return n.isName() && !n.getString().isEmpty();
  }
  static boolean isRelationalOperation(Node n) {
    switch (n.getType()){
      case Token.GT:
      case Token.GE:
      case Token.LT:
      case Token.LE:
      return true;
    }
    return false;
  }
  static boolean isSimpleOperator(Node n) {
    return isSimpleOperatorType(n.getType());
  }
  static boolean isSimpleOperatorType(int type) {
    switch (type){
      case Token.ADD:
      case Token.BITAND:
      case Token.BITNOT:
      case Token.BITOR:
      case Token.BITXOR:
      case Token.COMMA:
      case Token.DIV:
      case Token.EQ:
      case Token.GE:
      case Token.GETELEM:
      case Token.GETPROP:
      case Token.GT:
      case Token.INSTANCEOF:
      case Token.LE:
      case Token.LSH:
      case Token.LT:
      case Token.MOD:
      case Token.MUL:
      case Token.NE:
      case Token.NOT:
      case Token.RSH:
      case Token.SHEQ:
      case Token.SHNE:
      case Token.SUB:
      case Token.TYPEOF:
      case Token.VOID:
      case Token.POS:
      case Token.NEG:
      case Token.URSH:
      return true;
      default:
      return false;
    }
  }
  static boolean isStatement(Node n) {
    return isStatementParent(n.getParent());
  }
  static boolean isStatementBlock(Node n) {
    return n.isScript() || n.isBlock();
  }
  static boolean isStatementParent(Node parent) {
    Preconditions.checkState(parent != null);
    switch (parent.getType()){
      case Token.SCRIPT:
      case Token.BLOCK:
      case Token.LABEL:
      return true;
      default:
      return false;
    }
  }
  static boolean isSwitchCase(Node n) {
    return n.isCase() || n.isDefaultCase();
  }
  static boolean isSymmetricOperation(Node n) {
    switch (n.getType()){
      case Token.EQ:
      case Token.NE:
      case Token.SHEQ:
      case Token.SHNE:
      case Token.MUL:
      return true;
    }
    return false;
  }
  private static boolean isToStringMethodCall(Node call) {
    Node getNode = call.getFirstChild();
    if(isGet(getNode)) {
      Node propNode = getNode.getLastChild();
      return propNode.isString() && "toString".equals(propNode.getString());
    }
    return false;
  }
  static boolean isTryCatchNodeContainer(Node n) {
    Node parent = n.getParent();
    return parent.isTry() && parent.getFirstChild().getNext() == n;
  }
  static boolean isTryFinallyNode(Node parent, Node child) {
    return parent.isTry() && parent.getChildCount() == 3 && child == parent.getLastChild();
  }
  static boolean isUndefined(Node n) {
    switch (n.getType()){
      case Token.VOID:
      return true;
      case Token.NAME:
      return n.getString().equals("undefined");
    }
    return false;
  }
  static boolean isValidDefineValue(Node val, Set<String> defines) {
    switch (val.getType()){
      case Token.STRING:
      case Token.NUMBER:
      case Token.TRUE:
      case Token.FALSE:
      return true;
      case Token.ADD:
      case Token.BITAND:
      case Token.BITNOT:
      case Token.BITOR:
      case Token.BITXOR:
      case Token.DIV:
      case Token.EQ:
      case Token.GE:
      case Token.GT:
      case Token.LE:
      case Token.LSH:
      case Token.LT:
      case Token.MOD:
      case Token.MUL:
      case Token.NE:
      case Token.RSH:
      case Token.SHEQ:
      case Token.SHNE:
      case Token.SUB:
      case Token.URSH:
      return isValidDefineValue(val.getFirstChild(), defines) && isValidDefineValue(val.getLastChild(), defines);
      case Token.NOT:
      case Token.NEG:
      case Token.POS:
      return isValidDefineValue(val.getFirstChild(), defines);
      case Token.NAME:
      case Token.GETPROP:
      if(val.isQualifiedName()) {
        return defines.contains(val.getQualifiedName());
      }
    }
    return false;
  }
  static boolean isValidPropertyName(String name) {
    return isValidSimpleName(name);
  }
  public static boolean isValidQualifiedName(String name) {
    if(name.endsWith(".") || name.startsWith(".")) {
      return false;
    }
    String[] parts = name.split("\\.");
    for (String part : parts) {
      if(!isValidSimpleName(part)) {
        return false;
      }
    }
    return true;
  }
  static boolean isValidSimpleName(String name) {
    return TokenStream.isJSIdentifier(name) && !TokenStream.isKeyword(name) && isLatin(name);
  }
  static boolean isVarArgsFunction(Node function) {
    Preconditions.checkArgument(function.isFunction());
    return isNameReferenced(function.getLastChild(), "arguments", MATCH_NOT_FUNCTION);
  }
  static boolean isVarDeclaration(Node n) {
    return n.isName() && n.getParent().isVar();
  }
  static boolean isVarOrSimpleAssignLhs(Node n, Node parent) {
    return (parent.isAssign() && parent.getFirstChild() == n) || parent.isVar();
  }
  static boolean isWithinLoop(Node n) {
    for (Node parent : n.getAncestors()) {
      if(NodeUtil.isLoopStructure(parent)) {
        return true;
      }
      if(parent.isFunction()) {
        break ;
      }
    }
    return false;
  }
  static boolean mayBeString(Node n) {
    return mayBeString(n, true);
  }
  static boolean mayBeString(Node n, boolean recurse) {
    if(recurse) {
      return anyResultsMatch(n, MAY_BE_STRING_PREDICATE);
    }
    else {
      return mayBeStringHelper(n);
    }
  }
  static boolean mayBeStringHelper(Node n) {
    return !isNumericResult(n) && !isBooleanResult(n) && !isUndefined(n) && !n.isNull();
  }
  static boolean mayEffectMutableState(Node n) {
    return mayEffectMutableState(n, null);
  }
  static boolean mayEffectMutableState(Node n, AbstractCompiler compiler) {
    return checkForStateChangeHelper(n, true, compiler);
  }
  static boolean mayHaveSideEffects(Node n) {
    return mayHaveSideEffects(n, null);
  }
  static boolean mayHaveSideEffects(Node n, AbstractCompiler compiler) {
    return checkForStateChangeHelper(n, false, compiler);
  }
  static boolean newHasLocalResult(Node n) {
    Preconditions.checkState(n.isNew());
    return n.isOnlyModifiesThisCall();
  }
  static boolean nodeTypeMayHaveSideEffects(Node n) {
    return nodeTypeMayHaveSideEffects(n, null);
  }
  static boolean nodeTypeMayHaveSideEffects(Node n, AbstractCompiler compiler) {
    if(isAssignmentOp(n)) {
      return true;
    }
    switch (n.getType()){
      case Token.DELPROP:
      case Token.DEC:
      case Token.INC:
      case Token.THROW:
      return true;
      case Token.CALL:
      return NodeUtil.functionCallHasSideEffects(n, compiler);
      case Token.NEW:
      return NodeUtil.constructorCallHasSideEffects(n, compiler);
      case Token.NAME:
      return n.hasChildren();
      default:
      return false;
    }
  }
  static boolean referencesThis(Node n) {
    Node start = (n.isFunction()) ? n.getLastChild() : n;
    return containsType(start, Token.THIS, MATCH_NOT_FUNCTION);
  }
  static boolean tryMergeBlock(Node block) {
    Preconditions.checkState(block.isBlock());
    Node parent = block.getParent();
    if(isStatementBlock(parent)) {
      Node previous = block;
      while(block.hasChildren()){
        Node child = block.removeFirstChild();
        parent.addChildAfter(child, previous);
        previous = child;
      }
      parent.removeChild(block);
      return true;
    }
    else {
      return false;
    }
  }
  static int getCount(Node n, Predicate<Node> pred, Predicate<Node> traverseChildrenPred) {
    int total = 0;
    if(pred.apply(n)) {
      total++;
    }
    if(traverseChildrenPred.apply(n)) {
      for(com.google.javascript.rhino.Node c = n.getFirstChild(); c != null; c = c.getNext()) {
        total += getCount(c, pred, traverseChildrenPred);
      }
    }
    return total;
  }
  static int getInverseOperator(int type) {
    switch (type){
      case Token.GT:
      return Token.LT;
      case Token.LT:
      return Token.GT;
      case Token.GE:
      return Token.LE;
      case Token.LE:
      return Token.GE;
    }
    return Token.ERROR;
  }
  static int getNameReferenceCount(Node node, String name) {
    return getCount(node, new MatchNameNode(name), Predicates.<Node>alwaysTrue());
  }
  static int getNodeTypeReferenceCount(Node node, int type, Predicate<Node> traverseChildrenPred) {
    return getCount(node, new MatchNodeType(type), traverseChildrenPred);
  }
  static int getOpFromAssignmentOp(Node n) {
    switch (n.getType()){
      case Token.ASSIGN_BITOR:
      return Token.BITOR;
      case Token.ASSIGN_BITXOR:
      return Token.BITXOR;
      case Token.ASSIGN_BITAND:
      return Token.BITAND;
      case Token.ASSIGN_LSH:
      return Token.LSH;
      case Token.ASSIGN_RSH:
      return Token.RSH;
      case Token.ASSIGN_URSH:
      return Token.URSH;
      case Token.ASSIGN_ADD:
      return Token.ADD;
      case Token.ASSIGN_SUB:
      return Token.SUB;
      case Token.ASSIGN_MUL:
      return Token.MUL;
      case Token.ASSIGN_DIV:
      return Token.DIV;
      case Token.ASSIGN_MOD:
      return Token.MOD;
    }
    throw new IllegalArgumentException("Not an assignment op:" + n);
  }
  static int precedence(int type) {
    switch (type){
      case Token.COMMA:
      return 0;
      case Token.ASSIGN_BITOR:
      case Token.ASSIGN_BITXOR:
      case Token.ASSIGN_BITAND:
      case Token.ASSIGN_LSH:
      case Token.ASSIGN_RSH:
      case Token.ASSIGN_URSH:
      case Token.ASSIGN_ADD:
      case Token.ASSIGN_SUB:
      case Token.ASSIGN_MUL:
      case Token.ASSIGN_DIV:
      case Token.ASSIGN_MOD:
      case Token.ASSIGN:
      return 1;
      case Token.HOOK:
      return 2;
      case Token.OR:
      return 3;
      case Token.AND:
      return 4;
      case Token.BITOR:
      return 5;
      case Token.BITXOR:
      return 6;
      case Token.BITAND:
      return 7;
      case Token.EQ:
      case Token.NE:
      case Token.SHEQ:
      case Token.SHNE:
      return 8;
      case Token.LT:
      case Token.GT:
      case Token.LE:
      case Token.GE:
      case Token.INSTANCEOF:
      case Token.IN:
      return 9;
      case Token.LSH:
      case Token.RSH:
      case Token.URSH:
      return 10;
      case Token.SUB:
      case Token.ADD:
      return 11;
      case Token.MUL:
      case Token.MOD:
      case Token.DIV:
      return 12;
      case Token.INC:
      case Token.DEC:
      case Token.NEW:
      case Token.DELPROP:
      case Token.TYPEOF:
      case Token.VOID:
      case Token.NOT:
      case Token.BITNOT:
      case Token.POS:
      case Token.NEG:
      return 13;
      case Token.CALL:
      case Token.GETELEM:
      case Token.GETPROP:
      case Token.ARRAYLIT:
      case Token.EMPTY:
      case Token.FALSE:
      case Token.FUNCTION:
      case Token.NAME:
      case Token.NULL:
      case Token.NUMBER:
      case Token.OBJECTLIT:
      case Token.REGEXP:
      case Token.STRING:
      case Token.STRING_KEY:
      case Token.THIS:
      case Token.TRUE:
      return 15;
      case Token.CAST:
      return 16;
      default:
      throw new Error("Unknown precedence for " + Token.name(type) + " (type " + type + ")");
    }
  }
  static void copyNameAnnotations(Node source, Node destination) {
    if(source.getBooleanProp(Node.IS_CONSTANT_NAME)) {
      destination.putBooleanProp(Node.IS_CONSTANT_NAME, true);
    }
  }
  static void maybeAddFinally(Node tryNode) {
    Preconditions.checkState(tryNode.isTry());
    if(!NodeUtil.hasFinally(tryNode)) {
      tryNode.addChildrenToBack(IR.block().srcref(tryNode));
    }
  }
  static void redeclareVarsInsideBranch(Node branch) {
    Collection<Node> vars = getVarsDeclaredInBranch(branch);
    if(vars.isEmpty()) {
      return ;
    }
    Node parent = getAddingRoot(branch);
    for (Node nameNode : vars) {
      Node var = IR.var(IR.name(nameNode.getString()).srcref(nameNode)).srcref(nameNode);
      copyNameAnnotations(nameNode, var.getFirstChild());
      parent.addChildToFront(var);
    }
  }
  static void removeChild(Node parent, Node node) {
    if(isTryFinallyNode(parent, node)) {
      if(NodeUtil.hasCatchHandler(getCatchBlock(parent))) {
        parent.removeChild(node);
      }
      else {
        node.detachChildren();
      }
    }
    else 
      if(node.isCatch()) {
        Node tryNode = node.getParent().getParent();
        Preconditions.checkState(NodeUtil.hasFinally(tryNode));
        node.detachFromParent();
      }
      else 
        if(isTryCatchNodeContainer(node)) {
          Node tryNode = node.getParent();
          Preconditions.checkState(NodeUtil.hasFinally(tryNode));
          node.detachChildren();
        }
        else 
          if(node.isBlock()) {
            node.detachChildren();
          }
          else 
            if(isStatementBlock(parent) || isSwitchCase(node)) {
              parent.removeChild(node);
            }
            else 
              if(parent.isVar()) {
                if(parent.hasMoreThanOneChild()) {
                  parent.removeChild(node);
                }
                else {
                  parent.removeChild(node);
                  removeChild(parent.getParent(), parent);
                }
              }
              else 
                if(parent.isLabel() && node == parent.getLastChild()) {
                  parent.removeChild(node);
                  removeChild(parent.getParent(), parent);
                }
                else 
                  if(parent.isFor() && parent.getChildCount() == 4) {
                    parent.replaceChild(node, IR.empty());
                  }
                  else {
                    throw new IllegalStateException("Invalid attempt to remove node: " + node.toString() + " of " + parent.toString());
                  }
  }
  static void setDebugInformation(Node node, Node basisNode, String originalName) {
    node.copyInformationFromForTree(basisNode);
    node.putProp(Node.ORIGINALNAME_PROP, originalName);
  }
  static void visitPostOrder(Node node, Visitor visitor, Predicate<Node> traverseChildrenPred) {
    if(traverseChildrenPred.apply(node)) {
      for(com.google.javascript.rhino.Node c = node.getFirstChild(); c != null; c = c.getNext()) {
        visitPostOrder(c, visitor, traverseChildrenPred);
      }
    }
    visitor.visit(node);
  }
  static void visitPreOrder(Node node, Visitor visitor, Predicate<Node> traverseChildrenPred) {
    visitor.visit(node);
    if(traverseChildrenPred.apply(node)) {
      for(com.google.javascript.rhino.Node c = node.getFirstChild(); c != null; c = c.getNext()) {
        visitPreOrder(c, visitor, traverseChildrenPred);
      }
    }
  }
  
  static class BooleanResultPredicate implements Predicate<Node>  {
    @Override() public boolean apply(Node n) {
      return isBooleanResultHelper(n);
    }
  }
  
  static class MatchDeclaration implements Predicate<Node>  {
    @Override() public boolean apply(Node n) {
      return isFunctionDeclaration(n) || n.isVar();
    }
  }
  
  private static class MatchNameNode implements Predicate<Node>  {
    final String name;
    MatchNameNode(String name) {
      super();
      this.name = name;
    }
    @Override() public boolean apply(Node n) {
      return n.isName() && n.getString().equals(name);
    }
  }
  
  static class MatchNodeType implements Predicate<Node>  {
    final int type;
    MatchNodeType(int type) {
      super();
      this.type = type;
    }
    @Override() public boolean apply(Node n) {
      return n.getType() == type;
    }
  }
  
  private static class MatchNotFunction implements Predicate<Node>  {
    @Override() public boolean apply(Node n) {
      return !n.isFunction();
    }
  }
  
  static class MatchShallowStatement implements Predicate<Node>  {
    @Override() public boolean apply(Node n) {
      Node parent = n.getParent();
      return n.isBlock() || (!n.isFunction() && (parent == null || isControlStructure(parent) || isStatementBlock(parent)));
    }
  }
  
  static class MayBeStringResultPredicate implements Predicate<Node>  {
    @Override() public boolean apply(Node n) {
      return mayBeStringHelper(n);
    }
  }
  
  static class NumbericResultPredicate implements Predicate<Node>  {
    @Override() public boolean apply(Node n) {
      return isNumericResultHelper(n);
    }
  }
  
  private static class VarCollector implements Visitor  {
    final Map<String, Node> vars = Maps.newLinkedHashMap();
    @Override() public void visit(Node n) {
      if(n.isName()) {
        Node parent = n.getParent();
        if(parent != null && parent.isVar()) {
          String name = n.getString();
          if(!vars.containsKey(name)) {
            vars.put(name, n);
          }
        }
      }
    }
  }
  
  static interface Visitor  {
    void visit(Node node);
  }
}