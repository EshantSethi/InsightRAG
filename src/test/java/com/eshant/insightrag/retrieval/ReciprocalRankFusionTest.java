package com.eshant.insightrag.retrieval;

import com.eshant.insightrag.ingestion.Chunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies Reciprocal Rank Fusion: a chunk found by both retrieval arms should accumulate
 * contributions from both and outrank a chunk found by only one — the "semantically AND lexically
 * relevant" signal the hybrid pipeline trusts most.
 */
class ReciprocalRankFusionTest {

    private static RetrievedChunk hit(String id, int rank, RetrievedChunk.Origin origin) {
        Chunk c = new Chunk(id, id + ".md", "", 0, "text " + id, 2);
        return new RetrievedChunk(c, 1.0, origin, rank);
    }

    @Test
    void chunkRankedByBothListsRisesToTop() {
        // "shared" is rank 1 in vectors and rank 1 in keywords; "vOnly"/"kOnly" appear in one list each.
        List<RetrievedChunk> vector = List.of(
                hit("vOnly", 0, RetrievedChunk.Origin.VECTOR),
                hit("shared", 1, RetrievedChunk.Origin.VECTOR));
        List<RetrievedChunk> keyword = List.of(
                hit("kOnly", 0, RetrievedChunk.Origin.KEYWORD),
                hit("shared", 1, RetrievedChunk.Origin.KEYWORD));

        List<RetrievedChunk> fused = ReciprocalRankFusion.fuse(
                ReciprocalRankFusion.DEFAULT_K, 10, List.of(vector, keyword));

        assertThat(fused.get(0).chunk().id()).isEqualTo("shared");
        assertThat(fused).allMatch(rc -> rc.origin() == RetrievedChunk.Origin.FUSED);
        assertThat(fused.get(0).rank()).isZero();
    }

    @Test
    void respectsTopKTruncation() {
        List<RetrievedChunk> a = List.of(hit("a", 0, RetrievedChunk.Origin.VECTOR),
                hit("b", 1, RetrievedChunk.Origin.VECTOR),
                hit("c", 2, RetrievedChunk.Origin.VECTOR));

        assertThat(ReciprocalRankFusion.fuse(ReciprocalRankFusion.DEFAULT_K, 2, List.of(a)))
                .hasSize(2);
    }
}
