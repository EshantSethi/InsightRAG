package com.eshant.insightrag.agent;

import com.eshant.insightrag.retrieval.QueryTrace;

import java.util.List;

/**
 * The agent's unified answer, regardless of which path produced it. Carries the routing decision so
 * callers (and the API's debug mode) can see not just the answer but how the agent decided to get it.
 *
 * @param question       the original question
 * @param answer         the answer text (or abstention message)
 * @param intent         the route taken (DOC_QA or STRUCTURED_DATA)
 * @param routeReason    why that route was chosen
 * @param abstained      true if the RAG path declined to answer
 * @param citations      source documents cited (RAG path) — empty for SQL
 * @param detail         supporting detail: the SQL used (SQL path) or null (RAG path)
 * @param groundingScore rule-based faithfulness score (RAG path) or null (SQL path)
 * @param trace          retrieval trace (RAG path) or null (SQL path); surfaced in debug mode
 */
public record AgentResponse(
        String question,
        String answer,
        Intent intent,
        String routeReason,
        boolean abstained,
        List<String> citations,
        String detail,
        Double groundingScore,
        QueryTrace trace) {
}
