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

class PhaseOptimizer implements CompilerPass  {
  @VisibleForTesting() final static List<String> OPTIMAL_ORDER = ImmutableList.of("deadAssignmentsElimination", "inlineFunctions", "removeUnusedPrototypeProperties", "removeUnreachableCode", "removeUnusedVars", "minimizeExitPoints", "inlineVariables", "collapseObjectLiterals", "peepholeOptimizations");
  final static int MAX_LOOPS = 100;
  final static String OPTIMIZE_LOOP_ERROR = "Fixed point loop exceeded the maximum number of iterations.";
  final private static Logger logger = Logger.getLogger(PhaseOptimizer.class.getName());
  final private List<CompilerPass> passes = Lists.newArrayList();
  final private AbstractCompiler compiler;
  final private PerformanceTracker tracker;
  final private CodeChangeHandler.RecentChange recentChange = new CodeChangeHandler.RecentChange();
  private boolean loopMutex = false;
  private Tracer currentTracer = null;
  private String currentPassName = null;
  private PassFactory sanityCheck = null;
  private double progress = 0.0D;
  private double progressStep = 0.0D;
  private static boolean randomizeLoops = false;
  private static List<List<String>> loopsRun = Lists.newArrayList();
  final private ProgressRange progressRange;
  PhaseOptimizer(AbstractCompiler compiler, PerformanceTracker tracker, ProgressRange progressRange) {
    super();
    this.compiler = compiler;
    this.tracker = tracker;
    this.progressRange = progressRange;
    compiler.addChangeHandler(recentChange);
  }
  static List<List<String>> getLoopsRun() {
    return loopsRun;
  }
  Loop addFixedPointLoop() {
    Loop loop = new Loop();
    passes.add(loop);
    return loop;
  }
  private Tracer newTracer(String passName) {
    String comment = passName + (recentChange.hasCodeChanged() ? " on recently changed AST" : "");
    if(tracker != null) {
      tracker.recordPassStart(passName);
    }
    return new Tracer("JSCompiler", comment);
  }
  private boolean hasHaltingErrors() {
    return compiler.hasHaltingErrors();
  }
  void addOneTimePass(PassFactory factory) {
    passes.add(new NamedPass(factory));
  }
  static void clearLoopsRun() {
    loopsRun.clear();
  }
  void consume(List<PassFactory> factories) {
    Loop currentLoop = new Loop();
    boolean isCurrentLoopPopulated = false;
    for (PassFactory factory : factories) {
      if(factory.isOneTimePass()) {
        if(isCurrentLoopPopulated) {
          passes.add(currentLoop);
          currentLoop = new Loop();
          isCurrentLoopPopulated = false;
        }
        addOneTimePass(factory);
      }
      else {
        currentLoop.addLoopedPass(factory);
        isCurrentLoopPopulated = true;
      }
    }
    if(isCurrentLoopPopulated) {
      passes.add(currentLoop);
    }
  }
  private void endPass(Node externs, Node root) {
    Preconditions.checkState(currentTracer != null && currentPassName != null);
    String passToCheck = currentPassName;
    try {
      if(progressRange == null) {
        compiler.setProgress(-1, currentPassName);
      }
      else {
        progress += progressStep;
        compiler.setProgress(progress, currentPassName);
      }
      stopTracer(currentTracer, currentPassName);
      currentPassName = null;
      currentTracer = null;
      maybeSanityCheck(externs, root);
    }
    catch (Exception e) {
      throw new RuntimeException("Sanity check failed for " + passToCheck, e);
    }
  }
  void maybeSanityCheck(Node externs, Node root) {
    if(sanityCheck != null) {
      sanityCheck.create(compiler).process(externs, root);
    }
  }
  @Override() public void process(Node externs, Node root) {
    progress = 0.0D;
    progressStep = 0.0D;
    if(progressRange != null) {
      double var_861 = progressRange.initialValue;
      progressStep = (progressRange.maxValue - var_861) / passes.size();
      progress = progressRange.initialValue;
    }
    for (CompilerPass pass : passes) {
      pass.process(externs, root);
      if(hasHaltingErrors()) {
        return ;
      }
    }
  }
  static void randomizeLoops() {
    randomizeLoops = true;
  }
  void setSanityCheck(PassFactory sanityCheck) {
    this.sanityCheck = sanityCheck;
  }
  private void startPass(String passName) {
    Preconditions.checkState(currentTracer == null && currentPassName == null);
    currentPassName = passName;
    currentTracer = newTracer(passName);
  }
  private void stopTracer(Tracer t, String passName) {
    long result = t.stop();
    if(tracker != null) {
      tracker.recordPassStop(passName, result);
    }
  }
  
