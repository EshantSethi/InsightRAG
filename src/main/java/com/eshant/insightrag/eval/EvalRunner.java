package com.eshant.insightrag.eval;

import com.eshant.insightrag.config.RagProperties;
import com.eshant.insightrag.embedding.EmbeddingService;
import com.eshant.insightrag.generation.Answer;
import com.eshant.insightrag.generation.AnswerGenerator;
import com.eshant.insightrag.generation.GenerationSettings;
import com.eshant.insightrag.generation.GroundingChecker;
import com.eshant.insightrag.generation.LlmClient;
import com.eshant.insightrag.ingestion.Chunk;
import com.eshant.insightrag.ingestion.DocumentLoader;
import com.eshant.insightrag.ingestion.RawDocument;
import com.eshant.insightrag.ingestion.chunking.Chunker;
import com.eshant.insightrag.ingestion.chunking.ChunkerFactory;
import com.eshant.insightrag.ingestion.chunking.ChunkingStrategy;
import com.eshant.insightrag.retrieval.Bm25Index;
import com.eshant.insightrag.retrieval.DeterministicReRanker;
import com.eshant.insightrag.retrieval.RetrievalResult;
import com.eshant.insightrag.retrieval.RetrievalService;
import com.eshant.insightrag.retrieval.RetrievalStrategy;
import com.eshant.insightrag.retrieval.VectorIndex;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * The eval harness entry point — the project's spine. It builds the two pipeline variants over their
 * own throwaway in-memory stores (so neither shares state with the live app or each other), runs every
 * labelled {@link EvalCase} through both, computes retrieval and answer metrics, prints a before/after
 * comparison, and writes the full report to {@code eval/results/} as JSON.
 *
 * <ul>
 *   <li><b>v0 (naive)</b> — fixed-size chunking + pure vector search + no abstention.</li>
 *   <li><b>v1 (improved)</b> — structure-aware chunking + hybrid (vector+BM25) + RRF + re-rank +
 *       abstention with the raw-cosine confidence gate.</li>
 * </ul>
 *
 * <p>Active only under the {@code eval} Spring profile:
 * {@code mvn spring-boot:run -Dspring-boot.run.profiles=eval}.
 */
