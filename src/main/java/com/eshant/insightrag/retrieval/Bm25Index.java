package com.eshant.insightrag.retrieval;

import com.eshant.insightrag.ingestion.Chunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Sparse / lexical retriever using the Okapi BM25 ranking function over an in-memory inverted index.
 *
 * <p>This is the {@code KEYWORD} arm of the hybrid pipeline. It exists because dense vector search,
 * for all its semantic strength, can miss <em>exact</em> tokens — a config key like
 * {@code NIMBUS_PROFILE}, a port number like {@code 842}, an API name. BM25 nails those literal
 * matches, which is a large part of why the hybrid (v1) strategy out-retrieves naive vector-only
 * (v0) in the eval. Implemented from scratch because LangChain4j 0.35.0 ships no BM25.
 *
 * <p><strong>Instantiable by design</strong>, mirroring {@link VectorIndex}: the eval builds a fresh
 * index per pipeline rather than sharing one global bean.
 */
public class Bm25Index {

    /** Standard BM25 term-frequency saturation parameter. */
    private static final double K1 = 1.5;
    /** Standard BM25 length-normalisation parameter. */
    private static final double B = 0.75;

    /** Split on anything that isn't a letter, digit, or underscore — keeps tokens like NIMBUS_PROFILE whole. */
    private static final Pattern TOKEN = Pattern.compile("[^\\p{Alnum}_]+");

    /** The indexed chunks, parallel to {@link #docTokenCounts} and {@link #docLengths}. */
    private final List<Chunk> chunks = new ArrayList<>();
    /** Per-document term frequencies: docTokenCounts.get(i).get(term) = count of term in chunk i. */
    private final List<Map<String, Integer>> docTokenCounts = new ArrayList<>();
    /** Per-document length (total tokens), used for BM25 length normalisation. */
    private final List<Integer> docLengths = new ArrayList<>();
    /** Document frequency: how many documents contain each term, for IDF. */
    private final Map<String, Integer> documentFrequency = new HashMap<>();

    private double averageDocLength = 0.0;

    /** Clears all indexed data so the index can be rebuilt from scratch (used by re-ingest). */
    public void reset() {
        chunks.clear();
        docTokenCounts.clear();
        docLengths.clear();
        documentFrequency.clear();
        averageDocLength = 0.0;
    }

    /** Indexes the given chunks. Call once; recomputes corpus statistics each time. */
    public void index(List<Chunk> newChunks) {
        for (Chunk chunk : newChunks) {
            List<String> tokens = tokenize(chunk.text());
            Map<String, Integer> counts = new HashMap<>();
            for (String token : tokens) {
                counts.merge(token, 1, Integer::sum);
            }
            chunks.add(chunk);
            docTokenCounts.add(counts);
            docLengths.add(tokens.size());
            for (String term : counts.keySet()) {
                documentFrequency.merge(term, 1, Integer::sum);
            }
        }
        long totalLength = docLengths.stream().mapToLong(Integer::longValue).sum();
        averageDocLength = docLengths.isEmpty() ? 0.0 : (double) totalLength / docLengths.size();
    }

    /**
     * Returns up to {@code topK} chunks ranked by BM25 score against {@code query}. Documents that
     * share no query terms (score 0) are excluded.
     */
    public List<RetrievedChunk> search(String query, int topK) {
        if (chunks.isEmpty()) {
            return List.of();
        }
        List<String> queryTerms = tokenize(query);
        int totalDocs = chunks.size();

        List<RetrievedChunk> scored = new ArrayList<>();
        for (int i = 0; i < totalDocs; i++) {
            double score = scoreDocument(i, queryTerms, totalDocs);
            if (score > 0.0) {
                scored.add(new RetrievedChunk(chunks.get(i), score, RetrievedChunk.Origin.KEYWORD, 0));
            }
        }
        scored.sort(Comparator.comparingDouble(RetrievedChunk::score).reversed());

        List<RetrievedChunk> top = new ArrayList<>(Math.min(topK, scored.size()));
        for (int rank = 0; rank < scored.size() && rank < topK; rank++) {
            top.add(scored.get(rank).reStaged(RetrievedChunk.Origin.KEYWORD, rank));
        }
        return top;
    }

    /** Sums BM25 contributions of each query term for document {@code docIndex}. */
    private double scoreDocument(int docIndex, List<String> queryTerms, int totalDocs) {
        Map<String, Integer> counts = docTokenCounts.get(docIndex);
        int docLength = docLengths.get(docIndex);
        double score = 0.0;
        for (String term : queryTerms) {
            Integer tf = counts.get(term);
            if (tf == null) {
                continue;
            }
            int df = documentFrequency.getOrDefault(term, 0);
            double idf = idf(df, totalDocs);
            double denom = tf + K1 * (1 - B + B * (docLength / averageDocLength));
            score += idf * (tf * (K1 + 1)) / denom;
        }
        return score;
    }

    /** Robertson–Spärck-Jones IDF with the +0.5 smoothing used by Okapi BM25. */
    private static double idf(int df, int totalDocs) {
        return Math.log(1 + (totalDocs - df + 0.5) / (df + 0.5));
    }

    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        for (String raw : TOKEN.split(text.toLowerCase())) {
            if (!raw.isEmpty()) {
                tokens.add(raw);
            }
        }
        return tokens;
    }
}
