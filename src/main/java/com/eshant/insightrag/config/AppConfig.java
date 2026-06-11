package com.eshant.insightrag.config;

import com.eshant.insightrag.generation.AnthropicLlmClient;
import com.eshant.insightrag.generation.LlmClient;
import com.eshant.insightrag.generation.MockLlmClient;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Wires the swappable AI infrastructure. The embedding model is always the in-process
 * all-MiniLM-L6-v2 (384-dim, no network); the vector store is selected by Spring profile:
 * the default profile uses an in-memory store, the {@code pgvector} profile uses Postgres.
 */
@Configuration
public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    /** Dimensionality produced by all-MiniLM-L6-v2. */
    public static final int EMBEDDING_DIMENSION = 384;

    @Bean
    public EmbeddingModel embeddingModel() {
        log.info("Embedding model: in-process all-MiniLM-L6-v2 ({} dims)", EMBEDDING_DIMENSION);
        return new AllMiniLmL6V2EmbeddingModel();
    }

    /** Default (no external services): a fast in-memory vector store. */
    @Bean
    @Profile("!pgvector")
    public EmbeddingStore<TextSegment> inMemoryEmbeddingStore() {
        log.info("Vector store: in-memory (no external services)");
        return new InMemoryEmbeddingStore<>();
    }

    /** Production path: pgvector-backed store, enabled with the {@code pgvector} profile. */
    @Bean
    @Profile("pgvector")
    public EmbeddingStore<TextSegment> pgVectorEmbeddingStore(
            @Value("${PGVECTOR_HOST:localhost}") String host,
            @Value("${PGVECTOR_PORT:5432}") int port,
            @Value("${PGVECTOR_DB:insightrag}") String database,
            @Value("${PGVECTOR_USER:insightrag}") String user,
            @Value("${PGVECTOR_PASSWORD:insightrag}") String password) {
        log.info("Vector store: pgvector at {}:{}/{}", host, port, database);
        return PgVectorEmbeddingStore.builder()
                .host(host)
                .port(port)
                .database(database)
                .user(user)
                .password(password)
                .table("insightrag_embeddings")
                .dimension(EMBEDDING_DIMENSION)
                .createTable(true)
                .build();
    }

    /**
     * Selects the LLM provider. With {@code provider=auto} (default) Claude is used when an API key is
     * present, otherwise the deterministic, free {@link MockLlmClient}. {@code provider} can force
     * either side ({@code anthropic} / {@code mock}); see {@link RagProperties.Llm#anthropicEnabled()}.
     */
    @Bean
    public LlmClient llmClient(RagProperties props) {
        RagProperties.Llm llm = props.getLlm();
        if (llm.anthropicEnabled()) {
            log.info("LLM: Anthropic (model={})", llm.getModel());
            return new AnthropicLlmClient(llm.getApiKey(), llm.getModel(),
                    llm.getTemperature(), llm.getMaxTokens());
        }
        log.info("LLM: mock (deterministic, extractive — no API key configured)");
        return new MockLlmClient();
    }
}
