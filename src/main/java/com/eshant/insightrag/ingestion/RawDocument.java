package com.eshant.insightrag.ingestion;

/**
 * A document as loaded from disk, before chunking.
 *
 * @param source the logical source name (the file name, used later for citations)
 * @param content the full raw text (markdown) of the document
 */
public record RawDocument(String source, String content) {
}
