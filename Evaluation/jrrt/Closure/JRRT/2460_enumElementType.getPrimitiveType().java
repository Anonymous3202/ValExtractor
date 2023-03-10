package com.google.javascript.jscomp.type;
import static com.google.javascript.rhino.jstype.JSTypeNative.ALL_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.BOOLEAN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.CHECKED_UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NO_OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NO_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NULL_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.OBJECT_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.STRING_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.U2U_CONSTRUCTOR_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;
import static com.google.javascript.rhino.jstype.JSTypeNative.VOID_TYPE;
import com.google.common.base.Preconditions;
import com.google.javascript.jscomp.CodingConvention;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.EnumElementType;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.ParameterizedType;
import com.google.javascript.rhino.jstype.StaticSlot;
import com.google.javascript.rhino.jstype.TemplateType;
import com.google.javascript.rhino.jstype.UnionType;
import com.google.javascript.rhino.jstype.Visitor;

abstract public class ChainableReverseAbstractInterpreter implements ReverseAbstractInterpreter  {
  final protected CodingConvention convention;
  final JSTypeRegistry typeRegistry;
  private ChainableReverseAbstractInterpreter firstLink;
  private ChainableReverseAbstractInterpreter nextLink;
  final private Visitor<JSType> restrictUndefinedVisitor = new Visitor<JSType>() {
      @Override() public JSType caseEnumElementType(EnumElementType enumElementType) {
        JSType var_2460 = enumElementType.getPrimitiveType();
        JSType type = var_2460.visit(this);
        if(type != null && enumElementType.getPrimitiveType().isEquivalentTo(type)) {
          return enumElementType;
        }
        else {
          return type;
        }
      }
      @Override() public JSType caseAllType() {
        return typeRegistry.createUnionType(OBJECT_TYPE, NUMBER_TYPE, STRING_TYPE, BOOLEAN_TYPE, NULL_TYPE);
      }
      @Override() public JSType caseNoObjectType() {
        return getNativeType(NO_OBJECT_TYPE);
      }
      @Override() public JSType caseNoType() {
        return getNativeType(NO_TYPE);
      }
      @Override() public JSType caseBooleanType() {
        return getNativeType(BOOLEAN_TYPE);
      }
      @Override() public JSType caseFunctionType(FunctionType type) {
        return type;
      }
      @Override() public JSType caseNullType() {
        return getNativeType(NULL_TYPE);
      }
      @Override() public JSType caseNumberType() {
        return getNativeType(NUMBER_TYPE);
      }
      @Override() public JSType caseObjectType(ObjectType type) {
        return type;
      }
      @Override() public JSType caseStringType() {
        return getNativeType(STRING_TYPE);
      }
      @Override() public JSType caseUnionType(UnionType type) {
        return type.getRestrictedUnion(getNativeType(VOID_TYPE));
      }
      @Override() public JSType caseUnknownType() {
        return getNativeType(UNKNOWN_TYPE);
      }
      @Override() public JSType caseVoidType() {
        return null;
      }
      @Override() public JSType caseParameterizedType(ParameterizedType type) {
        return caseObjectType(type);
      }
      @Override() public JSType caseTemplateType(TemplateType templateType) {
        return caseObjectType(templateType);
      }
  };
  final private Visitor<JSType> restrictNullVisitor = new Visitor<JSType>() {
      @Override() public JSType caseEnumElementType(EnumElementType enumElementType) {
        JSType type = enumElementType.getPrimitiveType().visit(this);
        if(type != null && enumElementType.getPrimitiveType().isEquivalentTo(type)) {
          return enumElementType;
        }
        else {
          return type;
        }
      }
      @Override() public JSType caseAllType() {
        return typeRegistry.createUnionType(OBJECT_TYPE, NUMBER_TYPE, STRING_TYPE, BOOLEAN_TYPE, VOID_TYPE);
      }
      @Override() public JSType caseNoObjectType() {
        return getNativeType(NO_OBJECT_TYPE);
      }
      @Override() public JSType caseNoType() {
        return getNativeType(NO_TYPE);
      }
      @Override() public JSType caseBooleanType() {
        return getNativeType(BOOLEAN_TYPE);
      }
      @Override() public JSType caseFunctionType(FunctionType type) {
        return type;
      }
      @Override() public JSType caseNullType() {
        return null;
      }
      @Override() public JSType caseNumberType() {
        return getNativeType(NUMBER_TYPE);
      }
      @Override() public JSType caseObjectType(ObjectType type) {
        return type;
      }
      @Override() public JSType caseStringType() {
        return getNativeType(STRING_TYPE);
      }
      @Override() public JSType caseUnionType(UnionType type) {
        return type.getRestrictedUnion(getNativeType(NULL_TYPE));
      }
      @Override() public JSType caseUnknownType() {
        return getNativeType(UNKNOWN_TYPE);
      }
      @Override() public JSType caseVoidType() {
        return getNativeType(VOID_TYPE);
      }
      @Override() public JSType caseParameterizedType(ParameterizedType type) {
        return caseObjectType(type);
      }
      @Override() public JSType caseTemplateType(TemplateType templateType) {
        return caseObjectType(templateType);
      }
  };
  public ChainableReverseAbstractInterpreter(CodingConvention convention, JSTypeRegistry typeRegistry) {
    super();
    Preconditions.checkNotNull(convention);
    this.convention = convention;
    this.typeRegistry = typeRegistry;
    firstLink = this;
    nextLink = null;
  }
  public ChainableReverseAbstractInterpreter append(ChainableReverseAbstractInterpreter lastLink) {
    Preconditions.checkArgument(lastLink.nextLink == null);
    this.nextLink = lastLink;
    lastLink.firstLink = this.firstLink;
    return lastLink;
  }
  public ChainableReverseAbstractInterpreter getFirst() {
    return firstLink;
  }
  protected FlowScope firstPreciserScopeKnowingConditionOutcome(Node condition, FlowScope blindScope, boolean outcome) {
    return firstLink.getPreciserScopeKnowingConditionOutcome(condition, blindScope, outcome);
  }
  protected FlowScope nextPreciserScopeKnowingConditionOutcome(Node condition, FlowScope blindScope, boolean outcome) {
    return nextLink != null ? nextLink.getPreciserScopeKnowingConditionOutcome(condition, blindScope, outcome) : blindScope;
  }
  JSType getNativeType(JSTypeNative typeId) {
    return typeRegistry.getNativeType(typeId);
  }
  private JSType getNativeTypeForTypeOf(String value) {
    if(value.equals("number")) {
      return getNativeType(NUMBER_TYPE);
    }
    else 
      if(value.equals("boolean")) {
        return getNativeType(BOOLEAN_TYPE);
      }
      else 
        if(value.equals("string")) {
          return getNativeType(STRING_TYPE);
        }
        else 
          if(value.equals("undefined")) {
            return getNativeType(VOID_TYPE);
          }
          else 
            if(value.equals("function")) {
              return getNativeType(U2U_CONSTRUCTOR_TYPE);
            }
            else {
              return null;
            }
  }
  JSType getRestrictedByTypeOfResult(JSType type, String value, boolean resultEqualsValue) {
    if(type == null) {
      if(resultEqualsValue) {
        JSType result = getNativeTypeForTypeOf(value);
        return result == null ? getNativeType(CHECKED_UNKNOWN_TYPE) : result;
      }
      else {
        return null;
      }
    }
    return type.visit(new RestrictByOneTypeOfResultVisitor(value, resultEqualsValue));
  }
  final protected JSType getRestrictedWithoutNull(JSType type) {
    return type == null ? null : type.visit(restrictNullVisitor);
  }
  final protected JSType getRestrictedWithoutUndefined(JSType type) {
    return type == null ? null : type.visit(restrictUndefinedVisitor);
  }
  protected JSType getTypeIfRefinable(Node node, FlowScope scope) {
    switch (node.getType()){
      case Token.NAME:
      StaticSlot<JSType> nameVar = scope.getSlot(node.getString());
      if(nameVar != null) {
        JSType nameVarType = nameVar.getType();
        if(nameVarType == null) {
          nameVarType = node.getJSType();
        }
        return nameVarType;
      }
      return null;
      case Token.GETPROP:
      String qualifiedName = node.getQualifiedName();
      if(qualifiedName == null) {
        return null;
      }
      StaticSlot<JSType> propVar = scope.getSlot(qualifiedName);
      JSType propVarType = null;
      if(propVar != null) {
        propVarType = propVar.getType();
      }
      if(propVarType == null) {
        propVarType = node.getJSType();
      }
      if(propVarType == null) {
        propVarType = getNativeType(UNKNOWN_TYPE);
      }
      return propVarType;
    }
    return null;
  }
  protected void declareNameInScope(FlowScope scope, Node node, JSType type) {
    switch (node.getType()){
      case Token.NAME:
      scope.inferSlotType(node.getString(), type);
      break ;
      case Token.GETPROP:
      String qualifiedName = node.getQualifiedName();
      Preconditions.checkNotNull(qualifiedName);
      JSType origType = node.getJSType();
      origType = origType == null ? getNativeType(UNKNOWN_TYPE) : origType;
      scope.inferQualifiedSlot(node, qualifiedName, origType, type);
      break ;
      case Token.THIS:
      break ;
      default:
      throw new IllegalArgumentException("Node cannot be refined. \n" + node.toStringTree());
    }
  }
  
