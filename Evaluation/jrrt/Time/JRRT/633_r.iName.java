package org.joda.time.tz;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.Map.Entry;
import org.joda.time.Chronology;
import org.joda.time.DateTime;
import org.joda.time.DateTimeField;
import org.joda.time.DateTimeZone;
import org.joda.time.LocalDate;
import org.joda.time.MutableDateTime;
import org.joda.time.chrono.ISOChronology;
import org.joda.time.chrono.LenientChronology;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;

public class ZoneInfoCompiler  {
  static DateTimeOfYear cStartOfYear;
  static Chronology cLenientISO;
  static ThreadLocal<Boolean> cVerbose = new ThreadLocal<Boolean>() {
      protected Boolean initialValue() {
        return Boolean.FALSE;
      }
  };
  private Map<String, RuleSet> iRuleSets;
  private List<Zone> iZones;
  private List<String> iLinks;
  public ZoneInfoCompiler() {
    super();
    iRuleSets = new HashMap<String, RuleSet>();
    iZones = new ArrayList<Zone>();
    iLinks = new ArrayList<String>();
  }
  static Chronology getLenientISOChronology() {
    if(cLenientISO == null) {
      cLenientISO = LenientChronology.getInstance(ISOChronology.getInstanceUTC());
    }
    return cLenientISO;
  }
  static DateTimeOfYear getStartOfYear() {
    if(cStartOfYear == null) {
      cStartOfYear = new DateTimeOfYear();
    }
    return cStartOfYear;
  }
  public Map<String, DateTimeZone> compile(File outputDir, File[] sources) throws IOException {
    if(sources != null) {
      for(int i = 0; i < sources.length; i++) {
        BufferedReader in = new BufferedReader(new FileReader(sources[i]));
        parseDataFile(in);
        in.close();
      }
    }
    if(outputDir != null) {
      if(!outputDir.exists()) {
        if(!outputDir.mkdirs()) {
          throw new IOException("Destination directory doesn\'t exist and cannot be created: " + outputDir);
        }
      }
      if(!outputDir.isDirectory()) {
        throw new IOException("Destination is not a directory: " + outputDir);
      }
    }
    Map<String, DateTimeZone> map = new TreeMap<String, DateTimeZone>();
    System.out.println("Writing zoneinfo files");
    for(int i = 0; i < iZones.size(); i++) {
      Zone zone = iZones.get(i);
      DateTimeZoneBuilder builder = new DateTimeZoneBuilder();
      zone.addToBuilder(builder, iRuleSets);
      final DateTimeZone original = builder.toDateTimeZone(zone.iName, true);
      DateTimeZone tz = original;
      if(test(tz.getID(), tz)) {
        map.put(tz.getID(), tz);
        if(outputDir != null) {
          if(ZoneInfoCompiler.verbose()) {
            System.out.println("Writing " + tz.getID());
          }
          File file = new File(outputDir, tz.getID());
          if(!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
          }
          OutputStream out = new FileOutputStream(file);
          try {
            builder.writeTo(zone.iName, out);
          }
          finally {
            out.close();
          }
          InputStream in = new FileInputStream(file);
          DateTimeZone tz2 = DateTimeZoneBuilder.readFrom(in, tz.getID());
          in.close();
          if(!original.equals(tz2)) {
            System.out.println("*e* Error in " + tz.getID() + ": Didn\'t read properly from file");
          }
        }
      }
    }
    for(int pass = 0; pass < 2; pass++) {
      for(int i = 0; i < iLinks.size(); i += 2) {
        String id = iLinks.get(i);
        String alias = iLinks.get(i + 1);
        DateTimeZone tz = map.get(id);
        if(tz == null) {
          if(pass > 0) {
            System.out.println("Cannot find time zone \'" + id + "\' to link alias \'" + alias + "\' to");
          }
        }
        else {
          map.put(alias, tz);
        }
      }
    }
    if(outputDir != null) {
      System.out.println("Writing ZoneInfoMap");
      File file = new File(outputDir, "ZoneInfoMap");
      if(!file.getParentFile().exists()) {
        file.getParentFile().mkdirs();
      }
      OutputStream out = new FileOutputStream(file);
      DataOutputStream dout = new DataOutputStream(out);
      try {
        Map<String, DateTimeZone> zimap = new TreeMap<String, DateTimeZone>(String.CASE_INSENSITIVE_ORDER);
        zimap.putAll(map);
        writeZoneInfoMap(dout, zimap);
      }
      finally {
        dout.close();
      }
    }
    return map;
  }
  static String parseOptional(String str) {
    return (str.equals("-")) ? null : str;
  }
  static boolean test(String id, DateTimeZone tz) {
    if(!id.equals(tz.getID())) {
      return true;
    }
    long millis = ISOChronology.getInstanceUTC().year().set(0, 1850);
    long end = ISOChronology.getInstanceUTC().year().set(0, 2050);
    int offset = tz.getOffset(millis);
    String key = tz.getNameKey(millis);
    List<Long> transitions = new ArrayList<Long>();
    while(true){
      long next = tz.nextTransition(millis);
      if(next == millis || next > end) {
        break ;
      }
      millis = next;
      int nextOffset = tz.getOffset(millis);
      String nextKey = tz.getNameKey(millis);
      if(offset == nextOffset && key.equals(nextKey)) {
        System.out.println("*d* Error in " + tz.getID() + " " + new DateTime(millis, ISOChronology.getInstanceUTC()));
        return false;
      }
      if(nextKey == null || (nextKey.length() < 3 && !"??".equals(nextKey))) {
        System.out.println("*s* Error in " + tz.getID() + " " + new DateTime(millis, ISOChronology.getInstanceUTC()) + ", nameKey=" + nextKey);
        return false;
      }
      transitions.add(Long.valueOf(millis));
      offset = nextOffset;
      key = nextKey;
    }
    millis = ISOChronology.getInstanceUTC().year().set(0, 2050);
    end = ISOChronology.getInstanceUTC().year().set(0, 1850);
    for(int i = transitions.size(); --i >= 0; ) {
      long prev = tz.previousTransition(millis);
      if(prev == millis || prev < end) {
        break ;
      }
      millis = prev;
      long trans = transitions.get(i).longValue();
      if(trans - 1 != millis) {
        System.out.println("*r* Error in " + tz.getID() + " " + new DateTime(millis, ISOChronology.getInstanceUTC()) + " != " + new DateTime(trans - 1, ISOChronology.getInstanceUTC()));
        return false;
      }
    }
    return true;
  }
  public static boolean verbose() {
    return cVerbose.get();
  }
  static char parseZoneChar(char c) {
    switch (c){
      case 's':
      case 'S':
      return 's';
      case 'u':
      case 'U':
      case 'g':
      case 'G':
      case 'z':
      case 'Z':
      return 'u';
      case 'w':
      case 'W':
      default:
      return 'w';
    }
  }
  static int parseDayOfWeek(String str) {
    DateTimeField field = ISOChronology.getInstanceUTC().dayOfWeek();
    return field.get(field.set(0, str, Locale.ENGLISH));
  }
  static int parseMonth(String str) {
    DateTimeField field = ISOChronology.getInstanceUTC().monthOfYear();
    return field.get(field.set(0, str, Locale.ENGLISH));
  }
  static int parseTime(String str) {
    DateTimeFormatter p = ISODateTimeFormat.hourMinuteSecondFraction();
    MutableDateTime mdt = new MutableDateTime(0, getLenientISOChronology());
    int pos = 0;
    if(str.startsWith("-")) {
      pos = 1;
    }
    int newPos = p.parseInto(mdt, str, pos);
    if(newPos == ~pos) {
      throw new IllegalArgumentException(str);
    }
    int millis = (int)mdt.getMillis();
    if(pos == 1) {
      millis = -millis;
    }
    return millis;
  }
  static int parseYear(String str, int def) {
    str = str.toLowerCase();
    if(str.equals("minimum") || str.equals("min")) {
      return Integer.MIN_VALUE;
    }
    else 
      if(str.equals("maximum") || str.equals("max")) {
        return Integer.MAX_VALUE;
      }
      else 
        if(str.equals("only")) {
          return def;
        }
    return Integer.parseInt(str);
  }
  public static void main(String[] args) throws Exception {
    if(args.length == 0) {
      printUsage();
      return ;
    }
    File inputDir = null;
    File outputDir = null;
    boolean verbose = false;
    int i;
    for(i = 0; i < args.length; i++) {
      try {
        if("-src".equals(args[i])) {
          inputDir = new File(args[++i]);
        }
        else 
          if("-dst".equals(args[i])) {
            outputDir = new File(args[++i]);
          }
          else 
            if("-verbose".equals(args[i])) {
              verbose = true;
            }
            else 
              if("-?".equals(args[i])) {
                printUsage();
                return ;
              }
              else {
                break ;
              }
      }
      catch (IndexOutOfBoundsException e) {
        printUsage();
        return ;
      }
    }
    if(i >= args.length) {
      printUsage();
      return ;
    }
    File[] sources = new File[args.length - i];
    for(int j = 0; i < args.length; i++, j++) {
      sources[j] = inputDir == null ? new File(args[i]) : new File(inputDir, args[i]);
    }
    cVerbose.set(verbose);
    ZoneInfoCompiler zic = new ZoneInfoCompiler();
    zic.compile(outputDir, sources);
  }
  public void parseDataFile(BufferedReader in) throws IOException {
    Zone zone = null;
    String line;
    while((line = in.readLine()) != null){
      String trimmed = line.trim();
      if(trimmed.length() == 0 || trimmed.charAt(0) == '#') {
        continue ;
      }
      int index = line.indexOf('#');
      if(index >= 0) {
        line = line.substring(0, index);
      }
      StringTokenizer st = new StringTokenizer(line, " \t");
      if(Character.isWhitespace(line.charAt(0)) && st.hasMoreTokens()) {
        if(zone != null) {
          zone.chain(st);
        }
        continue ;
      }
      else {
        if(zone != null) {
          iZones.add(zone);
        }
        zone = null;
      }
      if(st.hasMoreTokens()) {
        String token = st.nextToken();
        if(token.equalsIgnoreCase("Rule")) {
          Rule r = new Rule(st);
          String var_633 = r.iName;
          RuleSet rs = iRuleSets.get(var_633);
          if(rs == null) {
            rs = new RuleSet(r);
            iRuleSets.put(r.iName, rs);
          }
          else {
            rs.addRule(r);
          }
        }
        else 
          if(token.equalsIgnoreCase("Zone")) {
            zone = new Zone(st);
          }
          else 
            if(token.equalsIgnoreCase("Link")) {
              iLinks.add(st.nextToken());
              iLinks.add(st.nextToken());
            }
            else {
              System.out.println("Unknown line: " + line);
            }
      }
    }
    if(zone != null) {
      iZones.add(zone);
    }
  }
  private static void printUsage() {
    System.out.println("Usage: java org.joda.time.tz.ZoneInfoCompiler <options> <source files>");
    System.out.println("where possible options include:");
    System.out.println("  -src <directory>    Specify where to read source files");
    System.out.println("  -dst <directory>    Specify where to write generated files");
    System.out.println("  -verbose            Output verbosely (default false)");
  }
  static void writeZoneInfoMap(DataOutputStream dout, Map<String, DateTimeZone> zimap) throws IOException {
    Map<String, Short> idToIndex = new HashMap<String, Short>(zimap.size());
    TreeMap<Short, String> indexToId = new TreeMap<Short, String>();
    short count = 0;
    for (Entry<String, DateTimeZone> entry : zimap.entrySet()) {
      String id = (String)entry.getKey();
      if(!idToIndex.containsKey(id)) {
        Short index = Short.valueOf(count);
        idToIndex.put(id, index);
        indexToId.put(index, id);
        if(++count == 0) {
          throw new InternalError("Too many time zone ids");
        }
      }
      id = ((DateTimeZone)entry.getValue()).getID();
      if(!idToIndex.containsKey(id)) {
        Short index = Short.valueOf(count);
        idToIndex.put(id, index);
        indexToId.put(index, id);
        if(++count == 0) {
          throw new InternalError("Too many time zone ids");
        }
      }
    }
    dout.writeShort(indexToId.size());
    for (String id : indexToId.values()) {
      dout.writeUTF(id);
    }
    dout.writeShort(zimap.size());
    for (Entry<String, DateTimeZone> entry : zimap.entrySet()) {
      String id = entry.getKey();
      dout.writeShort(idToIndex.get(id).shortValue());
      id = entry.getValue().getID();
      dout.writeShort(idToIndex.get(id).shortValue());
    }
  }
  
