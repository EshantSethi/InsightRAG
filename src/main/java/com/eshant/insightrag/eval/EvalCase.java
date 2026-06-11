package com.eshant.insightrag.eval;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * One labelled evaluation example — the "ground truth" the harness scores both pipelines against.
 *
 * <p>Cases come in two flavours (see {@link CaseType}). In-corpus cases carry the documents that
 * <em>should</em> be retrieved ({@code expectedSources}) and the facts a correct answer must mention
 * ({@code expectedKeywords}); adversarial out-of-corpus cases carry neither — the only correct
 * behaviour is to abstain. This split is what lets the eval measure both retrieval quality (on
 * in-corpus cases) and the anti-hallucination story (abstention correctness, driven by the
 * adversarial cases).
 *
 * @param id              stable short id, e.g. {@code "q01"}
 * @param question        the user question
 * @param type            whether the answer is present in the corpus or deliberately absent
 * @param expectedSources document names that should be retrieved (empty for out-of-corpus)
 * @param expectedKeywords facts a correct answer must contain (empty for out-of-corpus)
 * @param note            short human description of what the case probes
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EvalCase(
        String id,
        String question,
        CaseType type,
        List<String> expectedSources,
        List<String> expectedKeywords,
        String note) {

    /** Whether the answer lives in the documentation or is deliberately absent (adversarial). */
    public enum CaseType {
        /** The answer is present in the corpus; the system should retrieve it and answer. */
        IN_CORPUS,
        /** The answer is not in the corpus; the only correct behaviour is to abstain. */
        OUT_OF_CORPUS
    }

    /** True when the correct behaviour is to refuse rather than answer. */
    public boolean shouldAbstain() {
        return type == CaseType.OUT_OF_CORPUS;
    }
}
