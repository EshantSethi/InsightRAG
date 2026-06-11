package com.eshant.insightrag.ingestion;

/**
 * A single retrievable unit of text plus the metadata needed to cite it.
 *
 * <p>The {@code id} is stable and human-readable ({@code source#ordinal}) so it can appear in
 * traces, eval reports, and citations. {@code section} is the heading path the chunk came from
 * (empty for fixed-size chunking, which ignores structure).
 *
 * @param id          stable identifier, e.g. {@code "spring-core.md#3"}
 * @param source      originating document name, used for citations
 * @param section     heading path within the document (e.g. "Dependency Injection > Constructor Injection")
 * @param ordinal     0-based position of this chunk within its source document
 * @param text        the chunk text
 * @param tokenCount  approximate token count (whitespace words) — used for budgeting and logging
 */
public record Chunk(
        String id,
        String source,
        String section,
        int ordinal,
        String text,
        int tokenCount) {

    /** Builds the conventional {@code source#ordinal} id. */
    public static String idFor(String source, int ordinal) {
        return source + "#" + ordinal;
    }
}