  class Loop implements CompilerPass  {
    final private List<NamedPass> myPasses = Lists.newArrayList();
    final private Set<String> myNames = Sets.newHashSet();
    private List<String> getPassOrder() {
      List<String> order = Lists.newArrayList();
      for (NamedPass pass : myPasses) {
        order.add(pass.name);
      }
      return order;
    }
    void addLoopedPass(PassFactory factory) {
      String name = factory.getName();
      Preconditions.checkArgument(!myNames.contains(name), "Already a pass with name \'%s\' in this loop", name);
      myNames.add(name);
      myPasses.add(new NamedPass(factory));
    }
    private void optimizePasses() {
      List<NamedPass> optimalPasses = Lists.newArrayList();
      for (String passName : OPTIMAL_ORDER) {
        for (NamedPass pass : myPasses) {
          if(pass.name.equals(passName)) {
            optimalPasses.add(pass);
            break ;
          }
        }
      }
      myPasses.removeAll(optimalPasses);
      myPasses.addAll(optimalPasses);
    }
    @Override() public void process(Node externs, Node root) {
      Preconditions.checkState(!loopMutex, "Nested loops are forbidden");
      loopMutex = true;
      if(randomizeLoops) {
        randomizePasses();
      }
      else {
        optimizePasses();
      }
      Set<NamedPass> madeChanges = new HashSet<NamedPass>();
      Set<NamedPass> runInPrevIter = new HashSet<NamedPass>();
      State s = State.RUN_PASSES_NOT_RUN_IN_PREV_ITER;
      boolean lastIterMadeChanges;
      int count = 0;
      try {
        while(true){
          if(count++ > MAX_LOOPS) {
            compiler.throwInternalError(OPTIMIZE_LOOP_ERROR, null);
          }
          lastIterMadeChanges = false;
          for (NamedPass pass : myPasses) {
            recentChange.reset();
            if((s == State.RUN_PASSES_NOT_RUN_IN_PREV_ITER && !runInPrevIter.contains(pass)) || (s == State.RUN_PASSES_THAT_CHANGED_STH_IN_PREV_ITER && madeChanges.contains(pass))) {
              pass.process(externs, root);
              runInPrevIter.add(pass);
              if(hasHaltingErrors()) {
                return ;
              }
              else 
                if(recentChange.hasCodeChanged()) {
                  madeChanges.add(pass);
                  lastIterMadeChanges = true;
                }
                else {
                  madeChanges.remove(pass);
                }
            }
            else {
              runInPrevIter.remove(pass);
            }
          }
          if(s == State.RUN_PASSES_NOT_RUN_IN_PREV_ITER) {
            if(lastIterMadeChanges) {
              s = State.RUN_PASSES_THAT_CHANGED_STH_IN_PREV_ITER;
            }
            else {
              return ;
            }
          }
          else 
            if(!lastIterMadeChanges) {
              s = State.RUN_PASSES_NOT_RUN_IN_PREV_ITER;
            }
        }
      }
      finally {
        loopMutex = false;
      }
    }
    private void randomizePasses() {
      Collections.shuffle(myPasses);
    }
  }
  
  class NamedPass implements CompilerPass  {
    final String name;
    final private PassFactory factory;
    NamedPass(PassFactory factory) {
      super();
      this.name = factory.getName();
      this.factory = factory;
    }
    @Override() public void process(Node externs, Node root) {
      logger.fine(name);
      startPass(name);
      factory.create(compiler).process(externs, root);
      endPass(externs, root);
    }
  }
  
  static class ProgressRange  {
    final public double initialValue;
    final public double maxValue;
    public ProgressRange(double initialValue, double maxValue) {
      super();
      this.initialValue = initialValue;
      this.maxValue = maxValue;
    }
  }
  enum State {
    RUN_PASSES_NOT_RUN_IN_PREV_ITER(),

    RUN_PASSES_THAT_CHANGED_STH_IN_PREV_ITER(),

  ;
  private State() {
  }
  }
}