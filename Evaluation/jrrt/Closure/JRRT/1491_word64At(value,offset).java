package com.google.javascript.jscomp;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;

public class JsMessage  {
  final private static String MESSAGE_REPRESENTATION_FORMAT = "{$%s}";
  final private String key;
  final private String id;
  final private List<CharSequence> parts;
  final private Set<String> placeholders;
  final private String desc;
  final private boolean hidden;
  final private String meaning;
  final private String sourceName;
  final private boolean isAnonymous;
  final private boolean isExternal;
  private JsMessage(String sourceName, String key, boolean isAnonymous, boolean isExternal, String id, List<CharSequence> parts, Set<String> placeholders, String desc, boolean hidden, String meaning) {
    super();
    Preconditions.checkState(key != null);
    Preconditions.checkState(id != null);
    this.key = key;
    this.id = id;
    this.parts = Collections.unmodifiableList(parts);
    this.placeholders = Collections.unmodifiableSet(placeholders);
    this.desc = desc;
    this.hidden = hidden;
    this.meaning = meaning;
    this.sourceName = sourceName;
    this.isAnonymous = isAnonymous;
    this.isExternal = isExternal;
  }
  public List<CharSequence> parts() {
    return parts;
  }
  public Set<String> placeholders() {
    return placeholders;
  }
  public String getDesc() {
    return desc;
  }
  public String getId() {
    return id;
  }
  public String getKey() {
    return key;
  }
  String getMeaning() {
    return meaning;
  }
  public String getSourceName() {
    return sourceName;
  }
  @Override() public String toString() {
    StringBuilder sb = new StringBuilder();
    for (CharSequence p : parts) {
      sb.append(p.toString());
    }
    return sb.toString();
  }
  @Override() public boolean equals(Object o) {
    if(o == this) 
      return true;
    if(!(o instanceof JsMessage)) 
      return false;
    JsMessage m = (JsMessage)o;
    return id.equals(m.id) && key.equals(m.key) && isAnonymous == m.isAnonymous && parts.equals(m.parts) && (meaning == null ? m.meaning == null : meaning.equals(m.meaning)) && placeholders.equals(m.placeholders) && (desc == null ? m.desc == null : desc.equals(m.desc)) && (sourceName == null ? m.sourceName == null : sourceName.equals(m.sourceName)) && hidden == m.hidden;
  }
  public boolean isAnonymous() {
    return isAnonymous;
  }
  public boolean isEmpty() {
    for (CharSequence part : parts) {
      if(part.length() > 0) {
        return false;
      }
    }
    return true;
  }
  public boolean isExternal() {
    return isExternal;
  }
  public boolean isHidden() {
    return hidden;
  }
  @Override() public int hashCode() {
    int hash = key.hashCode();
    hash = 31 * hash + (isAnonymous ? 1 : 0);
    hash = 31 * hash + id.hashCode();
    hash = 31 * hash + parts.hashCode();
    hash = 31 * hash + (desc != null ? desc.hashCode() : 0);
    hash = 31 * hash + (hidden ? 1 : 0);
    hash = 31 * hash + (sourceName != null ? sourceName.hashCode() : 0);
    return hash;
  }
  
