package com.eshant.insightrag.retrieval;

import com.eshant.insightrag.generation.LlmClient;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Optional LLM-based {@link ReRanker}: asks a real model to reorder candidates by relevance to the
 * query. Used only when a real LLM is configured; with the mock (or any unparseable response) it
 * transparently falls back to a deterministic reranker, so it is always safe to wire in.
 *
 * <p>An LLM can judge semantic relevance more subtly than the lexical {@link DeterministicReRanker},
 * but it costs a call and is non-deterministic — hence it is the optional upgrade while the
 * deterministic reranker remains the dependable, always-on floor that produces the eval's stable
 * before/after numbers.
 */
public class LlmReRanker implements ReRanker {

    private static final Pattern NUMBER = Pattern.compile("\\d+");

    private final LlmClient llm;
    private final ReRanker fallback;

    public LlmReRanker(LlmClient llm, ReRanker fallback) {
        this.llm = llm;
        this.fallback = fallback;
    }

    @Override
    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topK) {
        // The mock is extractive, not a ranker — don't pretend; use the deterministic floor.
        if (candidates.isEmpty() || llm.isMock()) {
            return fallback.rerank(query, candidates, topK);
        }

        String response;
        try {
            response = llm.generate(buildPrompt(query, candidates));
        } catch (RuntimeException e) {
            return fallback.rerank(query, candidates, topK);
        }

        List<Integer> order = parseOrder(response, candidates.size());
        if (order.isEmpty()) {
            return fallback.rerank(query, candidates, topK);
        }

        List<RetrievedChunk> reranked = new ArrayList<>(Math.min(topK, order.size()));
        int rank = 0;
        for (int idx : order) {
            if (rank >= topK) {
                break;
            }
            RetrievedChunk rc = candidates.get(idx);
            // Score by descending position so downstream ordering is meaningful.
            double score = (double) (order.size() - rank) / order.size();
            reranked.add(rc.reScored(score, RetrievedChunk.Origin.RERANKED, rank));
            rank++;
        }
        return reranked;
    }

    private static String buildPrompt(String query, List<RetrievedChunk> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("Rank the passages below by how well they answer the question. ")
                .append("Reply with ONLY the passage numbers, most relevant first, comma-separated ")
                .append("(e.g. 3,1,2). Do not add any other text.\n\nQUESTION: ")
                .append(query).append("\n\nPASSAGES:\n");
        for (int i = 0; i < candidates.size(); i++) {
            sb.append(i).append(". ").append(oneLine(candidates.get(i).chunk().text())).append('\n');
        }
        sb.append("\nRANKING:");
        return sb.toString();
    }

    /** Parses a comma/space-separated list of indices, keeping only valid, distinct ones in order. */
    private static List<Integer> parseOrder(String response, int size) {
        Set<Integer> seen = new LinkedHashSet<>();
        Matcher m = NUMBER.matcher(response);
        while (m.find()) {
            int idx = Integer.parseInt(m.group());
            if (idx >= 0 && idx < size) {
                seen.add(idx);
            }
        }
        return new ArrayList<>(seen);
    }

    private static String oneLine(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }
}
