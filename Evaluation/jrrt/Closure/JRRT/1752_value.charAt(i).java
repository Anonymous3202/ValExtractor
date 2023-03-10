package com.google.javascript.jscomp;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharStreams;
import com.google.common.io.Files;
import java.io.*;
import java.text.*;
import java.util.*;

public class VariableMap  {
  final private ImmutableMap<String, String> map;
  private ImmutableMap<String, String> reverseMap = null;
  final private static char SEPARATOR = ':';
  VariableMap(Map<String, String> map) {
    super();
    this.map = ImmutableMap.copyOf(map);
  }
  public Map<String, String> getNewNameToOriginalNameMap() {
    initReverseMap();
    return reverseMap;
  }
  public Map<String, String> getOriginalNameToNewNameMap() {
    return map;
  }
  @VisibleForTesting() Map<String, String> toMap() {
    return map;
  }
  private static String escape(String value) {
    return value.replace("\\", "\\\\").replace(":", "\\:").replace("\n", "\\n");
  }
  public String lookupNewName(String sourceName) {
    return map.get(sourceName);
  }
  public String lookupSourceName(String newName) {
    initReverseMap();
    return reverseMap.get(newName);
  }
  private static String unescape(CharSequence value) {
    StringBuilder sb = new StringBuilder();
    int len = value.length();
    for(int i = 0; i < len; i++) {
      char var_1752 = value.charAt(i);
      char c = var_1752;
      if(c == '\\' && ++i < len) {
        c = value.charAt(i);
      }
      sb.append(c);
    }
    return sb.toString();
  }
  public static VariableMap fromBytes(byte[] bytes) throws ParseException {
    Iterable<String> lines;
    try {
      lines = CharStreams.readLines(CharStreams.newReaderSupplier(ByteStreams.newInputStreamSupplier(bytes), Charsets.UTF_8));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    ImmutableMap.Builder<String, String> map = ImmutableMap.builder();
    for (String line : lines) {
      int pos = findIndexOfChar(line, SEPARATOR);
      if(pos <= 0 || pos == line.length() - 1) {
        throw new ParseException("Bad line: " + line, 0);
      }
      map.put(unescape(line.substring(0, pos)), unescape(line.substring(pos + 1)));
    }
    return new VariableMap(map.build());
  }
  public static VariableMap fromMap(Map<String, String> map) {
    return new VariableMap(map);
  }
  public static VariableMap load(String filename) throws IOException {
    try {
      return fromBytes(Files.toByteArray(new File(filename)));
    }
    catch (ParseException e) {
      throw new IOException(e);
    }
  }
  public byte[] toBytes() {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    Writer writer = new OutputStreamWriter(baos, Charsets.UTF_8);
    try {
      for (Map.Entry<String, String> entry : map.entrySet()) {
        writer.write(escape(entry.getKey()));
        writer.write(SEPARATOR);
        writer.write(escape(entry.getValue()));
        writer.write('\n');
      }
      writer.close();
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    return baos.toByteArray();
  }
  private static int findIndexOfChar(String value, char stopChar) {
    int len = value.length();
    for(int i = 0; i < len; i++) {
      char c = value.charAt(i);
      if(c == '\\' && ++i < len) {
        c = value.charAt(i);
      }
      else 
        if(c == stopChar) {
          return i;
        }
    }
    return -1;
  }
  private synchronized void initReverseMap() {
    if(reverseMap == null) {
      ImmutableMap.Builder<String, String> rm = ImmutableMap.builder();
      for (Map.Entry<String, String> entry : map.entrySet()) {
        rm.put(entry.getValue(), entry.getKey());
      }
      reverseMap = rm.build();
    }
  }
  public void save(String filename) throws IOException {
    Files.write(toBytes(), new File(filename));
  }
}