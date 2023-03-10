package com.google.javascript.jscomp.jsonml;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class JsonML  {
  final private TagType type;
  private Map<TagAttr, Object> attributes = new EnumMap<TagAttr, Object>(TagAttr.class);
  private List<JsonML> children = new ArrayList<JsonML>();
  public JsonML(TagType type) {
    super();
    this.type = type;
  }
  public JsonML(TagType type, JsonML ... children) {
    this(type, Arrays.asList(children));
  }
  public JsonML(TagType type, List<? extends JsonML> children) {
    this(type, Collections.<TagAttr, Object>emptyMap(), children);
  }
  public JsonML(TagType type, Map<? extends TagAttr, ?> attributes) {
    this(type, attributes, Collections.<JsonML>emptyList());
  }
  public JsonML(TagType type, Map<? extends TagAttr, ?> attributes, List<? extends JsonML> children) {
    super();
    this.type = type;
    this.attributes.putAll(attributes);
    appendChildren(children);
  }
  public JsonML getChild(int index) {
    return children.get(index);
  }
  public List<JsonML> getChildren() {
    return children;
  }
  public List<JsonML> getChildren(int fromIndex, int toIndex) {
    return children.subList(fromIndex, toIndex);
  }
  public Map<TagAttr, Object> getAttributes() {
    return attributes;
  }
  public Object getAttribute(TagAttr name) {
    return attributes.get(name);
  }
  @Override() public String toString() {
    StringBuilder sb = new StringBuilder();
    toString(sb, true, true);
    return sb.toString();
  }
  public String toStringTree() {
    try {
      StringBuilder s = new StringBuilder();
      toStringTreeHelper(this, 0, s);
      return s.toString();
    }
    catch (IOException e) {
      throw new RuntimeException("Should not happen\n" + e);
    }
  }
  public TagType getType() {
    return type;
  }
  public boolean hasChildren() {
    return !children.isEmpty();
  }
  public int childrenSize() {
    return children.size();
  }
  public void addChild(int index, JsonML element) {
    children.add(index, element);
  }
  public void appendChild(JsonML element) {
    children.add(element);
  }
  public void appendChildren(Collection<? extends JsonML> elements) {
    children.addAll(elements);
  }
  public void clearChildren() {
    setChildren();
  }
  private static void escapeStringOnto(String s, StringBuilder sb) {
    int pos = 0;
    int n = s.length();
    for(int i = 0; i < n; ++i) {
      char ch = s.charAt(i);
      switch (ch){
        case '\r':
        case '\n':
        case '\"':
        case '\\':
        case '\u2028':
        case '\u2029':
        String hex = Integer.toString(ch, 16);
        sb.append(s, pos, i).append("\\u").append("0000", hex.length(), 4).append(hex);
        pos = i + 1;
        break ;
      }
    }
    sb.append(s, pos, n);
  }
  public void setAttribute(TagAttr name, Object value) {
    attributes.put(name, value);
  }
  public void setAttributes(Map<TagAttr, Object> attributes) {
    this.attributes = attributes;
  }
  public void setChild(int index, JsonML element) {
    children.set(index, element);
  }
  public void setChildren(JsonML ... children) {
    this.children.clear();
    this.children.addAll(Arrays.asList(children));
  }
  public void setChildren(List<JsonML> children) {
    this.children = children;
  }
  private void toString(StringBuilder sb, boolean printAttributes, boolean printChildren) {
    sb.append("[\"");
    escapeStringOnto(type.name(), sb);
    StringBuilder var_2226 = sb.append('\"');
    if(printAttributes) {
      sb.append(", {");
      boolean first = true;
      for (Entry<TagAttr, Object> entry : attributes.entrySet()) {
        if(first) {
          first = false;
        }
        else {
          sb.append(", ");
        }
        sb.append('\"');
        escapeStringOnto(entry.getKey().toString(), sb);
        sb.append("\": ");
        Object value = entry.getValue();
        if(value == null) {
          sb.append("null");
        }
        else 
          if(value instanceof String) {
            sb.append('\"');
            escapeStringOnto((String)value, sb);
            sb.append('\"');
          }
          else {
            sb.append(value);
          }
      }
      sb.append("}");
    }
    if(printChildren) {
      for (JsonML child : children) {
        sb.append(", ");
        sb.append(child.toString());
      }
    }
    sb.append(']');
  }
  private static void toStringTreeHelper(JsonML element, int level, StringBuilder sb) throws IOException {
    for(int i = 0; i < level; ++i) {
      sb.append("    ");
    }
    element.toString(sb, true, false);
    sb.append("\n");
    for (JsonML child : element.getChildren()) {
      toStringTreeHelper(child, level + 1, sb);
    }
  }
}