package com.eshant.insightrag.generation;

import com.eshant.insightrag.ingestion.Chunk;
import com.eshant.insightrag.retrieval.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies the rule-based faithfulness check — the deterministic floor under the project's
 * groundedness metric. An answer whose content words are all backed by context scores high; one that
 * asserts new facts scores low; an explicit abstention is faithful by definition.
 */
class GroundingCheckerTest {

    private static final List<RetrievedChunk> CONTEXT = List.of(new RetrievedChunk(
            new Chunk("c#0", "c.md", "", 0, "The server listens on port 842 by default.", 8),
            0.9, RetrievedChunk.Origin.RERANKED, 0));

    private final GroundingChecker checker = new GroundingChecker();

    @Test
    void groundedAnswerScoresHighAndIgnoresCitationMarkers() {
        GroundingResult r = checker.check("The server listens on port 842 [1]", CONTEXT);

        assertThat(r.score()).isEqualTo(1.0);
        assertThat(r.grounded()).isTrue();
    }

    @Test
    void answerWithUnsupportedClaimsScoresLow() {
        GroundingResult r = checker.check("Bananas grow in tropical rainforests worldwide", CONTEXT);

        assertThat(r.score()).isLessThan(GroundingChecker.DEFAULT_THRESHOLD);
        assertThat(r.grounded()).isFalse();
    }

    @Test
    void abstentionIsTreatedAsFaithful() {
        GroundingResult r = checker.check(PromptBuilder.ABSTAIN_ANSWER, CONTEXT);

        assertThat(r.grounded()).isTrue();
        assertThat(r.score()).isEqualTo(1.0);
    }
}
