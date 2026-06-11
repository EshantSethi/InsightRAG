package com.eshant.insightrag.retrieval;

import com.eshant.insightrag.ingestion.Chunk;

/**
 * A {@link Chunk} surfaced by retrieval, annotated with how it was found.
 *
 * <p>Every retriever (vector, keyword, fusion, rerank) returns these so the rest of the pipeline —
 * and especially the eval harness and per-query trace — can explain <em>why</em> a chunk was chosen
 * and with what confidence. Keeping provenance ({@link Origin}) on each item is what makes the
 * hybrid pipeline auditable rather than a black box.
 *
 * @param chunk the underlying retrievable unit (carries id/source/section for citation)
 * @param score relevance score; meaning depends on {@link Origin} (cosine for VECTOR, BM25 for
 *              KEYWORD, fused RRF score for FUSED, rerank score for RERANKED)
 * @param origin which stage produced this result
 * @param rank   0-based position in the result list this chunk came from
 */
public record RetrievedChunk(Chunk chunk, double score, Origin origin, int rank) {

    /** Which retrieval stage produced a result — recorded for tracing and eval. */
    public enum Origin {
        /** Dense semantic match from the vector store. */
        VECTOR,
        /** Sparse lexical match from the BM25 keyword index. */
        KEYWORD,
        /** Combined result after reciprocal-rank fusion of multiple retrievers. */
        FUSED,
        /** Result after a re-ranking pass reordered the candidate set. */
        RERANKED
    }

    /** Returns a copy with a new origin and rank, preserving chunk and score (used when re-staging). */
    public RetrievedChunk reStaged(Origin newOrigin, int newRank) {
        return new RetrievedChunk(chunk, score, newOrigin, newRank);
    }

    /** Returns a copy with a new score, origin, and rank (used by fusion/rerank that rescore). */
    public RetrievedChunk reScored(double newScore, Origin newOrigin, int newRank) {
        return new RetrievedChunk(chunk, newScore, newOrigin, newRank);
    }
}
