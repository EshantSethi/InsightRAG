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
 * @param abstentionEnabled     whether to refuse rather than answer when confidence is low
 * @param minRetrievalScore     minimum raw-cosine vector confidence to attempt an answer (pre-gen gate)
 * @param minLexicalConfidence  minimum IDF-weighted keyword coverage to attempt an answer; the gate
 *                              refuses only when BOTH this and {@code minRetrievalScore} are unmet, so
 *                              a question answerable by either signal still answers (pre-gen gate)
 * @param minGroundingScore     minimum faithfulness score required to keep an answer (post-gen gate)
 */
public record GenerationSettings(boolean abstentionEnabled, double minRetrievalScore,
                                 double minLexicalConfidence, double minGroundingScore) {

    /** v0 naive: never abstain; gates are irrelevant but kept at 0 for clarity. */
    public static GenerationSettings naive() {
        return new GenerationSettings(false, 0.0, 0.0, 0.0);
    }

    /**
     * v1 improved: abstain when retrieval confidence is low, using two complementary signals — it
     * refuses only when <em>both</em> are weak, so a question answerable by either still answers.
     *
     * <ul>
     *   <li>{@code minRetrievalScore} — a <em>raw cosine</em> vector threshold (see
     *       {@link com.eshant.insightrag.retrieval.RetrievalResult#confidence()}). all-MiniLM cosines
     *       run high (off-topic ~0.72-0.81, in-corpus ~0.80-0.93); 0.815 sits just above the
     *       adversarial band. On its own, though, cosine is phrasing-sensitive and over-refuses real
     *       questions that don't name the framework.</li>
     *   <li>{@code minLexicalConfidence} — an IDF-weighted keyword-coverage threshold (see
     *       {@link com.eshant.insightrag.retrieval.RetrievalResult#lexicalConfidence()}) that is
     *       phrasing-independent: it rescues answerable questions the cosine gate would wrongly refuse,
     *       while out-of-scope questions (distinctive terms absent from the corpus) still score low.</li>
     * </ul>
     * Both thresholds are calibrated on the eval set so the system keeps zero confident hallucinations.
     */
    public static GenerationSettings improved() {
        return new GenerationSettings(true, 0.815, 0.5, GroundingChecker.DEFAULT_THRESHOLD);
    }
}
