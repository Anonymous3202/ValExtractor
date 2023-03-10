package com.google.javascript.jscomp;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import java.util.List;
import java.util.Locale;

class PeepholeReplaceKnownMethods extends AbstractPeepholeOptimization  {
  final private static Locale ROOT_LOCALE = new Locale("");
  final private boolean late;
  PeepholeReplaceKnownMethods(boolean late) {
    super();
    this.late = late;
  }
  @Override() Node optimizeSubtree(Node subtree) {
    if(subtree.isCall()) {
      return tryFoldKnownMethods(subtree);
    }
    return subtree;
  }
  private Node tryFoldArrayJoin(Node n) {
    Node callTarget = n.getFirstChild();
    if(callTarget == null || !callTarget.isGetProp()) {
      return n;
    }
    Node right = callTarget.getNext();
    if(right != null) {
      if(right.getNext() != null || !NodeUtil.isImmutableValue(right)) {
        return n;
      }
    }
    Node arrayNode = callTarget.getFirstChild();
    Node functionName = arrayNode.getNext();
    if(!arrayNode.isArrayLit() || !functionName.getString().equals("join")) {
      return n;
    }
    if(right != null && right.isString() && ",".equals(right.getString())) {
      n.removeChild(right);
      reportCodeChange();
    }
    String joinString = (right == null) ? "," : NodeUtil.getStringValue(right);
    List<Node> arrayFoldedChildren = Lists.newLinkedList();
    StringBuilder sb = null;
    int foldedSize = 0;
    Node prev = null;
    Node elem = arrayNode.getFirstChild();
    while(elem != null){
      if(NodeUtil.isImmutableValue(elem) || elem.isEmpty()) {
        if(sb == null) {
          sb = new StringBuilder();
        }
        else {
          sb.append(joinString);
        }
        sb.append(NodeUtil.getArrayElementStringValue(elem));
      }
      else {
        if(sb != null) {
          Preconditions.checkNotNull(prev);
          foldedSize += sb.length() + 2;
          arrayFoldedChildren.add(IR.string(sb.toString()).copyInformationFrom(prev));
          sb = null;
        }
        foldedSize += InlineCostEstimator.getCost(elem);
        arrayFoldedChildren.add(elem);
      }
      prev = elem;
      elem = elem.getNext();
    }
    if(sb != null) {
      Preconditions.checkNotNull(prev);
      foldedSize += sb.length() + 2;
      arrayFoldedChildren.add(IR.string(sb.toString()).copyInformationFrom(prev));
    }
    foldedSize += arrayFoldedChildren.size() - 1;
    int originalSize = InlineCostEstimator.getCost(n);
    switch (arrayFoldedChildren.size()){
      case 0:
      Node emptyStringNode = IR.string("");
      n.getParent().replaceChild(n, emptyStringNode);
      reportCodeChange();
      return emptyStringNode;
      case 1:
      Node foldedStringNode = arrayFoldedChildren.remove(0);
      if(foldedSize > originalSize) {
        return n;
      }
      arrayNode.detachChildren();
      if(!foldedStringNode.isString()) {
        Node replacement = IR.add(IR.string("").srcref(n), foldedStringNode);
        foldedStringNode = replacement;
      }
      n.getParent().replaceChild(n, foldedStringNode);
      reportCodeChange();
      return foldedStringNode;
      default:
      if(arrayFoldedChildren.size() == arrayNode.getChildCount()) {
        return n;
      }
      int kJoinOverhead = "[].join()".length();
      foldedSize += kJoinOverhead;
      foldedSize += (right != null) ? InlineCostEstimator.getCost(right) : 0;
      if(foldedSize > originalSize) {
        return n;
      }
      arrayNode.detachChildren();
      for (Node node : arrayFoldedChildren) {
        arrayNode.addChildToBack(node);
      }
      reportCodeChange();
      break ;
    }
    return n;
  }
  private Node tryFoldKnownMethods(Node subtree) {
    subtree = tryFoldArrayJoin(subtree);
    if(subtree.isCall()) {
      Node callTarget = subtree.getFirstChild();
      if(callTarget == null) {
        return subtree;
      }
      if(NodeUtil.isGet(callTarget)) {
        subtree = tryFoldKnownStringMethods(subtree);
      }
      else {
        subtree = tryFoldKnownNumericMethods(subtree);
      }
    }
    return subtree;
  }
  private Node tryFoldKnownNumericMethods(Node subtree) {
    Preconditions.checkArgument(subtree.isCall());
    if(isASTNormalized()) {
      Node callTarget = subtree.getFirstChild();
      if(!callTarget.isName()) {
        return subtree;
      }
      String functionNameString = callTarget.getString();
      Node firstArgument = callTarget.getNext();
      if((firstArgument != null) && (firstArgument.isString() || firstArgument.isNumber())) {
        if(functionNameString.equals("parseInt") || functionNameString.equals("parseFloat")) {
          subtree = tryFoldParseNumber(subtree, functionNameString, firstArgument);
        }
      }
    }
    return subtree;
  }
  private Node tryFoldKnownStringMethods(Node subtree) {
    Preconditions.checkArgument(subtree.isCall());
    Node callTarget = subtree.getFirstChild();
    if(callTarget == null) {
      return subtree;
    }
    if(!NodeUtil.isGet(callTarget)) {
      return subtree;
    }
    Node stringNode = callTarget.getFirstChild();
    Node functionName = stringNode.getNext();
    if((!stringNode.isString()) || (!functionName.isString())) {
      return subtree;
    }
    String functionNameString = functionName.getString();
    Node firstArg = callTarget.getNext();
    if(functionNameString.equals("split")) {
      subtree = tryFoldStringSplit(subtree, stringNode, firstArg);
    }
    else 
      if(firstArg == null) {
        if(functionNameString.equals("toLowerCase")) {
          subtree = tryFoldStringToLowerCase(subtree, stringNode);
        }
        else 
          if(functionNameString.equals("toUpperCase")) {
            subtree = tryFoldStringToUpperCase(subtree, stringNode);
          }
        return subtree;
      }
      else 
        if(NodeUtil.isImmutableValue(firstArg)) {
          if(functionNameString.equals("indexOf") || functionNameString.equals("lastIndexOf")) {
            subtree = tryFoldStringIndexOf(subtree, functionNameString, stringNode, firstArg);
          }
          else 
            if(functionNameString.equals("substr")) {
              subtree = tryFoldStringSubstr(subtree, stringNode, firstArg);
            }
            else 
              if(functionNameString.equals("substring")) {
                subtree = tryFoldStringSubstring(subtree, stringNode, firstArg);
              }
              else 
                if(functionNameString.equals("charAt")) {
                  subtree = tryFoldStringCharAt(subtree, stringNode, firstArg);
                }
                else 
                  if(functionNameString.equals("charCodeAt")) {
                    subtree = tryFoldStringCharCodeAt(subtree, stringNode, firstArg);
                  }
        }
    return subtree;
  }
  private Node tryFoldParseNumber(Node n, String functionName, Node firstArg) {
    Preconditions.checkArgument(n.isCall());
    boolean isParseInt = functionName.equals("parseInt");
    Node secondArg = firstArg.getNext();
    int radix = 0;
    if(secondArg != null) {
      if(!isParseInt) {
        return n;
      }
      if(secondArg.getNext() != null || !secondArg.isNumber()) {
        return n;
      }
      else {
        double tmpRadix = secondArg.getDouble();
        if(tmpRadix != (int)tmpRadix) 
          return n;
        radix = (int)tmpRadix;
        if(radix < 0 || radix == 1 || radix > 36) {
          return n;
        }
      }
    }
    String stringVal = null;
    Double checkVal;
    if(firstArg.isNumber()) {
      checkVal = NodeUtil.getNumberValue(firstArg);
      if(!(radix == 0 || radix == 10) && isParseInt) {
        stringVal = String.valueOf(checkVal.intValue());
      }
      else {
        Node numericNode;
        if(isParseInt) {
          numericNode = IR.number(checkVal.intValue());
        }
        else {
          numericNode = IR.number(checkVal);
        }
        n.getParent().replaceChild(n, numericNode);
        reportCodeChange();
        return numericNode;
      }
    }
    else {
      stringVal = NodeUtil.getStringValue(firstArg);
      if(stringVal == null) {
        return n;
      }
      checkVal = NodeUtil.getStringNumberValue(stringVal);
      if(checkVal == null) {
        return n;
      }
      stringVal = NodeUtil.trimJsWhiteSpace(stringVal);
      int var_1712 = stringVal.length();
      if(var_1712 == 0) {
        return n;
      }
    }
    Node newNode;
    if(stringVal.equals("0")) {
      newNode = IR.number(0);
    }
    else 
      if(isParseInt) {
        if(radix == 0 || radix == 16) {
          if(stringVal.length() > 1 && stringVal.substring(0, 2).equalsIgnoreCase("0x")) {
            radix = 16;
            stringVal = stringVal.substring(2);
          }
          else 
            if(radix == 0) {
              if(!isEcmaScript5OrGreater() && stringVal.substring(0, 1).equals("0")) {
                return n;
              }
              radix = 10;
            }
        }
        int newVal = 0;
        try {
          newVal = Integer.parseInt(stringVal, radix);
        }
        catch (NumberFormatException e) {
          return n;
        }
        newNode = IR.number(newVal);
      }
      else {
        String normalizedNewVal = "0";
        try {
          double newVal = Double.parseDouble(stringVal);
          newNode = IR.number(newVal);
          normalizedNewVal = normalizeNumericString(String.valueOf(newVal));
        }
        catch (NumberFormatException e) {
          return n;
        }
        if(!normalizeNumericString(stringVal).equals(normalizedNewVal)) {
          return n;
        }
      }
    n.getParent().replaceChild(n, newNode);
    reportCodeChange();
    return newNode;
  }
  private Node tryFoldStringCharAt(Node n, Node stringNode, Node arg1) {
    Preconditions.checkArgument(n.isCall());
    Preconditions.checkArgument(stringNode.isString());
    int index;
    String stringAsString = stringNode.getString();
    if(arg1 != null && arg1.isNumber() && arg1.getNext() == null) {
      index = (int)arg1.getDouble();
    }
    else {
      return n;
    }
    if(index < 0 || stringAsString.length() <= index) {
      return n;
    }
    Node resultNode = IR.string(stringAsString.substring(index, index + 1));
    Node parent = n.getParent();
    parent.replaceChild(n, resultNode);
    reportCodeChange();
    return resultNode;
  }
  private Node tryFoldStringCharCodeAt(Node n, Node stringNode, Node arg1) {
    Preconditions.checkArgument(n.isCall());
    Preconditions.checkArgument(stringNode.isString());
    int index;
    String stringAsString = stringNode.getString();
    if(arg1 != null && arg1.isNumber() && arg1.getNext() == null) {
      index = (int)arg1.getDouble();
    }
    else {
      return n;
    }
    if(index < 0 || stringAsString.length() <= index) {
      return n;
    }
    Node resultNode = IR.number(stringAsString.charAt(index));
    Node parent = n.getParent();
    parent.replaceChild(n, resultNode);
    reportCodeChange();
    return resultNode;
  }
  private Node tryFoldStringIndexOf(Node n, String functionName, Node lstringNode, Node firstArg) {
    Preconditions.checkArgument(n.isCall());
    Preconditions.checkArgument(lstringNode.isString());
    String lstring = NodeUtil.getStringValue(lstringNode);
    boolean isIndexOf = functionName.equals("indexOf");
    Node secondArg = firstArg.getNext();
    String searchValue = NodeUtil.getStringValue(firstArg);
    if(searchValue == null) {
      return n;
    }
    int fromIndex = isIndexOf ? 0 : lstring.length();
    if(secondArg != null) {
      if(secondArg.getNext() != null || !secondArg.isNumber()) {
        return n;
      }
      else {
        fromIndex = (int)secondArg.getDouble();
      }
    }
    int indexVal = isIndexOf ? lstring.indexOf(searchValue, fromIndex) : lstring.lastIndexOf(searchValue, fromIndex);
    Node newNode = IR.number(indexVal);
    n.getParent().replaceChild(n, newNode);
    reportCodeChange();
    return newNode;
  }
  private Node tryFoldStringSplit(Node n, Node stringNode, Node arg1) {
    if(late) {
      return n;
    }
    Preconditions.checkArgument(n.isCall());
    Preconditions.checkArgument(stringNode.isString());
    String separator = null;
    String stringValue = stringNode.getString();
    int limit = stringValue.length() + 1;
    if(arg1 != null) {
      if(arg1.isString()) {
        separator = arg1.getString();
      }
      else 
        if(!arg1.isNull()) {
          return n;
        }
      Node arg2 = arg1.getNext();
      if(arg2 != null) {
        if(arg2.isNumber()) {
          limit = Math.min((int)arg2.getDouble(), limit);
          if(limit < 0) {
            return n;
          }
        }
        else {
          return n;
        }
      }
    }
    String[] stringArray = jsSplit(stringValue, separator, limit);
    Node arrayOfStrings = IR.arraylit();
    for(int i = 0; i < stringArray.length; i++) {
      arrayOfStrings.addChildToBack(IR.string(stringArray[i]).srcref(stringNode));
    }
    Node parent = n.getParent();
    parent.replaceChild(n, arrayOfStrings);
    reportCodeChange();
    return arrayOfStrings;
  }
  private Node tryFoldStringSubstr(Node n, Node stringNode, Node arg1) {
    Preconditions.checkArgument(n.isCall());
    Preconditions.checkArgument(stringNode.isString());
    int start;
    int length;
    String stringAsString = stringNode.getString();
    if(arg1 != null && arg1.isNumber()) {
      start = (int)arg1.getDouble();
    }
    else {
      return n;
    }
    Node arg2 = arg1.getNext();
    if(arg2 != null) {
      if(arg2.isNumber()) {
        length = (int)arg2.getDouble();
      }
      else {
        return n;
      }
      if(arg2.getNext() != null) {
        return n;
      }
    }
    else {
      length = stringAsString.length() - start;
    }
    if((start + length) > stringAsString.length() || (length < 0) || (start < 0)) {
      return n;
    }
    String result = stringAsString.substring(start, start + length);
    Node resultNode = IR.string(result);
    Node parent = n.getParent();
    parent.replaceChild(n, resultNode);
    reportCodeChange();
    return resultNode;
  }
  private Node tryFoldStringSubstring(Node n, Node stringNode, Node arg1) {
    Preconditions.checkArgument(n.isCall());
    Preconditions.checkArgument(stringNode.isString());
    int start;
    int end;
    String stringAsString = stringNode.getString();
    if(arg1 != null && arg1.isNumber()) {
      start = (int)arg1.getDouble();
    }
    else {
      return n;
    }
    Node arg2 = arg1.getNext();
    if(arg2 != null) {
      if(arg2.isNumber()) {
        end = (int)arg2.getDouble();
      }
      else {
        return n;
      }
      if(arg2.getNext() != null) {
        return n;
      }
    }
    else {
      end = stringAsString.length();
    }
    if((end > stringAsString.length()) || (start > stringAsString.length()) || (end < 0) || (start < 0)) {
      return n;
    }
    String result = stringAsString.substring(start, end);
    Node resultNode = IR.string(result);
    Node parent = n.getParent();
    parent.replaceChild(n, resultNode);
    reportCodeChange();
    return resultNode;
  }
  private Node tryFoldStringToLowerCase(Node subtree, Node stringNode) {
    String lowered = stringNode.getString().toLowerCase(ROOT_LOCALE);
    Node replacement = IR.string(lowered);
    subtree.getParent().replaceChild(subtree, replacement);
    reportCodeChange();
    return replacement;
  }
  private Node tryFoldStringToUpperCase(Node subtree, Node stringNode) {
    String upped = stringNode.getString().toUpperCase(ROOT_LOCALE);
    Node replacement = IR.string(upped);
    subtree.getParent().replaceChild(subtree, replacement);
    reportCodeChange();
    return replacement;
  }
  private String normalizeNumericString(String input) {
    if(input == null || input.length() == 0) {
      return input;
    }
    int startIndex = 0;
    int endIndex = input.length() - 1;
    while(startIndex < input.length() && input.charAt(startIndex) == '0' && input.charAt(startIndex) != '.'){
      startIndex++;
    }
    if(input.indexOf('.') >= 0) {
      while(endIndex >= 0 && input.charAt(endIndex) == '0'){
        endIndex--;
      }
      if(input.charAt(endIndex) == '.') {
        endIndex--;
      }
    }
    if(startIndex >= endIndex) {
      return input;
    }
    return input.substring(startIndex, endIndex + 1);
  }
  private String[] jsSplit(String stringValue, String separator, int limit) {
    Preconditions.checkArgument(limit >= 0);
    Preconditions.checkArgument(stringValue != null);
    if(limit == 0) {
      return new String[0];
    }
    if(separator == null) {
      return new String[]{ stringValue } ;
    }
    List<String> splitStrings = Lists.newArrayList();
    if(separator.length() == 0) {
      for(int i = 0; i < stringValue.length() && i < limit; i++) {
        splitStrings.add(stringValue.substring(i, i + 1));
      }
    }
    else {
      int startIndex = 0;
      int matchIndex;
      while((matchIndex = jsSplitMatch(stringValue, startIndex, separator)) >= 0 && splitStrings.size() < limit){
        splitStrings.add(stringValue.substring(startIndex, matchIndex));
        startIndex = matchIndex + separator.length();
      }
      if(splitStrings.size() < limit) {
        if(startIndex < stringValue.length()) {
          splitStrings.add(stringValue.substring(startIndex));
        }
        else {
          splitStrings.add("");
        }
      }
    }
    return splitStrings.toArray(new String[splitStrings.size()]);
  }
  private int jsSplitMatch(String stringValue, int startIndex, String separator) {
    if(startIndex + separator.length() > stringValue.length()) {
      return -1;
    }
    int matchIndex = stringValue.indexOf(separator, startIndex);
    if(matchIndex < 0) {
      return -1;
    }
    return matchIndex;
  }
}