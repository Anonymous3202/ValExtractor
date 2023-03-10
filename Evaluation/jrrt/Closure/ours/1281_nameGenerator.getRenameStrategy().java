/*
 * Copyright 2009 The Closure Compiler Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.javascript.jscomp;

import com.google.common.base.Preconditions;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.Maps;
import com.google.debugging.sourcemap.Base64;
import com.google.javascript.jscomp.NodeTraversal.AbstractPostOrderCallback;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.Node;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Replaces calls to id generators with ids.
 *
 * Use this to get unique and short ids.
 *
 */
class ReplaceIdGenerators implements CompilerPass {
  static final DiagnosticType NON_GLOBAL_ID_GENERATOR_CALL =
      DiagnosticType.error(
          "JSC_NON_GLOBAL_ID_GENERATOR_CALL",
          "Id generator call must be in the global scope");

  static final DiagnosticType CONDITIONAL_ID_GENERATOR_CALL =
      DiagnosticType.error(
          "JSC_CONDITIONAL_ID_GENERATOR_CALL",
          "Id generator call must be unconditional");

  static final DiagnosticType CONFLICTING_GENERATOR_TYPE =
      DiagnosticType.error(
          "JSC_CONFLICTING_ID_GENERATOR_TYPE",
          "Id generator can only be one of consistent, inconsistent, or stable.");

  static final DiagnosticType INVALID_GENERATOR_ID_MAPPING =
      DiagnosticType.error(
          "JSC_INVALID_GENERATOR_ID_MAPPING",
          "Invalid generator id mapping. {0}");

  private final AbstractCompiler compiler;
  private final Map<String, NameSupplier> nameGenerators;
  private final Map<String, Map<String, String>> consistNameMap;

  private final Map<String, Map<String, String>> idGeneratorMaps;
  private final Map<String, BiMap<String, String>> previousMap;

  private final boolean generatePseudoNames;

  public ReplaceIdGenerators(
      AbstractCompiler compiler, Set<String> idGens,
      boolean generatePseudoNames,
      String previousMapSerialized) {
    this.compiler = compiler;
    this.generatePseudoNames = generatePseudoNames;
    nameGenerators = Maps.newLinkedHashMap();
    idGeneratorMaps = Maps.newLinkedHashMap();
    consistNameMap = Maps.newLinkedHashMap();

    Map<String, BiMap<String, String>> previousMap;
    previousMap = parsePreviousResults(previousMapSerialized);
    this.previousMap = previousMap;

    if (idGens != null) {
      for (String gen : idGens) {
        nameGenerators.put(
            gen, createNameSupplier(RenameStrategy.INCONSISTENT, previousMap.get(gen)));
        idGeneratorMaps.put(gen, Maps.<String, String>newLinkedHashMap());
      }
    }
  }

  private enum RenameStrategy {
    CONSISTENT,
    INCONSISTENT,
    STABLE
  }

  private static interface NameSupplier {
    String getName(String id, String name);
    RenameStrategy getRenameStrategy();
  }

  private static class ObfuscatedNameSuppier implements NameSupplier {
    private final NameGenerator generator;
    private final Map<String, String> previousMappings;
    private RenameStrategy renameStrategy;

    public ObfuscatedNameSuppier(
        RenameStrategy renameStrategy, BiMap<String, String> previousMappings) {
      this.previousMappings = previousMappings.inverse();
      this.generator =
          new NameGenerator(previousMappings.keySet(), "", null);
      this.renameStrategy = renameStrategy;
    }

    @Override
    public String getName(String id, String name) {
      String newName = previousMappings.get(id);
      if (newName == null) {
        newName = generator.generateNextName();
      }
      return newName;
    }

    @Override
    public RenameStrategy getRenameStrategy() {
      return renameStrategy;
    }
  }

  private static class PseudoNameSuppier implements NameSupplier {
    private int counter = 0;
    private RenameStrategy renameStrategy;

