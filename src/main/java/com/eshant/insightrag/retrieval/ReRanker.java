package com.eshant.insightrag.retrieval;

import java.util.List;

/**
 * Reorders a candidate list to put the most relevant chunks first.
 *
 * <p>Re-ranking is the precision-sharpening stage: retrieval (vector + keyword + fusion) is tuned for
 * <em>recall</em> — get the right chunk into the candidate set — while the re-ranker is tuned for
 * <em>precision@k</em>, making sure the best candidate ends up at rank 0 where the generator will
 * actually use it. Two implementations exist: an always-on deterministic one
 * ({@link DeterministicReRanker}) and an optional LLM-based one used when a real model is configured.
 */
public interface ReRanker {

    /**
     * Returns a re-ordered, re-scored copy of {@code candidates} (origin {@code RERANKED}), truncated
     * to {@code topK}.
     *
     * @param query      the user query, used to judge relevance
     * @param candidates the candidate chunks to reorder (typically the fused list)
     * @param topK       maximum number of results to keep
     */
    List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topK);
}