  abstract class RestrictByFalseTypeOfResultVisitor extends RestrictByTypeOfResultVisitor  {
    @Override() public JSType caseBooleanType() {
      return getNativeType(BOOLEAN_TYPE);
    }
    @Override() public JSType caseFunctionType(FunctionType type) {
      return type;
    }
    @Override() public JSType caseNoObjectType() {
      return getNativeType(NO_OBJECT_TYPE);
    }
    @Override() public JSType caseNullType() {
      return getNativeType(NULL_TYPE);
    }
    @Override() public JSType caseNumberType() {
      return getNativeType(NUMBER_TYPE);
    }
    @Override() public JSType caseObjectType(ObjectType type) {
      return type;
    }
    @Override() public JSType caseStringType() {
      return getNativeType(STRING_TYPE);
    }
    @Override() protected JSType caseTopType(JSType topType) {
      return topType;
    }
    @Override() public JSType caseVoidType() {
      return getNativeType(VOID_TYPE);
    }
  }
  
  private class RestrictByOneTypeOfResultVisitor extends RestrictByTypeOfResultVisitor  {
    final private String value;
    final private boolean resultEqualsValue;
    RestrictByOneTypeOfResultVisitor(String value, boolean resultEqualsValue) {
      super();
      this.value = value;
      this.resultEqualsValue = resultEqualsValue;
    }
    @Override() public JSType caseBooleanType() {
      return matchesExpectation("boolean") ? getNativeType(BOOLEAN_TYPE) : null;
    }
    @Override() public JSType caseFunctionType(FunctionType type) {
      return matchesExpectation("function") ? type : null;
    }
    @Override() public JSType caseNoObjectType() {
      return (value.equals("object") || value.equals("function")) == resultEqualsValue ? getNativeType(NO_OBJECT_TYPE) : null;
    }
    @Override() public JSType caseNullType() {
      return matchesExpectation("object") ? getNativeType(NULL_TYPE) : null;
    }
    @Override() public JSType caseNumberType() {
      return matchesExpectation("number") ? getNativeType(NUMBER_TYPE) : null;
    }
    @Override() public JSType caseObjectType(ObjectType type) {
      if(value.equals("function")) {
        JSType ctorType = getNativeType(U2U_CONSTRUCTOR_TYPE);
        if(resultEqualsValue) {
          return ctorType.getGreatestSubtype(type);
        }
        else {
          return type.isSubtype(ctorType) ? null : type;
        }
      }
      return matchesExpectation("object") ? type : null;
    }
    @Override() public JSType caseStringType() {
      return matchesExpectation("string") ? getNativeType(STRING_TYPE) : null;
    }
    @Override() protected JSType caseTopType(JSType topType) {
      JSType result = topType;
      if(resultEqualsValue) {
        JSType typeByName = getNativeTypeForTypeOf(value);
        if(typeByName != null) {
          result = typeByName;
        }
      }
      return result;
    }
    @Override() public JSType caseVoidType() {
      return matchesExpectation("undefined") ? getNativeType(VOID_TYPE) : null;
    }
    private boolean matchesExpectation(String result) {
      return result.equals(value) == resultEqualsValue;
    }
  }
  
