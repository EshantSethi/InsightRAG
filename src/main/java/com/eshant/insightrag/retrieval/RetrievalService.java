package com.eshant.insightrag.retrieval;

import java.util.List;

/**
 * Orchestrates the retrieval pipeline behind a single {@link RetrievalStrategy} switch — the seam the
 * eval harness flips to produce the project's before/after comparison.
 *
 * <p><strong>Instantiable by design</strong>, like the indexes it composes: the eval builds one
 * service per pipeline (a NAIVE one over fixed-size chunks, a HYBRID_RERANK one over structure-aware
 * chunks) so the two configurations never share state. The live application wires one as a bean.
 *
 * <ul>
 *   <li>{@link RetrievalStrategy#NAIVE} — vector search only, top-k, with the configured min score.</li>
 *   <li>{@link RetrievalStrategy#HYBRID_RERANK} — vector and BM25 each fetch a wider candidate pool
 *       ({@code topK * candidateMultiplier}); the two lists are fused with RRF, then re-ranked down to
 *       topK.</li>
 * </ul>
 *
 * Every call returns a {@link RetrievalResult} carrying a {@link QueryTrace} of what happened.
 */
public class RetrievalService {

    private final VectorIndex vectorIndex;
    private final Bm25Index keywordIndex;
    private final ReRanker reRanker;
    private final RetrievalStrategy strategy;
    private final int topK;
    private final double minScore;
    private final int candidateMultiplier;

    public RetrievalService(VectorIndex vectorIndex,
                            Bm25Index keywordIndex,
                            ReRanker reRanker,
                            RetrievalStrategy strategy,
                            int topK,
                            double minScore,
                            int candidateMultiplier) {
        this.vectorIndex = vectorIndex;
        this.keywordIndex = keywordIndex;
        this.reRanker = reRanker;
        this.strategy = strategy;
        this.topK = topK;
        this.minScore = minScore;
        this.candidateMultiplier = Math.max(1, candidateMultiplier);
    }

    /** Retrieves using this service's configured strategy. */
    public RetrievalResult retrieve(String query) {
        return retrieve(query, strategy);
    }

    /** Retrieves using an explicit strategy (used by the API debug/compare mode). */
    public RetrievalResult retrieve(String query, RetrievalStrategy strategyOverride) {
        QueryTrace trace = new QueryTrace(query, strategyOverride);
        List<RetrievedChunk> results = switch (strategyOverride) {
            case NAIVE -> retrieveNaive(query, trace);
            case HYBRID_RERANK -> retrieveHybrid(query, trace);
        };
        return new RetrievalResult(results, trace, trace.confidence());
    }

    /**
     * The honest retrieval-confidence signal: the highest raw cosine score among vector hits, taken
     * before fusion/re-ranking rescore them. Vector hits arrive sorted by descending similarity, so the
     * first one is the max; 0 means nothing matched at all.
     */
    private static double topCosine(List<RetrievedChunk> vectorHits) {
        return vectorHits.isEmpty() ? 0.0 : vectorHits.get(0).score();
    }

    /** v0: pure dense vector search. */
    private List<RetrievedChunk> retrieveNaive(String query, QueryTrace trace) {
        long start = System.currentTimeMillis();
        List<RetrievedChunk> vectorHits = vectorIndex.search(query, topK, minScore);
        trace.addStage("vector", vectorHits.size(), System.currentTimeMillis() - start,
                "topK=" + topK + " minScore=" + minScore);
        trace.setConfidence(topCosine(vectorHits));
        return vectorHits;
    }

    /** v1: vector + BM25 -> RRF fusion -> re-rank. */
    private List<RetrievedChunk> retrieveHybrid(String query, QueryTrace trace) {
        int candidateK = topK * candidateMultiplier;

        long t0 = System.currentTimeMillis();
        // Use a relaxed min score for candidate gathering; precision is restored by fusion + rerank.
        List<RetrievedChunk> vectorHits = vectorIndex.search(query, candidateK, 0.0);
        trace.addStage("vector", vectorHits.size(), System.currentTimeMillis() - t0,
                "candidateK=" + candidateK);
        // Capture confidence from the RAW cosine candidates, before RRF/rerank rescore everything.
        trace.setConfidence(topCosine(vectorHits));

        long t1 = System.currentTimeMillis();
        List<RetrievedChunk> keywordHits = keywordIndex.search(query, candidateK);
        trace.addStage("keyword(bm25)", keywordHits.size(), System.currentTimeMillis() - t1,
                "candidateK=" + candidateK);

        long t2 = System.currentTimeMillis();
        List<RetrievedChunk> fused = ReciprocalRankFusion.fuse(ReciprocalRankFusion.DEFAULT_K,
                candidateK, List.of(vectorHits, keywordHits));
        trace.addStage("rrf-fusion", fused.size(), System.currentTimeMillis() - t2,
                "k=" + ReciprocalRankFusion.DEFAULT_K);

        long t3 = System.currentTimeMillis();
        List<RetrievedChunk> reranked = reRanker.rerank(query, fused, topK);
        trace.addStage("rerank", reranked.size(), System.currentTimeMillis() - t3,
                "deterministic, topK=" + topK);

        return reranked;
    }
}
