package org.joda.time.tz;
import java.text.DateFormatSymbols;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.joda.time.DateTimeUtils;

@SuppressWarnings(value = {"unchecked", }) public class DefaultNameProvider implements NameProvider  {
  private HashMap<Locale, Map<String, Map<String, Object>>> iByLocaleCache = createCache();
  public DefaultNameProvider() {
    super();
  }
  private HashMap createCache() {
    return new HashMap(7);
  }
  public String getName(Locale locale, String id, String nameKey) {
    String[] nameSet = getNameSet(locale, id, nameKey);
    return nameSet == null ? null : nameSet[1];
  }
  public String getShortName(Locale locale, String id, String nameKey) {
    String[] nameSet = getNameSet(locale, id, nameKey);
    return nameSet == null ? null : nameSet[0];
  }
  private synchronized String[] getNameSet(Locale locale, String id, String nameKey) {
    if(locale == null || id == null || nameKey == null) {
      return null;
    }
    Map<String, Map<String, Object>> byIdCache = iByLocaleCache.get(locale);
    if(byIdCache == null) {
      iByLocaleCache.put(locale, byIdCache = createCache());
    }
    Map<String, Object> byNameKeyCache = byIdCache.get(id);
    if(byNameKeyCache == null) {
      byIdCache.put(id, byNameKeyCache = createCache());
      String[][] zoneStringsEn = DateTimeUtils.getDateFormatSymbols(Locale.ENGLISH).getZoneStrings();
      String[] setEn = null;
      for (String[] strings : zoneStringsEn) {
        if(strings != null && strings.length == 5 && id.equals(strings[0])) {
          setEn = strings;
          break ;
        }
      }
      String[][] zoneStringsLoc = DateTimeUtils.getDateFormatSymbols(locale).getZoneStrings();
      String[] setLoc = null;
      for (String[] strings : zoneStringsLoc) {
        if(strings != null && strings.length == 5 && id.equals(strings[0])) {
          setLoc = strings;
          break ;
        }
      }
      if(setEn != null && setLoc != null) {
        byNameKeyCache.put(setEn[2], new String[]{ setLoc[2], setLoc[1] } );
        if(setEn[2].equals(setEn[4])) {
          String var_602 = setEn[4];
          byNameKeyCache.put(var_602 + "-Summer", new String[]{ setLoc[4], setLoc[3] } );
        }
        else {
          byNameKeyCache.put(setEn[4], new String[]{ setLoc[4], setLoc[3] } );
        }
      }
    }
    return (String[])byNameKeyCache.get(nameKey);
  }
}