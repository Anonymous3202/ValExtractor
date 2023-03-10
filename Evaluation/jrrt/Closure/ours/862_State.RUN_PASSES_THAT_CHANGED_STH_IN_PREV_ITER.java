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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.javascript.rhino.Node;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Optimizes the order of compiler passes.
 * @author nicksantos@google.com (Nick Santos)
 */
class PhaseOptimizer implements CompilerPass {

  // This ordering is computed offline by running with compute_phase_ordering.
  @VisibleForTesting
  static final List<String> OPTIMAL_ORDER = ImmutableList.of(
     "deadAssignmentsElimination",
     "inlineFunctions",
     "removeUnusedPrototypeProperties",
     "removeUnreachableCode",
     "removeUnusedVars",
     "minimizeExitPoints",
     "inlineVariables",
     "collapseObjectLiterals",
     "peepholeOptimizations");

  static final int MAX_LOOPS = 100;
  static final String OPTIMIZE_LOOP_ERROR =
      "Fixed point loop exceeded the maximum number of iterations.";

  // Only used by Loop/process, but enum types can't be local
  enum State {
    RUN_PASSES_NOT_RUN_IN_PREV_ITER,
    RUN_PASSES_THAT_CHANGED_STH_IN_PREV_ITER
  }

  private static final Logger logger =
      Logger.getLogger(PhaseOptimizer.class.getName());

  private final List<CompilerPass> passes = Lists.newArrayList();

  private final AbstractCompiler compiler;
  private final PerformanceTracker tracker;
  private final CodeChangeHandler.RecentChange recentChange =
      new CodeChangeHandler.RecentChange();
  private boolean loopMutex = false;
  private Tracer currentTracer = null;
  private String currentPassName = null;
  private PassFactory sanityCheck = null;

  private double progress = 0.0;
  private double progressStep = 0.0;

  // The following static properties are only used for computing optimal
  // phase orderings. They should not be touched by normal compiler runs.
  private static boolean randomizeLoops = false;
  private static List<List<String>> loopsRun = Lists.newArrayList();

  private final ProgressRange progressRange;

  /**
   * @param compiler the compiler that owns/creates this.
   * @param tracker an optional performance tracker
   * @param progressRange the progress range for the process function or null
   * if progress should not be reported.
   */
  PhaseOptimizer(AbstractCompiler compiler, PerformanceTracker tracker,
      ProgressRange progressRange) {
    this.compiler = compiler;
    this.tracker = tracker;
    this.progressRange = progressRange;
    compiler.addChangeHandler(recentChange);
  }

  /**
   * Randomizes loops. This should only be used when computing optimal phase
   * orderings.
   */
  static void randomizeLoops() {
    randomizeLoops = true;
  }

  /**
   * Get the phase ordering of loops during this run.
   * Returns an empty list when the loops are not randomized.
   */
  static List<List<String>> getLoopsRun() {
    return loopsRun;
  }

  /**
   * Clears the phase ordering of loops during this run.
   */
  static void clearLoopsRun() {
    loopsRun.clear();
  }

  /**
   * Add the passes generated by the given factories to the compile sequence.
   *
   * Automatically pulls multi-run passes into fixed point loops. If there
   * are 1 or more multi-run passes in a row, they will run together in
   * the same fixed point loop. The passes will run until they are finished
   * making changes.
   *
   * The PhaseOptimizer is free to tweak the order and frequency of multi-run
   * passes in a fixed-point loop.
   */
  void consume(List<PassFactory> factories) {
    Loop currentLoop = new Loop();
    boolean isCurrentLoopPopulated = false;
    for (PassFactory factory : factories) {
      if (factory.isOneTimePass()) {
        if (isCurrentLoopPopulated) {
          passes.add(currentLoop);
          currentLoop = new Loop();
          isCurrentLoopPopulated = false;
        }
        addOneTimePass(factory);
      } else {
        currentLoop.addLoopedPass(factory);
        isCurrentLoopPopulated = true;
      }
    }

    if (isCurrentLoopPopulated) {
      passes.add(currentLoop);
    }
  }

