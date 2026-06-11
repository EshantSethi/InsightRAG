package com.eshant.insightrag.generation;

import com.eshant.insightrag.retrieval.RetrievalResult;
import com.eshant.insightrag.retrieval.RetrievedChunk;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns retrieved context into a final {@link Answer}, applying the abstention policy.
 *
 * <p>This is the stage where the project's anti-hallucination behaviour lives. It enforces two gates
 * (active only when {@link GenerationSettings#abstentionEnabled()}):
 * <ol>
 *   <li><b>pre-generation</b> — if nothing was retrieved or the top chunk is below
 *       {@code minRetrievalScore}, refuse without even calling the LLM;</li>
 *   <li><b>post-generation</b> — if the produced answer isn't grounded enough
 *       ({@code minGroundingScore}), discard it and abstain instead.</li>
 * </ol>
 *
 * <p>With abstention off (v0) it answers regardless — reproducing naive RAG's habit of confidently
 * answering questions it shouldn't. The two behaviours over the same adversarial questions are what
 * the eval's abstention-correctness metric measures. <strong>Instantiable</strong> so the eval builds
 * a naive and an improved generator side by side.
 */
public class AnswerGenerator {

    private static final Pattern CITATION = Pattern.compile("\\[(\\d+)]");

    private final LlmClient llm;
    private final GroundingChecker groundingChecker;
    private final GenerationSettings settings;

    public AnswerGenerator(LlmClient llm, GroundingChecker groundingChecker, GenerationSettings settings) {
        this.llm = llm;
        this.groundingChecker = groundingChecker;
        this.settings = settings;
    }

    public Answer generate(String question, RetrievalResult retrieval) {
        List<RetrievedChunk> context = retrieval.chunks();

        // Gate 1: pre-generation — refuse only when retrieval is weak by BOTH measures. We avoid the
        // re-ranked top-chunk score (a normalized blend that runs high even for off-topic queries) and
        // instead combine two honest signals: raw-cosine vector confidence and IDF-weighted lexical
        // (keyword) coverage. Either being strong means we found something; only when both are weak do
        // we abstain. This both refuses adversarial out-of-corpus questions and avoids over-refusing
        // answerable questions that happen to be phrased without the framework name.
        if (settings.abstentionEnabled()
                && !hasConfidentContext(context, retrieval.confidence(), retrieval.lexicalConfidence())) {
            return abstain(question);
        }

        String text = llm.generate(PromptBuilder.build(question, context));

        // The model may decline on its own (extractive mock with no overlap) — honour that.
        if (text.trim().equals(PromptBuilder.ABSTAIN_ANSWER)) {
            return abstain(question);
        }

        double threshold = settings.abstentionEnabled()
                ? settings.minGroundingScore() : GroundingChecker.DEFAULT_THRESHOLD;
        GroundingResult grounding = groundingChecker.check(text, context, threshold);

        // Gate 2: post-generation — discard an ungrounded answer rather than serve a hallucination.
        if (settings.abstentionEnabled() && !grounding.grounded()) {
            return abstain(question);
        }

        return new Answer(question, text, false, grounding.grounded(), grounding.score(),
                extractCitations(text, context), llm.name());
    }

    /** True if we retrieved anything and either confidence signal clears its gate. */
    private boolean hasConfidentContext(List<RetrievedChunk> context, double confidence,
                                        double lexicalConfidence) {
        if (context.isEmpty()) {
            return false;
        }
        return confidence >= settings.minRetrievalScore()
                || lexicalConfidence >= settings.minLexicalConfidence();
    }

    private Answer abstain(String question) {
        return new Answer(question, PromptBuilder.ABSTAIN_ANSWER, true, true, 1.0, List.of(), llm.name());
    }

    /** Maps {@code [n]} markers in the answer back to the source names of the cited chunks. */
    private static List<String> extractCitations(String text, List<RetrievedChunk> context) {
        Set<String> sources = new LinkedHashSet<>();
        Matcher m = CITATION.matcher(text);
        while (m.find()) {
            int idx = Integer.parseInt(m.group(1)) - 1; // citations are 1-based
            if (idx >= 0 && idx < context.size()) {
                sources.add(context.get(idx).chunk().source());
            }
        }
        return new ArrayList<>(sources);
    }
}
