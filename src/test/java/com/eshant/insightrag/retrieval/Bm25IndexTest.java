package com.eshant.insightrag.retrieval;

import com.eshant.insightrag.ingestion.Chunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the from-scratch BM25 keyword arm: it must rank by lexical relevance and — crucially for
 * the hybrid pipeline — match exact technical tokens like {@code NIMBUS_PROFILE} that dense retrieval
 * can blur.
 */
class Bm25IndexTest {

    private static Chunk chunk(String id, String text) {
        return new Chunk(id, id + ".md", "", 0, text, text.split("\\s+").length);
    }

    private Bm25Index indexed() {
        Bm25Index index = new Bm25Index();
        index.index(List.of(
                chunk("pool", "The default connection pool size is 10 connections."),
                chunk("profile", "Activate a profile with the NIMBUS_PROFILE environment variable."),
                chunk("cache", "The cache time-to-live is 300 seconds.")));
        return index;
    }

    @Test
    void ranksTheMostLexicallyRelevantChunkFirst() {
        List<RetrievedChunk> hits = indexed().search("connection pool size", 3);

        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).chunk().id()).isEqualTo("pool");
        assertThat(hits.get(0).origin()).isEqualTo(RetrievedChunk.Origin.KEYWORD);
    }

    @Test
    void matchesExactUnderscoreTokenWhole() {
        List<RetrievedChunk> hits = indexed().search("NIMBUS_PROFILE", 3);

        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).chunk().id()).isEqualTo("profile");
    }

    @Test
    void returnsNothingWhenNoQueryTermMatches() {
        assertThat(indexed().search("kubernetes helm chart", 3)).isEmpty();
    }

    @Test
    void resetClearsTheIndex() {
        Bm25Index index = indexed();
        index.reset();
        assertThat(index.search("connection pool", 3)).isEmpty();
    }
}
