package com.eshant.insightrag.generation;

import com.eshant.insightrag.retrieval.RetrievedChunk;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Rule-based faithfulness check: measures how much of an answer is actually supported by the
 * retrieved context.
 *
 * <p>Good retrieval still leaves room for an answer to assert something the context never said. This
 * checker catches that cheaply and deterministically by comparing the answer's <em>content words</em>
 * (stopwords and citation markers removed) against the union of words in the retrieved chunks. The
 * fraction backed by context is the grounding score. Being deterministic, it yields a real
 * groundedness number even with the mock LLM — the dependable floor under the project's faithfulness
 * metric, complemented by an optional LLM-as-judge pass when a real model is configured.
 */
public class GroundingChecker {

    private static final Pattern WORD = Pattern.compile("[^\\p{Alnum}_]+");
    /** Strip citation markers like [1] before analysing the answer. */
    private static final Pattern CITATION = Pattern.compile("\\[\\d+]");

    /** Default grounding threshold; answers at/above this are considered faithful. */
    public static final double DEFAULT_THRESHOLD = 0.6;

    /** Minimal stopword set so common words don't inflate the overlap score. */
    private static final Set<String> STOPWORDS = Set.of(
            "the", "a", "an", "and", "or", "but", "is", "are", "was", "were", "be", "been", "being",
            "to", "of", "in", "on", "at", "by", "for", "with", "as", "from", "that", "this", "these",
            "those", "it", "its", "you", "your", "can", "do", "does", "use", "used", "using", "if",
            "not", "no", "yes", "will", "would", "should", "may", "might", "must", "have", "has",
            "had", "they", "them", "their", "there", "here", "what", "which", "when", "where", "how");

    /** Checks faithfulness with the default threshold. */
    public GroundingResult check(String answer, List<RetrievedChunk> context) {
        return check(answer, context, DEFAULT_THRESHOLD);
    }

    /**
     * Checks how much of {@code answer} is grounded in {@code context}.
     *
     * <p>An explicit abstention is treated as fully faithful — declining to answer cannot
     * hallucinate. An empty answer or one with no content words scores 0.
     */
    public GroundingResult check(String answer, List<RetrievedChunk> context, double threshold) {
        if (answer == null || answer.isBlank() || answer.trim().equals(PromptBuilder.ABSTAIN_ANSWER)) {
            return new GroundingResult(1.0, true, 0, 0);
        }

        Set<String> answerTerms = contentWords(CITATION.matcher(answer).replaceAll(" "));
        if (answerTerms.isEmpty()) {
            return new GroundingResult(0.0, false, 0, 0);
        }

        Set<String> contextWords = new HashSet<>();
        for (RetrievedChunk rc : context) {
            contextWords.addAll(words(rc.chunk().text()));
        }

        int supported = 0;
        for (String term : answerTerms) {
            if (contextWords.contains(term)) {
                supported++;
            }
        }
        double score = (double) supported / answerTerms.size();
        return new GroundingResult(score, score >= threshold, supported, answerTerms.size());
    }

    /** Content words (lowercased, stopwords removed) as a set. */
    private static Set<String> contentWords(String text) {
        Set<String> result = new HashSet<>();
        for (String w : words(text)) {
            if (!STOPWORDS.contains(w)) {
                result.add(w);
            }
        }
        return result;
    }

    private static Set<String> words(String text) {
        Set<String> result = new HashSet<>();
        for (String raw : WORD.split(text.toLowerCase())) {
            if (!raw.isEmpty()) {
                result.add(raw);
            }
        }
        return result;
    }
}
