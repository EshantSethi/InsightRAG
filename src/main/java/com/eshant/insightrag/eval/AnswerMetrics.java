package com.eshant.insightrag.eval;

import java.util.List;
import java.util.Locale;

/**
 * Answer-quality measures for the eval. Faithfulness/groundedness already rides along on each
 * {@link com.eshant.insightrag.generation.Answer} (the rule-based grounding score); this class adds
 * the two measures the harness computes from the labelled dataset:
 *
 * <ul>
 *   <li><b>answer relevance</b> — did the answer actually contain the ground-truth fact(s)? Measured
 *       as the fraction of a case's expected keywords that appear in the answer text. A wrongful
 *       abstention scores 0 here, so over-cautiousness is penalised, not rewarded.</li>
 *   <li><b>abstention correctness</b> — was the decision to answer-vs-refuse right? Classified into
 *       four outcomes so the report can show, separately, naive RAG's confident wrong answers
 *       (the headline failure mode) and the improved pipeline's correct refusals.</li>
 * </ul>
 */
public final class AnswerMetrics {

    private AnswerMetrics() {
    }

    /** The four ways the answer-or-abstain decision can land, relative to ground truth. */
    public enum AbstentionOutcome {
        /** In-corpus question, system answered — correct. */
        CORRECT_ANSWER,
        /** Out-of-corpus question, system abstained — correct (the anti-hallucination win). */
        CORRECT_ABSTENTION,
        /** Out-of-corpus question, system answered anyway — a confident hallucination (v0's failure). */
        WRONG_ANSWER,
        /** In-corpus question, system abstained — over-cautious, a real answer was available. */
        WRONG_ABSTENTION;

        public boolean isCorrect() {
            return this == CORRECT_ANSWER || this == CORRECT_ABSTENTION;
        }
    }

    /** Classifies the answer/abstain decision against whether the case should have been refused. */
    public static AbstentionOutcome classifyAbstention(boolean shouldAbstain, boolean abstained) {
        if (shouldAbstain) {
            return abstained ? AbstentionOutcome.CORRECT_ABSTENTION : AbstentionOutcome.WRONG_ANSWER;
        }
        return abstained ? AbstentionOutcome.WRONG_ABSTENTION : AbstentionOutcome.CORRECT_ANSWER;
    }

    /**
     * Fraction of {@code expectedKeywords} that appear (case-insensitively) in {@code answerText}.
     * Returns 1.0 when a case has no expected keywords (nothing to satisfy). An abstention message
     * naturally scores 0 because it contains none of the facts.
     */
    public static double keywordCoverage(String answerText, List<String> expectedKeywords) {
        if (expectedKeywords == null || expectedKeywords.isEmpty()) {
            return 1.0;
        }
        String haystack = answerText.toLowerCase(Locale.ROOT);
        int found = 0;
        for (String kw : expectedKeywords) {
            if (haystack.contains(kw.toLowerCase(Locale.ROOT))) {
                found++;
            }
        }
        return (double) found / expectedKeywords.size();
    }

    /** Mean of a list of doubles (0 for an empty list). Small helper for aggregating per-case scores. */
    public static double mean(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        double sum = 0.0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.size();
    }
}