  static class DateTimeOfYear  {
    final public int iMonthOfYear;
    final public int iDayOfMonth;
    final public int iDayOfWeek;
    final public boolean iAdvanceDayOfWeek;
    final public int iMillisOfDay;
    final public char iZoneChar;
    DateTimeOfYear() {
      super();
      iMonthOfYear = 1;
      iDayOfMonth = 1;
      iDayOfWeek = 0;
      iAdvanceDayOfWeek = false;
      iMillisOfDay = 0;
      iZoneChar = 'w';
    }
    DateTimeOfYear(StringTokenizer st) {
      super();
      int month = 1;
      int day = 1;
      int dayOfWeek = 0;
      int millis = 0;
      boolean advance = false;
      char zoneChar = 'w';
      if(st.hasMoreTokens()) {
        month = parseMonth(st.nextToken());
        if(st.hasMoreTokens()) {
          String str = st.nextToken();
          if(str.startsWith("last")) {
            day = -1;
            dayOfWeek = parseDayOfWeek(str.substring(4));
            advance = false;
          }
          else {
            try {
              day = Integer.parseInt(str);
              dayOfWeek = 0;
              advance = false;
            }
            catch (NumberFormatException e) {
              int index = str.indexOf(">=");
              if(index > 0) {
                day = Integer.parseInt(str.substring(index + 2));
                dayOfWeek = parseDayOfWeek(str.substring(0, index));
                advance = true;
              }
              else {
                index = str.indexOf("<=");
                if(index > 0) {
                  day = Integer.parseInt(str.substring(index + 2));
                  dayOfWeek = parseDayOfWeek(str.substring(0, index));
                  advance = false;
                }
                else {
                  throw new IllegalArgumentException(str);
                }
              }
            }
          }
          if(st.hasMoreTokens()) {
            str = st.nextToken();
            zoneChar = parseZoneChar(str.charAt(str.length() - 1));
            if(str.equals("24:00")) {
              LocalDate date = (day == -1 ? new LocalDate(2001, month, 1).plusMonths(1) : new LocalDate(2001, month, day).plusDays(1));
              advance = (day != -1);
              month = date.getMonthOfYear();
              day = date.getDayOfMonth();
              dayOfWeek = ((dayOfWeek - 1 + 1) % 7) + 1;
            }
            else {
              millis = parseTime(str);
            }
          }
        }
      }
      iMonthOfYear = month;
      iDayOfMonth = day;
      iDayOfWeek = dayOfWeek;
      iAdvanceDayOfWeek = advance;
      iMillisOfDay = millis;
      iZoneChar = zoneChar;
    }
    public String toString() {
      return "MonthOfYear: " + iMonthOfYear + "\n" + "DayOfMonth: " + iDayOfMonth + "\n" + "DayOfWeek: " + iDayOfWeek + "\n" + "AdvanceDayOfWeek: " + iAdvanceDayOfWeek + "\n" + "MillisOfDay: " + iMillisOfDay + "\n" + "ZoneChar: " + iZoneChar + "\n";
    }
    public void addCutover(DateTimeZoneBuilder builder, int year) {
      builder.addCutover(year, iZoneChar, iMonthOfYear, iDayOfMonth, iDayOfWeek, iAdvanceDayOfWeek, iMillisOfDay);
    }
    public void addRecurring(DateTimeZoneBuilder builder, String nameKey, int saveMillis, int fromYear, int toYear) {
      builder.addRecurringSavings(nameKey, saveMillis, fromYear, toYear, iZoneChar, iMonthOfYear, iDayOfMonth, iDayOfWeek, iAdvanceDayOfWeek, iMillisOfDay);
    }
  }
  
