package com.eshant.insightrag.agent;

/**
 * The router's decision for a query: the chosen {@link Intent}, a human-readable reason, and whether
 * an LLM made the call (vs the deterministic rules). The reason is logged and surfaced in API debug
 * output so the routing is fully explainable — never a silent black box.
 *
 * @param intent    the chosen route
 * @param reason    why this route was chosen (matched signals, or the LLM's verdict)
 * @param llmRouted true if a real LLM classified it; false if the rule-based fallback did
 */
public record RouteDecision(Intent intent, String reason, boolean llmRouted) {
}
