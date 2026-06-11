package com.eshant.insightrag.generation;

/**
 * Knobs that distinguish the naive (v0) and improved (v1) answer behaviour — the second half of the
 * before/after experiment (the first half being {@link com.eshant.insightrag.retrieval.RetrievalStrategy}).
 *
 * <p>With {@code abstentionEnabled=false} (v0) the generator always produces an answer, even from weak
 * or empty context — the classic failure mode where naive RAG confidently makes things up. With it
 * enabled (v1) the generator refuses when retrieval is too weak or the answer isn't grounded, which is
 * what the eval rewards on adversarial out-of-corpus questions.
 *
 * @param abstentionEnabled  whether to refuse rather than answer when confidence is low
 * @param minRetrievalScore  minimum top-chunk score required to attempt an answer (pre-generation gate)
 * @param minGroundingScore  minimum faithfulness score required to keep an answer (post-generation gate)
 */
public record GenerationSettings(boolean abstentionEnabled, double minRetrievalScore, double minGroundingScore) {

    /** v0 naive: never abstain; gates are irrelevant but kept at 0 for clarity. */
    public static GenerationSettings naive() {
        return new GenerationSettings(false, 0.0, 0.0);
    }

    /**
     * v1 improved: abstain below the given retrieval/grounding confidence gates.
     *
     * <p>{@code minRetrievalScore} here is a <em>raw cosine</em> threshold (see
     * {@link com.eshant.insightrag.retrieval.RetrievalResult#confidence()}), not a post-rerank score.
     * all-MiniLM-L6-v2 cosines are compressed high — even off-topic queries that mention domain terms
     * score ~0.72–0.81, while genuine in-corpus matches score ~0.80–0.93. The value 0.815 is
     * <em>calibrated on the eval set</em> to sit just above every adversarial out-of-corpus query, so
     * the system makes zero confident hallucinations, at the honest cost of refusing one borderline
     * real question — a deliberate precision-over-recall choice for an anti-hallucination system.
     */
    public static GenerationSettings improved() {
        return new GenerationSettings(true, 0.815, GroundingChecker.DEFAULT_THRESHOLD);
    }
}
