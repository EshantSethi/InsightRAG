package com.eshant.insightrag.api.dto;

/**
 * Request body for {@code POST /ask}.
 *
 * @param question the user's natural-language question
 */
public record AskRequest(String question) {
}