@Component
@Profile("eval")
public class EvalRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(EvalRunner.class);
    private static final Path RESULTS_DIR = Path.of("eval", "results");

    private final EmbeddingService embeddingService;
    private final RagProperties props;
    private final LlmClient llm;
    private final DocumentLoader loader;
    private final ChunkerFactory chunkerFactory;

    public EvalRunner(EmbeddingService embeddingService, RagProperties props, LlmClient llm,
                      DocumentLoader loader, ChunkerFactory chunkerFactory) {
        this.embeddingService = embeddingService;
        this.props = props;
        this.llm = llm;
        this.loader = loader;
        this.chunkerFactory = chunkerFactory;
    }

    /** A fully-built pipeline variant: its retriever and its generator, with a label. */
    private record Pipeline(String name, RetrievalService retrieval, AnswerGenerator generator) {
    }

    @Override
    public void run(String... args) {
        List<EvalCase> cases = EvalDataset.load();
        List<RawDocument> docs = loader.loadAll();
        log.info("Eval: {} cases over {} documents, LLM={}", cases.size(), docs.size(), llm.name());

        LlmJudge judge = new LlmJudge(llm);
        EvalReport.Variant v0 = evaluate(buildNaive(docs), cases, judge);
        EvalReport.Variant v1 = evaluate(buildImproved(docs), cases, judge);

        EvalReport report = new EvalReport(
                OffsetDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                llm.name(), cases.size(), v0, v1);

        printReport(report);
        Path saved = save(report);
        System.out.println("\nSaved report to " + saved.toAbsolutePath() + "\n");

        // Exit so the run terminates instead of leaving the web server up.
        System.exit(0);
    }

    // ---- pipeline construction -------------------------------------------------------------------

    /** v0: fixed-size chunks, vector-only retrieval, no abstention. */
    private Pipeline buildNaive(List<RawDocument> docs) {
        RagProperties.Retrieval r = props.getRetrieval();
        List<Chunk> chunks = chunk(docs, ChunkingStrategy.FIXED);

        VectorIndex vector = new VectorIndex(embeddingService, new InMemoryEmbeddingStore<TextSegment>());
        vector.index(chunks);
        Bm25Index keyword = new Bm25Index(); // unused by NAIVE, but the service composes both arms

        RetrievalService retrieval = new RetrievalService(vector, keyword, new DeterministicReRanker(),
                RetrievalStrategy.NAIVE, r.getTopK(), r.getMinScore(), r.getCandidateMultiplier());
        AnswerGenerator generator = new AnswerGenerator(llm, new GroundingChecker(), GenerationSettings.naive());
        return new Pipeline("v0-naive", retrieval, generator);
    }

    /** v1: structure-aware chunks, hybrid retrieval + re-rank, abstention on. */
    private Pipeline buildImproved(List<RawDocument> docs) {
        RagProperties.Retrieval r = props.getRetrieval();
        List<Chunk> chunks = chunk(docs, ChunkingStrategy.STRUCTURE_AWARE);

        VectorIndex vector = new VectorIndex(embeddingService, new InMemoryEmbeddingStore<TextSegment>());
        vector.index(chunks);
        Bm25Index keyword = new Bm25Index();
        keyword.index(chunks);

        RetrievalService retrieval = new RetrievalService(vector, keyword, new DeterministicReRanker(),
                RetrievalStrategy.HYBRID_RERANK, r.getTopK(), r.getMinScore(), r.getCandidateMultiplier());
        AnswerGenerator generator = new AnswerGenerator(llm, new GroundingChecker(), GenerationSettings.improved());
        return new Pipeline("v1-improved", retrieval, generator);
    }

    private List<Chunk> chunk(List<RawDocument> docs, ChunkingStrategy strategy) {
        Chunker chunker = chunkerFactory.create(strategy);
        return docs.stream().flatMap(doc -> chunker.chunk(doc).stream()).toList();
    }

    // ---- scoring ---------------------------------------------------------------------------------

    private EvalReport.Variant evaluate(Pipeline pipeline, List<EvalCase> cases, LlmJudge judge) {
        List<RetrievalMetrics.Score> retrievalScores = new ArrayList<>(); // in-corpus only
        List<Double> faithfulness = new ArrayList<>();                    // answered in-corpus only
        List<Double> relevance = new ArrayList<>();                       // all in-corpus
        List<Double> judgeScores = new ArrayList<>();                     // answered in-corpus, judge available
        int correctAnswers = 0, correctAbstentions = 0, wrongAnswers = 0, wrongAbstentions = 0;
        List<EvalReport.CaseResult> results = new ArrayList<>();

        for (EvalCase c : cases) {
            RetrievalResult retrieval = pipeline.retrieval().retrieve(c.question());
            Answer answer = pipeline.generator().generate(c.question(), retrieval);

            AnswerMetrics.AbstentionOutcome outcome =
                    AnswerMetrics.classifyAbstention(c.shouldAbstain(), answer.abstained());
            switch (outcome) {
                case CORRECT_ANSWER -> correctAnswers++;
                case CORRECT_ABSTENTION -> correctAbstentions++;
                case WRONG_ANSWER -> wrongAnswers++;
                case WRONG_ABSTENTION -> wrongAbstentions++;
            }

            List<String> retrievedSources = retrieval.chunks().stream()
                    .map(rc -> rc.chunk().source()).toList();
            double rel = AnswerMetrics.keywordCoverage(answer.text(), c.expectedKeywords());
            double judgeScore = Double.NaN;

            if (c.type() == EvalCase.CaseType.IN_CORPUS) {
                retrievalScores.add(RetrievalMetrics.score(retrieval.chunks(), c.expectedSources()));
                relevance.add(rel);
                if (!answer.abstained()) {
                    faithfulness.add(answer.groundingScore());
                    if (judge.available()) {
                        judgeScore = judge.judge(c.question(), answer.text(), retrieval.chunks());
                        if (!Double.isNaN(judgeScore)) {
                            judgeScores.add(judgeScore);
                        }
                    }
                }
            }

            results.add(new EvalReport.CaseResult(c.id(), c.type().name(), answer.abstained(),
                    outcome.name(), retrieval.confidence(), answer.groundingScore(), rel, judgeScore,
                    retrievedSources, answer.text()));
        }

        double abstentionAccuracy = (double) (correctAnswers + correctAbstentions) / cases.size();
        double avgJudge = judgeScores.isEmpty() ? Double.NaN : AnswerMetrics.mean(judgeScores);
        return new EvalReport.Variant(pipeline.name(), RetrievalMetrics.mean(retrievalScores),
                AnswerMetrics.mean(faithfulness), AnswerMetrics.mean(relevance), avgJudge,
                abstentionAccuracy, correctAnswers, correctAbstentions, wrongAnswers, wrongAbstentions,
                results);
    }

    // ---- output ----------------------------------------------------------------------------------

    private void printReport(EvalReport report) {
        EvalReport.Variant v0 = report.v0();
        EvalReport.Variant v1 = report.v1();
        System.out.println("\n================= INSIGHTRAG EVAL: BEFORE / AFTER =================");
        System.out.println("LLM: " + report.llmName() + "   cases: " + report.caseCount()
                + "   at: " + report.generatedAt());
        System.out.printf("%n%-26s %12s %12s %10s%n", "metric", "v0 (naive)", "v1 (improved)", "delta");
        System.out.println("-------------------------------------------------------------------");
        row("Retrieval precision@k", v0.retrieval().precision(), v1.retrieval().precision());
        row("Retrieval recall@k", v0.retrieval().recall(), v1.retrieval().recall());
        row("Retrieval hit-rate", v0.retrieval().hitRate(), v1.retrieval().hitRate());
        row("Retrieval MRR", v0.retrieval().reciprocalRank(), v1.retrieval().reciprocalRank());
        row("Faithfulness (rules)", v0.avgFaithfulness(), v1.avgFaithfulness());
        row("Answer relevance", v0.avgAnswerRelevance(), v1.avgAnswerRelevance());
        row("Abstention accuracy", v0.abstentionAccuracy(), v1.abstentionAccuracy());
        if (!Double.isNaN(v0.avgLlmJudge()) || !Double.isNaN(v1.avgLlmJudge())) {
            row("Faithfulness (LLM judge)", v0.avgLlmJudge(), v1.avgLlmJudge());
        }
        System.out.println("-------------------------------------------------------------------");
        System.out.printf("Confident hallucinations (answered an out-of-corpus question): v0=%d  v1=%d%n",
                v0.wrongAnswers(), v1.wrongAnswers());
        System.out.printf("Correct refusals (abstained on out-of-corpus):                 v0=%d  v1=%d%n",
                v0.correctAbstentions(), v1.correctAbstentions());
        System.out.printf("Over-cautious refusals (abstained on an answerable question):  v0=%d  v1=%d%n",
                v0.wrongAbstentions(), v1.wrongAbstentions());
        System.out.println("===================================================================");
    }

    private static void row(String label, double v0, double v1) {
        String delta = String.format("%+.3f", v1 - v0);
        System.out.printf("%-26s %12s %12s %10s%n", label, fmt(v0), fmt(v1), delta);
    }

    private static String fmt(double v) {
        return Double.isNaN(v) ? "n/a" : String.format("%.3f", v);
    }

    private Path save(EvalReport report) {
        try {
            Files.createDirectories(RESULTS_DIR);
            ObjectMapper mapper = new ObjectMapper();
            String stamp = OffsetDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path timestamped = RESULTS_DIR.resolve("eval-" + stamp + ".json");
            Path latest = RESULTS_DIR.resolve("eval-latest.json");
            byte[] json = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(report);
            Files.write(timestamped, json);
            Files.write(latest, json);
            return latest;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write eval results", e);
        }
    }
}
