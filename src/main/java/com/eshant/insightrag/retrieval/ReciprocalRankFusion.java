package com.eshant.insightrag.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Merges several ranked result lists into one using Reciprocal Rank Fusion (RRF).
 *
 * <p>RRF scores each chunk by {@code Σ 1 / (k + rank)} over every list it appears in. Its key virtue
 * is that it fuses on <em>rank</em>, not score, so it needs no normalisation between retrievers whose
 * scores live on different scales — cosine similarity (0–1) from {@link VectorIndex} versus unbounded
 * BM25 from {@link Bm25Index}. A chunk ranked highly by both arms accumulates contributions from both
 * and rises to the top, which is exactly the "found semantically AND lexically" signal we trust most.
 *
 * <p>Reference: Cormack, Clarke &amp; Büttcher (2009), "Reciprocal Rank Fusion outperforms Condorcet
 * and individual rank learning methods."
 */
public final class ReciprocalRankFusion {

    /** Standard RRF dampening constant; 60 is the value from the original paper. */
    public static final int DEFAULT_K = 60;

    private ReciprocalRankFusion() {
    }

    /** Fuses the given lists with the default k and returns the top-{@code topK} {@code FUSED} results. */
    @SafeVarargs
    public static List<RetrievedChunk> fuse(int topK, List<RetrievedChunk>... lists) {
        return fuse(DEFAULT_K, topK, List.of(lists));
    }

    /**
     * Fuses ranked lists into one descending list of {@link RetrievedChunk.Origin#FUSED} results.
     *
     * @param k     dampening constant (higher = flatter contribution from rank position)
     * @param topK  maximum number of fused results to return
     * @param lists the per-retriever ranked lists to combine; each item's {@code rank()} is used
     */
    public static List<RetrievedChunk> fuse(int k, int topK, List<List<RetrievedChunk>> lists) {
        // Accumulate fused score per chunk id, keeping the chunk itself for the winner.
        Map<String, Double> fusedScore = new LinkedHashMap<>();
        Map<String, RetrievedChunk> byId = new LinkedHashMap<>();

        for (List<RetrievedChunk> list : lists) {
            for (RetrievedChunk rc : list) {
                String id = rc.chunk().id();
                fusedScore.merge(id, 1.0 / (k + rc.rank()), Double::sum);
                byId.putIfAbsent(id, rc);
            }
        }

        List<Map.Entry<String, Double>> ranked = new ArrayList<>(fusedScore.entrySet());
        ranked.sort(Comparator.<Map.Entry<String, Double>>comparingDouble(Map.Entry::getValue).reversed());

        List<RetrievedChunk> fused = new ArrayList<>(Math.min(topK, ranked.size()));
        for (int rank = 0; rank < ranked.size() && rank < topK; rank++) {
            Map.Entry<String, Double> entry = ranked.get(rank);
            RetrievedChunk original = byId.get(entry.getKey());
            fused.add(original.reScored(entry.getValue(), RetrievedChunk.Origin.FUSED, rank));
        }
        return fused;
    }
}
