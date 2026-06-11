package com.eshant.insightrag.eval;

import java.util.List;

/**
 * The serializable result of an evaluation run: the two pipeline variants scored side by side. This
 * is the object behind the README's headline before/after table — it is both printed to the console
 * and written to {@code eval/results/} as JSON so the numbers are reproducible and citable.
 *
 * @param generatedAt ISO-8601 timestamp of the run
 * @param llmName     which LLM produced the answers (mock vs real Claude) — for honest labelling
 * @param caseCount   total number of evaluation cases
 * @param v0          the naive baseline variant
 * @param v1          the improved variant
 */
public record EvalReport(
        String generatedAt,
        String llmName,
        int caseCount,
        Variant v0,
        Variant v1) {

    /**
     * Aggregated scores for one pipeline variant plus the per-case detail.
     *
     * @param name             "v0-naive" / "v1-improved"
     * @param retrieval        averaged retrieval metrics over in-corpus cases
     * @param avgFaithfulness  mean rule-based grounding score over answered in-corpus cases
     * @param avgAnswerRelevance mean expected-keyword coverage over in-corpus cases (abstentions score 0)
     * @param avgLlmJudge      mean LLM-judge faithfulness over answered in-corpus cases, or NaN if mock
     * @param abstentionAccuracy fraction of cases where the answer-vs-abstain decision was correct
     * @param correctAnswers   in-corpus cases answered correctly (count)
     * @param correctAbstentions out-of-corpus cases correctly refused (count)
     * @param wrongAnswers     out-of-corpus cases answered anyway — confident hallucinations (count)
     * @param wrongAbstentions in-corpus cases wrongly refused — over-cautious (count)
     * @param cases            per-case breakdown
     */
    public record Variant(
            String name,
            RetrievalMetrics.Score retrieval,
            double avgFaithfulness,
            double avgAnswerRelevance,
            double avgLlmJudge,
            double abstentionAccuracy,
            int correctAnswers,
            int correctAbstentions,
            int wrongAnswers,
            int wrongAbstentions,
            List<CaseResult> cases) {
    }

    /**
     * One case's outcome under one variant.
     *
     * @param id               case id
     * @param type             IN_CORPUS / OUT_OF_CORPUS
     * @param abstained        whether the system refused
     * @param outcome          the four-way abstention classification
     * @param confidence       retrieval confidence (max raw cosine, pre-rerank) — what the gate keys off
     * @param groundingScore   rule-based faithfulness of the produced answer
     * @param answerRelevance  expected-keyword coverage
     * @param llmJudge         LLM-judge faithfulness, or NaN
     * @param retrievedSources document names of the retrieved chunks, in rank order
     * @param answer           the produced answer text (or abstention message)
     */
    public record CaseResult(
            String id,
            String type,
            boolean abstained,
            String outcome,
            double confidence,
            double groundingScore,
            double answerRelevance,
            double llmJudge,
            List<String> retrievedSources,
            String answer) {
    }
}
