package com.eshant.insightrag;

import com.eshant.insightrag.config.RagProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * InsightRAG — a production-style Retrieval-Augmented Generation backend over technical
 * documentation, with an agentic layer that routes precise-data questions to a SQL tool.
 *
 * <p>Runs end-to-end with no external services or API keys by default:
 * <ul>
 *     <li>Embeddings: in-process all-MiniLM-L6-v2 (ONNX)</li>
 *     <li>Vector store: in-memory (or pgvector when the {@code pgvector} profile is active)</li>
 *     <li>LLM: a clearly-labeled mock unless {@code ANTHROPIC_API_KEY} is set</li>
 * </ul>
 */
@SpringBootApplication
@EnableConfigurationProperties(RagProperties.class)
public class InsightRagApplication {

    public static void main(String[] args) {
        SpringApplication.run(InsightRagApplication.class, args);
    }
}
