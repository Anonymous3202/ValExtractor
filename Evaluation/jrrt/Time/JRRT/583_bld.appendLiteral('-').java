package org.joda.time.format;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.joda.time.DateTimeFieldType;

public class ISODateTimeFormat  {
  protected ISODateTimeFormat() {
    super();
  }
  public static DateTimeFormatter basicDate() {
    return Constants.bd;
  }
  public static DateTimeFormatter basicDateTime() {
    return Constants.bdt;
  }
  public static DateTimeFormatter basicDateTimeNoMillis() {
    return Constants.bdtx;
  }
  public static DateTimeFormatter basicOrdinalDate() {
    return Constants.bod;
  }
  public static DateTimeFormatter basicOrdinalDateTime() {
    return Constants.bodt;
  }
  public static DateTimeFormatter basicOrdinalDateTimeNoMillis() {
    return Constants.bodtx;
  }
  public static DateTimeFormatter basicTTime() {
    return Constants.btt;
  }
  public static DateTimeFormatter basicTTimeNoMillis() {
    return Constants.bttx;
  }
  public static DateTimeFormatter basicTime() {
    return Constants.bt;
  }
  public static DateTimeFormatter basicTimeNoMillis() {
    return Constants.btx;
  }
  public static DateTimeFormatter basicWeekDate() {
    return Constants.bwd;
  }
  public static DateTimeFormatter basicWeekDateTime() {
    return Constants.bwdt;
  }
  public static DateTimeFormatter basicWeekDateTimeNoMillis() {
    return Constants.bwdtx;
  }
  public static DateTimeFormatter date() {
    return yearMonthDay();
  }
  public static DateTimeFormatter dateElementParser() {
    return Constants.dpe;
  }
  public static DateTimeFormatter dateHour() {
    return Constants.dh;
  }
  public static DateTimeFormatter dateHourMinute() {
    return Constants.dhm;
  }
  public static DateTimeFormatter dateHourMinuteSecond() {
    return Constants.dhms;
  }
  public static DateTimeFormatter dateHourMinuteSecondFraction() {
    return Constants.dhmsf;
  }
  public static DateTimeFormatter dateHourMinuteSecondMillis() {
    return Constants.dhmsl;
  }
  public static DateTimeFormatter dateOptionalTimeParser() {
    return Constants.dotp;
  }
  public static DateTimeFormatter dateParser() {
    return Constants.dp;
  }
  public static DateTimeFormatter dateTime() {
    return Constants.dt;
  }
  public static DateTimeFormatter dateTimeNoMillis() {
    return Constants.dtx;
  }
  public static DateTimeFormatter dateTimeParser() {
    return Constants.dtp;
  }
  public static DateTimeFormatter forFields(Collection<DateTimeFieldType> fields, boolean extended, boolean strictISO) {
    if(fields == null || fields.size() == 0) {
      throw new IllegalArgumentException("The fields must not be null or empty");
    }
    Set<DateTimeFieldType> workingFields = new HashSet<DateTimeFieldType>(fields);
    int inputSize = workingFields.size();
    boolean reducedPrec = false;
    DateTimeFormatterBuilder bld = new DateTimeFormatterBuilder();
    if(workingFields.contains(DateTimeFieldType.monthOfYear())) {
      reducedPrec = dateByMonth(bld, workingFields, extended, strictISO);
    }
    else 
      if(workingFields.contains(DateTimeFieldType.dayOfYear())) {
        reducedPrec = dateByOrdinal(bld, workingFields, extended, strictISO);
      }
      else 
        if(workingFields.contains(DateTimeFieldType.weekOfWeekyear())) {
          reducedPrec = dateByWeek(bld, workingFields, extended, strictISO);
        }
        else 
          if(workingFields.contains(DateTimeFieldType.dayOfMonth())) {
            reducedPrec = dateByMonth(bld, workingFields, extended, strictISO);
          }
          else 
            if(workingFields.contains(DateTimeFieldType.dayOfWeek())) {
              reducedPrec = dateByWeek(bld, workingFields, extended, strictISO);
            }
            else 
              if(workingFields.remove(DateTimeFieldType.year())) {
                bld.append(Constants.ye);
                reducedPrec = true;
              }
              else 
                if(workingFields.remove(DateTimeFieldType.weekyear())) {
                  bld.append(Constants.we);
                  reducedPrec = true;
                }
    boolean datePresent = (workingFields.size() < inputSize);
    time(bld, workingFields, extended, strictISO, reducedPrec, datePresent);
    if(bld.canBuildFormatter() == false) {
      throw new IllegalArgumentException("No valid format for fields: " + fields);
    }
    try {
      fields.retainAll(workingFields);
    }
    catch (UnsupportedOperationException ex) {
    }
    return bld.toFormatter();
  }
  public static DateTimeFormatter hour() {
    return Constants.hde;
  }
  public static DateTimeFormatter hourMinute() {
    return Constants.hm;
  }
  public static DateTimeFormatter hourMinuteSecond() {
    return Constants.hms;
  }
  public static DateTimeFormatter hourMinuteSecondFraction() {
    return Constants.hmsf;
  }
  public static DateTimeFormatter hourMinuteSecondMillis() {
    return Constants.hmsl;
  }
  public static DateTimeFormatter localDateOptionalTimeParser() {
    return Constants.ldotp;
  }
  public static DateTimeFormatter localDateParser() {
    return Constants.ldp;
  }
  public static DateTimeFormatter localTimeParser() {
    return Constants.ltp;
  }
  public static DateTimeFormatter ordinalDate() {
    return Constants.od;
  }
  public static DateTimeFormatter ordinalDateTime() {
    return Constants.odt;
  }
  public static DateTimeFormatter ordinalDateTimeNoMillis() {
    return Constants.odtx;
  }
  public static DateTimeFormatter tTime() {
    return Constants.tt;
  }
  public static DateTimeFormatter tTimeNoMillis() {
    return Constants.ttx;
  }
  public static DateTimeFormatter time() {
    return Constants.t;
  }
  public static DateTimeFormatter timeElementParser() {
    return Constants.tpe;
  }
  public static DateTimeFormatter timeNoMillis() {
    return Constants.tx;
  }
  public static DateTimeFormatter timeParser() {
    return Constants.tp;
  }
  public static DateTimeFormatter weekDate() {
    return Constants.wwd;
  }
  public static DateTimeFormatter weekDateTime() {
    return Constants.wdt;
  }
  public static DateTimeFormatter weekDateTimeNoMillis() {
    return Constants.wdtx;
  }
  public static DateTimeFormatter weekyear() {
    return Constants.we;
  }
  public static DateTimeFormatter weekyearWeek() {
    return Constants.ww;
  }
  public static DateTimeFormatter weekyearWeekDay() {
    return Constants.wwd;
  }
  public static DateTimeFormatter year() {
    return Constants.ye;
  }
  public static DateTimeFormatter yearMonth() {
    return Constants.ym;
  }
  public static DateTimeFormatter yearMonthDay() {
    return Constants.ymd;
  }
  private static boolean dateByMonth(DateTimeFormatterBuilder bld, Collection<DateTimeFieldType> fields, boolean extended, boolean strictISO) {
    boolean reducedPrec = false;
    if(fields.remove(DateTimeFieldType.year())) {
      bld.append(Constants.ye);
      if(fields.remove(DateTimeFieldType.monthOfYear())) {
        if(fields.remove(DateTimeFieldType.dayOfMonth())) {
          appendSeparator(bld, extended);
          bld.appendMonthOfYear(2);
          appendSeparator(bld, extended);
          bld.appendDayOfMonth(2);
        }
        else {
          bld.appendLiteral('-');
          bld.appendMonthOfYear(2);
          reducedPrec = true;
        }
      }
      else {
        if(fields.remove(DateTimeFieldType.dayOfMonth())) {
          checkNotStrictISO(fields, strictISO);
          bld.appendLiteral('-');
          bld.appendLiteral('-');
          bld.appendDayOfMonth(2);
        }
        else {
          reducedPrec = true;
        }
      }
    }
    else 
      if(fields.remove(DateTimeFieldType.monthOfYear())) {
        bld.appendLiteral('-');
        bld.appendLiteral('-');
        bld.appendMonthOfYear(2);
        if(fields.remove(DateTimeFieldType.dayOfMonth())) {
          appendSeparator(bld, extended);
          bld.appendDayOfMonth(2);
        }
        else {
          reducedPrec = true;
        }
      }
      else 
        if(fields.remove(DateTimeFieldType.dayOfMonth())) {
          bld.appendLiteral('-');
          bld.appendLiteral('-');
          bld.appendLiteral('-');
          bld.appendDayOfMonth(2);
        }
    return reducedPrec;
  }
  private static boolean dateByOrdinal(DateTimeFormatterBuilder bld, Collection<DateTimeFieldType> fields, boolean extended, boolean strictISO) {
    boolean reducedPrec = false;
    if(fields.remove(DateTimeFieldType.year())) {
      bld.append(Constants.ye);
      if(fields.remove(DateTimeFieldType.dayOfYear())) {
        appendSeparator(bld, extended);
        bld.appendDayOfYear(3);
      }
      else {
        reducedPrec = true;
      }
    }
    else 
      if(fields.remove(DateTimeFieldType.dayOfYear())) {
        bld.appendLiteral('-');
        bld.appendDayOfYear(3);
      }
    return reducedPrec;
  }
  private static boolean dateByWeek(DateTimeFormatterBuilder bld, Collection<DateTimeFieldType> fields, boolean extended, boolean strictISO) {
    boolean reducedPrec = false;
    if(fields.remove(DateTimeFieldType.weekyear())) {
      bld.append(Constants.we);
      if(fields.remove(DateTimeFieldType.weekOfWeekyear())) {
        appendSeparator(bld, extended);
        bld.appendLiteral('W');
        bld.appendWeekOfWeekyear(2);
        if(fields.remove(DateTimeFieldType.dayOfWeek())) {
          appendSeparator(bld, extended);
          bld.appendDayOfWeek(1);
        }
        else {
          reducedPrec = true;
        }
      }
      else {
        if(fields.remove(DateTimeFieldType.dayOfWeek())) {
          checkNotStrictISO(fields, strictISO);
          appendSeparator(bld, extended);
          bld.appendLiteral('W');
          DateTimeFormatterBuilder var_583 = bld.appendLiteral('-');
          bld.appendDayOfWeek(1);
        }
        else {
          reducedPrec = true;
        }
      }
    }
    else 
      if(fields.remove(DateTimeFieldType.weekOfWeekyear())) {
        bld.appendLiteral('-');
        bld.appendLiteral('W');
        bld.appendWeekOfWeekyear(2);
        if(fields.remove(DateTimeFieldType.dayOfWeek())) {
          appendSeparator(bld, extended);
          bld.appendDayOfWeek(1);
        }
        else {
          reducedPrec = true;
        }
      }
      else 
        if(fields.remove(DateTimeFieldType.dayOfWeek())) {
          bld.appendLiteral('-');
          bld.appendLiteral('W');
          bld.appendLiteral('-');
          bld.appendDayOfWeek(1);
        }
    return reducedPrec;
  }
  private static void appendSeparator(DateTimeFormatterBuilder bld, boolean extended) {
    if(extended) {
      bld.appendLiteral('-');
    }
  }
  private static void checkNotStrictISO(Collection<DateTimeFieldType> fields, boolean strictISO) {
    if(strictISO) {
      throw new IllegalArgumentException("No valid ISO8601 format for fields: " + fields);
    }
  }
  private static void time(DateTimeFormatterBuilder bld, Collection<DateTimeFieldType> fields, boolean extended, boolean strictISO, boolean reducedPrec, boolean datePresent) {
    boolean hour = fields.remove(DateTimeFieldType.hourOfDay());
    boolean minute = fields.remove(DateTimeFieldType.minuteOfHour());
    boolean second = fields.remove(DateTimeFieldType.secondOfMinute());
    boolean milli = fields.remove(DateTimeFieldType.millisOfSecond());
    if(!hour && !minute && !second && !milli) {
      return ;
    }
    if(hour || minute || second || milli) {
      if(strictISO && reducedPrec) {
        throw new IllegalArgumentException("No valid ISO8601 format for fields because Date was reduced precision: " + fields);
      }
      if(datePresent) {
        bld.appendLiteral('T');
      }
    }
    if(hour && minute && second || (hour && !second && !milli)) {
    }
    else {
      if(strictISO && datePresent) {
        throw new IllegalArgumentException("No valid ISO8601 format for fields because Time was truncated: " + fields);
      }
      if(!hour && (minute && second || (minute && !milli) || second)) {
      }
      else {
        if(strictISO) {
          throw new IllegalArgumentException("No valid ISO8601 format for fields: " + fields);
        }
      }
    }
    if(hour) {
      bld.appendHourOfDay(2);
    }
    else 
      if(minute || second || milli) {
        bld.appendLiteral('-');
      }
    if(extended && hour && minute) {
      bld.appendLiteral(':');
    }
    if(minute) {
      bld.appendMinuteOfHour(2);
    }
    else 
      if(second || milli) {
        bld.appendLiteral('-');
      }
    if(extended && minute && second) {
      bld.appendLiteral(':');
    }
    if(second) {
      bld.appendSecondOfMinute(2);
    }
    else 
      if(milli) {
        bld.appendLiteral('-');
      }
    if(milli) {
      bld.appendLiteral('.');
      bld.appendMillisOfSecond(3);
    }
  }
  