    public PseudoNameSuppier(RenameStrategy renameStrategy) {
      this.renameStrategy = renameStrategy;
    }

    @Override
    public String getName(String id, String name) {
      if (renameStrategy == RenameStrategy.INCONSISTENT) {
        return name + "$" + counter++;
      }
      return name + "$0";
    }

    @Override
    public RenameStrategy getRenameStrategy() {
      return renameStrategy;
    }
  }

  private static class StableNameSupplier implements NameSupplier {
    @Override
    public String getName(String id, String name) {
      return Base64.base64EncodeInt(name.hashCode());
    }
    @Override
    public RenameStrategy getRenameStrategy() {
      return RenameStrategy.STABLE;
    }
  }

  private NameSupplier createNameSupplier(
      RenameStrategy renameStrategy, BiMap<String, String> previousMappings) {
    previousMappings = previousMappings != null ?
        previousMappings :
        ImmutableBiMap.<String, String>of();
    if (renameStrategy == RenameStrategy.STABLE) {
      return new StableNameSupplier();
    } else if (generatePseudoNames) {
      return new PseudoNameSuppier(renameStrategy);
    } else {
      return new ObfuscatedNameSuppier(renameStrategy, previousMappings);
    }
  }

  private class GatherGenerators extends AbstractPostOrderCallback {

    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      JSDocInfo doc = n.getJSDocInfo();
      if (doc == null) {
        return;
      }

      int numGeneratorAnnotations =
          (doc.isConsistentIdGenerator() ? 1 : 0) +
          (doc.isIdGenerator() ? 1 : 0) +
          (doc.isStableIdGenerator() ? 1 : 0);
      if (numGeneratorAnnotations == 0) {
        return;
      } else if (numGeneratorAnnotations > 1) {
        compiler.report(t.makeError(n, CONFLICTING_GENERATOR_TYPE));
      }

      String name = null;
      if (n.isAssign()) {
        name = n.getFirstChild().getQualifiedName();
      } else if (n.isVar()) {
        name = n.getFirstChild().getString();
      } else if (n.isFunction()){
        name = n.getFirstChild().getString();
        if (name.isEmpty()) {
          return;
        }
      }