  private static class Rule  {
    final public String iName;
    final public int iFromYear;
    final public int iToYear;
    final public String iType;
    final public DateTimeOfYear iDateTimeOfYear;
    final public int iSaveMillis;
    final public String iLetterS;
    Rule(StringTokenizer st) {
      super();
      iName = st.nextToken().intern();
      iFromYear = parseYear(st.nextToken(), 0);
      iToYear = parseYear(st.nextToken(), iFromYear);
      if(iToYear < iFromYear) {
        throw new IllegalArgumentException();
      }
      iType = parseOptional(st.nextToken());
      iDateTimeOfYear = new DateTimeOfYear(st);
      iSaveMillis = parseTime(st.nextToken());
      iLetterS = parseOptional(st.nextToken());
    }
    private String formatName(String nameFormat) {
      int index = nameFormat.indexOf('/');
      if(index > 0) {
        if(iSaveMillis == 0) {
          return nameFormat.substring(0, index).intern();
        }
        else {
          return nameFormat.substring(index + 1).intern();
        }
      }
      index = nameFormat.indexOf("%s");
      if(index < 0) {
        return nameFormat;
      }
      String left = nameFormat.substring(0, index);
      String right = nameFormat.substring(index + 2);
      String name;
      if(iLetterS == null) {
        name = left.concat(right);
      }
      else {
        name = left + iLetterS + right;
      }
      return name.intern();
    }
    public String toString() {
      return "[Rule]\n" + "Name: " + iName + "\n" + "FromYear: " + iFromYear + "\n" + "ToYear: " + iToYear + "\n" + "Type: " + iType + "\n" + iDateTimeOfYear + "SaveMillis: " + iSaveMillis + "\n" + "LetterS: " + iLetterS + "\n";
    }
    public void addRecurring(DateTimeZoneBuilder builder, String nameFormat) {
      String nameKey = formatName(nameFormat);
      iDateTimeOfYear.addRecurring(builder, nameKey, iSaveMillis, iFromYear, iToYear);
    }
  }
  
