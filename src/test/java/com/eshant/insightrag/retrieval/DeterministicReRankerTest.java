package com.eshant.insightrag.retrieval;

import com.eshant.insightrag.ingestion.Chunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the always-on deterministic re-ranker: a chunk that literally covers the whole query (and
 * contains the exact phrase) should be promoted above a chunk that merely had a high fusion prior but
 * doesn't address the question. This is the cheap precision boost that holds even with the mock LLM.
 */
class DeterministicReRankerTest {

    private static RetrievedChunk candidate(String id, String text, double priorScore) {
        Chunk c = new Chunk(id, id + ".md", "", 0, text, text.split("\\s+").length);
        return new RetrievedChunk(c, priorScore, RetrievedChunk.Origin.FUSED, 0);
    }

    @Test
    void promotesExactCoverageOverHighPriorButIrrelevant() {
        // "off-topic" has the higher fusion prior, but "on-topic" actually contains the query phrase.
        List<RetrievedChunk> candidates = List.of(
                candidate("off-topic", "Configuration precedence and profiles overview.", 1.0),
                candidate("on-topic", "The default connection pool size is ten connections.", 0.5));

        List<RetrievedChunk> reranked = new DeterministicReRanker()
                .rerank("connection pool size", candidates, 5);

        assertThat(reranked.get(0).chunk().id()).isEqualTo("on-topic");
        assertThat(reranked.get(0).origin()).isEqualTo(RetrievedChunk.Origin.RERANKED);
        assertThat(reranked.get(0).rank()).isZero();
    }

    @Test
    void truncatesToTopKAndHandlesEmpty() {
        assertThat(new DeterministicReRanker().rerank("q", List.of(), 5)).isEmpty();

        List<RetrievedChunk> three = List.of(
                candidate("a", "alpha pool", 0.9),
                candidate("b", "beta pool", 0.8),
                candidate("c", "gamma pool", 0.7));
        assertThat(new DeterministicReRanker().rerank("pool", three, 2)).hasSize(2);
    }
}
