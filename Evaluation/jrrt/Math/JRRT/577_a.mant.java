package org.apache.commons.math3.dfp;
import java.util.Arrays;
import org.apache.commons.math3.RealFieldElement;
import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.util.FastMath;

public class Dfp implements RealFieldElement<Dfp>  {
  final public static int RADIX = 10000;
  final public static int MIN_EXP = -32767;
  final public static int MAX_EXP = 32768;
  final public static int ERR_SCALE = 32760;
  final public static byte FINITE = 0;
  final public static byte INFINITE = 1;
  final public static byte SNAN = 2;
  final public static byte QNAN = 3;
  final private static String NAN_STRING = "NaN";
  final private static String POS_INFINITY_STRING = "Infinity";
  final private static String NEG_INFINITY_STRING = "-Infinity";
  final private static String ADD_TRAP = "add";
  final private static String MULTIPLY_TRAP = "multiply";
  final private static String DIVIDE_TRAP = "divide";
  final private static String SQRT_TRAP = "sqrt";
  final private static String ALIGN_TRAP = "align";
  final private static String TRUNC_TRAP = "trunc";
  final private static String NEXT_AFTER_TRAP = "nextAfter";
  final private static String LESS_THAN_TRAP = "lessThan";
  final private static String GREATER_THAN_TRAP = "greaterThan";
  final private static String NEW_INSTANCE_TRAP = "newInstance";
  protected int[] mant;
  protected byte sign;
  protected int exp;
  protected byte nans;
  final private DfpField field;
  public Dfp(final Dfp d) {
    super();
    mant = d.mant.clone();
    sign = d.sign;
    exp = d.exp;
    nans = d.nans;
    field = d.field;
  }
  protected Dfp(final DfpField field) {
    super();
    mant = new int[field.getRadixDigits()];
    sign = 1;
    exp = 0;
    nans = FINITE;
    this.field = field;
  }
  protected Dfp(final DfpField field, byte x) {
    this(field, (long)x);
  }
  protected Dfp(final DfpField field, double x) {
    super();
    mant = new int[field.getRadixDigits()];
    sign = 1;
    exp = 0;
    nans = FINITE;
    this.field = field;
    long bits = Double.doubleToLongBits(x);
    long mantissa = bits & 0x000fffffffffffffL;
    int exponent = (int)((bits & 0x7ff0000000000000L) >> 52) - 1023;
    if(exponent == -1023) {
      if(x == 0) {
        if((bits & 0x8000000000000000L) != 0) {
          sign = -1;
        }
        return ;
      }
      exponent++;
      while((mantissa & 0x0010000000000000L) == 0){
        exponent--;
        mantissa <<= 1;
      }
      mantissa &= 0x000fffffffffffffL;
    }
    if(exponent == 1024) {
      if(x != x) {
        sign = (byte)1;
        nans = QNAN;
      }
      else 
        if(x < 0) {
          sign = (byte)-1;
          nans = INFINITE;
        }
        else {
          sign = (byte)1;
          nans = INFINITE;
        }
      return ;
    }
    Dfp xdfp = new Dfp(field, mantissa);
    xdfp = xdfp.divide(new Dfp(field, 4503599627370496L)).add(field.getOne());
    xdfp = xdfp.multiply(DfpMath.pow(field.getTwo(), exponent));
    if((bits & 0x8000000000000000L) != 0) {
      xdfp = xdfp.negate();
    }
    System.arraycopy(xdfp.mant, 0, mant, 0, mant.length);
    sign = xdfp.sign;
    exp = xdfp.exp;
    nans = xdfp.nans;
  }
  protected Dfp(final DfpField field, final String s) {
    super();
    mant = new int[field.getRadixDigits()];
    sign = 1;
    exp = 0;
    nans = FINITE;
    this.field = field;
    boolean decimalFound = false;
    final int rsize = 4;
    final int offset = 4;
    final char[] striped = new char[getRadixDigits() * rsize + offset * 2];
    if(s.equals(POS_INFINITY_STRING)) {
      sign = (byte)1;
      nans = INFINITE;
      return ;
    }
    if(s.equals(NEG_INFINITY_STRING)) {
      sign = (byte)-1;
      nans = INFINITE;
      return ;
    }
    if(s.equals(NAN_STRING)) {
      sign = (byte)1;
      nans = QNAN;
      return ;
    }
    int p = s.indexOf("e");
    if(p == -1) {
      p = s.indexOf("E");
    }
    final String fpdecimal;
    int sciexp = 0;
    if(p != -1) {
      fpdecimal = s.substring(0, p);
      String fpexp = s.substring(p + 1);
      boolean negative = false;
      for(int i = 0; i < fpexp.length(); i++) {
        if(fpexp.charAt(i) == '-') {
          negative = true;
          continue ;
        }
        if(fpexp.charAt(i) >= '0' && fpexp.charAt(i) <= '9') {
          sciexp = sciexp * 10 + fpexp.charAt(i) - '0';
        }
      }
      if(negative) {
        sciexp = -sciexp;
      }
    }
    else {
      fpdecimal = s;
    }
    if(fpdecimal.indexOf("-") != -1) {
      sign = -1;
    }
    p = 0;
    int decimalPos = 0;
    for(; true; ) {
      if(fpdecimal.charAt(p) >= '1' && fpdecimal.charAt(p) <= '9') {
        break ;
      }
      if(decimalFound && fpdecimal.charAt(p) == '0') {
        decimalPos--;
      }
      if(fpdecimal.charAt(p) == '.') {
        decimalFound = true;
      }
      p++;
      if(p == fpdecimal.length()) {
        break ;
      }
    }
    int q = offset;
    striped[0] = '0';
    striped[1] = '0';
    striped[2] = '0';
    striped[3] = '0';
    int significantDigits = 0;
    for(; true; ) {
      if(p == (fpdecimal.length())) {
        break ;
      }
      if(q == mant.length * rsize + offset + 1) {
        break ;
      }
      if(fpdecimal.charAt(p) == '.') {
        decimalFound = true;
        decimalPos = significantDigits;
        p++;
        continue ;
      }
      if(fpdecimal.charAt(p) < '0' || fpdecimal.charAt(p) > '9') {
        p++;
        continue ;
      }
      striped[q] = fpdecimal.charAt(p);
      q++;
      p++;
      significantDigits++;
    }
    if(decimalFound && q != offset) {
      for(; true; ) {
        q--;
        if(q == offset) {
          break ;
        }
        if(striped[q] == '0') {
          significantDigits--;
        }
        else {
          break ;
        }
      }
    }
    if(decimalFound && significantDigits == 0) {
      decimalPos = 0;
    }
    if(!decimalFound) {
      decimalPos = q - offset;
    }
    q = offset;
    p = significantDigits - 1 + offset;
    while(p > q){
      if(striped[p] != '0') {
        break ;
      }
      p--;
    }
    int i = ((rsize * 100) - decimalPos - sciexp % rsize) % rsize;
    q -= i;
    decimalPos += i;
    while((p - q) < (mant.length * rsize)){
      for(i = 0; i < rsize; i++) {
        striped[++p] = '0';
      }
    }
    for(i = mant.length - 1; i >= 0; i--) {
      mant[i] = (striped[q] - '0') * 1000 + (striped[q + 1] - '0') * 100 + (striped[q + 2] - '0') * 10 + (striped[q + 3] - '0');
      q += 4;
    }
    exp = (decimalPos + sciexp) / rsize;
    if(q < striped.length) {
      round((striped[q] - '0') * 1000);
    }
  }
  protected Dfp(final DfpField field, final byte sign, final byte nans) {
    super();
    this.field = field;
    this.mant = new int[field.getRadixDigits()];
    this.sign = sign;
    this.exp = 0;
    this.nans = nans;
  }
  protected Dfp(final DfpField field, int x) {
    this(field, (long)x);
  }
  protected Dfp(final DfpField field, long x) {
    super();
    mant = new int[field.getRadixDigits()];
    nans = FINITE;
    this.field = field;
    boolean isLongMin = false;
    if(x == Long.MIN_VALUE) {
      isLongMin = true;
      ++x;
    }
    if(x < 0) {
      sign = -1;
      x = -x;
    }
    else {
      sign = 1;
    }
    exp = 0;
    while(x != 0){
      System.arraycopy(mant, mant.length - exp, mant, mant.length - 1 - exp, exp);
      mant[mant.length - 1] = (int)(x % RADIX);
      x /= RADIX;
      exp++;
    }
    if(isLongMin) {
      for(int i = 0; i < mant.length - 1; i++) {
        if(mant[i] != 0) {
          mant[i]++;
          break ;
        }
      }
    }
  }
  public Dfp abs() {
    Dfp result = newInstance(this);
    result.sign = 1;
    return result;
  }
  public Dfp acos() {
    return DfpMath.acos(this);
  }
  public Dfp acosh() {
    return multiply(this).subtract(getOne()).sqrt().add(this).log();
  }
  public Dfp add(final double a) {
    return add(newInstance(a));
  }
  public Dfp add(final Dfp x) {
    if(field.getRadixDigits() != x.field.getRadixDigits()) {
      field.setIEEEFlagsBits(DfpField.FLAG_INVALID);
      final Dfp result = newInstance(getZero());
      result.nans = QNAN;
      return dotrap(DfpField.FLAG_INVALID, ADD_TRAP, x, result);
    }
    if(nans != FINITE || x.nans != FINITE) {
      if(isNaN()) {
        return this;
      }
      if(x.isNaN()) {
        return x;
      }
      if(nans == INFINITE && x.nans == FINITE) {
        return this;
      }
      if(x.nans == INFINITE && nans == FINITE) {
        return x;
      }
      if(x.nans == INFINITE && nans == INFINITE && sign == x.sign) {
        return x;
      }
      if(x.nans == INFINITE && nans == INFINITE && sign != x.sign) {
        field.setIEEEFlagsBits(DfpField.FLAG_INVALID);
        Dfp result = newInstance(getZero());
        result.nans = QNAN;
        result = dotrap(DfpField.FLAG_INVALID, ADD_TRAP, x, result);
        return result;
      }
    }
    Dfp a = newInstance(this);
    Dfp b = newInstance(x);
    Dfp result = newInstance(getZero());
    final byte asign = a.sign;
    final byte bsign = b.sign;
    a.sign = 1;
    b.sign = 1;
    byte rsign = bsign;
    if(compare(a, b) > 0) {
      rsign = asign;
    }
    if(b.mant[mant.length - 1] == 0) {
      b.exp = a.exp;
    }
    if(a.mant[mant.length - 1] == 0) {
      a.exp = b.exp;
    }
    int aextradigit = 0;
    int bextradigit = 0;
    if(a.exp < b.exp) {
      aextradigit = a.align(b.exp);
    }
    else {
      bextradigit = b.align(a.exp);
    }
    if(asign != bsign) {
      if(asign == rsign) {
        bextradigit = b.complement(bextradigit);
      }
      else {
        aextradigit = a.complement(aextradigit);
      }
    }
    int rh = 0;
    for(int i = 0; i < mant.length; i++) {
      final int r = a.mant[i] + b.mant[i] + rh;
      rh = r / RADIX;
      result.mant[i] = r - rh * RADIX;
    }
    result.exp = a.exp;
    result.sign = rsign;
    if(rh != 0 && (asign == bsign)) {
      final int lostdigit = result.mant[0];
      result.shiftRight();
      result.mant[mant.length - 1] = rh;
      final int excp = result.round(lostdigit);
      if(excp != 0) {
        result = dotrap(excp, ADD_TRAP, x, result);
      }
    }
    for(int i = 0; i < mant.length; i++) {
      if(result.mant[mant.length - 1] != 0) {
        break ;
      }
      result.shiftLeft();
      if(i == 0) {
        result.mant[0] = aextradigit + bextradigit;
        aextradigit = 0;
        bextradigit = 0;
      }
    }
    if(result.mant[mant.length - 1] == 0) {
      result.exp = 0;
      if(asign != bsign) {
        result.sign = 1;
      }
    }
    final int excp = result.round(aextradigit + bextradigit);
    if(excp != 0) {
      result = dotrap(excp, ADD_TRAP, x, result);
    }
    return result;
  }
  public Dfp asin() {
    return DfpMath.asin(this);
  }
  public Dfp asinh() {
    return multiply(this).add(getOne()).sqrt().add(this).log();
  }
  public Dfp atan() {
    return DfpMath.atan(this);
  }
  public Dfp atan2(final Dfp x) throws DimensionMismatchException {
    final Dfp r = x.multiply(x).add(multiply(this)).sqrt();
    if(x.sign >= 0) {
      return getTwo().multiply(divide(r.add(x)).atan());
    }
    else {
      final Dfp tmp = getTwo().multiply(divide(r.subtract(x)).atan());
      final Dfp pmPi = newInstance((tmp.sign <= 0) ? -FastMath.PI : FastMath.PI);
      return pmPi.subtract(tmp);
    }
  }
  public Dfp atanh() {
    return getOne().add(this).divide(getOne().subtract(this)).log().divide(2);
  }
  public Dfp cbrt() {
    return rootN(3);
  }
  public Dfp ceil() {
    return trunc(DfpField.RoundingMode.ROUND_CEIL);
  }
  public Dfp copySign(final double s) {
    long sb = Double.doubleToLongBits(s);
    if((sign >= 0 && sb >= 0) || (sign < 0 && sb < 0)) {
      return this;
    }
    return negate();
  }
  public Dfp copySign(final Dfp s) {
    if((sign >= 0 && s.sign >= 0) || (sign < 0 && s.sign < 0)) {
      return this;
    }
    return negate();
  }
  public static Dfp copysign(final Dfp x, final Dfp y) {
    Dfp result = x.newInstance(x);
    result.sign = y.sign;
    return result;
  }
  public Dfp cos() {
    return DfpMath.cos(this);
  }
  public Dfp cosh() {
    return DfpMath.exp(this).add(DfpMath.exp(negate())).divide(2);
  }
  public Dfp divide(final double a) {
    return divide(newInstance(a));
  }
  public Dfp divide(int divisor) {
    if(nans != FINITE) {
      if(isNaN()) {
        return this;
      }
      if(nans == INFINITE) {
        return newInstance(this);
      }
    }
    if(divisor == 0) {
      field.setIEEEFlagsBits(DfpField.FLAG_DIV_ZERO);
      Dfp result = newInstance(getZero());
      result.sign = sign;
      result.nans = INFINITE;
      result = dotrap(DfpField.FLAG_DIV_ZERO, DIVIDE_TRAP, getZero(), result);
      return result;
    }
    if(divisor < 0 || divisor >= RADIX) {
      field.setIEEEFlagsBits(DfpField.FLAG_INVALID);
      Dfp result = newInstance(getZero());
      result.nans = QNAN;
      result = dotrap(DfpField.FLAG_INVALID, DIVIDE_TRAP, result, result);
      return result;
    }
    Dfp result = newInstance(this);
    int rl = 0;
    for(int i = mant.length - 1; i >= 0; i--) {
      final int r = rl * RADIX + result.mant[i];
      final int rh = r / divisor;
      rl = r - rh * divisor;
      result.mant[i] = rh;
    }
    if(result.mant[mant.length - 1] == 0) {
      result.shiftLeft();
      final int r = rl * RADIX;
      final int rh = r / divisor;
      rl = r - rh * divisor;
      result.mant[0] = rh;
    }
    final int excp = result.round(rl * RADIX / divisor);
    if(excp != 0) {
      result = dotrap(excp, DIVIDE_TRAP, result, result);
    }
    return result;
  }
  public Dfp divide(Dfp divisor) {
    int[] dividend;
    int[] quotient;
    int[] remainder;
    int qd;
    int nsqd;
    int trial = 0;
    int minadj;
    boolean trialgood;
    int md = 0;
    int excp;
    if(field.getRadixDigits() != divisor.field.getRadixDigits()) {
      field.setIEEEFlagsBits(DfpField.FLAG_INVALID);
      final Dfp result = newInstance(getZero());
      result.nans = QNAN;
      return dotrap(DfpField.FLAG_INVALID, DIVIDE_TRAP, divisor, result);
    }
    Dfp result = newInstance(getZero());
    if(nans != FINITE || divisor.nans != FINITE) {
      if(isNaN()) {
        return this;
      }
      if(divisor.isNaN()) {
        return divisor;
      }
      if(nans == INFINITE && divisor.nans == FINITE) {
        result = newInstance(this);
        result.sign = (byte)(sign * divisor.sign);
        return result;
      }
      if(divisor.nans == INFINITE && nans == FINITE) {
        result = newInstance(getZero());
        result.sign = (byte)(sign * divisor.sign);
        return result;
      }
      if(divisor.nans == INFINITE && nans == INFINITE) {
        field.setIEEEFlagsBits(DfpField.FLAG_INVALID);
        result = newInstance(getZero());
        result.nans = QNAN;
        result = dotrap(DfpField.FLAG_INVALID, DIVIDE_TRAP, divisor, result);
        return result;
      }
    }
    if(divisor.mant[mant.length - 1] == 0) {
      field.setIEEEFlagsBits(DfpField.FLAG_DIV_ZERO);
      result = newInstance(getZero());
      result.sign = (byte)(sign * divisor.sign);
      result.nans = INFINITE;
      result = dotrap(DfpField.FLAG_DIV_ZERO, DIVIDE_TRAP, divisor, result);
      return result;
    }
    dividend = new int[mant.length + 1];
    quotient = new int[mant.length + 2];
    remainder = new int[mant.length + 1];
    dividend[mant.length] = 0;
    quotient[mant.length] = 0;
    quotient[mant.length + 1] = 0;
    remainder[mant.length] = 0;
    for(int i = 0; i < mant.length; i++) {
      dividend[i] = mant[i];
      quotient[i] = 0;
      remainder[i] = 0;
    }
    nsqd = 0;
    for(qd = mant.length + 1; qd >= 0; qd--) {
      final int divMsb = dividend[mant.length] * RADIX + dividend[mant.length - 1];
      int min = divMsb / (divisor.mant[mant.length - 1] + 1);
      int max = (divMsb + 1) / divisor.mant[mant.length - 1];
      trialgood = false;
      while(!trialgood){
        trial = (min + max) / 2;
        int rh = 0;
        for(int i = 0; i < mant.length + 1; i++) {
          int dm = (i < mant.length) ? divisor.mant[i] : 0;
          final int r = (dm * trial) + rh;
          rh = r / RADIX;
          remainder[i] = r - rh * RADIX;
        }
        rh = 1;
        for(int i = 0; i < mant.length + 1; i++) {
          final int r = ((RADIX - 1) - remainder[i]) + dividend[i] + rh;
          rh = r / RADIX;
          remainder[i] = r - rh * RADIX;
        }
        if(rh == 0) {
          max = trial - 1;
          continue ;
        }
        minadj = (remainder[mant.length] * RADIX) + remainder[mant.length - 1];
        minadj = minadj / (divisor.mant[mant.length - 1] + 1);
        if(minadj >= 2) {
          min = trial + minadj;
          continue ;
        }
        trialgood = false;
        for(int i = mant.length - 1; i >= 0; i--) {
          if(divisor.mant[i] > remainder[i]) {
            trialgood = true;
          }
          if(divisor.mant[i] < remainder[i]) {
            break ;
          }
        }
        if(remainder[mant.length] != 0) {
          trialgood = false;
        }
        if(trialgood == false) {
          min = trial + 1;
        }
      }
      quotient[qd] = trial;
      if(trial != 0 || nsqd != 0) {
        nsqd++;
      }
      if(field.getRoundingMode() == DfpField.RoundingMode.ROUND_DOWN && nsqd == mant.length) {
        break ;
      }
      if(nsqd > mant.length) {
        break ;
      }
      dividend[0] = 0;
      for(int i = 0; i < mant.length; i++) {
        dividend[i + 1] = remainder[i];
      }
    }
    md = mant.length;
    for(int i = mant.length + 1; i >= 0; i--) {
      if(quotient[i] != 0) {
        md = i;
        break ;
      }
    }
    for(int i = 0; i < mant.length; i++) {
      result.mant[mant.length - i - 1] = quotient[md - i];
    }
    result.exp = exp - divisor.exp + md - mant.length;
    result.sign = (byte)((sign == divisor.sign) ? 1 : -1);
    if(result.mant[mant.length - 1] == 0) {
      result.exp = 0;
    }
    if(md > (mant.length - 1)) {
      excp = result.round(quotient[md - mant.length]);
    }
    else {
      excp = result.round(0);
    }
    if(excp != 0) {
      result = dotrap(excp, DIVIDE_TRAP, divisor, result);
    }
    return result;
  }
  public Dfp dotrap(int type, String what, Dfp oper, Dfp result) {
    Dfp def = result;
    switch (type){
      case DfpField.FLAG_INVALID:
      def = newInstance(getZero());
      def.sign = result.sign;
      def.nans = QNAN;
      break ;
      case DfpField.FLAG_DIV_ZERO:
      if(nans == FINITE && mant[mant.length - 1] != 0) {
        def = newInstance(getZero());
        def.sign = (byte)(sign * oper.sign);
        def.nans = INFINITE;
      }
      if(nans == FINITE && mant[mant.length - 1] == 0) {
        def = newInstance(getZero());
        def.nans = QNAN;
      }
      if(nans == INFINITE || nans == QNAN) {
        def = newInstance(getZero());
        def.nans = QNAN;
      }
      if(nans == INFINITE || nans == SNAN) {
        def = newInstance(getZero());
        def.nans = QNAN;
      }
      break ;
      case DfpField.FLAG_UNDERFLOW:
      if((result.exp + mant.length) < MIN_EXP) {
        def = newInstance(getZero());
        def.sign = result.sign;
      }
      else {
        def = newInstance(result);
      }
      result.exp = result.exp + ERR_SCALE;
      break ;
      case DfpField.FLAG_OVERFLOW:
      result.exp = result.exp - ERR_SCALE;
      def = newInstance(getZero());
      def.sign = result.sign;
      def.nans = INFINITE;
      break ;
      default:
      def = result;
      break ;
    }
    return trap(type, what, oper, def, result);
  }
  public Dfp exp() {
    return DfpMath.exp(this);
  }
  public Dfp expm1() {
    return DfpMath.exp(this).subtract(getOne());
  }
  public Dfp floor() {
    return trunc(DfpField.RoundingMode.ROUND_FLOOR);
  }
  public Dfp getOne() {
    return field.getOne();
  }
  public Dfp getTwo() {
    return field.getTwo();
  }
  public Dfp getZero() {
    return field.getZero();
  }
  public Dfp hypot(final Dfp y) {
    return multiply(this).add(y.multiply(y)).sqrt();
  }
  public Dfp linearCombination(final double a1, final Dfp b1, final double a2, final Dfp b2) {
    return b1.multiply(a1).add(b2.multiply(a2));
  }
  public Dfp linearCombination(final double a1, final Dfp b1, final double a2, final Dfp b2, final double a3, final Dfp b3) {
    return b1.multiply(a1).add(b2.multiply(a2)).add(b3.multiply(a3));
  }
  public Dfp linearCombination(final double a1, final Dfp b1, final double a2, final Dfp b2, final double a3, final Dfp b3, final double a4, final Dfp b4) {
    return b1.multiply(a1).add(b2.multiply(a2)).add(b3.multiply(a3)).add(b4.multiply(a4));
  }
  public Dfp linearCombination(final double[] a, final Dfp[] b) throws DimensionMismatchException {
    if(a.length != b.length) {
      throw new DimensionMismatchException(a.length, b.length);
    }
    Dfp r = getZero();
    for(int i = 0; i < a.length; ++i) {
      r = r.add(b[i].multiply(a[i]));
    }
    return r;
  }
  public Dfp linearCombination(final Dfp a1, final Dfp b1, final Dfp a2, final Dfp b2) {
    return a1.multiply(b1).add(a2.multiply(b2));
  }
  public Dfp linearCombination(final Dfp a1, final Dfp b1, final Dfp a2, final Dfp b2, final Dfp a3, final Dfp b3) {
    return a1.multiply(b1).add(a2.multiply(b2)).add(a3.multiply(b3));
  }
  public Dfp linearCombination(final Dfp a1, final Dfp b1, final Dfp a2, final Dfp b2, final Dfp a3, final Dfp b3, final Dfp a4, final Dfp b4) {
    return a1.multiply(b1).add(a2.multiply(b2)).add(a3.multiply(b3)).add(a4.multiply(b4));
  }
  public Dfp linearCombination(final Dfp[] a, final Dfp[] b) throws DimensionMismatchException {
    if(a.length != b.length) {
      throw new DimensionMismatchException(a.length, b.length);
    }
    Dfp r = getZero();
    for(int i = 0; i < a.length; ++i) {
      r = r.add(a[i].multiply(b[i]));
    }
    return r;
  }
  public Dfp log() {
    return DfpMath.log(this);
  }
  public Dfp log1p() {
    return DfpMath.log(this.add(getOne()));
  }
  public Dfp multiply(final double a) {
    return multiply(newInstance(a));
  }
  public Dfp multiply(final int x) {
    if(x >= 0 && x < RADIX) {
      return multiplyFast(x);
    }
    else {
      return multiply(newInstance(x));
    }
  }
  public Dfp multiply(final Dfp x) {
    if(field.getRadixDigits() != x.field.getRadixDigits()) {
      field.setIEEEFlagsBits(DfpField.FLAG_INVALID);
      final Dfp result = newInstance(getZero());
      result.nans = QNAN;
      return dotrap(DfpField.FLAG_INVALID, MULTIPLY_TRAP, x, result);
    }
    Dfp result = newInstance(getZero());
    if(nans != FINITE || x.nans != FINITE) {
      if(isNaN()) {
        return this;
      }
      if(x.isNaN()) {
        return x;
      }
      if(nans == INFINITE && x.nans == FINITE && x.mant[mant.length - 1] != 0) {
        result = newInstance(this);
        result.sign = (byte)(sign * x.sign);
        return result;
      }
      if(x.nans == INFINITE && nans == FINITE && mant[mant.length - 1] != 0) {
        result = newInstance(x);
        result.sign = (byte)(sign * x.sign);
        return result;
      }
      if(x.nans == INFINITE && nans == INFINITE) {
        result = newInstance(this);
        result.sign = (byte)(sign * x.sign);
        return result;
      }
      if((x.nans == INFINITE && nans == FINITE && mant[mant.length - 1] == 0) || (nans == INFINITE && x.nans == FINITE && x.mant[mant.length - 1] == 0)) {
        field.setIEEEFlagsBits(DfpField.FLAG_INVALID);
        result = newInstance(getZero());
        result.nans = QNAN;
        result = dotrap(DfpField.FLAG_INVALID, MULTIPLY_TRAP, x, result);
        return result;
      }
    }
    int[] product = new int[mant.length * 2];
    for(int i = 0; i < mant.length; i++) {
      int rh = 0;
      for(int j = 0; j < mant.length; j++) {
        int r = mant[i] * x.mant[j];
        r = r + product[i + j] + rh;
        rh = r / RADIX;
        product[i + j] = r - rh * RADIX;
      }
      product[i + mant.length] = rh;
    }
    int md = mant.length * 2 - 1;
    for(int i = mant.length * 2 - 1; i >= 0; i--) {
      if(product[i] != 0) {
        md = i;
        break ;
      }
    }
    for(int i = 0; i < mant.length; i++) {
      result.mant[mant.length - i - 1] = product[md - i];
    }
    result.exp = exp + x.exp + md - 2 * mant.length + 1;
    result.sign = (byte)((sign == x.sign) ? 1 : -1);
    if(result.mant[mant.length - 1] == 0) {
      result.exp = 0;
    }
    final int excp;
    if(md > (mant.length - 1)) {
      excp = result.round(product[md - mant.length]);
    }
    else {
      excp = result.round(0);
    }
    if(excp != 0) {
      result = dotrap(excp, MULTIPLY_TRAP, x, result);
    }
    return result;
  }
  private Dfp multiplyFast(final int x) {
    Dfp result = newInstance(this);
    if(nans != FINITE) {
      if(isNaN()) {
        return this;
      }
      if(nans == INFINITE && x != 0) {
        result = newInstance(this);
        return result;
      }
      if(nans == INFINITE && x == 0) {
        field.setIEEEFlagsBits(DfpField.FLAG_INVALID);
        result = newInstance(getZero());
        result.nans = QNAN;
        result = dotrap(DfpField.FLAG_INVALID, MULTIPLY_TRAP, newInstance(getZero()), result);
        return result;
      }
    }
    if(x < 0 || x >= RADIX) {
      field.setIEEEFlagsBits(DfpField.FLAG_INVALID);
      result = newInstance(getZero());
      result.nans = QNAN;
      result = dotrap(DfpField.FLAG_INVALID, MULTIPLY_TRAP, result, result);
      return result;
    }
    int rh = 0;
    for(int i = 0; i < mant.length; i++) {
      final int r = mant[i] * x + rh;
      rh = r / RADIX;
      result.mant[i] = r - rh * RADIX;
    }
    int lostdigit = 0;
    if(rh != 0) {
      lostdigit = result.mant[0];
      result.shiftRight();
      result.mant[mant.length - 1] = rh;
    }
    if(result.mant[mant.length - 1] == 0) {
      result.exp = 0;
    }
    final int excp = result.round(lostdigit);
    if(excp != 0) {
      result = dotrap(excp, MULTIPLY_TRAP, result, result);
    }
    return result;
  }
  public Dfp negate() {
    Dfp result = newInstance(this);
    result.sign = (byte)-result.sign;
    return result;
  }
  public Dfp newInstance() {
    return new Dfp(getField());
  }
  public Dfp newInstance(final byte x) {
    return new Dfp(getField(), x);
  }
  public Dfp newInstance(final byte sig, final byte code) {
    return field.newDfp(sig, code);
  }
  public Dfp newInstance(final double x) {
    return new Dfp(getField(), x);
  }
  public Dfp newInstance(final int x) {
    return new Dfp(getField(), x);
  }
  public Dfp newInstance(final String s) {
    return new Dfp(field, s);
  }
  public Dfp newInstance(final long x) {
    return new Dfp(getField(), x);
  }
  public Dfp newInstance(final Dfp d) {
    if(field.getRadixDigits() != d.field.getRadixDigits()) {
      field.setIEEEFlagsBits(DfpField.FLAG_INVALID);
      final Dfp result = newInstance(getZero());
      result.nans = QNAN;
      return dotrap(DfpField.FLAG_INVALID, NEW_INSTANCE_TRAP, d, result);
    }
    return new Dfp(d);
  }
  public Dfp nextAfter(final Dfp x) {
    if(field.getRadixDigits() != x.field.getRadixDigits()) {
      field.setIEEEFlagsBits(DfpField.FLAG_INVALID);
      final Dfp result = newInstance(getZero());
      result.nans = QNAN;
      return dotrap(DfpField.FLAG_INVALID, NEXT_AFTER_TRAP, x, result);
    }
    boolean up = false;
    if(this.lessThan(x)) {
      up = true;
    }
    if(compare(this, x) == 0) {
      return newInstance(x);
    }
    if(lessThan(getZero())) {
      up = !up;
    }
    final Dfp inc;
    Dfp result;
    if(up) {
      inc = newInstance(getOne());
      inc.exp = this.exp - mant.length + 1;
      inc.sign = this.sign;
      if(this.equals(getZero())) {
        inc.exp = MIN_EXP - mant.length;
      }
      result = add(inc);
    }
    else {
      inc = newInstance(getOne());
      inc.exp = this.exp;
      inc.sign = this.sign;
      if(this.equals(inc)) {
        inc.exp = this.exp - mant.length;
      }
      else {
        inc.exp = this.exp - mant.length + 1;
      }
      if(this.equals(getZero())) {
        inc.exp = MIN_EXP - mant.length;
      }
      result = this.subtract(inc);
    }
    if(result.classify() == INFINITE && this.classify() != INFINITE) {
      field.setIEEEFlagsBits(DfpField.FLAG_INEXACT);
      result = dotrap(DfpField.FLAG_INEXACT, NEXT_AFTER_TRAP, x, result);
    }
    if(result.equals(getZero()) && this.equals(getZero()) == false) {
      field.setIEEEFlagsBits(DfpField.FLAG_INEXACT);
      result = dotrap(DfpField.FLAG_INEXACT, NEXT_AFTER_TRAP, x, result);
    }
    return result;
  }
  public Dfp pow(final double p) {
    return DfpMath.pow(this, newInstance(p));
  }
  public Dfp pow(final int n) {
    return DfpMath.pow(this, n);
  }
  public Dfp pow(final Dfp e) {
    return DfpMath.pow(this, e);
  }
  public Dfp power10(final int e) {
    Dfp d = newInstance(getOne());
    if(e >= 0) {
      d.exp = e / 4 + 1;
    }
    else {
      d.exp = (e + 1) / 4;
    }
    switch ((e % 4 + 4) % 4){
      case 0:
      break ;
      case 1:
      d = d.multiply(10);
      break ;
      case 2:
      d = d.multiply(100);
      break ;
      default:
      d = d.multiply(1000);
    }
    return d;
  }
  public Dfp power10K(final int e) {
    Dfp d = newInstance(getOne());
    d.exp = e + 1;
    return d;
  }
  public Dfp reciprocal() {
    return field.getOne().divide(this);
  }
  public Dfp remainder(final double a) {
    return remainder(newInstance(a));
  }
  public Dfp remainder(final Dfp d) {
    final Dfp result = this.subtract(this.divide(d).rint().multiply(d));
    if(result.mant[mant.length - 1] == 0) {
      result.sign = sign;
    }
    return result;
  }
  public Dfp rint() {
    return trunc(DfpField.RoundingMode.ROUND_HALF_EVEN);
  }
  public Dfp rootN(final int n) {
    return (sign >= 0) ? DfpMath.pow(this, getOne().divide(n)) : DfpMath.pow(negate(), getOne().divide(n)).negate();
  }
  public Dfp scalb(final int n) {
    return multiply(DfpMath.pow(getTwo(), n));
  }
  public Dfp signum() {
    if(isNaN() || isZero()) {
      return this;
    }
    else {
      return newInstance(sign > 0 ? +1 : -1);
    }
  }
  public Dfp sin() {
    return DfpMath.sin(this);
  }
  public Dfp sinh() {
    return DfpMath.exp(this).subtract(DfpMath.exp(negate())).divide(2);
  }
  public Dfp sqrt() {
    if(nans == FINITE && mant[mant.length - 1] == 0) {
      return newInstance(this);
    }
    if(nans != FINITE) {
      if(nans == INFINITE && sign == 1) {
        return newInstance(this);
      }
      if(nans == QNAN) {
        return newInstance(this);
      }
      if(nans == SNAN) {
        Dfp result;
        field.setIEEEFlagsBits(DfpField.FLAG_INVALID);
        result = newInstance(this);
        result = dotrap(DfpField.FLAG_INVALID, SQRT_TRAP, null, result);
        return result;
      }
    }
    if(sign == -1) {
      Dfp result;
      field.setIEEEFlagsBits(DfpField.FLAG_INVALID);
      result = newInstance(this);
      result.nans = QNAN;
      result = dotrap(DfpField.FLAG_INVALID, SQRT_TRAP, null, result);
      return result;
    }
    Dfp x = newInstance(this);
    if(x.exp < -1 || x.exp > 1) {
      x.exp = this.exp / 2;
    }
    switch (x.mant[mant.length - 1] / 2000){
      case 0:
      x.mant[mant.length - 1] = x.mant[mant.length - 1] / 2 + 1;
      break ;
      case 2:
      x.mant[mant.length - 1] = 1500;
      break ;
      case 3:
      x.mant[mant.length - 1] = 2200;
      break ;
      default:
      x.mant[mant.length - 1] = 3000;
    }
    Dfp dx = newInstance(x);
    Dfp px = getZero();
    Dfp ppx = getZero();
    while(x.unequal(px)){
      dx = newInstance(x);
      dx.sign = -1;
      dx = dx.add(this.divide(x));
      dx = dx.divide(2);
      ppx = px;
      px = x;
      x = x.add(dx);
      if(x.equals(ppx)) {
        break ;
      }
      if(dx.mant[mant.length - 1] == 0) {
        break ;
      }
    }
    return x;
  }
  public Dfp subtract(final double a) {
    return subtract(newInstance(a));
  }
  public Dfp subtract(final Dfp x) {
    return add(x.negate());
  }
  public Dfp tan() {
    return DfpMath.tan(this);
  }
  public Dfp tanh() {
    final Dfp ePlus = DfpMath.exp(this);
    final Dfp eMinus = DfpMath.exp(negate());
    return ePlus.subtract(eMinus).divide(ePlus.add(eMinus));
  }
  protected Dfp trap(int type, String what, Dfp oper, Dfp def, Dfp result) {
    return def;
  }
  protected Dfp trunc(final DfpField.RoundingMode rmode) {
    boolean changed = false;
    if(isNaN()) {
      return newInstance(this);
    }
    if(nans == INFINITE) {
      return newInstance(this);
    }
    if(mant[mant.length - 1] == 0) {
      return newInstance(this);
    }
    if(exp < 0) {
      field.setIEEEFlagsBits(DfpField.FLAG_INEXACT);
      Dfp result = newInstance(getZero());
      result = dotrap(DfpField.FLAG_INEXACT, TRUNC_TRAP, this, result);
      return result;
    }
    if(exp >= mant.length) {
      return newInstance(this);
    }
    Dfp result = newInstance(this);
    for(int i = 0; i < mant.length - result.exp; i++) {
      changed |= result.mant[i] != 0;
      result.mant[i] = 0;
    }
    if(changed) {
      switch (rmode){
        case ROUND_FLOOR:
        if(result.sign == -1) {
          result = result.add(newInstance(-1));
        }
        break ;
        case ROUND_CEIL:
        if(result.sign == 1) {
          result = result.add(getOne());
        }
        break ;
        case ROUND_HALF_EVEN:
        default:
        final Dfp half = newInstance("0.5");
        Dfp a = subtract(result);
        a.sign = 1;
        if(a.greaterThan(half)) {
          a = newInstance(getOne());
          a.sign = sign;
          result = result.add(a);
        }
        if(a.equals(half) && result.exp > 0 && (result.mant[mant.length - result.exp] & 1) != 0) {
          a = newInstance(getOne());
          a.sign = sign;
          result = result.add(a);
        }
        break ;
      }
      field.setIEEEFlagsBits(DfpField.FLAG_INEXACT);
      result = dotrap(DfpField.FLAG_INEXACT, TRUNC_TRAP, this, result);
      return result;
    }
    return result;
  }
  public DfpField getField() {
    return field;
  }
  protected String dfp2sci() {
    char[] rawdigits = new char[mant.length * 4];
    char[] outputbuffer = new char[mant.length * 4 + 20];
    int p;
    int q;
    int e;
    int ae;
    int shf;
    p = 0;
    for(int i = mant.length - 1; i >= 0; i--) {
      rawdigits[p++] = (char)((mant[i] / 1000) + '0');
      rawdigits[p++] = (char)(((mant[i] / 100) % 10) + '0');
      rawdigits[p++] = (char)(((mant[i] / 10) % 10) + '0');
      rawdigits[p++] = (char)(((mant[i]) % 10) + '0');
    }
    for(p = 0; p < rawdigits.length; p++) {
      if(rawdigits[p] != '0') {
        break ;
      }
    }
    shf = p;
    q = 0;
    if(sign == -1) {
      outputbuffer[q++] = '-';
    }
    if(p != rawdigits.length) {
      outputbuffer[q++] = rawdigits[p++];
      outputbuffer[q++] = '.';
      while(p < rawdigits.length){
        outputbuffer[q++] = rawdigits[p++];
      }
    }
    else {
      outputbuffer[q++] = '0';
      outputbuffer[q++] = '.';
      outputbuffer[q++] = '0';
      outputbuffer[q++] = 'e';
      outputbuffer[q++] = '0';
      return new String(outputbuffer, 0, 5);
    }
    outputbuffer[q++] = 'e';
    e = exp * 4 - shf - 1;
    ae = e;
    if(e < 0) {
      ae = -e;
    }
    for(p = 1000000000; p > ae; p /= 10) {
    }
    if(e < 0) {
      outputbuffer[q++] = '-';
    }
    while(p > 0){
      outputbuffer[q++] = (char)(ae / p + '0');
      ae = ae % p;
      p = p / 10;
    }
    return new String(outputbuffer, 0, q);
  }
  protected String dfp2string() {
    char[] buffer = new char[mant.length * 4 + 20];
    int p = 1;
    int q;
    int e = exp;
    boolean pointInserted = false;
    buffer[0] = ' ';
    if(e <= 0) {
      buffer[p++] = '0';
      buffer[p++] = '.';
      pointInserted = true;
    }
    while(e < 0){
      buffer[p++] = '0';
      buffer[p++] = '0';
      buffer[p++] = '0';
      buffer[p++] = '0';
      e++;
    }
    for(int i = mant.length - 1; i >= 0; i--) {
      buffer[p++] = (char)((mant[i] / 1000) + '0');
      buffer[p++] = (char)(((mant[i] / 100) % 10) + '0');
      buffer[p++] = (char)(((mant[i] / 10) % 10) + '0');
      buffer[p++] = (char)(((mant[i]) % 10) + '0');
      if(--e == 0) {
        buffer[p++] = '.';
        pointInserted = true;
      }
    }
    while(e > 0){
      buffer[p++] = '0';
      buffer[p++] = '0';
      buffer[p++] = '0';
      buffer[p++] = '0';
      e--;
    }
    if(!pointInserted) {
      buffer[p++] = '.';
    }
    q = 1;
    while(buffer[q] == '0'){
      q++;
    }
    if(buffer[q] == '.') {
      q--;
    }
    while(buffer[p - 1] == '0'){
      p--;
    }
    if(sign < 0) {
      buffer[--q] = '-';
    }
    return new String(buffer, q, p - q);
  }
  @Override() public String toString() {
    if(nans != FINITE) {
      if(nans == INFINITE) {
        return (sign < 0) ? NEG_INFINITY_STRING : POS_INFINITY_STRING;
      }
      else {
        return NAN_STRING;
      }
    }
    if(exp > mant.length || exp < -1) {
      return dfp2sci();
    }
    return dfp2string();
  }
  @Override() public boolean equals(final Object other) {
    if(other instanceof Dfp) {
      final Dfp x = (Dfp)other;
      if(isNaN() || x.isNaN() || field.getRadixDigits() != x.field.getRadixDigits()) {
        return false;
      }
      return compare(this, x) == 0;
    }
    return false;
  }
  public boolean greaterThan(final Dfp x) {
    if(field.getRadixDigits() != x.field.getRadixDigits()) {
      field.setIEEEFlagsBits(DfpField.FLAG_INVALID);
      final Dfp result = newInstance(getZero());
      result.nans = QNAN;
      dotrap(DfpField.FLAG_INVALID, GREATER_THAN_TRAP, x, result);
      return false;
    }
    if(isNaN() || x.isNaN()) {
      field.setIEEEFlagsBits(DfpField.FLAG_INVALID);
      dotrap(DfpField.FLAG_INVALID, GREATER_THAN_TRAP, x, newInstance(getZero()));
      return false;
    }
    return compare(this, x) > 0;
  }
  public boolean isInfinite() {
    return nans == INFINITE;
  }
  public boolean isNaN() {
    return (nans == QNAN) || (nans == SNAN);
  }
  public boolean isZero() {
    if(isNaN()) {
      field.setIEEEFlagsBits(DfpField.FLAG_INVALID);
      dotrap(DfpField.FLAG_INVALID, LESS_THAN_TRAP, this, newInstance(getZero()));
      return false;
    }
    return (mant[mant.length - 1] == 0) && !isInfinite();
  }
  public boolean lessThan(final Dfp x) {
    if(field.getRadixDigits() != x.field.getRadixDigits()) {
      field.setIEEEFlagsBits(DfpField.FLAG_INVALID);
      final Dfp result = newInstance(getZero());
      result.nans = QNAN;
      dotrap(DfpField.FLAG_INVALID, LESS_THAN_TRAP, x, result);
      return false;
    }
    if(isNaN() || x.isNaN()) {
      field.setIEEEFlagsBits(DfpField.FLAG_INVALID);
      dotrap(DfpField.FLAG_INVALID, LESS_THAN_TRAP, x, newInstance(getZero()));
      return false;
    }
    return compare(this, x) < 0;
  }
  public boolean negativeOrNull() {
    if(isNaN()) {
      field.setIEEEFlagsBits(DfpField.FLAG_INVALID);
      dotrap(DfpField.FLAG_INVALID, LESS_THAN_TRAP, this, newInstance(getZero()));
      return false;
    }
    return (sign < 0) || ((mant[mant.length - 1] == 0) && !isInfinite());
  }
  public boolean positiveOrNull() {
    if(isNaN()) {
      field.setIEEEFlagsBits(DfpField.FLAG_INVALID);
      dotrap(DfpField.FLAG_INVALID, LESS_THAN_TRAP, this, newInstance(getZero()));
      return false;
    }
    return (sign > 0) || ((mant[mant.length - 1] == 0) && !isInfinite());
  }
  public boolean strictlyNegative() {
    if(isNaN()) {
      field.setIEEEFlagsBits(DfpField.FLAG_INVALID);
      dotrap(DfpField.FLAG_INVALID, LESS_THAN_TRAP, this, newInstance(getZero()));
      return false;
    }
    return (sign < 0) && ((mant[mant.length - 1] != 0) || isInfinite());
  }
  public boolean strictlyPositive() {
    if(isNaN()) {
      field.setIEEEFlagsBits(DfpField.FLAG_INVALID);
      dotrap(DfpField.FLAG_INVALID, LESS_THAN_TRAP, this, newInstance(getZero()));
      return false;
    }
    return (sign > 0) && ((mant[mant.length - 1] != 0) || isInfinite());
  }
  public boolean unequal(final Dfp x) {
    if(isNaN() || x.isNaN() || field.getRadixDigits() != x.field.getRadixDigits()) {
      return false;
    }
    return greaterThan(x) || lessThan(x);
  }
  public double getReal() {
    return toDouble();
  }
  public double toDouble() {
    if(isInfinite()) {
      if(lessThan(getZero())) {
        return Double.NEGATIVE_INFINITY;
      }
      else {
        return Double.POSITIVE_INFINITY;
      }
    }
    if(isNaN()) {
      return Double.NaN;
    }
    Dfp y = this;
    boolean negate = false;
    int cmp0 = compare(this, getZero());
    if(cmp0 == 0) {
      return sign < 0 ? -0.0D : +0.0D;
    }
    else 
      if(cmp0 < 0) {
        y = negate();
        negate = true;
      }
    int exponent = (int)(y.intLog10() * 3.32D);
    if(exponent < 0) {
      exponent--;
    }
    Dfp tempDfp = DfpMath.pow(getTwo(), exponent);
    while(tempDfp.lessThan(y) || tempDfp.equals(y)){
      tempDfp = tempDfp.multiply(2);
      exponent++;
    }
    exponent--;
    y = y.divide(DfpMath.pow(getTwo(), exponent));
    if(exponent > -1023) {
      y = y.subtract(getOne());
    }
    if(exponent < -1074) {
      return 0;
    }
    if(exponent > 1023) {
      return negate ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
    }
    y = y.multiply(newInstance(4503599627370496L)).rint();
    String str = y.toString();
    str = str.substring(0, str.length() - 1);
    long mantissa = Long.parseLong(str);
    if(mantissa == 4503599627370496L) {
      mantissa = 0;
      exponent++;
    }
    if(exponent <= -1023) {
      exponent--;
    }
    while(exponent < -1023){
      exponent++;
      mantissa >>>= 1;
    }
    long bits = mantissa | ((exponent + 1023L) << 52);
    double x = Double.longBitsToDouble(bits);
    if(negate) {
      x = -x;
    }
    return x;
  }
  public double[] toSplitDouble() {
    double[] split = new double[2];
    long mask = 0xffffffffc0000000L;
    split[0] = Double.longBitsToDouble(Double.doubleToLongBits(toDouble()) & mask);
    split[1] = subtract(newInstance(split[0])).toDouble();
    return split;
  }
  protected int align(int e) {
    int lostdigit = 0;
    boolean inexact = false;
    int diff = exp - e;
    int adiff = diff;
    if(adiff < 0) {
      adiff = -adiff;
    }
    if(diff == 0) {
      return 0;
    }
    if(adiff > (mant.length + 1)) {
      Arrays.fill(mant, 0);
      exp = e;
      field.setIEEEFlagsBits(DfpField.FLAG_INEXACT);
      dotrap(DfpField.FLAG_INEXACT, ALIGN_TRAP, this, this);
      return 0;
    }
    for(int i = 0; i < adiff; i++) {
      if(diff < 0) {
        if(lostdigit != 0) {
          inexact = true;
        }
        lostdigit = mant[0];
        shiftRight();
      }
      else {
        shiftLeft();
      }
    }
    if(inexact) {
      field.setIEEEFlagsBits(DfpField.FLAG_INEXACT);
      dotrap(DfpField.FLAG_INEXACT, ALIGN_TRAP, this, this);
    }
    return lostdigit;
  }
  public int classify() {
    return nans;
  }
  private static int compare(final Dfp a, final Dfp b) {
    int[] var_577 = a.mant;
    if(var_577[a.mant.length - 1] == 0 && b.mant[b.mant.length - 1] == 0 && a.nans == FINITE && b.nans == FINITE) {
      return 0;
    }
    if(a.sign != b.sign) {
      if(a.sign == -1) {
        return -1;
      }
      else {
        return 1;
      }
    }
    if(a.nans == INFINITE && b.nans == FINITE) {
      return a.sign;
    }
    if(a.nans == FINITE && b.nans == INFINITE) {
      return -b.sign;
    }
    if(a.nans == INFINITE && b.nans == INFINITE) {
      return 0;
    }
    if(b.mant[b.mant.length - 1] != 0 && a.mant[b.mant.length - 1] != 0) {
      if(a.exp < b.exp) {
        return -a.sign;
      }
      if(a.exp > b.exp) {
        return a.sign;
      }
    }
    for(int i = a.mant.length - 1; i >= 0; i--) {
      if(a.mant[i] > b.mant[i]) {
        return a.sign;
      }
      if(a.mant[i] < b.mant[i]) {
        return -a.sign;
      }
    }
    return 0;
  }
  protected int complement(int extra) {
    extra = RADIX - extra;
    for(int i = 0; i < mant.length; i++) {
      mant[i] = RADIX - mant[i] - 1;
    }
    int rh = extra / RADIX;
    extra = extra - rh * RADIX;
    for(int i = 0; i < mant.length; i++) {
      final int r = mant[i] + rh;
      rh = r / RADIX;
      mant[i] = r - rh * RADIX;
    }
    return extra;
  }
  public int getRadixDigits() {
    return field.getRadixDigits();
  }
  @Override() public int hashCode() {
    return 17 + (sign << 8) + (nans << 16) + exp + Arrays.hashCode(mant);
  }
  public int intLog10() {
    if(mant[mant.length - 1] > 1000) {
      return exp * 4 - 1;
    }
    if(mant[mant.length - 1] > 100) {
      return exp * 4 - 2;
    }
    if(mant[mant.length - 1] > 10) {
      return exp * 4 - 3;
    }
    return exp * 4 - 4;
  }
  public int intValue() {
    Dfp rounded;
    int result = 0;
    rounded = rint();
    if(rounded.greaterThan(newInstance(2147483647))) {
      return 2147483647;
    }
    if(rounded.lessThan(newInstance(-2147483648))) {
      return -2147483648;
    }
    for(int i = mant.length - 1; i >= mant.length - rounded.exp; i--) {
      result = result * RADIX + rounded.mant[i];
    }
    if(rounded.sign == -1) {
      result = -result;
    }
    return result;
  }
  @Deprecated() public int log10() {
    return intLog10();
  }
  public int log10K() {
    return exp - 1;
  }
  protected int round(int n) {
    boolean inc = false;
    switch (field.getRoundingMode()){
      case ROUND_DOWN:
      inc = false;
      break ;
      case ROUND_UP:
      inc = n != 0;
      break ;
      case ROUND_HALF_UP:
      inc = n >= 5000;
      break ;
      case ROUND_HALF_DOWN:
      inc = n > 5000;
      break ;
      case ROUND_HALF_EVEN:
      inc = n > 5000 || (n == 5000 && (mant[0] & 1) == 1);
      break ;
      case ROUND_HALF_ODD:
      inc = n > 5000 || (n == 5000 && (mant[0] & 1) == 0);
      break ;
      case ROUND_CEIL:
      inc = sign == 1 && n != 0;
      break ;
      case ROUND_FLOOR:
      default:
      inc = sign == -1 && n != 0;
      break ;
    }
    if(inc) {
      int rh = 1;
      for(int i = 0; i < mant.length; i++) {
        final int r = mant[i] + rh;
        rh = r / RADIX;
        mant[i] = r - rh * RADIX;
      }
      if(rh != 0) {
        shiftRight();
        mant[mant.length - 1] = rh;
      }
    }
    if(exp < MIN_EXP) {
      field.setIEEEFlagsBits(DfpField.FLAG_UNDERFLOW);
      return DfpField.FLAG_UNDERFLOW;
    }
    if(exp > MAX_EXP) {
      field.setIEEEFlagsBits(DfpField.FLAG_OVERFLOW);
      return DfpField.FLAG_OVERFLOW;
    }
    if(n != 0) {
      field.setIEEEFlagsBits(DfpField.FLAG_INEXACT);
      return DfpField.FLAG_INEXACT;
    }
    return 0;
  }
  public long round() {
    return FastMath.round(toDouble());
  }
  protected void shiftLeft() {
    for(int i = mant.length - 1; i > 0; i--) {
      mant[i] = mant[i - 1];
    }
    mant[0] = 0;
    exp--;
  }
  protected void shiftRight() {
    for(int i = 0; i < mant.length - 1; i++) {
      mant[i] = mant[i + 1];
    }
    mant[mant.length - 1] = 0;
    exp++;
  }
}