  public static class Builder  {
    final private static Pattern MSG_EXTERNAL_PATTERN = Pattern.compile("MSG_EXTERNAL_(\\d+)");
    private String key;
    private String meaning;
    private String desc;
    private boolean hidden;
    private List<CharSequence> parts = Lists.newLinkedList();
    private Set<String> placeholders = Sets.newHashSet();
    private String sourceName;
    public Builder() {
      this(null);
    }
    public Builder(String key) {
      super();
      this.key = key;
    }
    public Builder appendPlaceholderReference(String name) {
      Preconditions.checkNotNull(name, "Placeholder name could not be null");
      parts.add(new PlaceholderReference(name));
      placeholders.add(name);
      return this;
    }
    public Builder appendStringPart(String part) {
      Preconditions.checkNotNull(part, "String part of the message could not be null");
      parts.add(part);
      return this;
    }
    public Builder setDesc(String desc) {
      this.desc = desc;
      return this;
    }
    public Builder setIsHidden(boolean hidden) {
      this.hidden = hidden;
      return this;
    }
    public Builder setKey(String key) {
      this.key = key;
      return this;
    }
    public Builder setMeaning(String meaning) {
      this.meaning = meaning;
      return this;
    }
    public Builder setSourceName(String sourceName) {
      this.sourceName = sourceName;
      return this;
    }
    public JsMessage build() {
      return build(null);
    }
    public JsMessage build(IdGenerator idGenerator) {
      boolean isAnonymous = false;
      boolean isExternal = false;
      String id = null;
      if(getKey() == null) {
        key = JsMessageVisitor.MSG_PREFIX + fingerprint(getParts());
        isAnonymous = true;
      }
      if(!isAnonymous) {
        String externalId = getExternalMessageId(key);
        if(externalId != null) {
          isExternal = true;
          id = externalId;
        }
      }
      if(!isExternal) {
        String defactoMeaning = meaning != null ? meaning : key;
        id = idGenerator == null ? defactoMeaning : idGenerator.generateId(defactoMeaning, parts);
      }
      return new JsMessage(sourceName, key, isAnonymous, isExternal, id, parts, placeholders, desc, hidden, meaning);
    }
    public List<CharSequence> getParts() {
      return parts;
    }
    public Set<String> getPlaceholders() {
      return placeholders;
    }
    private static String fingerprint(List<CharSequence> messageParts) {
      StringBuilder sb = new StringBuilder();
      for (CharSequence part : messageParts) {
        if(part instanceof JsMessage.PlaceholderReference) {
          sb.append(part.toString());
        }
        else {
          sb.append(part);
        }
      }
      long nonnegativeHash = Long.MAX_VALUE & Hash.hash64(sb.toString());
      return Long.toString(nonnegativeHash, 36).toUpperCase();
    }
    private static String getExternalMessageId(String identifier) {
      Matcher m = MSG_EXTERNAL_PATTERN.matcher(identifier);
      return m.matches() ? m.group(1) : null;
    }
    public String getKey() {
      return key;
    }
    public boolean hasParts() {
      return !parts.isEmpty();
    }
  }
  
