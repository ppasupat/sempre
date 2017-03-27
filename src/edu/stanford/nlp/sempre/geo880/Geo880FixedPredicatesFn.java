package edu.stanford.nlp.sempre.geo880;

import java.util.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.NaiveKnowledgeGraph.KnowledgeGraphTriple;
import fig.basic.*;

/**
 * Similar to LexiconFn, but list all binaries and unaries in the Geo880 domain.
 * 
 * Assume a fixed knowledge base.
 *
 * @author ppasupat
 */
public class Geo880FixedPredicatesFn extends SemanticFn {
  public static class Options {
    @Option public int verbose = 0;
  }
  public static Options opts = new Options();

  public enum FuzzyMatchFnMode { UNARY, BINARY };
  private FuzzyMatchFnMode mode;

  public void init(LispTree tree) {
    super.init(tree);
    for (int i = 1; i < tree.children.size(); i++) {
      String value = tree.child(i).value;
      if ("unary".equals(value)) this.mode = FuzzyMatchFnMode.UNARY;
      else if ("binary".equals(value)) this.mode = FuzzyMatchFnMode.BINARY;
      else throw new RuntimeException("Invalid argument: " + value);
    }
  }
  
  @Override
  public DerivationStream call(Example ex, Callable c) {
    return new LazyFuzzyMatchFnDerivs(ex, c, mode);
  }

  // ============================================================
  // Derivation Stream
  // ============================================================

  public static class LazyFuzzyMatchFnDerivs extends MultipleDerivationStream {
    final Example ex;
    final NaiveKnowledgeGraph graph;
    final Callable c;
    final FuzzyMatchFnMode mode;

    int index = 0;
    List<Formula> formulas;

    public LazyFuzzyMatchFnDerivs(Example ex, Callable c, FuzzyMatchFnMode mode) {
      this.ex = ex;
      this.graph = (NaiveKnowledgeGraph) ex.context.graph;
      this.c = c;
      this.mode = mode;
      if (opts.verbose >= 2)
        LogInfo.logs("FuzzyMatchFn[%s].call", this.mode);
    }

    @Override
    public Derivation createDerivation() {
      // Compute the formulas if not computed yet
      if (formulas == null) {
        if (mode == FuzzyMatchFnMode.BINARY)
          populateBinaries();
        else if (mode == FuzzyMatchFnMode.UNARY)
          populateUnaries();
      }

      // Use the next formula to create a derivation
      if (index >= formulas.size()) return null;
      Formula formula = formulas.get(index++);
      SemType type = TypeInference.inferType(formula);

      return new Derivation.Builder()
          .withCallable(c)
          .formula(formula)
          .type(type)
          .createDerivation();
    }
    
    // Cache the list of binary formulas for the GeoQuery database
    static List<Formula> BINARIES;
    
    private void populateBinaries() {
      if (BINARIES == null) {
        Set<NameValue> binaryNames = new HashSet<>();
        for (KnowledgeGraphTriple triple : graph.triples) {
          NameValue relation = (NameValue) triple.r;
          if (!(CanonicalNames.TYPE.equals(relation.id))) {
            binaryNames.add(relation);
          }
        }
        BINARIES = new ArrayList<>();
        for (NameValue binaryName : binaryNames) {
          BINARIES.add(new ValueFormula<>(binaryName));
        }
      }
      formulas = BINARIES;
    }
    
    // Cache the list of unary formulas for the GeoQuery database
    static List<Formula> UNARIES;
    
    private void populateUnaries() {
      if (UNARIES == null) {
        Set<NameValue> typeNames = new HashSet<>();
        for (KnowledgeGraphTriple triple : graph.triples) {
          NameValue relation = (NameValue) triple.r;
          if (CanonicalNames.TYPE.equals(relation.id)) {
            typeNames.add((NameValue) triple.e2);
          } 
        }
        UNARIES = new ArrayList<>();
        Formula typeNameFormula = new ValueFormula<>(new NameValue(CanonicalNames.TYPE));
        for (NameValue typeName : typeNames) {
          UNARIES.add(new JoinFormula(typeNameFormula, new ValueFormula<>(typeName)));
        }
      }
      formulas = UNARIES;
    }

  }

}
