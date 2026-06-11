package com.eshant.insightrag.eval;

import com.eshant.insightrag.retrieval.RetrievedChunk;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Standard information-retrieval metrics, scored at <em>source-document</em> granularity (the eval's
 * ground truth lists document names, and a document is split across several chunks). These are the
 * deterministic, LLM-free half of the before/after story — they measure purely whether the right
 * documents were surfaced, which is exactly where hybrid+rerank (v1) is expected to beat naive
 * vector-only retrieval (v0).
 *
 * <ul>
 *   <li><b>precision@k</b> — fraction of returned chunks that come from an expected document;</li>
 *   <li><b>recall@k</b> — fraction of expected documents that appear anywhere in the results;</li>
 *   <li><b>hit-rate</b> — 1 if at least one returned chunk is from an expected document, else 0;</li>
 *   <li><b>reciprocal rank</b> — 1/(rank of the first relevant chunk), 0 if none; its mean is MRR.</li>
 * </ul>
 */
public final class RetrievalMetrics {

    private RetrievalMetrics() {
    }

    /** Per-case retrieval scores; averaging these across cases gives the reported aggregates. */
    public record Score(double precision, double recall, double hitRate, double reciprocalRank) {
    }

    /**
     * Scores one query's results against its expected source documents. Cases with no expected
     * sources (adversarial out-of-corpus) are not meaningful here and should be filtered out by the
     * caller before aggregating.
     */
    public static Score score(List<RetrievedChunk> retrieved, List<String> expectedSources) {
        Set<String> expected = new HashSet<>(expectedSources);
        if (expected.isEmpty() || retrieved.isEmpty()) {
            return new Score(0, 0, 0, 0);
        }

        int relevantRetrieved = 0;
        double reciprocalRank = 0.0;
        Set<String> matchedExpected = new HashSet<>();
        for (int i = 0; i < retrieved.size(); i++) {
            String source = retrieved.get(i).chunk().source();
            if (expected.contains(source)) {
                relevantRetrieved++;
                matchedExpected.add(source);
                if (reciprocalRank == 0.0) {
                    reciprocalRank = 1.0 / (i + 1); // first relevant hit only
                }
            }
        }

        double precision = (double) relevantRetrieved / retrieved.size();
        double recall = (double) matchedExpected.size() / expected.size();
        double hitRate = relevantRetrieved > 0 ? 1.0 : 0.0;
        return new Score(precision, recall, hitRate, reciprocalRank);
    }

    /** Averages a list of per-case scores into a single aggregate (precision, recall, hit-rate, MRR). */
    public static Score mean(List<Score> scores) {
        if (scores.isEmpty()) {
            return new Score(0, 0, 0, 0);
        }
        double p = 0, r = 0, h = 0, rr = 0;
        for (Score s : scores) {
            p += s.precision();
            r += s.recall();
            h += s.hitRate();
            rr += s.reciprocalRank();
        }
        int n = scores.size();
        return new Score(p / n, r / n, h / n, rr / n);
    }
}