  /**
   * Add the pass generated by the given factory to the compile sequence.
   * This pass will be run once.
   */
  void addOneTimePass(PassFactory factory) {
    passes.add(new NamedPass(factory));
  }

  /**
   * Add a loop to the compile sequence. This loop will continue running
   * until the AST stops changing.
   * @return The loop structure. Pass suppliers should be added to the loop.
   */
  Loop addFixedPointLoop() {
    Loop loop = new Loop();
    passes.add(loop);
    return loop;
  }

  /**
   * Adds a sanity checker to be run after every pass. Intended for development.
   */
  void setSanityCheck(PassFactory sanityCheck) {
    this.sanityCheck = sanityCheck;
  }

  /**
   * Run all the passes in the optimizer.
   */
  @Override
  public void process(Node externs, Node root) {
    progress = 0.0;
    progressStep = 0.0;
    if (progressRange != null) {
      progressStep = (progressRange.maxValue - progressRange.initialValue)
          / passes.size();
      progress = progressRange.initialValue;
    }

    for (CompilerPass pass : passes) {
      pass.process(externs, root);
      if (hasHaltingErrors()) {
        return;
      }
    }
  }

  /**
   * Marks the beginning of a pass.
   */
  private void startPass(String passName) {
    Preconditions.checkState(currentTracer == null && currentPassName == null);
    currentPassName = passName;
    currentTracer = newTracer(passName);
  }

  /**
   * Marks the end of a pass.
   */
  private void endPass(Node externs, Node root) {
    Preconditions.checkState(currentTracer != null && currentPassName != null);

    String passToCheck = currentPassName;
    try {
      if (progressRange == null) {
        compiler.setProgress(-1, currentPassName);
      } else {
        progress += progressStep;
        compiler.setProgress(progress, currentPassName);
      }
      stopTracer(currentTracer, currentPassName);
      currentPassName = null;
      currentTracer = null;

      maybeSanityCheck(externs, root);
    } catch (Exception e) {
      // TODO(johnlenz): Remove this once the normalization checks report
      // errors instead of exceptions.
      throw new RuntimeException("Sanity check failed for " + passToCheck, e);
    }
  }

  /**
   * Runs the sanity check if it is available.
   */
  void maybeSanityCheck(Node externs, Node root) {
    if (sanityCheck != null) {
      sanityCheck.create(compiler).process(externs, root);
    }
  }

  private boolean hasHaltingErrors() {
    return compiler.hasHaltingErrors();
  }

  /**
   * Returns a new tracer for the given pass name.
   */
  private Tracer newTracer(String passName) {
    String comment = passName +
        (recentChange.hasCodeChanged() ? " on recently changed AST" : "");
    if (tracker != null) {
      tracker.recordPassStart(passName);
    }
    return new Tracer("JSCompiler", comment);
  }

  private void stopTracer(Tracer t, String passName) {
    long result = t.stop();
    if (tracker != null) {
      tracker.recordPassStop(passName, result);
    }
  }

  /**
   * A single compiler pass.
   */
  class NamedPass implements CompilerPass {
    final String name;
    private final PassFactory factory;

    NamedPass(PassFactory factory) {
      this.name = factory.getName();
      this.factory = factory;
    }

    @Override
    public void process(Node externs, Node root) {
      logger.fine(name);
      startPass(name);
      // Delay the creation of the actual pass until *after* all previous passes
      // have been processed.
      // Some precondition checks rely on this, eg, in CoalesceVariableNames.
      factory.create(compiler).process(externs, root);
      endPass(externs, root);
    }
  }