  final static class Constants  {
    final private static DateTimeFormatter ye = yearElement();
    final private static DateTimeFormatter mye = monthElement();
    final private static DateTimeFormatter dme = dayOfMonthElement();
    final private static DateTimeFormatter we = weekyearElement();
    final private static DateTimeFormatter wwe = weekElement();
    final private static DateTimeFormatter dwe = dayOfWeekElement();
    final private static DateTimeFormatter dye = dayOfYearElement();
    final private static DateTimeFormatter hde = hourElement();
    final private static DateTimeFormatter mhe = minuteElement();
    final private static DateTimeFormatter sme = secondElement();
    final private static DateTimeFormatter fse = fractionElement();
    final private static DateTimeFormatter ze = offsetElement();
    final private static DateTimeFormatter lte = literalTElement();
    final private static DateTimeFormatter ym = yearMonth();
    final private static DateTimeFormatter ymd = yearMonthDay();
    final private static DateTimeFormatter ww = weekyearWeek();
    final private static DateTimeFormatter wwd = weekyearWeekDay();
    final private static DateTimeFormatter hm = hourMinute();
    final private static DateTimeFormatter hms = hourMinuteSecond();
    final private static DateTimeFormatter hmsl = hourMinuteSecondMillis();
    final private static DateTimeFormatter hmsf = hourMinuteSecondFraction();
    final private static DateTimeFormatter dh = dateHour();
    final private static DateTimeFormatter dhm = dateHourMinute();
    final private static DateTimeFormatter dhms = dateHourMinuteSecond();
    final private static DateTimeFormatter dhmsl = dateHourMinuteSecondMillis();
    final private static DateTimeFormatter dhmsf = dateHourMinuteSecondFraction();
    final private static DateTimeFormatter t = time();
    final private static DateTimeFormatter tx = timeNoMillis();
    final private static DateTimeFormatter tt = tTime();
    final private static DateTimeFormatter ttx = tTimeNoMillis();
    final private static DateTimeFormatter dt = dateTime();
    final private static DateTimeFormatter dtx = dateTimeNoMillis();
    final private static DateTimeFormatter wdt = weekDateTime();
    final private static DateTimeFormatter wdtx = weekDateTimeNoMillis();
    final private static DateTimeFormatter od = ordinalDate();
    final private static DateTimeFormatter odt = ordinalDateTime();
    final private static DateTimeFormatter odtx = ordinalDateTimeNoMillis();
    final private static DateTimeFormatter bd = basicDate();
    final private static DateTimeFormatter bt = basicTime();
    final private static DateTimeFormatter btx = basicTimeNoMillis();
    final private static DateTimeFormatter btt = basicTTime();
    final private static DateTimeFormatter bttx = basicTTimeNoMillis();
    final private static DateTimeFormatter bdt = basicDateTime();
    final private static DateTimeFormatter bdtx = basicDateTimeNoMillis();
    final private static DateTimeFormatter bod = basicOrdinalDate();
    final private static DateTimeFormatter bodt = basicOrdinalDateTime();
    final private static DateTimeFormatter bodtx = basicOrdinalDateTimeNoMillis();
    final private static DateTimeFormatter bwd = basicWeekDate();
    final private static DateTimeFormatter bwdt = basicWeekDateTime();
    final private static DateTimeFormatter bwdtx = basicWeekDateTimeNoMillis();
    final private static DateTimeFormatter dpe = dateElementParser();
    final private static DateTimeFormatter tpe = timeElementParser();
    final private static DateTimeFormatter dp = dateParser();
    final private static DateTimeFormatter ldp = localDateParser();
    final private static DateTimeFormatter tp = timeParser();
    final private static DateTimeFormatter ltp = localTimeParser();
    final private static DateTimeFormatter dtp = dateTimeParser();
    final private static DateTimeFormatter dotp = dateOptionalTimeParser();
    final private static DateTimeFormatter ldotp = localDateOptionalTimeParser();
    private static DateTimeFormatter basicDate() {
      if(bd == null) {
        return new DateTimeFormatterBuilder().appendYear(4, 4).appendFixedDecimal(DateTimeFieldType.monthOfYear(), 2).appendFixedDecimal(DateTimeFieldType.dayOfMonth(), 2).toFormatter();
      }
      return bd;
    }
    private static DateTimeFormatter basicDateTime() {
      if(bdt == null) {
        return new DateTimeFormatterBuilder().append(basicDate()).append(basicTTime()).toFormatter();
      }
      return bdt;
    }
    private static DateTimeFormatter basicDateTimeNoMillis() {
      if(bdtx == null) {
        return new DateTimeFormatterBuilder().append(basicDate()).append(basicTTimeNoMillis()).toFormatter();
      }
      return bdtx;
    }
    private static DateTimeFormatter basicOrdinalDate() {
      if(bod == null) {
        return new DateTimeFormatterBuilder().appendYear(4, 4).appendFixedDecimal(DateTimeFieldType.dayOfYear(), 3).toFormatter();
      }
      return bod;
    }
    private static DateTimeFormatter basicOrdinalDateTime() {
      if(bodt == null) {
        return new DateTimeFormatterBuilder().append(basicOrdinalDate()).append(basicTTime()).toFormatter();
      }
      return bodt;
    }
    private static DateTimeFormatter basicOrdinalDateTimeNoMillis() {
      if(bodtx == null) {
        return new DateTimeFormatterBuilder().append(basicOrdinalDate()).append(basicTTimeNoMillis()).toFormatter();
      }
      return bodtx;
    }
    private static DateTimeFormatter basicTTime() {
      if(btt == null) {
        return new DateTimeFormatterBuilder().append(literalTElement()).append(basicTime()).toFormatter();
      }
      return btt;
    }
    private static DateTimeFormatter basicTTimeNoMillis() {
      if(bttx == null) {
        return new DateTimeFormatterBuilder().append(literalTElement()).append(basicTimeNoMillis()).toFormatter();
      }
      return bttx;
    }
    private static DateTimeFormatter basicTime() {
      if(bt == null) {
        return new DateTimeFormatterBuilder().appendFixedDecimal(DateTimeFieldType.hourOfDay(), 2).appendFixedDecimal(DateTimeFieldType.minuteOfHour(), 2).appendFixedDecimal(DateTimeFieldType.secondOfMinute(), 2).appendLiteral('.').appendFractionOfSecond(3, 9).appendTimeZoneOffset("Z", false, 2, 2).toFormatter();
      }
      return bt;
    }
    private static DateTimeFormatter basicTimeNoMillis() {
      if(btx == null) {
        return new DateTimeFormatterBuilder().appendFixedDecimal(DateTimeFieldType.hourOfDay(), 2).appendFixedDecimal(DateTimeFieldType.minuteOfHour(), 2).appendFixedDecimal(DateTimeFieldType.secondOfMinute(), 2).appendTimeZoneOffset("Z", false, 2, 2).toFormatter();
      }
      return btx;
    }
    private static DateTimeFormatter basicWeekDate() {
      if(bwd == null) {
        return new DateTimeFormatterBuilder().appendWeekyear(4, 4).appendLiteral('W').appendFixedDecimal(DateTimeFieldType.weekOfWeekyear(), 2).appendFixedDecimal(DateTimeFieldType.dayOfWeek(), 1).toFormatter();
      }
      return bwd;
    }
    private static DateTimeFormatter basicWeekDateTime() {
      if(bwdt == null) {
        return new DateTimeFormatterBuilder().append(basicWeekDate()).append(basicTTime()).toFormatter();
      }
      return bwdt;
    }
    private static DateTimeFormatter basicWeekDateTimeNoMillis() {
      if(bwdtx == null) {
        return new DateTimeFormatterBuilder().append(basicWeekDate()).append(basicTTimeNoMillis()).toFormatter();
      }
      return bwdtx;
    }
    private static DateTimeFormatter dateElementParser() {
      if(dpe == null) {
        return new DateTimeFormatterBuilder().append(null, new DateTimeParser[]{ new DateTimeFormatterBuilder().append(yearElement()).appendOptional(new DateTimeFormatterBuilder().append(monthElement()).appendOptional(dayOfMonthElement().getParser()).toParser()).toParser(), new DateTimeFormatterBuilder().append(weekyearElement()).append(weekElement()).appendOptional(dayOfWeekElement().getParser()).toParser(), new DateTimeFormatterBuilder().append(yearElement()).append(dayOfYearElement()).toParser() } ).toFormatter();
      }
      return dpe;
    }
    private static DateTimeFormatter dateHour() {
      if(dh == null) {
        return new DateTimeFormatterBuilder().append(date()).append(literalTElement()).append(hour()).toFormatter();
      }
      return dh;
    }
    private static DateTimeFormatter dateHourMinute() {
      if(dhm == null) {
        return new DateTimeFormatterBuilder().append(date()).append(literalTElement()).append(hourMinute()).toFormatter();
      }
      return dhm;
    }
    private static DateTimeFormatter dateHourMinuteSecond() {
      if(dhms == null) {
        return new DateTimeFormatterBuilder().append(date()).append(literalTElement()).append(hourMinuteSecond()).toFormatter();
      }
      return dhms;
    }
    private static DateTimeFormatter dateHourMinuteSecondFraction() {
      if(dhmsf == null) {
        return new DateTimeFormatterBuilder().append(date()).append(literalTElement()).append(hourMinuteSecondFraction()).toFormatter();
      }
      return dhmsf;
    }
    private static DateTimeFormatter dateHourMinuteSecondMillis() {
      if(dhmsl == null) {
        return new DateTimeFormatterBuilder().append(date()).append(literalTElement()).append(hourMinuteSecondMillis()).toFormatter();
      }
      return dhmsl;
    }
    private static DateTimeFormatter dateOptionalTimeParser() {
      if(dotp == null) {
        DateTimeParser timeOrOffset = new DateTimeFormatterBuilder().appendLiteral('T').appendOptional(timeElementParser().getParser()).appendOptional(offsetElement().getParser()).toParser();
        return new DateTimeFormatterBuilder().append(dateElementParser()).appendOptional(timeOrOffset).toFormatter();
      }
      return dotp;
    }
    private static DateTimeFormatter dateParser() {
      if(dp == null) {
        DateTimeParser tOffset = new DateTimeFormatterBuilder().appendLiteral('T').append(offsetElement()).toParser();
        return new DateTimeFormatterBuilder().append(dateElementParser()).appendOptional(tOffset).toFormatter();
      }
      return dp;
    }
    private static DateTimeFormatter dateTime() {
      if(dt == null) {
        return new DateTimeFormatterBuilder().append(date()).append(tTime()).toFormatter();
      }
      return dt;
    }
    private static DateTimeFormatter dateTimeNoMillis() {
      if(dtx == null) {
        return new DateTimeFormatterBuilder().append(date()).append(tTimeNoMillis()).toFormatter();
      }
      return dtx;
    }
    private static DateTimeFormatter dateTimeParser() {
      if(dtp == null) {
        DateTimeParser time = new DateTimeFormatterBuilder().appendLiteral('T').append(timeElementParser()).appendOptional(offsetElement().getParser()).toParser();
        return new DateTimeFormatterBuilder().append(null, new DateTimeParser[]{ time, dateOptionalTimeParser().getParser() } ).toFormatter();
      }
      return dtp;
    }
    private static DateTimeFormatter dayOfMonthElement() {
      if(dme == null) {
        return new DateTimeFormatterBuilder().appendLiteral('-').appendDayOfMonth(2).toFormatter();
      }
      return dme;
    }
    private static DateTimeFormatter dayOfWeekElement() {
      if(dwe == null) {
        return new DateTimeFormatterBuilder().appendLiteral('-').appendDayOfWeek(1).toFormatter();
      }
      return dwe;
    }
    private static DateTimeFormatter dayOfYearElement() {
      if(dye == null) {
        return new DateTimeFormatterBuilder().appendLiteral('-').appendDayOfYear(3).toFormatter();
      }
      return dye;
    }
    private static DateTimeFormatter fractionElement() {
      if(fse == null) {
        return new DateTimeFormatterBuilder().appendLiteral('.').appendFractionOfSecond(3, 9).toFormatter();
      }
      return fse;
    }
    private static DateTimeFormatter hourElement() {
      if(hde == null) {
        return new DateTimeFormatterBuilder().appendHourOfDay(2).toFormatter();
      }
      return hde;
    }
    private static DateTimeFormatter hourMinute() {
      if(hm == null) {
        return new DateTimeFormatterBuilder().append(hourElement()).append(minuteElement()).toFormatter();
      }
      return hm;
    }
    private static DateTimeFormatter hourMinuteSecond() {
      if(hms == null) {
        return new DateTimeFormatterBuilder().append(hourElement()).append(minuteElement()).append(secondElement()).toFormatter();
      }
      return hms;
    }
    private static DateTimeFormatter hourMinuteSecondFraction() {
      if(hmsf == null) {
        return new DateTimeFormatterBuilder().append(hourElement()).append(minuteElement()).append(secondElement()).append(fractionElement()).toFormatter();
      }
      return hmsf;
    }
    private static DateTimeFormatter hourMinuteSecondMillis() {
      if(hmsl == null) {
        return new DateTimeFormatterBuilder().append(hourElement()).append(minuteElement()).append(secondElement()).appendLiteral('.').appendFractionOfSecond(3, 3).toFormatter();
      }
      return hmsl;
    }
    private static DateTimeFormatter literalTElement() {
      if(lte == null) {
        return new DateTimeFormatterBuilder().appendLiteral('T').toFormatter();
      }
      return lte;
    }
    private static DateTimeFormatter localDateOptionalTimeParser() {
      if(ldotp == null) {
        DateTimeParser time = new DateTimeFormatterBuilder().appendLiteral('T').append(timeElementParser()).toParser();
        return new DateTimeFormatterBuilder().append(dateElementParser()).appendOptional(time).toFormatter().withZoneUTC();
      }
      return ldotp;
    }
    private static DateTimeFormatter localDateParser() {
      if(ldp == null) {
        return dateElementParser().withZoneUTC();
      }
      return ldp;
    }
    private static DateTimeFormatter localTimeParser() {
      if(ltp == null) {
        return new DateTimeFormatterBuilder().appendOptional(literalTElement().getParser()).append(timeElementParser()).toFormatter().withZoneUTC();
      }
      return ltp;
    }
    private static DateTimeFormatter minuteElement() {
      if(mhe == null) {
        return new DateTimeFormatterBuilder().appendLiteral(':').appendMinuteOfHour(2).toFormatter();
      }
      return mhe;
    }
    private static DateTimeFormatter monthElement() {
      if(mye == null) {
        return new DateTimeFormatterBuilder().appendLiteral('-').appendMonthOfYear(2).toFormatter();
      }
      return mye;
    }
    private static DateTimeFormatter offsetElement() {
      if(ze == null) {
        return new DateTimeFormatterBuilder().appendTimeZoneOffset("Z", true, 2, 4).toFormatter();
      }
      return ze;
    }
    private static DateTimeFormatter ordinalDate() {
      if(od == null) {
        return new DateTimeFormatterBuilder().append(yearElement()).append(dayOfYearElement()).toFormatter();
      }
      return od;
    }
    private static DateTimeFormatter ordinalDateTime() {
      if(odt == null) {
        return new DateTimeFormatterBuilder().append(ordinalDate()).append(tTime()).toFormatter();
      }
      return odt;
    }
    private static DateTimeFormatter ordinalDateTimeNoMillis() {
      if(odtx == null) {
        return new DateTimeFormatterBuilder().append(ordinalDate()).append(tTimeNoMillis()).toFormatter();
      }
      return odtx;
    }
    private static DateTimeFormatter secondElement() {
      if(sme == null) {
        return new DateTimeFormatterBuilder().appendLiteral(':').appendSecondOfMinute(2).toFormatter();
      }
      return sme;
    }
    private static DateTimeFormatter tTime() {
      if(tt == null) {
        return new DateTimeFormatterBuilder().append(literalTElement()).append(time()).toFormatter();
      }
      return tt;
    }
    private static DateTimeFormatter tTimeNoMillis() {
      if(ttx == null) {
        return new DateTimeFormatterBuilder().append(literalTElement()).append(timeNoMillis()).toFormatter();
      }
      return ttx;
    }
    private static DateTimeFormatter time() {
      if(t == null) {
        return new DateTimeFormatterBuilder().append(hourMinuteSecondFraction()).append(offsetElement()).toFormatter();
      }
      return t;
    }
    private static DateTimeFormatter timeElementParser() {
      if(tpe == null) {
        DateTimeParser decimalPoint = new DateTimeFormatterBuilder().append(null, new DateTimeParser[]{ new DateTimeFormatterBuilder().appendLiteral('.').toParser(), new DateTimeFormatterBuilder().appendLiteral(',').toParser() } ).toParser();
        return new DateTimeFormatterBuilder().append(hourElement()).append(null, new DateTimeParser[]{ new DateTimeFormatterBuilder().append(minuteElement()).append(null, new DateTimeParser[]{ new DateTimeFormatterBuilder().append(secondElement()).appendOptional(new DateTimeFormatterBuilder().append(decimalPoint).appendFractionOfSecond(1, 9).toParser()).toParser(), new DateTimeFormatterBuilder().append(decimalPoint).appendFractionOfMinute(1, 9).toParser(), null } ).toParser(), new DateTimeFormatterBuilder().append(decimalPoint).appendFractionOfHour(1, 9).toParser(), null } ).toFormatter();
      }
      return tpe;
    }
    private static DateTimeFormatter timeNoMillis() {
      if(tx == null) {
        return new DateTimeFormatterBuilder().append(hourMinuteSecond()).append(offsetElement()).toFormatter();
      }
      return tx;
    }
    private static DateTimeFormatter timeParser() {
      if(tp == null) {
        return new DateTimeFormatterBuilder().appendOptional(literalTElement().getParser()).append(timeElementParser()).appendOptional(offsetElement().getParser()).toFormatter();
      }
      return tp;
    }
    private static DateTimeFormatter weekDateTime() {
      if(wdt == null) {
        return new DateTimeFormatterBuilder().append(weekDate()).append(tTime()).toFormatter();
      }
      return wdt;
    }
    private static DateTimeFormatter weekDateTimeNoMillis() {
      if(wdtx == null) {
        return new DateTimeFormatterBuilder().append(weekDate()).append(tTimeNoMillis()).toFormatter();
      }
      return wdtx;
    }
    private static DateTimeFormatter weekElement() {
      if(wwe == null) {
        return new DateTimeFormatterBuilder().appendLiteral("-W").appendWeekOfWeekyear(2).toFormatter();
      }
      return wwe;
    }
    private static DateTimeFormatter weekyearElement() {
      if(we == null) {
        return new DateTimeFormatterBuilder().appendWeekyear(4, 9).toFormatter();
      }
      return we;
    }
    private static DateTimeFormatter weekyearWeek() {
      if(ww == null) {
        return new DateTimeFormatterBuilder().append(weekyearElement()).append(weekElement()).toFormatter();
      }
      return ww;
    }
    private static DateTimeFormatter weekyearWeekDay() {
      if(wwd == null) {
        return new DateTimeFormatterBuilder().append(weekyearElement()).append(weekElement()).append(dayOfWeekElement()).toFormatter();
      }
      return wwd;
    }
    private static DateTimeFormatter yearElement() {
      if(ye == null) {
        return new DateTimeFormatterBuilder().appendYear(4, 9).toFormatter();
      }
      return ye;
    }
    private static DateTimeFormatter yearMonth() {
      if(ym == null) {
        return new DateTimeFormatterBuilder().append(yearElement()).append(monthElement()).toFormatter();
      }
      return ym;
    }
    private static DateTimeFormatter yearMonthDay() {
      if(ymd == null) {
        return new DateTimeFormatterBuilder().append(yearElement()).append(monthElement()).append(dayOfMonthElement()).toFormatter();
      }
      return ymd;
    }
  }
}