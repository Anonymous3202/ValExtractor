package com.google.javascript.jscomp;
import com.google.common.base.Preconditions;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.head.ScriptRuntime;
import com.google.javascript.rhino.jstype.TernaryValue;

class PeepholeFoldConstants extends AbstractPeepholeOptimization  {
  final static DiagnosticType INVALID_GETELEM_INDEX_ERROR = DiagnosticType.warning("JSC_INVALID_GETELEM_INDEX_ERROR", "Array index not integer: {0}");
  final static DiagnosticType INDEX_OUT_OF_BOUNDS_ERROR = DiagnosticType.warning("JSC_INDEX_OUT_OF_BOUNDS_ERROR", "Array index out of bounds: {0}");
  final static DiagnosticType NEGATING_A_NON_NUMBER_ERROR = DiagnosticType.warning("JSC_NEGATING_A_NON_NUMBER_ERROR", "Can\'t negate non-numeric value: {0}");
  final static DiagnosticType BITWISE_OPERAND_OUT_OF_RANGE = DiagnosticType.warning("JSC_BITWISE_OPERAND_OUT_OF_RANGE", "Operand out of range, bitwise operation will lose information: {0}");
  final static DiagnosticType SHIFT_AMOUNT_OUT_OF_BOUNDS = DiagnosticType.warning("JSC_SHIFT_AMOUNT_OUT_OF_BOUNDS", "Shift amount out of bounds: {0}");
  final static DiagnosticType FRACTIONAL_BITWISE_OPERAND = DiagnosticType.warning("JSC_FRACTIONAL_BITWISE_OPERAND", "Fractional bitwise operand: {0}");
  final private static double MAX_FOLD_NUMBER = Math.pow(2, 53);
  final private boolean late;
  PeepholeFoldConstants(boolean late) {
    super();
    this.late = late;
  }
  @Override() Node optimizeSubtree(Node subtree) {
    switch (subtree.getType()){
      case Token.NEW:
      return tryFoldCtorCall(subtree);
      case Token.TYPEOF:
      return tryFoldTypeof(subtree);
      case Token.NOT:
      case Token.POS:
      case Token.NEG:
      case Token.BITNOT:
      tryReduceOperandsForOp(subtree);
      return tryFoldUnaryOperator(subtree);
      case Token.VOID:
      return tryReduceVoid(subtree);
      default:
      tryReduceOperandsForOp(subtree);
      return tryFoldBinaryOperator(subtree);
    }
  }
  private Node performArithmeticOp(int opType, Node left, Node right) {
    if(opType == Token.ADD && (NodeUtil.mayBeString(left, false) || NodeUtil.mayBeString(right, false))) {
      return null;
    }
    double result;
    Double lValObj = NodeUtil.getNumberValue(left);
    if(lValObj == null) {
      return null;
    }
    Double rValObj = NodeUtil.getNumberValue(right);
    if(rValObj == null) {
      return null;
    }
    double lval = lValObj;
    double rval = rValObj;
    switch (opType){
      case Token.BITAND:
      result = ScriptRuntime.toInt32(lval) & ScriptRuntime.toInt32(rval);
      break ;
      case Token.BITOR:
      result = ScriptRuntime.toInt32(lval) | ScriptRuntime.toInt32(rval);
      break ;
      case Token.BITXOR:
      result = ScriptRuntime.toInt32(lval) ^ ScriptRuntime.toInt32(rval);
      break ;
      case Token.ADD:
      result = lval + rval;
      break ;
      case Token.SUB:
      result = lval - rval;
      break ;
      case Token.MUL:
      result = lval * rval;
      break ;
      case Token.MOD:
      if(rval == 0) {
        return null;
      }
      result = lval % rval;
      break ;
      case Token.DIV:
      if(rval == 0) {
        return null;
      }
      result = lval / rval;
      break ;
      default:
      throw new Error("Unexpected arithmetic operator");
    }
    if((String.valueOf(result).length() <= String.valueOf(lval).length() + String.valueOf(rval).length() + 1 && Math.abs(result) <= MAX_FOLD_NUMBER) || Double.isNaN(result) || result == Double.POSITIVE_INFINITY || result == Double.NEGATIVE_INFINITY) {
      return NodeUtil.numberNode(result, null);
    }
    return null;
  }
  private Node tryFoldAdd(Node node, Node left, Node right) {
    Preconditions.checkArgument(node.isAdd());
    if(NodeUtil.mayBeString(node, true)) {
      if(NodeUtil.isLiteralValue(left, false) && NodeUtil.isLiteralValue(right, false)) {
        return tryFoldAddConstantString(node, left, right);
      }
      else {
        return tryFoldChildAddString(node, left, right);
      }
    }
    else {
      Node result = tryFoldArithmeticOp(node, left, right);
      if(result != node) {
        return result;
      }
      return tryFoldLeftChildOp(node, left, right);
    }
  }
  private Node tryFoldAddConstantString(Node n, Node left, Node right) {
    if(left.isString() || right.isString()) {
      String leftString = NodeUtil.getStringValue(left);
      String rightString = NodeUtil.getStringValue(right);
      if(leftString != null && rightString != null) {
        Node newStringNode = IR.string(leftString + rightString);
        n.getParent().replaceChild(n, newStringNode);
        reportCodeChange();
        return newStringNode;
      }
    }
    return n;
  }
  private Node tryFoldAndOr(Node n, Node left, Node right) {
    Node parent = n.getParent();
    Node result = null;
    int type = n.getType();
    TernaryValue leftVal = NodeUtil.getImpureBooleanValue(left);
    if(leftVal != TernaryValue.UNKNOWN) {
      boolean lval = leftVal.toBoolean(true);
      if(lval && type == Token.OR || !lval && type == Token.AND) {
        result = left;
      }
      else 
        if(!mayHaveSideEffects(left)) {
          result = right;
        }
    }
    if(result != null) {
      n.removeChild(result);
      parent.replaceChild(n, result);
      reportCodeChange();
      return result;
    }
    else {
      return n;
    }
  }
  private Node tryFoldArithmeticOp(Node n, Node left, Node right) {
    Node result = performArithmeticOp(n.getType(), left, right);
    if(result != null) {
      result.copyInformationFromForTree(n);
      n.getParent().replaceChild(n, result);
      reportCodeChange();
      return result;
    }
    return n;
  }
  private Node tryFoldArrayAccess(Node n, Node left, Node right) {
    Node parent = n.getParent();
    if(isAssignmentTarget(n)) {
      return n;
    }
    if(!right.isNumber()) {
      return n;
    }
    double index = right.getDouble();
    int intIndex = (int)index;
    if(intIndex != index) {
      report(INVALID_GETELEM_INDEX_ERROR, right);
      return n;
    }
    if(intIndex < 0) {
      report(INDEX_OUT_OF_BOUNDS_ERROR, right);
      return n;
    }
    Node current = left.getFirstChild();
    Node elem = null;
    for(int i = 0; current != null; i++) {
      if(i != intIndex) {
        if(mayHaveSideEffects(current)) {
          return n;
        }
      }
      else {
        elem = current;
      }
      current = current.getNext();
    }
    if(elem == null) {
      report(INDEX_OUT_OF_BOUNDS_ERROR, right);
      return n;
    }
    if(elem.isEmpty()) {
      elem = NodeUtil.newUndefinedNode(elem);
    }
    else {
      left.removeChild(elem);
    }
    n.getParent().replaceChild(n, elem);
    reportCodeChange();
    return elem;
  }
  private Node tryFoldAssign(Node n, Node left, Node right) {
    Preconditions.checkArgument(n.isAssign());
    if(!late) {
      return n;
    }
    if(!right.hasChildren() || right.getFirstChild().getNext() != right.getLastChild()) {
      return n;
    }
    if(mayHaveSideEffects(left)) {
      return n;
    }
    Node newRight;
    if(areNodesEqualForInlining(left, right.getFirstChild())) {
      newRight = right.getLastChild();
    }
    else 
      if(NodeUtil.isCommutative(right.getType()) && areNodesEqualForInlining(left, right.getLastChild())) {
        newRight = right.getFirstChild();
      }
      else {
        return n;
      }
    int newType = -1;
    switch (right.getType()){
      case Token.ADD:
      newType = Token.ASSIGN_ADD;
      break ;
      case Token.BITAND:
      newType = Token.ASSIGN_BITAND;
      break ;
      case Token.BITOR:
      newType = Token.ASSIGN_BITOR;
      break ;
      case Token.BITXOR:
      newType = Token.ASSIGN_BITXOR;
      break ;
      case Token.DIV:
      newType = Token.ASSIGN_DIV;
      break ;
      case Token.LSH:
      newType = Token.ASSIGN_LSH;
      break ;
      case Token.MOD:
      newType = Token.ASSIGN_MOD;
      break ;
      case Token.MUL:
      newType = Token.ASSIGN_MUL;
      break ;
      case Token.RSH:
      newType = Token.ASSIGN_RSH;
      break ;
      case Token.SUB:
      newType = Token.ASSIGN_SUB;
      break ;
      case Token.URSH:
      newType = Token.ASSIGN_URSH;
      break ;
      default:
      return n;
    }
    Node newNode = new Node(newType, left.detachFromParent(), newRight.detachFromParent());
    n.getParent().replaceChild(n, newNode);
    reportCodeChange();
    return newNode;
  }
  private Node tryFoldBinaryOperator(Node subtree) {
    Node left = subtree.getFirstChild();
    if(left == null) {
      return subtree;
    }
    Node right = left.getNext();
    if(right == null) {
      return subtree;
    }
    switch (subtree.getType()){
      case Token.GETPROP:
      return tryFoldGetProp(subtree, left, right);
      case Token.GETELEM:
      return tryFoldGetElem(subtree, left, right);
      case Token.INSTANCEOF:
      return tryFoldInstanceof(subtree, left, right);
      case Token.AND:
      case Token.OR:
      return tryFoldAndOr(subtree, left, right);
      case Token.LSH:
      case Token.RSH:
      case Token.URSH:
      return tryFoldShift(subtree, left, right);
      case Token.ASSIGN:
      return tryFoldAssign(subtree, left, right);
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
      return tryUnfoldAssignOp(subtree, left, right);
      case Token.ADD:
      return tryFoldAdd(subtree, left, right);
      case Token.SUB:
      case Token.DIV:
      case Token.MOD:
      Node var_128 = tryFoldArithmeticOp(subtree, left, right);
      return var_128;
      case Token.MUL:
      case Token.BITAND:
      case Token.BITOR:
      case Token.BITXOR:
      Node result = tryFoldArithmeticOp(subtree, left, right);
      if(result != subtree) {
        return result;
      }
      return tryFoldLeftChildOp(subtree, left, right);
      case Token.LT:
      case Token.GT:
      case Token.LE:
      case Token.GE:
      case Token.EQ:
      case Token.NE:
      case Token.SHEQ:
      case Token.SHNE:
      return tryFoldComparison(subtree, left, right);
      default:
      return subtree;
    }
  }
  private Node tryFoldChildAddString(Node n, Node left, Node right) {
    if(NodeUtil.isLiteralValue(right, false) && left.isAdd()) {
      Node ll = left.getFirstChild();
      Node lr = ll.getNext();
      if(lr.isString()) {
        String leftString = NodeUtil.getStringValue(lr);
        String rightString = NodeUtil.getStringValue(right);
        if(leftString != null && rightString != null) {
          left.removeChild(ll);
          String result = leftString + rightString;
          n.replaceChild(left, ll);
          n.replaceChild(right, IR.string(result));
          reportCodeChange();
          return n;
        }
      }
    }
    if(NodeUtil.isLiteralValue(left, false) && right.isAdd()) {
      Node rl = right.getFirstChild();
      Node rr = right.getLastChild();
      if(rl.isString()) {
        String leftString = NodeUtil.getStringValue(left);
        String rightString = NodeUtil.getStringValue(rl);
        if(leftString != null && rightString != null) {
          right.removeChild(rr);
          String result = leftString + rightString;
          n.replaceChild(right, rr);
          n.replaceChild(left, IR.string(result));
          reportCodeChange();
          return n;
        }
      }
    }
    return n;
  }
  @SuppressWarnings(value = {"fallthrough", }) private Node tryFoldComparison(Node n, Node left, Node right) {
    TernaryValue result = evaluateComparison(n.getType(), left, right);
    if(result == TernaryValue.UNKNOWN) {
      return n;
    }
    Node newNode = NodeUtil.booleanNode(result.toBoolean(true));
    n.getParent().replaceChild(n, newNode);
    reportCodeChange();
    return newNode;
  }
  private Node tryFoldCtorCall(Node n) {
    Preconditions.checkArgument(n.isNew());
    if(inForcedStringContext(n)) {
      return tryFoldInForcedStringContext(n);
    }
    return n;
  }
  private Node tryFoldGetElem(Node n, Node left, Node right) {
    Preconditions.checkArgument(n.isGetElem());
    if(left.isObjectLit()) {
      return tryFoldObjectPropAccess(n, left, right);
    }
    if(left.isArrayLit()) {
      return tryFoldArrayAccess(n, left, right);
    }
    return n;
  }
  private Node tryFoldGetProp(Node n, Node left, Node right) {
    Preconditions.checkArgument(n.isGetProp());
    if(left.isObjectLit()) {
      return tryFoldObjectPropAccess(n, left, right);
    }
    if(right.isString() && right.getString().equals("length")) {
      int knownLength = -1;
      switch (left.getType()){
        case Token.ARRAYLIT:
        if(mayHaveSideEffects(left)) {
          return n;
        }
        knownLength = left.getChildCount();
        break ;
        case Token.STRING:
        knownLength = left.getString().length();
        break ;
        default:
        return n;
      }
      Preconditions.checkState(knownLength != -1);
      Node lengthNode = IR.number(knownLength);
      n.getParent().replaceChild(n, lengthNode);
      reportCodeChange();
      return lengthNode;
    }
    return n;
  }
  private Node tryFoldInForcedStringContext(Node n) {
    Preconditions.checkArgument(n.isNew());
    Node objectType = n.getFirstChild();
    if(!objectType.isName()) {
      return n;
    }
    if(objectType.getString().equals("String")) {
      Node value = objectType.getNext();
      String stringValue = null;
      if(value == null) {
        stringValue = "";
      }
      else {
        if(!NodeUtil.isImmutableValue(value)) {
          return n;
        }
        stringValue = NodeUtil.getStringValue(value);
      }
      if(stringValue == null) {
        return n;
      }
      Node parent = n.getParent();
      Node newString = IR.string(stringValue);
      parent.replaceChild(n, newString);
      newString.copyInformationFrom(parent);
      reportCodeChange();
      return newString;
    }
    return n;
  }
  private Node tryFoldInstanceof(Node n, Node left, Node right) {
    Preconditions.checkArgument(n.isInstanceOf());
    if(NodeUtil.isLiteralValue(left, true) && !mayHaveSideEffects(right)) {
      Node replacementNode = null;
      if(NodeUtil.isImmutableValue(left)) {
        replacementNode = IR.falseNode();
      }
      else 
        if(right.isName() && "Object".equals(right.getString())) {
          replacementNode = IR.trueNode();
        }
      if(replacementNode != null) {
        n.getParent().replaceChild(n, replacementNode);
        reportCodeChange();
        return replacementNode;
      }
    }
    return n;
  }
  private Node tryFoldLeftChildOp(Node n, Node left, Node right) {
    int opType = n.getType();
    Preconditions.checkState((NodeUtil.isAssociative(opType) && NodeUtil.isCommutative(opType)) || n.isAdd());
    Preconditions.checkState(!n.isAdd() || !NodeUtil.mayBeString(n));
    Double rightValObj = NodeUtil.getNumberValue(right);
    if(rightValObj != null && left.getType() == opType) {
      Preconditions.checkState(left.getChildCount() == 2);
      Node ll = left.getFirstChild();
      Node lr = ll.getNext();
      Node valueToCombine = ll;
      Node replacement = performArithmeticOp(opType, valueToCombine, right);
      if(replacement == null) {
        valueToCombine = lr;
        replacement = performArithmeticOp(opType, valueToCombine, right);
      }
      if(replacement != null) {
        left.removeChild(valueToCombine);
        n.replaceChild(left, left.removeFirstChild());
        replacement.copyInformationFromForTree(right);
        n.replaceChild(right, replacement);
        reportCodeChange();
      }
    }
    return n;
  }
  private Node tryFoldObjectPropAccess(Node n, Node left, Node right) {
    Preconditions.checkArgument(NodeUtil.isGet(n));
    if(!left.isObjectLit() || !right.isString()) {
      return n;
    }
    if(isAssignmentTarget(n)) {
      return n;
    }
    Node key = null;
    Node value = null;
    for(com.google.javascript.rhino.Node c = left.getFirstChild(); c != null; c = c.getNext()) {
      if(c.getString().equals(right.getString())) {
        switch (c.getType()){
          case Token.SETTER_DEF:
          continue ;
          case Token.GETTER_DEF:
          case Token.STRING_KEY:
          if(value != null && mayHaveSideEffects(value)) {
            return n;
          }
          key = c;
          value = key.getFirstChild();
          break ;
          default:
          throw new IllegalStateException();
        }
      }
      else 
        if(mayHaveSideEffects(c.getFirstChild())) {
          return n;
        }
    }
    if(value == null) {
      return n;
    }
    if(value.isFunction() && NodeUtil.referencesThis(value)) {
      return n;
    }
    Node replacement = value.detachFromParent();
    if(key.isGetterDef()) {
      replacement = IR.call(replacement);
      replacement.putBooleanProp(Node.FREE_CALL, true);
    }
    n.getParent().replaceChild(n, replacement);
    reportCodeChange();
    return n;
  }
  private Node tryFoldShift(Node n, Node left, Node right) {
    if(left.isNumber() && right.isNumber()) {
      double result;
      double lval = left.getDouble();
      double rval = right.getDouble();
      if(!(lval >= Integer.MIN_VALUE && lval <= Integer.MAX_VALUE)) {
        report(BITWISE_OPERAND_OUT_OF_RANGE, left);
        return n;
      }
      if(!(rval >= 0 && rval < 32)) {
        report(SHIFT_AMOUNT_OUT_OF_BOUNDS, right);
        return n;
      }
      int lvalInt = (int)lval;
      if(lvalInt != lval) {
        report(FRACTIONAL_BITWISE_OPERAND, left);
        return n;
      }
      int rvalInt = (int)rval;
      if(rvalInt != rval) {
        report(FRACTIONAL_BITWISE_OPERAND, right);
        return n;
      }
      switch (n.getType()){
        case Token.LSH:
        result = lvalInt << rvalInt;
        break ;
        case Token.RSH:
        result = lvalInt >> rvalInt;
        break ;
        case Token.URSH:
        long lvalLong = lvalInt & 0xffffffffL;
        result = lvalLong >>> rvalInt;
        break ;
        default:
        throw new AssertionError("Unknown shift operator: " + Token.name(n.getType()));
      }
      Node newNumber = IR.number(result);
      n.getParent().replaceChild(n, newNumber);
      reportCodeChange();
      return newNumber;
    }
    return n;
  }
  private Node tryFoldTypeof(Node originalTypeofNode) {
    Preconditions.checkArgument(originalTypeofNode.isTypeOf());
    Node argumentNode = originalTypeofNode.getFirstChild();
    if(argumentNode == null || !NodeUtil.isLiteralValue(argumentNode, true)) {
      return originalTypeofNode;
    }
    String typeNameString = null;
    switch (argumentNode.getType()){
      case Token.FUNCTION:
      typeNameString = "function";
      break ;
      case Token.STRING:
      typeNameString = "string";
      break ;
      case Token.NUMBER:
      typeNameString = "number";
      break ;
      case Token.TRUE:
      case Token.FALSE:
      typeNameString = "boolean";
      break ;
      case Token.NULL:
      case Token.OBJECTLIT:
      case Token.ARRAYLIT:
      typeNameString = "object";
      break ;
      case Token.VOID:
      typeNameString = "undefined";
      break ;
      case Token.NAME:
      if("undefined".equals(argumentNode.getString())) {
        typeNameString = "undefined";
      }
      break ;
    }
    if(typeNameString != null) {
      Node newNode = IR.string(typeNameString);
      originalTypeofNode.getParent().replaceChild(originalTypeofNode, newNode);
      reportCodeChange();
      return newNode;
    }
    return originalTypeofNode;
  }
  private Node tryFoldUnaryOperator(Node n) {
    Preconditions.checkState(n.hasOneChild());
    Node left = n.getFirstChild();
    Node parent = n.getParent();
    if(left == null) {
      return n;
    }
    TernaryValue leftVal = NodeUtil.getPureBooleanValue(left);
    if(leftVal == TernaryValue.UNKNOWN) {
      return n;
    }
    switch (n.getType()){
      case Token.NOT:
      if(late && left.isNumber()) {
        double numValue = left.getDouble();
        if(numValue == 0 || numValue == 1) {
          return n;
        }
      }
      Node replacementNode = NodeUtil.booleanNode(!leftVal.toBoolean(true));
      parent.replaceChild(n, replacementNode);
      reportCodeChange();
      return replacementNode;
      case Token.POS:
      if(NodeUtil.isNumericResult(left)) {
        parent.replaceChild(n, left.detachFromParent());
        reportCodeChange();
        return left;
      }
      return n;
      case Token.NEG:
      if(left.isName()) {
        if(left.getString().equals("Infinity")) {
          return n;
        }
        else 
          if(left.getString().equals("NaN")) {
            n.removeChild(left);
            parent.replaceChild(n, left);
            reportCodeChange();
            return left;
          }
      }
      if(left.isNumber()) {
        double negNum = -left.getDouble();
        Node negNumNode = IR.number(negNum);
        parent.replaceChild(n, negNumNode);
        reportCodeChange();
        return negNumNode;
      }
      else {
        report(NEGATING_A_NON_NUMBER_ERROR, left);
        return n;
      }
      case Token.BITNOT:
      try {
        double val = left.getDouble();
        if(val >= Integer.MIN_VALUE && val <= Integer.MAX_VALUE) {
          int intVal = (int)val;
          if(intVal == val) {
            Node notIntValNode = IR.number(~intVal);
            parent.replaceChild(n, notIntValNode);
            reportCodeChange();
            return notIntValNode;
          }
          else {
            report(FRACTIONAL_BITWISE_OPERAND, left);
            return n;
          }
        }
        else {
          report(BITWISE_OPERAND_OUT_OF_RANGE, left);
          return n;
        }
      }
      catch (UnsupportedOperationException ex) {
        report(NEGATING_A_NON_NUMBER_ERROR, left);
        return n;
      }
      default:
      return n;
    }
  }
  private Node tryReduceVoid(Node n) {
    Node child = n.getFirstChild();
    if(!child.isNumber() || child.getDouble() != 0.0D) {
      if(!mayHaveSideEffects(n)) {
        n.replaceChild(child, IR.number(0));
        reportCodeChange();
      }
    }
    return n;
  }
  private Node tryUnfoldAssignOp(Node n, Node left, Node right) {
    if(late) {
      return n;
    }
    if(!n.hasChildren() || n.getFirstChild().getNext() != n.getLastChild()) {
      return n;
    }
    if(mayHaveSideEffects(left)) {
      return n;
    }
    int op = NodeUtil.getOpFromAssignmentOp(n);
    Node replacement = IR.assign(left.detachFromParent(), new Node(op, left.cloneTree(), right.detachFromParent()).srcref(n));
    n.getParent().replaceChild(n, replacement);
    reportCodeChange();
    return replacement;
  }
  private static TernaryValue areStringsEqual(String a, String b) {
    if(a.indexOf('\u000b') != -1 || b.indexOf('\u000b') != -1) {
      return TernaryValue.UNKNOWN;
    }
    else {
      return a.equals(b) ? TernaryValue.TRUE : TernaryValue.FALSE;
    }
  }
  private static TernaryValue compareAsNumbers(int op, Node left, Node right) {
    Double leftValue = NodeUtil.getNumberValue(left);
    if(leftValue == null) {
      return TernaryValue.UNKNOWN;
    }
    Double rightValue = NodeUtil.getNumberValue(right);
    if(rightValue == null) {
      return TernaryValue.UNKNOWN;
    }
    double lv = leftValue;
    double rv = rightValue;
    switch (op){
      case Token.SHEQ:
      case Token.EQ:
      Preconditions.checkState(left.isNumber() && right.isNumber());
      return TernaryValue.forBoolean(lv == rv);
      case Token.SHNE:
      case Token.NE:
      Preconditions.checkState(left.isNumber() && right.isNumber());
      return TernaryValue.forBoolean(lv != rv);
      case Token.LE:
      return TernaryValue.forBoolean(lv <= rv);
      case Token.LT:
      return TernaryValue.forBoolean(lv < rv);
      case Token.GE:
      return TernaryValue.forBoolean(lv >= rv);
      case Token.GT:
      return TernaryValue.forBoolean(lv > rv);
      default:
      return TernaryValue.UNKNOWN;
    }
  }
  static TernaryValue evaluateComparison(int op, Node left, Node right) {
    boolean leftLiteral = NodeUtil.isLiteralValue(left, true);
    boolean rightLiteral = NodeUtil.isLiteralValue(right, true);
    if(!leftLiteral || !rightLiteral) {
      if(op != Token.GT && op != Token.LT) {
        return TernaryValue.UNKNOWN;
      }
    }
    boolean undefinedRight = NodeUtil.isUndefined(right) && rightLiteral;
    boolean nullRight = right.isNull();
    int lhType = getNormalizedNodeType(left);
    int rhType = getNormalizedNodeType(right);
    switch (lhType){
      case Token.VOID:
      if(!leftLiteral) {
        return TernaryValue.UNKNOWN;
      }
      else 
        if(!rightLiteral) {
          return TernaryValue.UNKNOWN;
        }
        else {
          return TernaryValue.forBoolean(compareToUndefined(right, op));
        }
      case Token.NULL:
      if(rightLiteral && isEqualityOp(op)) {
        return TernaryValue.forBoolean(compareToNull(right, op));
      }
      case Token.TRUE:
      case Token.FALSE:
      if(undefinedRight) {
        return TernaryValue.forBoolean(compareToUndefined(left, op));
      }
      if(rhType != Token.TRUE && rhType != Token.FALSE && rhType != Token.NULL) {
        return TernaryValue.UNKNOWN;
      }
      switch (op){
        case Token.SHEQ:
        case Token.EQ:
        return TernaryValue.forBoolean(lhType == rhType);
        case Token.SHNE:
        case Token.NE:
        return TernaryValue.forBoolean(lhType != rhType);
        case Token.GE:
        case Token.LE:
        case Token.GT:
        case Token.LT:
        return compareAsNumbers(op, left, right);
      }
      return TernaryValue.UNKNOWN;
      case Token.THIS:
      if(!right.isThis()) {
        return TernaryValue.UNKNOWN;
      }
      switch (op){
        case Token.SHEQ:
        case Token.EQ:
        return TernaryValue.TRUE;
        case Token.SHNE:
        case Token.NE:
        return TernaryValue.FALSE;
      }
      return TernaryValue.UNKNOWN;
      case Token.STRING:
      if(undefinedRight) {
        return TernaryValue.forBoolean(compareToUndefined(left, op));
      }
      if(nullRight && isEqualityOp(op)) {
        return TernaryValue.forBoolean(compareToNull(left, op));
      }
      if(Token.STRING != right.getType()) {
        return TernaryValue.UNKNOWN;
      }
      switch (op){
        case Token.SHEQ:
        case Token.EQ:
        return areStringsEqual(left.getString(), right.getString());
        case Token.SHNE:
        case Token.NE:
        return areStringsEqual(left.getString(), right.getString()).not();
      }
      return TernaryValue.UNKNOWN;
      case Token.NUMBER:
      if(undefinedRight) {
        return TernaryValue.forBoolean(compareToUndefined(left, op));
      }
      if(nullRight && isEqualityOp(op)) {
        return TernaryValue.forBoolean(compareToNull(left, op));
      }
      if(Token.NUMBER != right.getType()) {
        return TernaryValue.UNKNOWN;
      }
      return compareAsNumbers(op, left, right);
      case Token.NAME:
      if(leftLiteral && undefinedRight) {
        return TernaryValue.forBoolean(compareToUndefined(left, op));
      }
      if(rightLiteral) {
        boolean undefinedLeft = (left.getString().equals("undefined"));
        if(undefinedLeft) {
          return TernaryValue.forBoolean(compareToUndefined(right, op));
        }
        if(leftLiteral && nullRight && isEqualityOp(op)) {
          return TernaryValue.forBoolean(compareToNull(left, op));
        }
      }
      if(Token.NAME != right.getType()) {
        return TernaryValue.UNKNOWN;
      }
      String ln = left.getString();
      String rn = right.getString();
      if(!ln.equals(rn)) {
        return TernaryValue.UNKNOWN;
      }
      switch (op){
        case Token.LT:
        case Token.GT:
        return TernaryValue.FALSE;
      }
      return TernaryValue.UNKNOWN;
      case Token.NEG:
      if(leftLiteral) {
        if(undefinedRight) {
          return TernaryValue.forBoolean(compareToUndefined(left, op));
        }
        if(nullRight && isEqualityOp(op)) {
          return TernaryValue.forBoolean(compareToNull(left, op));
        }
      }
      return TernaryValue.UNKNOWN;
      case Token.ARRAYLIT:
      case Token.OBJECTLIT:
      case Token.REGEXP:
      case Token.FUNCTION:
      if(leftLiteral) {
        if(undefinedRight) {
          return TernaryValue.forBoolean(compareToUndefined(left, op));
        }
        if(nullRight && isEqualityOp(op)) {
          return TernaryValue.forBoolean(compareToNull(left, op));
        }
      }
      return TernaryValue.UNKNOWN;
      default:
      return TernaryValue.UNKNOWN;
    }
  }
  private static boolean compareToNull(Node value, int op) {
    boolean valueUndefined = NodeUtil.isUndefined(value);
    boolean valueNull = (Token.NULL == value.getType());
    boolean equivalent = valueUndefined || valueNull;
    switch (op){
      case Token.EQ:
      return equivalent;
      case Token.NE:
      return !equivalent;
      case Token.SHEQ:
      return valueNull;
      case Token.SHNE:
      return !valueNull;
      default:
      throw new IllegalStateException("unexpected.");
    }
  }
  private static boolean compareToUndefined(Node value, int op) {
    Preconditions.checkState(NodeUtil.isLiteralValue(value, true));
    boolean valueUndefined = NodeUtil.isUndefined(value);
    boolean valueNull = (Token.NULL == value.getType());
    boolean equivalent = valueUndefined || valueNull;
    switch (op){
      case Token.EQ:
      return equivalent;
      case Token.NE:
      return !equivalent;
      case Token.SHEQ:
      return valueUndefined;
      case Token.SHNE:
      return !valueUndefined;
      case Token.LT:
      case Token.GT:
      case Token.LE:
      case Token.GE:
      return false;
      default:
      throw new IllegalStateException("unexpected.");
    }
  }
  private boolean inForcedStringContext(Node n) {
    if(n.getParent().isGetElem() && n.getParent().getLastChild() == n) {
      return true;
    }
    if(n.getParent().isAdd()) {
      return true;
    }
    return false;
  }
  private boolean isAssignmentTarget(Node n) {
    Node parent = n.getParent();
    if((NodeUtil.isAssignmentOp(parent) && parent.getFirstChild() == n) || parent.isInc() || parent.isDec()) {
      return true;
    }
    return false;
  }
  private static boolean isEqualityOp(int op) {
    switch (op){
      case Token.EQ:
      case Token.NE:
      case Token.SHEQ:
      case Token.SHNE:
      return true;
    }
    return false;
  }
  private static int getNormalizedNodeType(Node n) {
    int type = n.getType();
    if(type == Token.NOT) {
      TernaryValue value = NodeUtil.getPureBooleanValue(n);
      switch (value){
        case TRUE:
        return Token.TRUE;
        case FALSE:
        return Token.FALSE;
        case UNKNOWN:
        return type;
      }
    }
    return type;
  }
  private void tryConvertOperandsToNumber(Node n) {
    Node next;
    for(com.google.javascript.rhino.Node c = n.getFirstChild(); c != null; c = next) {
      next = c.getNext();
      tryConvertToNumber(c);
    }
  }
  private void tryConvertToNumber(Node n) {
    switch (n.getType()){
      case Token.NUMBER:
      return ;
      case Token.AND:
      case Token.OR:
      case Token.COMMA:
      tryConvertToNumber(n.getLastChild());
      return ;
      case Token.HOOK:
      tryConvertToNumber(n.getChildAtIndex(1));
      tryConvertToNumber(n.getLastChild());
      return ;
      case Token.NAME:
      if(!NodeUtil.isUndefined(n)) {
        return ;
      }
      break ;
    }
    Double result = NodeUtil.getNumberValue(n);
    if(result == null) {
      return ;
    }
    double value = result;
    Node replacement = NodeUtil.numberNode(value, n);
    if(replacement.isEquivalentTo(n)) {
      return ;
    }
    n.getParent().replaceChild(n, replacement);
    reportCodeChange();
  }
  private void tryReduceOperandsForOp(Node n) {
    switch (n.getType()){
      case Token.ADD:
      Node left = n.getFirstChild();
      Node right = n.getLastChild();
      if(!NodeUtil.mayBeString(left) && !NodeUtil.mayBeString(right)) {
        tryConvertOperandsToNumber(n);
      }
      break ;
      case Token.ASSIGN_BITOR:
      case Token.ASSIGN_BITXOR:
      case Token.ASSIGN_BITAND:
      case Token.ASSIGN_LSH:
      case Token.ASSIGN_RSH:
      case Token.ASSIGN_URSH:
      case Token.ASSIGN_SUB:
      case Token.ASSIGN_MUL:
      case Token.ASSIGN_MOD:
      case Token.ASSIGN_DIV:
      tryConvertToNumber(n.getLastChild());
      break ;
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
      case Token.POS:
      case Token.NEG:
      tryConvertOperandsToNumber(n);
      break ;
    }
  }
}