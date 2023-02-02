package org.joda.time.tz;
import org.joda.time.DateTimeZone;

public class CachedDateTimeZone extends DateTimeZone  {
  final private static long serialVersionUID = 5472298452022250685L;
  final private static int cInfoCacheMask;
  static {
    Integer i;
    try {
      i = Integer.getInteger("org.joda.time.tz.CachedDateTimeZone.size");
    }
    catch (SecurityException e) {
      i = null;
    }
    int cacheSize;
    if(i == null) {
      cacheSize = 512;
    }
    else {
      cacheSize = i.intValue();
      cacheSize--;
      int shift = 0;
      while(cacheSize > 0){
        shift++;
        cacheSize >>= 1;
      }
      cacheSize = 1 << shift;
    }
    cInfoCacheMask = cacheSize - 1;
  }
  final private DateTimeZone iZone;
  final private transient Info[] iInfoCache = new Info[cInfoCacheMask + 1];
  private CachedDateTimeZone(DateTimeZone zone) {
    super(zone.getID());
    iZone = zone;
  }
  public static CachedDateTimeZone forZone(DateTimeZone zone) {
    if(zone instanceof CachedDateTimeZone) {
      return (CachedDateTimeZone)zone;
    }
    return new CachedDateTimeZone(zone);
  }
  public DateTimeZone getUncachedZone() {
    return iZone;
  }
  private Info createInfo(long millis) {
    long periodStart = millis & (0xffffffffL << 32);
    Info info = new Info(iZone, periodStart);
    long end = periodStart | 0xffffffffL;
    Info chain = info;
    while(true){
      long next = iZone.nextTransition(periodStart);
      if(next == periodStart || next > end) {
        break ;
      }
      periodStart = next;
      chain = (chain.iNextInfo = new Info(iZone, periodStart));
    }
    return info;
  }
  private Info getInfo(long millis) {
    int period = (int)(millis >> 32);
    Info[] cache = iInfoCache;
    int index = period & cInfoCacheMask;
    Info var_713 = cache[index];
    Info info = var_713;
    if(info == null || (int)((info.iPeriodStart >> 32)) != period) {
      info = createInfo(millis);
      cache[index] = info;
    }
    return info;
  }
  public String getNameKey(long instant) {
    return getInfo(instant).getNameKey(instant);
  }
  public boolean equals(Object obj) {
    if(this == obj) {
      return true;
    }
    if(obj instanceof CachedDateTimeZone) {
      return iZone.equals(((CachedDateTimeZone)obj).iZone);
    }
    return false;
  }
  public boolean isFixed() {
    return iZone.isFixed();
  }
  public int getOffset(long instant) {
    return getInfo(instant).getOffset(instant);
  }
  public int getStandardOffset(long instant) {
    return getInfo(instant).getStandardOffset(instant);
  }
  public int hashCode() {
    return iZone.hashCode();
  }
  public long nextTransition(long instant) {
    return iZone.nextTransition(instant);
  }
  public long previousTransition(long instant) {
    return iZone.previousTransition(instant);
  }
  
  final private static class Info  {
    final public long iPeriodStart;
    final public DateTimeZone iZoneRef;
    Info iNextInfo;
    private String iNameKey;
    private int iOffset = Integer.MIN_VALUE;
    private int iStandardOffset = Integer.MIN_VALUE;
    Info(DateTimeZone zone, long periodStart) {
      super();
      iPeriodStart = periodStart;
      iZoneRef = zone;
    }
    public String getNameKey(long millis) {
      if(iNextInfo == null || millis < iNextInfo.iPeriodStart) {
        if(iNameKey == null) {
          iNameKey = iZoneRef.getNameKey(iPeriodStart);
        }
        return iNameKey;
      }
      return iNextInfo.getNameKey(millis);
    }
    public int getOffset(long millis) {
      if(iNextInfo == null || millis < iNextInfo.iPeriodStart) {
        if(iOffset == Integer.MIN_VALUE) {
          iOffset = iZoneRef.getOffset(iPeriodStart);
        }
        return iOffset;
      }
      return iNextInfo.getOffset(millis);
    }
    public int getStandardOffset(long millis) {
      if(iNextInfo == null || millis < iNextInfo.iPeriodStart) {
        if(iStandardOffset == Integer.MIN_VALUE) {
          iStandardOffset = iZoneRef.getStandardOffset(iPeriodStart);
        }
        return iStandardOffset;
      }
      return iNextInfo.getStandardOffset(millis);
    }
  }
}