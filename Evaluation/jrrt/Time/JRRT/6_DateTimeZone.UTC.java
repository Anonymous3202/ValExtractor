package org.joda.time;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamException;
import java.io.Serializable;
import java.lang.ref.Reference;
import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import org.joda.convert.FromString;
import org.joda.convert.ToString;
import org.joda.time.chrono.BaseChronology;
import org.joda.time.field.FieldUtils;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.DateTimeFormatterBuilder;
import org.joda.time.format.FormatUtils;
import org.joda.time.tz.DefaultNameProvider;
import org.joda.time.tz.FixedDateTimeZone;
import org.joda.time.tz.NameProvider;
import org.joda.time.tz.Provider;
import org.joda.time.tz.UTCProvider;
import org.joda.time.tz.ZoneInfoProvider;

abstract public class DateTimeZone implements Serializable  {
  final private static long serialVersionUID = 5546345482340108586L;
  final public static DateTimeZone UTC = new FixedDateTimeZone("UTC", "UTC", 0, 0);
  final private static int MAX_MILLIS = (86400 * 1000) - 1;
  private static Provider cProvider;
  private static NameProvider cNameProvider;
  private static Set<String> cAvailableIDs;
  private static volatile DateTimeZone cDefault;
  private static DateTimeFormatter cOffsetFormatter;
  private static Map<String, SoftReference<DateTimeZone>> iFixedOffsetCache;
  private static Map<String, String> cZoneIdConversion;
  static {
    setProvider0(null);
    setNameProvider0(null);
  }
  final private String iID;
  protected DateTimeZone(String id) {
    super();
    if(id == null) {
      throw new IllegalArgumentException("Id must not be null");
    }
    iID = id;
  }
  private static synchronized DateTimeFormatter offsetFormatter() {
    if(cOffsetFormatter == null) {
      cOffsetFormatter = new DateTimeFormatterBuilder().appendTimeZoneOffset(null, true, 2, 4).toFormatter();
    }
    return cOffsetFormatter;
  }
  private static synchronized DateTimeZone fixedOffsetZone(String id, int offset) {
    if(offset == 0) {
      return DateTimeZone.UTC;
    }
    if(iFixedOffsetCache == null) {
      iFixedOffsetCache = new HashMap<String, SoftReference<DateTimeZone>>();
    }
    DateTimeZone zone;
    Reference<DateTimeZone> ref = iFixedOffsetCache.get(id);
    if(ref != null) {
      zone = ref.get();
      if(zone != null) {
        return zone;
      }
    }
    zone = new FixedDateTimeZone(id, null, offset, offset);
    iFixedOffsetCache.put(id, new SoftReference<DateTimeZone>(zone));
    return zone;
  }
  @FromString() public static DateTimeZone forID(String id) {
    if(id == null) {
      return getDefault();
    }
    if(id.equals("UTC")) {
      return DateTimeZone.UTC;
    }
    DateTimeZone zone = cProvider.getZone(id);
    if(zone != null) {
      return zone;
    }
    if(id.startsWith("+") || id.startsWith("-")) {
      int offset = parseOffset(id);
      if(offset == 0L) {
        return DateTimeZone.UTC;
      }
      else {
        id = printOffset(offset);
        return fixedOffsetZone(id, offset);
      }
    }
    throw new IllegalArgumentException("The datetime zone id \'" + id + "\' is not recognised");
  }
  public static DateTimeZone forOffsetHours(int hoursOffset) throws IllegalArgumentException {
    return forOffsetHoursMinutes(hoursOffset, 0);
  }
  public static DateTimeZone forOffsetHoursMinutes(int hoursOffset, int minutesOffset) throws IllegalArgumentException {
    if(hoursOffset == 0 && minutesOffset == 0) {
      return DateTimeZone.UTC;
    }
    if(hoursOffset < -23 || hoursOffset > 23) {
      throw new IllegalArgumentException("Hours out of range: " + hoursOffset);
    }
    if(minutesOffset < -59 || minutesOffset > 59) {
      throw new IllegalArgumentException("Minutes out of range: " + minutesOffset);
    }
    if(hoursOffset > 0 && minutesOffset < 0) {
      throw new IllegalArgumentException("Positive hours must not have negative minutes: " + minutesOffset);
    }
    int offset = 0;
    try {
      int hoursInMinutes = hoursOffset * 60;
      if(hoursInMinutes < 0) {
        minutesOffset = hoursInMinutes - Math.abs(minutesOffset);
      }
      else {
        minutesOffset = hoursInMinutes + minutesOffset;
      }
      offset = FieldUtils.safeMultiply(minutesOffset, DateTimeConstants.MILLIS_PER_MINUTE);
    }
    catch (ArithmeticException ex) {
      throw new IllegalArgumentException("Offset is too large");
    }
    return forOffsetMillis(offset);
  }
  public static DateTimeZone forOffsetMillis(int millisOffset) {
    if(millisOffset < -MAX_MILLIS || millisOffset > MAX_MILLIS) {
      throw new IllegalArgumentException("Millis out of range: " + millisOffset);
    }
    String id = printOffset(millisOffset);
    return fixedOffsetZone(id, millisOffset);
  }
  public static DateTimeZone forTimeZone(TimeZone zone) {
    if(zone == null) {
      return getDefault();
    }
    final String id = zone.getID();
    if(id.equals("UTC")) {
      DateTimeZone var_6 = DateTimeZone.UTC;
      return var_6;
    }
    DateTimeZone dtz = null;
    String convId = getConvertedId(id);
    if(convId != null) {
      dtz = cProvider.getZone(convId);
    }
    if(dtz == null) {
      dtz = cProvider.getZone(id);
    }
    if(dtz != null) {
      return dtz;
    }
    if(convId == null) {
      convId = zone.getID();
      if(convId.startsWith("GMT+") || convId.startsWith("GMT-")) {
        convId = convId.substring(3);
        int offset = parseOffset(convId);
        if(offset == 0L) {
          return DateTimeZone.UTC;
        }
        else {
          convId = printOffset(offset);
          return fixedOffsetZone(convId, offset);
        }
      }
    }
    throw new IllegalArgumentException("The datetime zone id \'" + id + "\' is not recognised");
  }
  public static DateTimeZone getDefault() {
    DateTimeZone zone = cDefault;
    if(zone == null) {
      synchronized(DateTimeZone.class) {
        zone = cDefault;
        if(zone == null) {
          DateTimeZone temp = null;
          try {
            try {
              String id = System.getProperty("user.timezone");
              if(id != null) {
                temp = forID(id);
              }
            }
            catch (RuntimeException ex) {
            }
            if(temp == null) {
              temp = forTimeZone(TimeZone.getDefault());
            }
          }
          catch (IllegalArgumentException ex) {
          }
          if(temp == null) {
            temp = UTC;
          }
          cDefault = zone = temp;
        }
      }
    }
    return zone;
  }
  private static NameProvider getDefaultNameProvider() {
    NameProvider nameProvider = null;
    try {
      String providerClass = System.getProperty("org.joda.time.DateTimeZone.NameProvider");
      if(providerClass != null) {
        try {
          nameProvider = (NameProvider)Class.forName(providerClass).newInstance();
        }
        catch (Exception ex) {
          throw new RuntimeException(ex);
        }
      }
    }
    catch (SecurityException ex) {
    }
    if(nameProvider == null) {
      nameProvider = new DefaultNameProvider();
    }
    return nameProvider;
  }
  public static NameProvider getNameProvider() {
    return cNameProvider;
  }
  protected Object writeReplace() throws ObjectStreamException {
    return new Stub(iID);
  }
  private static Provider getDefaultProvider() {
    Provider provider = null;
    try {
      String providerClass = System.getProperty("org.joda.time.DateTimeZone.Provider");
      if(providerClass != null) {
        try {
          provider = (Provider)Class.forName(providerClass).newInstance();
        }
        catch (Exception ex) {
          throw new RuntimeException(ex);
        }
      }
    }
    catch (SecurityException ex) {
    }
    if(provider == null) {
      try {
        provider = new ZoneInfoProvider("org/joda/time/tz/data");
      }
      catch (Exception ex) {
        ex.printStackTrace();
      }
    }
    if(provider == null) {
      provider = new UTCProvider();
    }
    return provider;
  }
  public static Provider getProvider() {
    return cProvider;
  }
  public static Set<String> getAvailableIDs() {
    return cAvailableIDs;
  }
  private static synchronized String getConvertedId(String id) {
    Map<String, String> map = cZoneIdConversion;
    if(map == null) {
      map = new HashMap<String, String>();
      map.put("GMT", "UTC");
      map.put("WET", "WET");
      map.put("CET", "CET");
      map.put("MET", "CET");
      map.put("ECT", "CET");
      map.put("EET", "EET");
      map.put("MIT", "Pacific/Apia");
      map.put("HST", "Pacific/Honolulu");
      map.put("AST", "America/Anchorage");
      map.put("PST", "America/Los_Angeles");
      map.put("MST", "America/Denver");
      map.put("PNT", "America/Phoenix");
      map.put("CST", "America/Chicago");
      map.put("EST", "America/New_York");
      map.put("IET", "America/Indiana/Indianapolis");
      map.put("PRT", "America/Puerto_Rico");
      map.put("CNT", "America/St_Johns");
      map.put("AGT", "America/Argentina/Buenos_Aires");
      map.put("BET", "America/Sao_Paulo");
      map.put("ART", "Africa/Cairo");
      map.put("CAT", "Africa/Harare");
      map.put("EAT", "Africa/Addis_Ababa");
      map.put("NET", "Asia/Yerevan");
      map.put("PLT", "Asia/Karachi");
      map.put("IST", "Asia/Kolkata");
      map.put("BST", "Asia/Dhaka");
      map.put("VST", "Asia/Ho_Chi_Minh");
      map.put("CTT", "Asia/Shanghai");
      map.put("JST", "Asia/Tokyo");
      map.put("ACT", "Australia/Darwin");
      map.put("AET", "Australia/Sydney");
      map.put("SST", "Pacific/Guadalcanal");
      map.put("NST", "Pacific/Auckland");
      cZoneIdConversion = map;
    }
    return map.get(id);
  }
  @ToString() final public String getID() {
    return iID;
  }
  final public String getName(long instant) {
    return getName(instant, null);
  }
  public String getName(long instant, Locale locale) {
    if(locale == null) {
      locale = Locale.getDefault();
    }
    String nameKey = getNameKey(instant);
    if(nameKey == null) {
      return iID;
    }
    String name = cNameProvider.getName(locale, iID, nameKey);
    if(name != null) {
      return name;
    }
    return printOffset(getOffset(instant));
  }
  abstract public String getNameKey(long instant);
  final public String getShortName(long instant) {
    return getShortName(instant, null);
  }
  public String getShortName(long instant, Locale locale) {
    if(locale == null) {
      locale = Locale.getDefault();
    }
    String nameKey = getNameKey(instant);
    if(nameKey == null) {
      return iID;
    }
    String name = cNameProvider.getShortName(locale, iID, nameKey);
    if(name != null) {
      return name;
    }
    return printOffset(getOffset(instant));
  }
  private static String printOffset(int offset) {
    StringBuffer buf = new StringBuffer();
    if(offset >= 0) {
      buf.append('+');
    }
    else {
      buf.append('-');
      offset = -offset;
    }
    int hours = offset / DateTimeConstants.MILLIS_PER_HOUR;
    FormatUtils.appendPaddedInteger(buf, hours, 2);
    offset -= hours * (int)DateTimeConstants.MILLIS_PER_HOUR;
    int minutes = offset / DateTimeConstants.MILLIS_PER_MINUTE;
    buf.append(':');
    FormatUtils.appendPaddedInteger(buf, minutes, 2);
    offset -= minutes * DateTimeConstants.MILLIS_PER_MINUTE;
    if(offset == 0) {
      return buf.toString();
    }
    int seconds = offset / DateTimeConstants.MILLIS_PER_SECOND;
    buf.append(':');
    FormatUtils.appendPaddedInteger(buf, seconds, 2);
    offset -= seconds * DateTimeConstants.MILLIS_PER_SECOND;
    if(offset == 0) {
      return buf.toString();
    }
    buf.append('.');
    FormatUtils.appendPaddedInteger(buf, offset, 3);
    return buf.toString();
  }
  public String toString() {
    return getID();
  }
  abstract public boolean equals(Object object);
  abstract public boolean isFixed();
  public boolean isLocalDateTimeGap(LocalDateTime localDateTime) {
    if(isFixed()) {
      return false;
    }
    try {
      localDateTime.toDateTime(this);
      return false;
    }
    catch (IllegalInstantException ex) {
      return true;
    }
  }
  public boolean isStandardOffset(long instant) {
    return getOffset(instant) == getStandardOffset(instant);
  }
  abstract public int getOffset(long instant);
  final public int getOffset(ReadableInstant instant) {
    if(instant == null) {
      return getOffset(DateTimeUtils.currentTimeMillis());
    }
    return getOffset(instant.getMillis());
  }
  public int getOffsetFromLocal(long instantLocal) {
    final int offsetLocal = getOffset(instantLocal);
    final long instantAdjusted = instantLocal - offsetLocal;
    final int offsetAdjusted = getOffset(instantAdjusted);
    if(offsetLocal != offsetAdjusted) {
      if((offsetLocal - offsetAdjusted) < 0) {
        long nextLocal = nextTransition(instantAdjusted);
        long nextAdjusted = nextTransition(instantLocal - offsetAdjusted);
        if(nextLocal != nextAdjusted) {
          return offsetLocal;
        }
      }
    }
    else 
      if(offsetLocal >= 0) {
        long prev = previousTransition(instantAdjusted);
        if(prev < instantAdjusted) {
          int offsetPrev = getOffset(prev);
          int diff = offsetPrev - offsetLocal;
          if(instantAdjusted - prev <= diff) {
            return offsetPrev;
          }
        }
      }
    return offsetAdjusted;
  }
  abstract public int getStandardOffset(long instant);
  public int hashCode() {
    return 57 + getID().hashCode();
  }
  private static int parseOffset(String str) {
    Chronology chrono = new BaseChronology() {
        final private static long serialVersionUID = -3128740902654445468L;
        public DateTimeZone getZone() {
          return null;
        }
        public Chronology withUTC() {
          return this;
        }
        public Chronology withZone(DateTimeZone zone) {
          return this;
        }
        public String toString() {
          return getClass().getName();
        }
    };
    return -(int)offsetFormatter().withChronology(chrono).parseMillis(str);
  }
  public java.util.TimeZone toTimeZone() {
    return java.util.TimeZone.getTimeZone(iID);
  }
  public long adjustOffset(long instant, boolean earlierOrLater) {
    long instantBefore = instant - 3 * DateTimeConstants.MILLIS_PER_HOUR;
    long instantAfter = instant + 3 * DateTimeConstants.MILLIS_PER_HOUR;
    long offsetBefore = getOffset(instantBefore);
    long offsetAfter = getOffset(instantAfter);
    if(offsetBefore <= offsetAfter) {
      return instant;
    }
    long diff = offsetBefore - offsetAfter;
    long transition = nextTransition(instantBefore);
    long overlapStart = transition - diff;
    long overlapEnd = transition + diff;
    if(instant < overlapStart || instant >= overlapEnd) {
      return instant;
    }
    long afterStart = instant - overlapStart;
    if(afterStart >= diff) {
      return earlierOrLater ? instant : instant - diff;
    }
    else {
      return earlierOrLater ? instant + diff : instant;
    }
  }
  public long convertLocalToUTC(long instantLocal, boolean strict) {
    int offsetLocal = getOffset(instantLocal);
    int offset = getOffset(instantLocal - offsetLocal);
    if(offsetLocal != offset) {
      if(strict || offsetLocal < 0) {
        long nextLocal = nextTransition(instantLocal - offsetLocal);
        if(nextLocal == (instantLocal - offsetLocal)) {
          nextLocal = Long.MAX_VALUE;
        }
        long nextAdjusted = nextTransition(instantLocal - offset);
        if(nextAdjusted == (instantLocal - offset)) {
          nextAdjusted = Long.MAX_VALUE;
        }
        if(nextLocal != nextAdjusted) {
          if(strict) {
            throw new IllegalInstantException(instantLocal, getID());
          }
          else {
            offset = offsetLocal;
          }
        }
      }
    }
    long instantUTC = instantLocal - offset;
    if((instantLocal ^ instantUTC) < 0 && (instantLocal ^ offset) < 0) {
      throw new ArithmeticException("Subtracting time zone offset caused overflow");
    }
    return instantUTC;
  }
  public long convertLocalToUTC(long instantLocal, boolean strict, long originalInstantUTC) {
    int offsetOriginal = getOffset(originalInstantUTC);
    long instantUTC = instantLocal - offsetOriginal;
    int offsetLocalFromOriginal = getOffset(instantUTC);
    if(offsetLocalFromOriginal == offsetOriginal) {
      return instantUTC;
    }
    return convertLocalToUTC(instantLocal, strict);
  }
  public long convertUTCToLocal(long instantUTC) {
    int offset = getOffset(instantUTC);
    long instantLocal = instantUTC + offset;
    if((instantUTC ^ instantLocal) < 0 && (instantUTC ^ offset) >= 0) {
      throw new ArithmeticException("Adding time zone offset caused overflow");
    }
    return instantLocal;
  }
  public long getMillisKeepLocal(DateTimeZone newZone, long oldInstant) {
    if(newZone == null) {
      newZone = DateTimeZone.getDefault();
    }
    if(newZone == this) {
      return oldInstant;
    }
    long instantLocal = convertUTCToLocal(oldInstant);
    return newZone.convertLocalToUTC(instantLocal, false, oldInstant);
  }
  abstract public long nextTransition(long instant);
  abstract public long previousTransition(long instant);
  public static void setDefault(DateTimeZone zone) throws SecurityException {
    SecurityManager sm = System.getSecurityManager();
    if(sm != null) {
      sm.checkPermission(new JodaTimePermission("DateTimeZone.setDefault"));
    }
    if(zone == null) {
      throw new IllegalArgumentException("The datetime zone must not be null");
    }
    synchronized(DateTimeZone.class) {
      cDefault = zone;
    }
  }
  public static void setNameProvider(NameProvider nameProvider) throws SecurityException {
    SecurityManager sm = System.getSecurityManager();
    if(sm != null) {
      sm.checkPermission(new JodaTimePermission("DateTimeZone.setNameProvider"));
    }
    setNameProvider0(nameProvider);
  }
  private static void setNameProvider0(NameProvider nameProvider) {
    if(nameProvider == null) {
      nameProvider = getDefaultNameProvider();
    }
    cNameProvider = nameProvider;
  }
  public static void setProvider(Provider provider) throws SecurityException {
    SecurityManager sm = System.getSecurityManager();
    if(sm != null) {
      sm.checkPermission(new JodaTimePermission("DateTimeZone.setProvider"));
    }
    setProvider0(provider);
  }
  private static void setProvider0(Provider provider) {
    if(provider == null) {
      provider = getDefaultProvider();
    }
    Set<String> ids = provider.getAvailableIDs();
    if(ids == null || ids.size() == 0) {
      throw new IllegalArgumentException("The provider doesn\'t have any available ids");
    }
    if(!ids.contains("UTC")) {
      throw new IllegalArgumentException("The provider doesn\'t support UTC");
    }
    if(!UTC.equals(provider.getZone("UTC"))) {
      throw new IllegalArgumentException("Invalid UTC zone provided");
    }
    cProvider = provider;
    cAvailableIDs = ids;
  }
  
  final private static class Stub implements Serializable  {
    final private static long serialVersionUID = -6471952376487863581L;
    private transient String iID;
    Stub(String id) {
      super();
      iID = id;
    }
    private Object readResolve() throws ObjectStreamException {
      return forID(iID);
    }
    private void readObject(ObjectInputStream in) throws IOException {
      iID = in.readUTF();
    }
    private void writeObject(ObjectOutputStream out) throws IOException {
      out.writeUTF(iID);
    }
  }
}