package com.eshant.insightrag.generation;

import com.eshant.insightrag.ingestion.Chunk;
import com.eshant.insightrag.retrieval.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the deterministic extractive mock that lets the project run free and offline. It must quote
 * the most query-relevant context sentence with its citation, and decline (abstain) when nothing in
 * the context is relevant — the "grounded by construction" property the eval relies on.
 */
class MockLlmClientTest {

    private static final List<RetrievedChunk> CONTEXT = List.of(new RetrievedChunk(
            new Chunk("c#0", "nimbus-getting-started.md", "", 0,
                    "The server listens on port 842 by default.", 8),
            0.9, RetrievedChunk.Origin.RERANKED, 0));

    private final MockLlmClient mock = new MockLlmClient();

    @Test
    void extractsRelevantSentenceWithCitation() {
        String prompt = PromptBuilder.build("Which port does the server use?", CONTEXT);

        String answer = mock.generate(prompt);

        assertThat(answer).contains("842");
        assertThat(answer).contains("[1]");
    }

    @Test
    void abstainsWhenNoContextSentenceIsRelevant() {
        String prompt = PromptBuilder.build("Describe quantum chromodynamics theory", CONTEXT);

        assertThat(mock.generate(prompt)).isEqualTo(PromptBuilder.ABSTAIN_ANSWER);
    }

    @Test
    void identifiesItselfAsMock() {
        assertThat(mock.isMock()).isTrue();
        assertThat(mock.name()).isEqualTo("mock-extractive");
    }
}
