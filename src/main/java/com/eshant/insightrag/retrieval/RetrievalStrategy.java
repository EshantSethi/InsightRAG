package com.eshant.insightrag.retrieval;

/**
 * The two retrieval configurations the eval compares head-to-head. Flipping this single value is the
 * whole "before vs after" experiment that the project's headline before/after table is built on.
 */
public enum RetrievalStrategy {

    /**
     * v0 baseline: pure dense vector search, top-k, no keyword arm, no fusion, no re-ranking.
     * Represents the typical naive RAG demo that looks fine until it meets exact-term or adversarial
     * queries.
     */
    NAIVE,

    /**
     * v1 improved: vector + BM25 keyword retrieval, combined by reciprocal-rank fusion, then
     * re-ranked. The configuration whose measured gains over {@link #NAIVE} the README reports.
     */
    HYBRID_RERANK
}
