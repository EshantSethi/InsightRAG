package com.eshant.insightrag.api.dto;

/**
 * Response body for {@code POST /ingest}: how much was (re)indexed.
 *
 * @param documents number of source documents loaded
 * @param chunks    number of chunks indexed
 * @param message   human-readable status
 */
public record IngestResponse(int documents, int chunks, String message) {
}