  private static class RuleSet  {
    private List<Rule> iRules;
    RuleSet(Rule rule) {
      super();
      iRules = new ArrayList<Rule>();
      iRules.add(rule);
    }
    public void addRecurring(DateTimeZoneBuilder builder, String nameFormat) {
      for(int i = 0; i < iRules.size(); i++) {
        Rule rule = iRules.get(i);
        rule.addRecurring(builder, nameFormat);
      }
    }
    void addRule(Rule rule) {
      if(!(rule.iName.equals(iRules.get(0).iName))) {
        throw new IllegalArgumentException("Rule name mismatch");
      }
      iRules.add(rule);
    }
  }
  
  private static class Zone  {
    final public String iName;
    final public int iOffsetMillis;
    final public String iRules;
    final public String iFormat;
    final public int iUntilYear;
    final public DateTimeOfYear iUntilDateTimeOfYear;
    private Zone iNext;
    private Zone(String name, StringTokenizer st) {
      super();
      iName = name.intern();
      iOffsetMillis = parseTime(st.nextToken());
      iRules = parseOptional(st.nextToken());
      iFormat = st.nextToken().intern();
      int year = Integer.MAX_VALUE;
      DateTimeOfYear dtOfYear = getStartOfYear();
      if(st.hasMoreTokens()) {
        year = Integer.parseInt(st.nextToken());
        if(st.hasMoreTokens()) {
          dtOfYear = new DateTimeOfYear(st);
        }
      }
      iUntilYear = year;
      iUntilDateTimeOfYear = dtOfYear;
    }
    Zone(StringTokenizer st) {
      this(st.nextToken(), st);
    }
    public String toString() {
      String str = "[Zone]\n" + "Name: " + iName + "\n" + "OffsetMillis: " + iOffsetMillis + "\n" + "Rules: " + iRules + "\n" + "Format: " + iFormat + "\n" + "UntilYear: " + iUntilYear + "\n" + iUntilDateTimeOfYear;
      if(iNext == null) {
        return str;
      }
      return str + "...\n" + iNext.toString();
    }
    public void addToBuilder(DateTimeZoneBuilder builder, Map<String, RuleSet> ruleSets) {
      addToBuilder(this, builder, ruleSets);
    }
    private static void addToBuilder(Zone zone, DateTimeZoneBuilder builder, Map<String, RuleSet> ruleSets) {
      for(; zone != null; zone = zone.iNext) {
        builder.setStandardOffset(zone.iOffsetMillis);
        if(zone.iRules == null) {
          builder.setFixedSavings(zone.iFormat, 0);
        }
        else {
          try {
            int saveMillis = parseTime(zone.iRules);
            builder.setFixedSavings(zone.iFormat, saveMillis);
          }
          catch (Exception e) {
            RuleSet rs = ruleSets.get(zone.iRules);
            if(rs == null) {
              throw new IllegalArgumentException("Rules not found: " + zone.iRules);
            }
            rs.addRecurring(builder, zone.iFormat);
          }
        }
        if(zone.iUntilYear == Integer.MAX_VALUE) {
          break ;
        }
        zone.iUntilDateTimeOfYear.addCutover(builder, zone.iUntilYear);
      }
    }
    void chain(StringTokenizer st) {
      if(iNext != null) {
        iNext.chain(st);
      }
      else {
        iNext = new Zone(iName, st);
      }
    }
  }
}