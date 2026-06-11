package com.eshant.insightrag.generation;

import com.eshant.insightrag.ingestion.Chunk;
import com.eshant.insightrag.retrieval.QueryTrace;
import com.eshant.insightrag.retrieval.RetrievalResult;
import com.eshant.insightrag.retrieval.RetrievalStrategy;
import com.eshant.insightrag.retrieval.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The behavioural heart of the project: the abstention policy. These tests pin down exactly when v1
 * (improved, abstention on) refuses versus answers, and confirm v0 (naive) answers regardless — which
 * is the difference the eval's abstention-correctness metric measures.
 *
 * <p>Most importantly they cover the confidence fix: the pre-generation gate keys off the
 * <em>raw-cosine confidence</em> carried on {@link RetrievalResult}, not the (always-high) re-ranked
 * top-chunk score.
 */
class AnswerGeneratorTest {

    /** Minimal LlmClient that returns a canned answer — lets us isolate the generator's gating logic. */
    private record FixedLlm(String answer) implements LlmClient {
        @Override public String generate(String prompt) { return answer; }
        @Override public String name() { return "fixed"; }
        @Override public boolean isMock() { return false; }
    }

    private static RetrievalResult retrieval(double confidence, double lexicalConfidence,
                                             List<RetrievedChunk> chunks) {
        return new RetrievalResult(chunks, new QueryTrace("q", RetrievalStrategy.HYBRID_RERANK),
                confidence, lexicalConfidence);
    }

    private static List<RetrievedChunk> portContext() {
        return List.of(new RetrievedChunk(
                new Chunk("c#0", "c.md", "", 0, "The server listens on port 842 by default.", 8),
                0.95, RetrievedChunk.Origin.RERANKED, 0));
    }

    private AnswerGenerator improved(String cannedAnswer) {
        return new AnswerGenerator(new FixedLlm(cannedAnswer), new GroundingChecker(),
                GenerationSettings.improved());
    }

    @Test
    void improvedAnswersWhenConfidentAndGrounded() {
        Answer a = improved("The server listens on port 842 [1]")
                .generate("What port does the server use?", retrieval(0.90, 0.0, portContext()));

        assertThat(a.abstained()).isFalse();
        assertThat(a.text()).contains("842");
        assertThat(a.grounded()).isTrue();
    }

    @Test
    void improvedAnswersWhenLexicallyConfidentEvenIfCosineBelowGate() {
        // Cosine confidence (0.50) is below the gate, but strong keyword coverage (0.80) shows the
        // answer is in scope — the gate must NOT refuse. This is the over-cautiousness fix.
        Answer a = improved("The server listens on port 842 [1]")
                .generate("default port", retrieval(0.50, 0.80, portContext()));

        assertThat(a.abstained()).isFalse();
        assertThat(a.text()).contains("842");
    }

    @Test
    void improvedAbstainsWhenConfidenceBelowGate() {
        // Good chunks and a grounded answer available, but BOTH signals are weak (cosine 0.50, lexical
        // 0.10), so the pre-generation gate must refuse before the LLM is even consulted.
        Answer a = improved("The server listens on port 842 [1]")
                .generate("What port does the server use?", retrieval(0.50, 0.10, portContext()));

        assertThat(a.abstained()).isTrue();
        assertThat(a.text()).isEqualTo(PromptBuilder.ABSTAIN_ANSWER);
    }

    @Test
    void improvedAbstainsOnEmptyRetrieval() {
        Answer a = improved("anything").generate("q", retrieval(0.0, 0.0, List.of()));

        assertThat(a.abstained()).isTrue();
    }

    @Test
    void improvedAbstainsWhenAnswerIsUngrounded() {
        // Confident retrieval passes the pre-gate, but the LLM's answer isn't supported by context,
        // so the post-generation grounding gate discards it.
        Answer a = improved("Bananas grow in tropical rainforests worldwide")
                .generate("What port does the server use?", retrieval(0.90, 0.0, portContext()));

        assertThat(a.abstained()).isTrue();
    }

    @Test
    void naiveAnswersEvenOnLowConfidence() {
        AnswerGenerator naive = new AnswerGenerator(
                new FixedLlm("The server listens on port 842 [1]"),
                new GroundingChecker(), GenerationSettings.naive());

        Answer a = naive.generate("What port does the server use?", retrieval(0.20, 0.0, portContext()));

        assertThat(a.abstained()).isFalse();
        assertThat(a.citations()).containsExactly("c.md");
    }
}