  abstract class RestrictByTrueTypeOfResultVisitor extends RestrictByTypeOfResultVisitor  {
    @Override() public JSType caseBooleanType() {
      return null;
    }
    @Override() public JSType caseFunctionType(FunctionType type) {
      return null;
    }
    @Override() public JSType caseNoObjectType() {
      return null;
    }
    @Override() public JSType caseNullType() {
      return null;
    }
    @Override() public JSType caseNumberType() {
      return null;
    }
    @Override() public JSType caseObjectType(ObjectType type) {
      return null;
    }
    @Override() public JSType caseStringType() {
      return null;
    }
    @Override() public JSType caseVoidType() {
      return null;
    }
  }
  
  abstract class RestrictByTypeOfResultVisitor implements Visitor<JSType>  {
    @Override() public JSType caseAllType() {
      return caseTopType(getNativeType(ALL_TYPE));
    }
    @Override() public JSType caseEnumElementType(EnumElementType enumElementType) {
      JSType type = enumElementType.getPrimitiveType().visit(this);
      if(type != null && enumElementType.getPrimitiveType().isEquivalentTo(type)) {
        return enumElementType;
      }
      else {
        return type;
      }
    }
    @Override() public JSType caseNoType() {
      return getNativeType(NO_TYPE);
    }
    @Override() public JSType caseParameterizedType(ParameterizedType type) {
      return caseObjectType(type);
    }
    @Override() public JSType caseTemplateType(TemplateType templateType) {
      return caseObjectType(templateType);
    }
    abstract protected JSType caseTopType(JSType topType);
    @Override() public JSType caseUnionType(UnionType type) {
      JSType restricted = null;
      for (JSType alternate : type.getAlternates()) {
        JSType restrictedAlternate = alternate.visit(this);
        if(restrictedAlternate != null) {
          if(restricted == null) {
            restricted = restrictedAlternate;
          }
          else {
            restricted = restrictedAlternate.getLeastSupertype(restricted);
          }
        }
      }
      return restricted;
    }
    @Override() public JSType caseUnknownType() {
      return caseTopType(getNativeType(CHECKED_UNKNOWN_TYPE));
    }
  }
}