      if (doc.isConsistentIdGenerator()) {
        consistNameMap.put(name, Maps.<String, String>newLinkedHashMap());
        nameGenerators.put(
            name, createNameSupplier(RenameStrategy.CONSISTENT, previousMap.get(name)));
      } else if (doc.isStableIdGenerator()) {
        nameGenerators.put(
            name, createNameSupplier(RenameStrategy.STABLE, previousMap.get(name)));
      } else {
        nameGenerators.put(
            name, createNameSupplier(RenameStrategy.INCONSISTENT, previousMap.get(name)));
      }
      idGeneratorMaps.put(name, Maps.<String, String>newLinkedHashMap());
    }
  }

  @Override
  public void process(Node externs, Node root) {
    NodeTraversal.traverse(compiler, root, new GatherGenerators());
    if (!nameGenerators.isEmpty()) {
      NodeTraversal.traverse(compiler, root, new ReplaceGenerators());
    }
  }

  private class ReplaceGenerators extends AbstractPostOrderCallback {
    @Override
    public void visit(NodeTraversal t, Node n, Node parent) {
      if (!n.isCall()) {
        return;
      }

      String callName = n.getFirstChild().getQualifiedName();
      NameSupplier nameGenerator = nameGenerators.get(callName);
      if (nameGenerator == null) {
        return;
      }

      if (!t.inGlobalScope() &&
          nameGenerator.getRenameStrategy() == RenameStrategy.INCONSISTENT) {
        // Warn about calls not in the global scope.
        compiler.report(t.makeError(n, NON_GLOBAL_ID_GENERATOR_CALL));
        return;
      }

      RenameStrategy var_1281 = nameGenerator.getRenameStrategy();
	if (var_1281 == RenameStrategy.INCONSISTENT) {
        for (Node ancestor : n.getAncestors()) {
          if (NodeUtil.isControlStructure(ancestor)) {
            // Warn about conditional calls.
            compiler.report(t.makeError(n, CONDITIONAL_ID_GENERATOR_CALL));
            return;
          }
        }
      }

      Node id = n.getFirstChild().getNext();

      // TODO(user): Error on id not a string literal.
      if (!id.isString()) {
        return;
      }

      Map<String, String> idGeneratorMap = idGeneratorMaps.get(callName);
      String rename = null;

      String name = id.getString();
      String instanceId = getIdForGeneratorNode(
          nameGenerator.getRenameStrategy() == RenameStrategy.CONSISTENT, id);
      if (nameGenerator.getRenameStrategy() == RenameStrategy.CONSISTENT) {
        Map<String, String> entry = consistNameMap.get(callName);
        rename = entry.get(instanceId);
        if (rename == null) {
          rename = nameGenerator.getName(instanceId, name);
          entry.put(instanceId, rename);
        }
      } else {
        rename = nameGenerator.getName(instanceId, name);
      }

      parent.replaceChild(n, IR.string(rename));
      idGeneratorMap.put(rename, instanceId);

      compiler.reportCodeChange();
    }
  }

  /**
   * @return The serialize map of generators and their ids and their
   *     replacements.
   */
  public String getSerializedIdMappings() {
    StringBuilder sb = new StringBuilder();
    for (Map.Entry<String, Map<String, String>> replacements :
        idGeneratorMaps.entrySet()) {
      if (!replacements.getValue().isEmpty()) {
        sb.append("[");
        sb.append(replacements.getKey());
        sb.append("]\n\n");
        for (Map.Entry<String, String> replacement :
            replacements.getValue().entrySet()) {
          sb.append(replacement.getKey());
          sb.append(':');
          sb.append(replacement.getValue());
          sb.append("\n");
        }
        sb.append("\n");
      }
    }
    return sb.toString();
  }

  private Map<String, BiMap<String, String>> parsePreviousResults(
      String serializedMap) {

    //
    // The expected format looks like this:
    //
    // [generatorName]
    // someId:someFile:theLine:theColumn
    //
    //

    if (serializedMap == null || serializedMap.isEmpty()) {
      return Collections.emptyMap();
    }

    Map<String, BiMap<String, String>> resultMap = Maps.newHashMap();
    BufferedReader reader = new BufferedReader(new StringReader(serializedMap));
    BiMap<String, String> currentSectionMap = null;

    String line;
    int lineIndex = 0;
    try {
      while ((line = reader.readLine()) != null) {
        lineIndex++;
        if (line.isEmpty()) {
          continue;
        }
        if (line.charAt(0) == '[') {
          String currentSection = line.substring(1, line.length() - 1);
          currentSectionMap = resultMap.get(currentSection);
          if (currentSectionMap == null) {
            currentSectionMap = HashBiMap.create();
            resultMap.put(currentSection, currentSectionMap);
          } else {
            reportInvalidLine(line, lineIndex);
            return Collections.emptyMap();
          }
        } else {
          int split = line.indexOf(':');
          if (split != -1) {
            String name = line.substring(0, split);
            String location = line.substring(split + 1, line.length());
            currentSectionMap.put(name, location);
          } else {
            reportInvalidLine(line, lineIndex);
            return Collections.emptyMap();
          }
        }
      }
    } catch (IOException e) {
      JSError.make(INVALID_GENERATOR_ID_MAPPING, e.getMessage());
    }
    return resultMap;
  }

  private void reportInvalidLine(String line, int lineIndex) {
    JSError.make(INVALID_GENERATOR_ID_MAPPING,
        "line(" + line + "): " + lineIndex);
  }

  String getIdForGeneratorNode(boolean consistent, Node n) {
    Preconditions.checkState(n.isString());
    if (consistent) {
      return n.getString();
    } else {
      return n.getSourceFileName() + ':' + n.getLineno() + ":" + n.getCharno();
    }
  }
}