  /**
   * Runs a set of compiler passes until they reach a fixed point.
   *
   * Notice that this is a non-static class, because it includes the closure
   * of PhaseOptimizer.
   */
  class Loop implements CompilerPass {
    private final List<NamedPass> myPasses = Lists.newArrayList();
    private final Set<String> myNames = Sets.newHashSet();

    void addLoopedPass(PassFactory factory) {
      String name = factory.getName();
      Preconditions.checkArgument(!myNames.contains(name),
          "Already a pass with name '%s' in this loop", name);
      myNames.add(name);
      myPasses.add(new NamedPass(factory));
    }

    /**
     * Gets the pass names, in order.
     */
    private List<String> getPassOrder() {
      List<String> order = Lists.newArrayList();
      for (NamedPass pass : myPasses) {
        order.add(pass.name);
      }
      return order;
    }

    @Override
    public void process(Node externs, Node root) {
      Preconditions.checkState(!loopMutex, "Nested loops are forbidden");
      loopMutex = true;
      if (randomizeLoops) {
        randomizePasses();
      } else {
        optimizePasses();
      }

      // Contains a pass iff it made changes the last time it was run.
      Set<NamedPass> madeChanges = new HashSet<NamedPass>();
      // Contains a pass iff it was run during the last inner loop.
      Set<NamedPass> runInPrevIter = new HashSet<NamedPass>();
      State s = State.RUN_PASSES_NOT_RUN_IN_PREV_ITER;
      boolean lastIterMadeChanges;
      int count = 0;

      try {
        while (true) {
          if (count++ > MAX_LOOPS) {
            compiler.throwInternalError(OPTIMIZE_LOOP_ERROR, null);
          }
          lastIterMadeChanges = false;
          for (NamedPass pass : myPasses) {
            recentChange.reset();
            State var_862 = State.RUN_PASSES_THAT_CHANGED_STH_IN_PREV_ITER;
			if ((s == State.RUN_PASSES_NOT_RUN_IN_PREV_ITER &&
                !runInPrevIter.contains(pass)) ||
                (s == var_862 &&
                    madeChanges.contains(pass))) {
              pass.process(externs, root);
              runInPrevIter.add(pass);
              if (hasHaltingErrors()) {
                return;
              } else if (recentChange.hasCodeChanged()) {
                madeChanges.add(pass);
                lastIterMadeChanges = true;
              } else {
                madeChanges.remove(pass);
              }
            } else {
              runInPrevIter.remove(pass);
            }
          }
          if (s == State.RUN_PASSES_NOT_RUN_IN_PREV_ITER) {
            if (lastIterMadeChanges) {
              s = State.RUN_PASSES_THAT_CHANGED_STH_IN_PREV_ITER;
            } else {
              return;
            }
          } else if (!lastIterMadeChanges) {
            s = State.RUN_PASSES_NOT_RUN_IN_PREV_ITER;
          }
        }
      } finally {
        loopMutex = false;
      }
    }

    /** Re-arrange the passes in a random order. */
    private void randomizePasses() {
      Collections.shuffle(myPasses);
    }

    /** Re-arrange the passes in an optimal order. */
    private void optimizePasses() {
      // It's important that this ordering is deterministic, so that
      // multiple compiles with the same input produce exactly the same
      // results.
      //
      // To do this, grab any passes we recognize, and move them to the end
      // in an "optimal" order.
      List<NamedPass> optimalPasses = Lists.newArrayList();
      for (String passName : OPTIMAL_ORDER) {
        for (NamedPass pass : myPasses) {
          if (pass.name.equals(passName)) {
            optimalPasses.add(pass);
            break;
          }
        }
      }

      myPasses.removeAll(optimalPasses);
      myPasses.addAll(optimalPasses);
    }
  }

  static class ProgressRange {
    public final double initialValue;
    public final double maxValue;

    public ProgressRange(double initialValue, double maxValue) {
      this.initialValue = initialValue;
      this.maxValue = maxValue;
    }
  }
}
