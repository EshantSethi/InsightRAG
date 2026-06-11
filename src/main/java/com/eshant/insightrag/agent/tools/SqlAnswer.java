package com.eshant.insightrag.agent.tools;

/**
 * Result of a structured-data lookup. {@code found=false} signals the agent to fall back to RAG when
 * the question routed to SQL but couldn't be matched to a query. {@code sqlUsed} is recorded for the
 * trace so the data path is as auditable as the RAG path.
 *
 * @param found   whether a matching row/answer was produced
 * @param text    the natural-language answer (empty when not found)
 * @param sqlUsed the SQL that produced it, for tracing (empty when not found)
 */
public record SqlAnswer(boolean found, String text, String sqlUsed) {

    public static SqlAnswer notFound() {
        return new SqlAnswer(false, "", "");
    }
}
