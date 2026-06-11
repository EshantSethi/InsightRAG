package com.eshant.insightrag.generation;

import java.util.List;

/**
 * A fully-attributed answer produced by the generation stage.
 *
 * <p>Carries not just the text but the evidence and self-assessment around it — citations, whether the
 * system chose to abstain, and the grounding score — so the API can return an honest, auditable
 * response and the eval can score faithfulness and abstention correctness.
 *
 * @param question       the original question
 * @param text           the answer text (or the abstention message)
 * @param abstained      true if the system declined to answer
 * @param grounded       true if the answer met the grounding threshold
 * @param groundingScore rule-based faithfulness score [0,1]
 * @param citations      source document names referenced by the answer
 * @param llmName        which client produced it (e.g. "mock-extractive", "anthropic:claude-haiku-4-5")
 */
public record Answer(
        String question,
        String text,
        boolean abstained,
        boolean grounded,
        double groundingScore,
        List<String> citations,
        String llmName) {
}
