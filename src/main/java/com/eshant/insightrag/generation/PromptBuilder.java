package com.eshant.insightrag.generation;

import com.eshant.insightrag.retrieval.RetrievedChunk;

import java.util.List;

/**
 * Assembles the grounded-QA prompt from a question and its retrieved context.
 *
 * <p>The prompt encodes the two rules that make this RAG trustworthy rather than a confident
 * guesser: <b>cite</b> every claim with a {@code [n]} marker pointing at a numbered context block,
 * and <b>abstain</b> when the answer isn't present. The context is emitted as one numbered,
 * source-labelled line per chunk — a format both a real LLM and the deterministic
 * {@link MockLlmClient} can consume (the mock parses it directly).
 */
public final class PromptBuilder {

    /** Marker that begins the numbered context block. */
    public static final String CONTEXT_HEADER = "CONTEXT:";
    /** Marker that begins the user question. */
    public static final String QUESTION_HEADER = "QUESTION:";
    /** Canonical phrase used to abstain; the grounding/abstention logic keys off this. */
    public static final String ABSTAIN_ANSWER =
            "I don't have enough information in the provided documentation to answer that.";

    private PromptBuilder() {
    }

    /**
     * Builds the full prompt. Each chunk becomes a single line {@code [n] (source) text} so citations
     * map cleanly to sources and the line is trivially parseable.
     */
    public static String build(String question, List<RetrievedChunk> context) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a precise documentation assistant. Answer the question using ONLY the ")
                .append("numbered context below. Cite the sources you use with their bracket numbers, ")
                .append("e.g. [1]. If the context does not contain the answer, reply exactly: \"")
                .append(ABSTAIN_ANSWER).append("\" Do not use outside knowledge or guess.\n\n");

        sb.append(CONTEXT_HEADER).append('\n');
        if (context.isEmpty()) {
            sb.append("(no relevant context retrieved)\n");
        } else {
            for (int i = 0; i < context.size(); i++) {
                RetrievedChunk rc = context.get(i);
                sb.append('[').append(i + 1).append("] (").append(rc.chunk().source()).append(") ")
                        .append(oneLine(rc.chunk().text())).append('\n');
            }
        }

        sb.append('\n').append(QUESTION_HEADER).append(' ').append(question).append('\n');
        sb.append("ANSWER:");
        return sb.toString();
    }

    /** Collapses whitespace/newlines so each chunk occupies exactly one prompt line. */
    private static String oneLine(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }
}
