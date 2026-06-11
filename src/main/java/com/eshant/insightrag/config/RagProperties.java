package com.eshant.insightrag.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed configuration for the RAG pipeline, bound from the {@code insightrag.*}
 * namespace in {@code application.yml}. Every value is overridable via environment variables
 * (Spring relaxed binding), so nothing about retrieval behaviour is hard-coded.
 */
@ConfigurationProperties(prefix = "insightrag")
public class RagProperties {

    private final Ingestion ingestion = new Ingestion();
    private final Retrieval retrieval = new Retrieval();
    private final Llm llm = new Llm();

    public Ingestion getIngestion() {
        return ingestion;
    }

    public Retrieval getRetrieval() {
        return retrieval;
    }

    public Llm getLlm() {
        return llm;
    }

    /** Document loading + chunking parameters. */
    public static class Ingestion {
        /** Directory scanned for source documents (markdown / text). */
        private String dataDir = "data";
        /** Target chunk size in tokens (approximated as whitespace-delimited words). */
        private int chunkSizeTokens = 500;
        /** Overlap between consecutive chunks, in tokens. */
        private int chunkOverlapTokens = 80;

        public String getDataDir() {
            return dataDir;
        }

        public void setDataDir(String dataDir) {
            this.dataDir = dataDir;
        }

        public int getChunkSizeTokens() {
            return chunkSizeTokens;
        }

        public void setChunkSizeTokens(int chunkSizeTokens) {
            this.chunkSizeTokens = chunkSizeTokens;
        }

        public int getChunkOverlapTokens() {
            return chunkOverlapTokens;
        }

        public void setChunkOverlapTokens(int chunkOverlapTokens) {
            this.chunkOverlapTokens = chunkOverlapTokens;
        }
    }

    /** Similarity-search parameters. */
    public static class Retrieval {
        /** Number of chunks to retrieve per query (final result size). */
        private int topK = 4;
        /** Minimum cosine similarity (0..1) for a chunk to be considered relevant. */
        private double minScore = 0.5;
        /**
         * For the hybrid strategy: how many candidates each retriever fetches before fusion and
         * re-ranking narrow the set back down to {@code topK}. Candidate pool = topK * this.
         */
        private int candidateMultiplier = 4;

        public int getTopK() {
            return topK;
        }

        public void setTopK(int topK) {
            this.topK = topK;
        }

        public double getMinScore() {
            return minScore;
        }

        public void setMinScore(double minScore) {
            this.minScore = minScore;
        }

        public int getCandidateMultiplier() {
            return candidateMultiplier;
        }

        public void setCandidateMultiplier(int candidateMultiplier) {
            this.candidateMultiplier = candidateMultiplier;
        }
    }

    /** LLM provider configuration. */
    public static class Llm {
        /**
         * Provider id. {@code auto} (default) uses Anthropic when ANTHROPIC_API_KEY is present,
         * otherwise the mock. Force a provider with {@code anthropic} or {@code mock}.
         */
        private String provider = "auto";
        /** Anthropic API key, normally injected from the ANTHROPIC_API_KEY environment variable. */
        private String apiKey = "";
        /** Model id used when the Anthropic provider is active. */
        private String model = "claude-haiku-4-5";
        private double temperature = 0.0;
        private int maxTokens = 1024;

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public int getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
        }

        /** True when a non-blank API key is available and the provider is not forced to mock. */
        public boolean anthropicEnabled() {
            boolean keyPresent = apiKey != null && !apiKey.isBlank();
            return switch (provider == null ? "auto" : provider.toLowerCase()) {
                case "anthropic" -> keyPresent;
                case "mock" -> false;
                default -> keyPresent; // auto
            };
        }
    }
}
