package com.eshant.insightrag.config;

import com.eshant.insightrag.embedding.EmbeddingService;
import com.eshant.insightrag.generation.AnswerGenerator;
import com.eshant.insightrag.generation.GenerationSettings;
import com.eshant.insightrag.generation.GroundingChecker;
import com.eshant.insightrag.generation.LlmClient;
import com.eshant.insightrag.retrieval.Bm25Index;
import com.eshant.insightrag.retrieval.DeterministicReRanker;
import com.eshant.insightrag.retrieval.RetrievalService;
import com.eshant.insightrag.retrieval.RetrievalStrategy;
import com.eshant.insightrag.retrieval.VectorIndex;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the <em>live</em> RAG pipeline used by the running application — always the improved (v1)
 * configuration: hybrid retrieval + re-ranking and abstention on.
 *
 * <p>The indexes are created empty here and populated (and re-populated on {@code /ingest}) by
 * {@link com.eshant.insightrag.ingestion.IngestionService}; the {@link RetrievalService} holds the
 * same index instances, so it always queries the current corpus.
 *
 * <p>The eval harness deliberately does <strong>not</strong> use these beans — it builds its own
 * naive and improved pipelines over throwaway stores so it can compare them head-to-head.
 */
@Configuration
public class PipelineConfig {

    /** The vector index for the live corpus; filled by the ingestion service. */
    @Bean
    public VectorIndex vectorIndex(EmbeddingService embeddingService, EmbeddingStore<TextSegment> store) {
        return new VectorIndex(embeddingService, store);
    }

    /** The BM25 keyword index for the live corpus; filled by the ingestion service. */
    @Bean
    public Bm25Index keywordIndex() {
        return new Bm25Index();
    }

    /** The live hybrid retriever over the shared indexes. */
    @Bean
    public RetrievalService retrievalService(VectorIndex vectorIndex, Bm25Index keywordIndex,
                                             RagProperties props) {
        RagProperties.Retrieval r = props.getRetrieval();
        return new RetrievalService(vectorIndex, keywordIndex, new DeterministicReRanker(),
                RetrievalStrategy.HYBRID_RERANK, r.getTopK(), r.getMinScore(), r.getCandidateMultiplier());
    }

    /** The live answer generator: improved settings (abstention on) over the configured LLM client. */
    @Bean
    public AnswerGenerator answerGenerator(LlmClient llm) {
        return new AnswerGenerator(llm, new GroundingChecker(), GenerationSettings.improved());
    }
}
