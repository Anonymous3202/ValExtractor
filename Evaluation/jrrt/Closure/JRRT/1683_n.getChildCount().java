package com.google.javascript.jscomp;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

public class AstValidator implements CompilerPass  {
  final private ViolationHandler violationHandler;
  public AstValidator() {
    super();
    this.violationHandler = new ViolationHandler() {
        @Override() public void handleViolation(String message, Node n) {
          throw new IllegalStateException(message + " Reference node " + n.toString());
        }
    };
  }
  public AstValidator(ViolationHandler handler) {
    super();
    this.violationHandler = handler;
  }
  @Override() public void process(Node externs, Node root) {
    if(externs != null) {
      validateCodeRoot(externs);
    }
    if(root != null) {
      validateCodeRoot(root);
    }
  }
  private void validateArrayLit(Node n) {
    validateNodeType(Token.ARRAYLIT, n);
    for(com.google.javascript.rhino.Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateOptionalExpression(c);
    }
  }
  private void validateAssignmentExpression(Node n) {
    validateChildCount(n, 2);
    validateAssignmentTarget(n.getFirstChild());
    validateExpression(n.getLastChild());
  }
  private void validateAssignmentTarget(Node n) {
    switch (n.getType()){
      case Token.NAME:
      case Token.GETELEM:
      case Token.GETPROP:
      validateExpression(n);
      return ;
      default:
      violation("Expected assignment target expression but was " + Token.name(n.getType()), n);
    }
  }
  private void validateBinaryOp(Node n) {
    validateChildCount(n, 2);
    validateExpression(n.getFirstChild());
    validateExpression(n.getLastChild());
  }
  private void validateBlock(Node n) {
    validateNodeType(Token.BLOCK, n);
    for(com.google.javascript.rhino.Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateStatement(c);
    }
  }
  private void validateBreak(Node n) {
    validateNodeType(Token.BREAK, n);
    validateMaximumChildCount(n, 1);
    if(n.hasChildren()) {
      validateLabelName(n.getFirstChild());
    }
  }
  private void validateCall(Node n) {
    validateNodeType(Token.CALL, n);
    validateMinimumChildCount(n, 1);
    for(com.google.javascript.rhino.Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateExpression(c);
    }
  }
  private void validateCase(Node n) {
    validateNodeType(Token.CASE, n);
    validateChildCount(n, 2);
    validateExpression(n.getFirstChild());
    validateSyntheticBlock(n.getLastChild());
  }
  private void validateCatch(Node n) {
    validateNodeType(Token.CATCH, n);
    validateChildCount(n, 2);
    validateName(n.getFirstChild());
    validateBlock(n.getLastChild());
  }
  private void validateChildCount(Node n, int i) {
    boolean valid = false;
    if(i == 0) {
      valid = !n.hasChildren();
    }
    else 
      if(i == 1) {
        valid = n.hasOneChild();
      }
      else {
        valid = (n.getChildCount() == i);
      }
    if(!valid) {
      violation("Expected " + i + " children, but was " + n.getChildCount(), n);
    }
  }
  private void validateChildless(Node n) {
    validateChildCount(n, 0);
  }
  public void validateCodeRoot(Node n) {
    validateNodeType(Token.BLOCK, n);
    validateIsSynthetic(n);
    for(com.google.javascript.rhino.Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateScript(c);
    }
  }
  private void validateContinue(Node n) {
    validateNodeType(Token.CONTINUE, n);
    validateMaximumChildCount(n, 1);
    if(n.hasChildren()) {
      validateLabelName(n.getFirstChild());
    }
  }
  private void validateDefault(Node n) {
    validateNodeType(Token.DEFAULT_CASE, n);
    validateChildCount(n, 1);
    validateSyntheticBlock(n.getLastChild());
  }
  private void validateDo(Node n) {
    validateNodeType(Token.DO, n);
    validateChildCount(n, 2);
    validateBlock(n.getFirstChild());
    validateExpression(n.getLastChild());
  }
  private void validateExprStmt(Node n) {
    validateNodeType(Token.EXPR_RESULT, n);
    validateChildCount(n, 1);
    validateExpression(n.getFirstChild());
  }
  public void validateExpression(Node n) {
    switch (n.getType()){
      case Token.FALSE:
      case Token.NULL:
      case Token.THIS:
      case Token.TRUE:
      validateChildless(n);
      return ;
      case Token.DELPROP:
      case Token.POS:
      case Token.NEG:
      case Token.NOT:
      case Token.INC:
      case Token.DEC:
      case Token.TYPEOF:
      case Token.VOID:
      case Token.BITNOT:
      case Token.CAST:
      validateUnaryOp(n);
      return ;
      case Token.COMMA:
      case Token.OR:
      case Token.AND:
      case Token.BITOR:
      case Token.BITXOR:
      case Token.BITAND:
      case Token.EQ:
      case Token.NE:
      case Token.SHEQ:
      case Token.SHNE:
      case Token.LT:
      case Token.GT:
      case Token.LE:
      case Token.GE:
      case Token.INSTANCEOF:
      case Token.IN:
      case Token.LSH:
      case Token.RSH:
      case Token.URSH:
      case Token.SUB:
      case Token.ADD:
      case Token.MUL:
      case Token.MOD:
      case Token.DIV:
      validateBinaryOp(n);
      return ;
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
      validateAssignmentExpression(n);
      return ;
      case Token.HOOK:
      validateTrinaryOp(n);
      return ;
      case Token.STRING:
      validateString(n);
      return ;
      case Token.NUMBER:
      validateNumber(n);
      return ;
      case Token.NAME:
      validateName(n);
      return ;
      case Token.GETELEM:
      validateBinaryOp(n);
      return ;
      case Token.GETPROP:
      validateGetProp(n);
      return ;
      case Token.ARRAYLIT:
      validateArrayLit(n);
      return ;
      case Token.OBJECTLIT:
      validateObjectLit(n);
      return ;
      case Token.REGEXP:
      validateRegExpLit(n);
      return ;
      case Token.CALL:
      validateCall(n);
      return ;
      case Token.NEW:
      validateNew(n);
      return ;
      case Token.FUNCTION:
      validateFunctionExpression(n);
      return ;
      default:
      violation("Expected expression but was " + Token.name(n.getType()), n);
    }
  }
  private void validateFor(Node n) {
    validateNodeType(Token.FOR, n);
    validateMinimumChildCount(n, 3);
    validateMaximumChildCount(n, 4);
    if(NodeUtil.isForIn(n)) {
      validateChildCount(n, 3);
      validateVarOrAssignmentTarget(n.getFirstChild());
      validateExpression(n.getChildAtIndex(1));
    }
    else {
      validateChildCount(n, 4);
      validateVarOrOptionalExpression(n.getFirstChild());
      validateOptionalExpression(n.getChildAtIndex(1));
      validateOptionalExpression(n.getChildAtIndex(2));
    }
    validateBlock(n.getLastChild());
  }
  private void validateFunctionExpression(Node n) {
    validateNodeType(Token.FUNCTION, n);
    validateChildCount(n, 3);
    validateOptionalName(n.getFirstChild());
    validateParameters(n.getChildAtIndex(1));
    validateBlock(n.getLastChild());
  }
  private void validateFunctionStatement(Node n) {
    validateNodeType(Token.FUNCTION, n);
    validateChildCount(n, 3);
    validateName(n.getFirstChild());
    validateParameters(n.getChildAtIndex(1));
    validateBlock(n.getLastChild());
  }
  private void validateGetProp(Node n) {
    validateNodeType(Token.GETPROP, n);
    validateChildCount(n, 2);
    validateExpression(n.getFirstChild());
    Node prop = n.getLastChild();
    validateNodeType(Token.STRING, prop);
    validateNonEmptyString(prop);
  }
  private void validateHasInputId(Node n) {
    InputId inputId = n.getInputId();
    if(inputId == null) {
      violation("Missing \'input id\' annotation.", n);
    }
  }
  private void validateHasSourceName(Node n) {
    String sourceName = n.getSourceFileName();
    if(sourceName == null || sourceName.isEmpty()) {
      violation("Missing \'source name\' annotation.", n);
    }
  }
  private void validateIf(Node n) {
    validateNodeType(Token.IF, n);
    validateMinimumChildCount(n, 2);
    validateMaximumChildCount(n, 3);
    validateExpression(n.getFirstChild());
    validateBlock(n.getChildAtIndex(1));
    if(n.getChildCount() == 3) {
      validateBlock(n.getLastChild());
    }
  }
  private void validateIsSynthetic(Node n) {
    if(!n.getBooleanProp(Node.SYNTHETIC_BLOCK_PROP)) {
      violation("Missing \'synthetic block\' annotation.", n);
    }
  }
  private void validateLabel(Node n) {
    validateNodeType(Token.LABEL, n);
    validateChildCount(n, 2);
    validateLabelName(n.getFirstChild());
    validateStatement(n.getLastChild());
  }
  private void validateLabelName(Node n) {
    validateNodeType(Token.LABEL_NAME, n);
    validateNonEmptyString(n);
    validateChildCount(n, 0);
  }
  private void validateMaximumChildCount(Node n, int i) {
    boolean valid = false;
    if(i == 1) {
      valid = !n.hasMoreThanOneChild();
    }
    else {
      int var_1683 = n.getChildCount();
      valid = var_1683 <= i;
    }
    if(!valid) {
      violation("Expected no more than " + i + " children, but was " + n.getChildCount(), n);
    }
  }
  private void validateMinimumChildCount(Node n, int i) {
    boolean valid = false;
    if(i == 1) {
      valid = n.hasChildren();
    }
    else 
      if(i == 2) {
        valid = n.hasMoreThanOneChild();
      }
      else {
        valid = n.getChildCount() >= i;
      }
    if(!valid) {
      violation("Expected at least " + i + " children, but was " + n.getChildCount(), n);
    }
  }
  private void validateName(Node n) {
    validateNodeType(Token.NAME, n);
    validateNonEmptyString(n);
    validateChildCount(n, 0);
  }
  private void validateNew(Node n) {
    validateNodeType(Token.NEW, n);
    validateMinimumChildCount(n, 1);
    for(com.google.javascript.rhino.Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateExpression(c);
    }
  }
  private void validateNodeType(int type, Node n) {
    if(n.getType() != type) {
      violation("Expected " + Token.name(type) + " but was " + Token.name(n.getType()), n);
    }
  }
  private void validateNonEmptyString(Node n) {
    validateNonNullString(n);
    if(n.getString().isEmpty()) {
      violation("Expected non-empty string.", n);
    }
  }
  private void validateNonNullString(Node n) {
    if(n.getString() == null) {
      violation("Expected non-null string.", n);
    }
  }
  private void validateNumber(Node n) {
    validateNodeType(Token.NUMBER, n);
    validateChildCount(n, 0);
    try {
      n.getDouble();
    }
    catch (UnsupportedOperationException e) {
      violation("Invalid NUMBER node.", n);
    }
  }
  private void validateObjectLit(Node n) {
    validateNodeType(Token.OBJECTLIT, n);
    for(com.google.javascript.rhino.Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateObjectLitKey(c);
    }
  }
  private void validateObjectLitGetKey(Node n) {
    validateNodeType(Token.GETTER_DEF, n);
    validateChildCount(n, 1);
    validateObjectLiteralKeyName(n);
    Node function = n.getFirstChild();
    validateFunctionExpression(function);
    if(!function.getFirstChild().getString().isEmpty()) {
      violation("Expected unnamed function expression.", n);
    }
    Node functionParams = function.getChildAtIndex(1);
    if(functionParams.hasChildren()) {
      violation("get methods must not have parameters.", n);
    }
  }
  private void validateObjectLitKey(Node n) {
    switch (n.getType()){
      case Token.GETTER_DEF:
      validateObjectLitGetKey(n);
      return ;
      case Token.SETTER_DEF:
      validateObjectLitSetKey(n);
      return ;
      case Token.STRING_KEY:
      validateObjectLitStringKey(n);
      return ;
      default:
      violation("Expected object literal key expression but was " + Token.name(n.getType()), n);
    }
  }
  private void validateObjectLitSetKey(Node n) {
    validateNodeType(Token.SETTER_DEF, n);
    validateChildCount(n, 1);
    validateObjectLiteralKeyName(n);
    Node function = n.getFirstChild();
    validateFunctionExpression(function);
    if(!function.getFirstChild().getString().isEmpty()) {
      violation("Expected unnamed function expression.", n);
    }
    Node functionParams = function.getChildAtIndex(1);
    if(!functionParams.hasOneChild()) {
      violation("set methods must have exactly one parameter.", n);
    }
  }
  private void validateObjectLitStringKey(Node n) {
    validateNodeType(Token.STRING_KEY, n);
    validateChildCount(n, 1);
    validateObjectLiteralKeyName(n);
    validateExpression(n.getFirstChild());
  }
  private void validateObjectLiteralKeyName(Node n) {
    if(n.isQuotedString()) {
      try {
        n.getString();
      }
      catch (UnsupportedOperationException e) {
        violation("getString failed for" + Token.name(n.getType()), n);
      }
    }
    else {
      validateNonEmptyString(n);
    }
  }
  private void validateOptionalExpression(Node n) {
    if(n.isEmpty()) {
      validateChildless(n);
    }
    else {
      validateExpression(n);
    }
  }
  private void validateOptionalName(Node n) {
    validateNodeType(Token.NAME, n);
    validateNonNullString(n);
    validateChildCount(n, 0);
  }
  private void validateParameters(Node n) {
    validateNodeType(Token.PARAM_LIST, n);
    for(com.google.javascript.rhino.Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateName(c);
    }
  }
  private void validateRegExpLit(Node n) {
    validateNodeType(Token.REGEXP, n);
    validateMinimumChildCount(n, 1);
    validateMaximumChildCount(n, 2);
    for(com.google.javascript.rhino.Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateString(c);
    }
  }
  private void validateReturn(Node n) {
    validateNodeType(Token.RETURN, n);
    validateMaximumChildCount(n, 1);
    if(n.hasChildren()) {
      validateExpression(n.getFirstChild());
    }
  }
  public void validateRoot(Node n) {
    validateNodeType(Token.BLOCK, n);
    validateIsSynthetic(n);
    validateChildCount(n, 2);
    validateCodeRoot(n.getFirstChild());
    validateCodeRoot(n.getLastChild());
  }
  public void validateScript(Node n) {
    validateNodeType(Token.SCRIPT, n);
    validateHasSourceName(n);
    validateHasInputId(n);
    for(com.google.javascript.rhino.Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateStatement(c);
    }
  }
  public void validateStatement(Node n) {
    switch (n.getType()){
      case Token.LABEL:
      validateLabel(n);
      return ;
      case Token.BLOCK:
      validateBlock(n);
      return ;
      case Token.FUNCTION:
      validateFunctionStatement(n);
      return ;
      case Token.WITH:
      validateWith(n);
      return ;
      case Token.FOR:
      validateFor(n);
      return ;
      case Token.WHILE:
      validateWhile(n);
      return ;
      case Token.DO:
      validateDo(n);
      return ;
      case Token.SWITCH:
      validateSwitch(n);
      return ;
      case Token.IF:
      validateIf(n);
      return ;
      case Token.VAR:
      validateVar(n);
      return ;
      case Token.EXPR_RESULT:
      validateExprStmt(n);
      return ;
      case Token.RETURN:
      validateReturn(n);
      return ;
      case Token.THROW:
      validateThrow(n);
      return ;
      case Token.TRY:
      validateTry(n);
      return ;
      case Token.BREAK:
      validateBreak(n);
      return ;
      case Token.CONTINUE:
      validateContinue(n);
      return ;
      case Token.EMPTY:
      validateChildless(n);
      return ;
      case Token.DEBUGGER:
      validateChildless(n);
      return ;
      default:
      violation("Expected statement but was " + Token.name(n.getType()) + ".", n);
    }
  }
  private void validateString(Node n) {
    validateNodeType(Token.STRING, n);
    validateChildCount(n, 0);
    try {
      n.getString();
    }
    catch (UnsupportedOperationException e) {
      violation("Invalid STRING node.", n);
    }
  }
  private void validateSwitch(Node n) {
    validateNodeType(Token.SWITCH, n);
    validateMinimumChildCount(n, 1);
    validateExpression(n.getFirstChild());
    int defaults = 0;
    for(com.google.javascript.rhino.Node c = n.getFirstChild().getNext(); c != null; c = c.getNext()) {
      validateSwitchMember(n.getLastChild());
      if(c.isDefaultCase()) {
        defaults++;
      }
    }
    if(defaults > 1) {
      violation("Expected at most 1 \'default\' in switch but was " + defaults, n);
    }
  }
  private void validateSwitchMember(Node n) {
    switch (n.getType()){
      case Token.CASE:
      validateCase(n);
      return ;
      case Token.DEFAULT_CASE:
      validateDefault(n);
      return ;
      default:
      violation("Expected switch member but was " + Token.name(n.getType()), n);
    }
  }
  private void validateSyntheticBlock(Node n) {
    validateNodeType(Token.BLOCK, n);
    validateIsSynthetic(n);
    for(com.google.javascript.rhino.Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateStatement(c);
    }
  }
  private void validateThrow(Node n) {
    validateNodeType(Token.THROW, n);
    validateChildCount(n, 1);
    validateExpression(n.getFirstChild());
  }
  private void validateTrinaryOp(Node n) {
    validateChildCount(n, 3);
    Node first = n.getFirstChild();
    validateExpression(first);
    validateExpression(first.getNext());
    validateExpression(n.getLastChild());
  }
  private void validateTry(Node n) {
    validateNodeType(Token.TRY, n);
    validateMinimumChildCount(n, 2);
    validateMaximumChildCount(n, 3);
    validateBlock(n.getFirstChild());
    boolean seenCatchOrFinally = false;
    Node catches = n.getChildAtIndex(1);
    validateNodeType(Token.BLOCK, catches);
    validateMaximumChildCount(catches, 1);
    if(catches.hasChildren()) {
      validateCatch(catches.getFirstChild());
      seenCatchOrFinally = true;
    }
    if(n.getChildCount() == 3) {
      validateBlock(n.getLastChild());
      seenCatchOrFinally = true;
    }
    if(!seenCatchOrFinally) {
      violation("Missing catch or finally for try statement.", n);
    }
  }
  private void validateUnaryOp(Node n) {
    validateChildCount(n, 1);
    validateExpression(n.getFirstChild());
  }
  private void validateVar(Node n) {
    validateNodeType(Token.VAR, n);
    this.validateMinimumChildCount(n, 1);
    for(com.google.javascript.rhino.Node c = n.getFirstChild(); c != null; c = c.getNext()) {
      validateNodeType(Token.NAME, c);
      validateNonEmptyString(c);
      validateMaximumChildCount(c, 1);
      if(c.hasChildren()) {
        validateExpression(c.getFirstChild());
      }
    }
  }
  private void validateVarOrAssignmentTarget(Node n) {
    if(n.isVar()) {
      this.validateChildCount(n, 1);
      validateVar(n);
    }
    else {
      validateAssignmentTarget(n);
    }
  }
  private void validateVarOrOptionalExpression(Node n) {
    if(n.isVar()) {
      validateVar(n);
    }
    else {
      validateOptionalExpression(n);
    }
  }
  private void validateWhile(Node n) {
    validateNodeType(Token.WHILE, n);
    validateChildCount(n, 2);
    validateExpression(n.getFirstChild());
    validateBlock(n.getLastChild());
  }
  private void validateWith(Node n) {
    validateNodeType(Token.WITH, n);
    validateChildCount(n, 2);
    validateExpression(n.getFirstChild());
    validateBlock(n.getLastChild());
  }
  private void violation(String message, Node n) {
    violationHandler.handleViolation(message, n);
  }
  
  public interface ViolationHandler  {
    void handleViolation(String message, Node n);
  }
}