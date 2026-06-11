package com.eshant.insightrag.retrieval;

import com.eshant.insightrag.ingestion.Chunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /**
     * Question/function words stripped before computing {@link #lexicalConfidence(String)} — they carry
     * no topical signal and (being absent from a technical corpus) would otherwise get a large IDF and
     * distort the score. Deliberately excludes content words.
     */
    private static final Set<String> STOPWORDS = Set.of(
            "the", "a", "an", "and", "or", "but", "is", "are", "was", "were", "be", "been", "being",
            "to", "of", "in", "on", "at", "by", "for", "with", "as", "from", "that", "this", "these",
            "those", "it", "its", "you", "your", "i", "me", "my", "we", "our", "can", "do", "does",
            "did", "if", "not", "will", "would", "should", "shall", "may", "might", "must", "have",
            "has", "had", "they", "them", "their", "there", "what", "which", "when", "where", "how",
            "who", "whom", "why", "into", "about", "over", "than", "then", "so", "such");

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

    /**
     * IDF-weighted lexical confidence in [0,1]: of the query's informative-term "importance mass"
     * (Σ IDF over non-stopword terms), how much is covered by the single best-matching chunk?
     *
     * <p>This is a phrasing-independent "is this even in scope?" signal that complements the vector
     * confidence. Query terms absent from the corpus (e.g. {@code smtp}, {@code graphql}) carry a large
     * IDF and can never be matched, so a question whose distinctive terms aren't in the documentation
     * scores near 0 — even if it shares generic words like "default" or the framework name. Conversely
     * an answerable question scores high whether or not it names the framework, which fixes the
     * over-cautious refusals a vector-only gate produces.
     */
    public double lexicalConfidence(String query) {
        if (chunks.isEmpty()) {
            return 0.0;
        }
        List<String> terms = new ArrayList<>(new LinkedHashSet<>(tokenize(query)));
        terms.removeIf(STOPWORDS::contains);
        if (terms.isEmpty()) {
            return 0.0;
        }
        int totalDocs = chunks.size();
        Map<String, Double> termIdf = new HashMap<>();
        double totalIdf = 0.0;
        for (String term : terms) {
            double idf = idf(documentFrequency.getOrDefault(term, 0), totalDocs);
            termIdf.put(term, idf);
            totalIdf += idf;
        }
        if (totalIdf <= 0.0) {
            return 0.0;
        }
        double best = 0.0;
        for (Map<String, Integer> counts : docTokenCounts) {
            double matched = 0.0;
            for (String term : terms) {
                if (counts.containsKey(term)) {
                    matched += termIdf.get(term);
                }
            }
            best = Math.max(best, matched);
        }
        return Math.min(1.0, best / totalIdf);
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
