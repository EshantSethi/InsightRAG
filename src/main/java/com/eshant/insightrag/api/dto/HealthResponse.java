package com.eshant.insightrag.api.dto;

/**
 * Response body for {@code GET /health}: liveness plus a quick view of what's loaded.
 *
 * @param status    "UP" when the service is ready
 * @param llm       active LLM client name (e.g. "mock-extractive" or "anthropic:claude-haiku-4-5")
 * @param documents number of documents currently indexed
 * @param chunks    number of chunks currently indexed
 */
public record HealthResponse(String status, String llm, int documents, int chunks) {
}