  final static class Hash  {
    final private static long SEED64 = 0x2b992ddfa23249d6L;
    final private static long CONSTANT64 = 0xe08c1d668b756f82L;
    private Hash() {
      super();
    }
    @SuppressWarnings(value = {"fallthrough", }) private static long hash64(byte[] value, int offset, int length, long seed) {
      long a = CONSTANT64;
      long b = a;
      long c = seed;
      int keylen;
      for(keylen = length; keylen >= 24; keylen -= 24, offset += 24) {
        long var_1491 = word64At(value, offset);
        a += var_1491;
        b += word64At(value, offset + 8);
        c += word64At(value, offset + 16);
        a -= b;
        a -= c;
        a ^= c >>> 43;
        b -= c;
        b -= a;
        b ^= a << 9;
        c -= a;
        c -= b;
        c ^= b >>> 8;
        a -= b;
        a -= c;
        a ^= c >>> 38;
        b -= c;
        b -= a;
        b ^= a << 23;
        c -= a;
        c -= b;
        c ^= b >>> 5;
        a -= b;
        a -= c;
        a ^= c >>> 35;
        b -= c;
        b -= a;
        b ^= a << 49;
        c -= a;
        c -= b;
        c ^= b >>> 11;
        a -= b;
        a -= c;
        a ^= c >>> 12;
        b -= c;
        b -= a;
        b ^= a << 18;
        c -= a;
        c -= b;
        c ^= b >>> 22;
      }
      c += length;
      switch (keylen){
        case 23:
        c += ((long)value[offset + 22]) << 56;
        case 22:
        c += (value[offset + 21] & 0xffL) << 48;
        case 21:
        c += (value[offset + 20] & 0xffL) << 40;
        case 20:
        c += (value[offset + 19] & 0xffL) << 32;
        case 19:
        c += (value[offset + 18] & 0xffL) << 24;
        case 18:
        c += (value[offset + 17] & 0xffL) << 16;
        case 17:
        c += (value[offset + 16] & 0xffL) << 8;
        case 16:
        b += word64At(value, offset + 8);
        a += word64At(value, offset);
        break ;
        case 15:
        b += (value[offset + 14] & 0xffL) << 48;
        case 14:
        b += (value[offset + 13] & 0xffL) << 40;
        case 13:
        b += (value[offset + 12] & 0xffL) << 32;
        case 12:
        b += (value[offset + 11] & 0xffL) << 24;
        case 11:
        b += (value[offset + 10] & 0xffL) << 16;
        case 10:
        b += (value[offset + 9] & 0xffL) << 8;
        case 9:
        b += (value[offset + 8] & 0xffL);
        case 8:
        a += word64At(value, offset);
        break ;
        case 7:
        a += (value[offset + 6] & 0xffL) << 48;
        case 6:
        a += (value[offset + 5] & 0xffL) << 40;
        case 5:
        a += (value[offset + 4] & 0xffL) << 32;
        case 4:
        a += (value[offset + 3] & 0xffL) << 24;
        case 3:
        a += (value[offset + 2] & 0xffL) << 16;
        case 2:
        a += (value[offset + 1] & 0xffL) << 8;
        case 1:
        a += (value[offset + 0] & 0xffL);
      }
      return mix64(a, b, c);
    }
    private static long hash64(byte[] value, long seed) {
      return hash64(value, 0, value == null ? 0 : value.length, seed);
    }
    static long hash64(@Nullable() String value) {
      return hash64(value, SEED64);
    }
    private static long hash64(@Nullable() String value, long seed) {
      if(value == null) {
        return hash64(null, 0, 0, seed);
      }
      return hash64(value.getBytes(), seed);
    }
    private static long mix64(long a, long b, long c) {
      a -= b;
      a -= c;
      a ^= c >>> 43;
      b -= c;
      b -= a;
      b ^= a << 9;
      c -= a;
      c -= b;
      c ^= b >>> 8;
      a -= b;
      a -= c;
      a ^= c >>> 38;
      b -= c;
      b -= a;
      b ^= a << 23;
      c -= a;
      c -= b;
      c ^= b >>> 5;
      a -= b;
      a -= c;
      a ^= c >>> 35;
      b -= c;
      b -= a;
      b ^= a << 49;
      c -= a;
      c -= b;
      c ^= b >>> 11;
      a -= b;
      a -= c;
      a ^= c >>> 12;
      b -= c;
      b -= a;
      b ^= a << 18;
      c -= a;
      c -= b;
      c ^= b >>> 22;
      return c;
    }
    private static long word64At(byte[] bytes, int offset) {
      return (bytes[offset + 0] & 0xffL) + ((bytes[offset + 1] & 0xffL) << 8) + ((bytes[offset + 2] & 0xffL) << 16) + ((bytes[offset + 3] & 0xffL) << 24) + ((bytes[offset + 4] & 0xffL) << 32) + ((bytes[offset + 5] & 0xffL) << 40) + ((bytes[offset + 6] & 0xffL) << 48) + ((bytes[offset + 7] & 0xffL) << 56);
    }
  }
  
  public interface IdGenerator  {
    String generateId(String meaning, List<CharSequence> messageParts);
  }
  
  public static class PlaceholderReference implements CharSequence  {
    final private String name;
    PlaceholderReference(String name) {
      super();
      this.name = name;
    }
    @Override() public CharSequence subSequence(int start, int end) {
      return name.subSequence(start, end);
    }
    public String getName() {
      return name;
    }
    @Override() public String toString() {
      return String.format(MESSAGE_REPRESENTATION_FORMAT, name);
    }
    @Override() public boolean equals(Object o) {
      return o == this || o instanceof PlaceholderReference && name.equals(((PlaceholderReference)o).name);
    }
    @Override() public char charAt(int index) {
      return name.charAt(index);
    }
    @Override() public int hashCode() {
      return 31 * name.hashCode();
    }
    @Override() public int length() {
      return name.length();
    }
  }
  public enum Style {
    LEGACY(),

    RELAX(),

    CLOSURE(),

  ;
    static Style getFromParams(boolean useClosure, boolean allowLegacyMessages) {
      if(useClosure) {
        return allowLegacyMessages ? RELAX : CLOSURE;
      }
      else {
        return LEGACY;
      }
    }
  private Style() {
  }
  }
}