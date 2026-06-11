package com.eshant.insightrag.api.dto;

import com.eshant.insightrag.agent.AgentResponse;
import com.eshant.insightrag.retrieval.QueryTrace;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Response body for {@code POST /ask}. The {@code debug} block is populated only when the caller
 * passes {@code ?debug=true}, exposing the routing reason, SQL used, grounding score, and retrieval
 * trace so the whole decision is inspectable.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AskResponse(
        String question,
        String answer,
        String route,
        boolean abstained,
        List<String> citations,
        Debug debug) {

    /** Diagnostic detail, included only in debug mode. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Debug(String routeReason, String sqlUsed, Double groundingScore, Trace trace) {
    }

    /** Serializable view of a {@link QueryTrace}. */
    public record Trace(String strategy, double confidence, double lexicalConfidence, long totalMillis,
                        List<Stage> stages) {
    }

    public record Stage(String name, int candidateCount, long elapsedMillis, String note) {
    }

    /** Maps the internal agent response to the API shape, attaching debug info only when requested. */
    public static AskResponse from(AgentResponse r, boolean debug) {
        Debug dbg = debug ? new Debug(r.routeReason(), r.detail(), r.groundingScore(), toTrace(r.trace())) : null;
        return new AskResponse(r.question(), r.answer(), r.intent().name(), r.abstained(),
                r.citations(), dbg);
    }

    private static Trace toTrace(QueryTrace trace) {
        if (trace == null) {
            return null;
        }
        List<Stage> stages = trace.stages().stream()
                .map(s -> new Stage(s.name(), s.candidateCount(), s.elapsedMillis(), s.note()))
                .toList();
        return new Trace(trace.strategy().name(), trace.confidence(), trace.lexicalConfidence(),
                trace.totalMillis(), stages);
    }
}
