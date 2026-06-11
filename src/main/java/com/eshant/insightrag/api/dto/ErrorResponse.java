package com.eshant.insightrag.api.dto;

/**
 * Uniform error body returned by the {@link com.eshant.insightrag.api.GlobalExceptionHandler}.
 *
 * @param error   short error category (e.g. "bad_request", "internal_error")
 * @param message human-readable detail
 */
public record ErrorResponse(String error, String message) {
}
