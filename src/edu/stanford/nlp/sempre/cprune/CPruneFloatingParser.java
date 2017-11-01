package edu.stanford.nlp.sempre.cprune;

import java.util.List;

import edu.stanford.nlp.sempre.*;
import fig.basic.LogInfo;

/**
 * A parser that first tries to exploit the macro grammar and only fall back to full search when needed.
 */
public class CPruneFloatingParser extends FloatingParser {

  CollaborativePruner pruner;
  FloatingParser exploreParser;

  public CPruneFloatingParser(Spec spec) {
    super(spec);
    pruner = new CollaborativePruner();
    exploreParser = new FloatingParser(spec).setEarlyStopping(true, CollaborativePruner.opts.maxDerivations);
  }

  @Override
  public void onBeginDataGroup(int iter, int numIters, String group) {
    if (pruner.uidToCachedNeighbors == null) {
      pruner.customGrammar.init(grammar);
      pruner.loadNeighbors();
    }
    pruner.stats.reset(iter + "." + group);
  }

  @Override
  public ParserState newParserState(Params params, Example ex, boolean computeExpectedCounts) {
    return new CPruneFloatingParserState(this, params, ex, computeExpectedCounts);
  }

}

class CPruneFloatingParserState extends ParserState {

  CollaborativePruner pruner;

  public CPruneFloatingParserState(Parser parser, Params params, Example ex, boolean computeExpectedCounts) {
    super(parser, params, ex, computeExpectedCounts);
    pruner = ((CPruneFloatingParser) parser).pruner;
  }

  @Override
  public void infer() {
    LogInfo.begin_track("CPruneFloatingParser.infer()");
    boolean exploitSucceeds = exploit();
    if (computeExpectedCounts) {
      LogInfo.begin_track("Summary of Collaborative Pruning");
      LogInfo.logs("Exploit succeeds: " + exploitSucceeds);
      LogInfo.logs("Exploit success rate: " + pruner.stats.successfulExploit + "/" + pruner.stats.totalExploit);
      LogInfo.end_track();
    }
    // Explore only on the first training iteration
    if (pruner.stats.iter.equals("0.train") && computeExpectedCounts && !exploitSucceeds
        && (pruner.stats.totalExplore <= CollaborativePruner.opts.maxExplorationIters)) {
      explore();
      LogInfo.logs("Consistent pattern: " + pruner.getConsistentPattern(ex));
      LogInfo.logs("Explore success rate: " + pruner.stats.successfulExplore + "/" + pruner.stats.totalExplore);
    }
    LogInfo.end_track();
  }

  public void explore() {
    LogInfo.begin_track("Explore");
    pruner.initialize(ex, CollaborativePruner.Mode.EXPLORE);
    ParserState exploreParserState = ((CPruneFloatingParser) parser).exploreParser.newParserState(params, ex, computeExpectedCounts);
    exploreParserState.infer();
    predDerivations.clear();
    predDerivations.addAll(exploreParserState.predDerivations);
    expectedCounts = exploreParserState.expectedCounts;
    if (computeExpectedCounts) {
      for (Derivation deriv : predDerivations)
        pruner.updateConsistentPattern(parser.valueEvaluator, ex, deriv);
    }
    pruner.stats.totalExplore += 1;
    if (pruner.foundConsistentDerivation)
      pruner.stats.successfulExplore += 1;
    LogInfo.end_track();
  }

  public boolean exploit() {
    LogInfo.begin_track("Exploit");
    pruner.initialize(ex, CollaborativePruner.Mode.EXPLOIT);
    Grammar miniGrammar = new MiniGrammar(pruner.predictedRules);
    Parser exploitParser = new FloatingParser(new Parser.Spec(miniGrammar, parser.extractor, parser.executor, parser.valueEvaluator));
    ParserState exploitParserState = exploitParser.newParserState(params, ex, computeExpectedCounts);
    exploitParserState.infer();
    predDerivations.clear();
    predDerivations.addAll(exploitParserState.predDerivations);
    expectedCounts = exploitParserState.expectedCounts;
    if (computeExpectedCounts) {
      for (Derivation deriv : predDerivations)
        pruner.updateConsistentPattern(parser.valueEvaluator, ex, deriv);
    }
    boolean succeeds = pruner.foundConsistentDerivation;
    pruner.stats.totalExploit += 1;
    if (succeeds)
      pruner.stats.successfulExploit += 1;
    LogInfo.end_track();
    return succeeds;
  }
}

// ============================================================
// Helper classes
// ============================================================

class MiniGrammar extends Grammar {

  public MiniGrammar(List<Rule> rules) {
    this.rules.addAll(rules);
    if (CollaborativePruner.opts.verbose >= 2) {
      LogInfo.begin_track("MiniGrammar Rules");
      for (Rule rule : rules)
        LogInfo.logs("%s %s", rule, rule.isAnchored() ? "[A]" : "[F]");
      LogInfo.end_track();
    }
  }

}
