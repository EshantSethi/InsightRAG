package com.eshant.insightrag.eval;

import com.eshant.insightrag.ingestion.Chunk;
import com.eshant.insightrag.retrieval.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Verifies the eval's scoring maths and dataset integrity — the numbers that end up in the README's
 * before/after table, so they must be exactly right.
 */
class EvalMetricsTest {

    private static RetrievedChunk fromSource(String source, int rank) {
        return new RetrievedChunk(new Chunk(source + "#" + rank, source, "", rank, "text", 1),
                1.0, RetrievedChunk.Origin.RERANKED, rank);
    }

    @Test
    void retrievalScoreComputesPrecisionRecallHitAndReciprocalRank() {
        // expected = a.md; retrieved = [a.md, b.md, a.md] -> 2 of 3 relevant, first hit at rank 0.
        RetrievalMetrics.Score s = RetrievalMetrics.score(
                List.of(fromSource("a.md", 0), fromSource("b.md", 1), fromSource("a.md", 2)),
                List.of("a.md"));

        assertThat(s.precision()).isCloseTo(2.0 / 3, within(1e-9));
        assertThat(s.recall()).isEqualTo(1.0);
        assertThat(s.hitRate()).isEqualTo(1.0);
        assertThat(s.reciprocalRank()).isEqualTo(1.0);
    }

    @Test
    void retrievalReciprocalRankReflectsFirstRelevantPosition() {
        // First relevant doc appears at index 1 -> reciprocal rank 1/2; recall 1 of 2 expected.
        RetrievalMetrics.Score s = RetrievalMetrics.score(
                List.of(fromSource("c.md", 0), fromSource("b.md", 1)),
                List.of("a.md", "b.md"));

        assertThat(s.reciprocalRank()).isEqualTo(0.5);
        assertThat(s.recall()).isEqualTo(0.5);
        assertThat(s.precision()).isEqualTo(0.5);
    }

    @Test
    void retrievalScoreIsZeroWhenNoExpectedSources() {
        RetrievalMetrics.Score s = RetrievalMetrics.score(List.of(fromSource("a.md", 0)), List.of());
        assertThat(s.precision()).isZero();
        assertThat(s.hitRate()).isZero();
    }

    @Test
    void abstentionClassificationCoversAllFourQuadrants() {
        assertThat(AnswerMetrics.classifyAbstention(false, false))
                .isEqualTo(AnswerMetrics.AbstentionOutcome.CORRECT_ANSWER);
        assertThat(AnswerMetrics.classifyAbstention(true, true))
                .isEqualTo(AnswerMetrics.AbstentionOutcome.CORRECT_ABSTENTION);
        assertThat(AnswerMetrics.classifyAbstention(true, false))
                .isEqualTo(AnswerMetrics.AbstentionOutcome.WRONG_ANSWER);
        assertThat(AnswerMetrics.classifyAbstention(false, true))
                .isEqualTo(AnswerMetrics.AbstentionOutcome.WRONG_ABSTENTION);
    }

    @Test
    void keywordCoverageMeasuresFactPresence() {
        assertThat(AnswerMetrics.keywordCoverage("The port is 842 by default.", List.of("842"))).isEqualTo(1.0);
        assertThat(AnswerMetrics.keywordCoverage("No facts here.", List.of("842"))).isZero();
        assertThat(AnswerMetrics.keywordCoverage("anything", List.of())).isEqualTo(1.0); // n/a -> 1.0
    }

    @Test
    void llmJudgeParsesScoreAndDegradesGracefully() {
        assertThat(LlmJudge.parseScore("0.8")).isEqualTo(0.8);
        assertThat(LlmJudge.parseScore("Score: 0.5 out of 1")).isEqualTo(0.5);
        assertThat(LlmJudge.parseScore("no number at all")).isNaN();
    }

    @Test
    void datasetLoadsExpectedShape() {
        List<EvalCase> cases = EvalDataset.load();

        assertThat(cases).hasSize(25);
        assertThat(cases).allSatisfy(c -> {
            assertThat(c.id()).isNotBlank();
            assertThat(c.question()).isNotBlank();
        });
        long inCorpus = cases.stream().filter(c -> c.type() == EvalCase.CaseType.IN_CORPUS).count();
        long outOfCorpus = cases.stream().filter(EvalCase::shouldAbstain).count();
        assertThat(inCorpus).isEqualTo(18);
        assertThat(outOfCorpus).isEqualTo(7);
    }
}
