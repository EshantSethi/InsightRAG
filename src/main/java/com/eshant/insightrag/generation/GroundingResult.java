package com.eshant.insightrag.generation;

/**
 * Outcome of a rule-based faithfulness check: how much of an answer is supported by the retrieved
 * context. Surfaced in traces and aggregated by the eval as the always-on "groundedness" metric.
 *
 * @param score          fraction [0,1] of the answer's content words found in the context
 * @param grounded       whether {@code score} met the configured threshold
 * @param supportedTerms count of answer content words backed by the context
 * @param totalTerms     count of answer content words considered
 */
public record GroundingResult(double score, boolean grounded, int supportedTerms, int totalTerms) {
}
