package com.eshant.insightrag.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Always-on, dependency-free re-ranker that rescores candidates with cheap lexical features the
 * embedding model is weak at, then reorders them.
 *
 * <p>It blends three signals into a final score:
 * <ul>
 *   <li><b>fusion prior</b> — the incoming RRF score, so retrieval consensus still counts;</li>
 *   <li><b>query-term coverage</b> — fraction of distinct query terms present in the chunk, which
 *       rewards chunks that address the <em>whole</em> question, not just one word;</li>
 *   <li><b>exact-phrase bonus</b> — a boost when the chunk literally contains the query phrase, the
 *       strongest possible lexical relevance signal.</li>
 * </ul>
 *
 * <p>Being deterministic, it improves precision@k for free and identically on every run — so the
 * before/after eval delta it produces is real even when the pipeline uses the mock (extractive) LLM.
 * For nuanced semantic ordering, an LLM-based {@link ReRanker} can be swapped in when a real model is
 * configured; this one is the dependable floor.
 */
public class DeterministicReRanker implements ReRanker {

    private static final Pattern TOKEN = Pattern.compile("[^\\p{Alnum}_]+");

    /** Weight of the incoming fusion/retrieval score (normalised) in the final blend. */
    private static final double W_PRIOR = 0.4;
    /** Weight of query-term coverage. */
    private static final double W_COVERAGE = 0.4;
    /** Weight of the exact-phrase bonus. */
    private static final double W_PHRASE = 0.2;

    @Override
    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topK) {
        if (candidates.isEmpty()) {
            return List.of();
        }
        Set<String> queryTerms = new HashSet<>(tokenize(query));
        String normalisedQuery = query.toLowerCase().trim();
        double maxPrior = candidates.stream().mapToDouble(RetrievedChunk::score).max().orElse(1.0);
        if (maxPrior <= 0.0) {
            maxPrior = 1.0;
        }

        List<RetrievedChunk> rescored = new ArrayList<>(candidates.size());
        for (RetrievedChunk rc : candidates) {
            String text = rc.chunk().text().toLowerCase();

            double prior = rc.score() / maxPrior;
            double coverage = queryTerms.isEmpty() ? 0.0 : coverage(text, queryTerms);
            double phrase = !normalisedQuery.isEmpty() && text.contains(normalisedQuery) ? 1.0 : 0.0;

            double finalScore = W_PRIOR * prior + W_COVERAGE * coverage + W_PHRASE * phrase;
            rescored.add(rc.reScored(finalScore, RetrievedChunk.Origin.RERANKED, rc.rank()));
        }
        rescored.sort(Comparator.comparingDouble(RetrievedChunk::score).reversed());

        List<RetrievedChunk> top = new ArrayList<>(Math.min(topK, rescored.size()));
        for (int rank = 0; rank < rescored.size() && rank < topK; rank++) {
            top.add(rescored.get(rank).reScored(rescored.get(rank).score(), RetrievedChunk.Origin.RERANKED, rank));
        }
        return top;
    }

    /** Fraction of distinct query terms that appear at least once in the chunk text. */
    private static double coverage(String text, Set<String> queryTerms) {
        Set<String> textTokens = new HashSet<>(tokenize(text));
        int hit = 0;
        for (String term : queryTerms) {
            if (textTokens.contains(term)) {
                hit++;
            }
        }
        return (double) hit / queryTerms.size();
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
