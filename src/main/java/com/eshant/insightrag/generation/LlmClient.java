package com.eshant.insightrag.generation;

/**
 * The single abstraction the rest of the app uses to ask a language model for text.
 *
 * <p>Decoupling everything from a concrete provider behind this interface is what lets the project
 * run 100% free by default (the deterministic {@link MockLlmClient}) yet upgrade to real Claude
 * ({@link AnthropicLlmClient}) just by setting an API key — no call-site changes. The API layer, the
 * agent, and the eval harness all depend only on this interface.
 */
public interface LlmClient {

    /** Sends a fully-formed prompt to the model and returns its text response. */
    String generate(String prompt);

    /** Human-readable provider/model id, surfaced in traces and eval reports. */
    String name();

    /**
     * True if this is the deterministic mock rather than a real LLM. The eval uses this to label
     * results honestly (mock answers are extractive and grounded-by-construction).
     */
    boolean isMock();
}
