package com.eshant.insightrag.ingestion.chunking;

/**
 * Selects how documents are split. Exposed in config so the eval harness can compare the
 * naive baseline against the improved strategy and quantify the difference.
 */
public enum ChunkingStrategy {
    /** Naive baseline: fixed-size sliding window over raw words, ignores document structure. */
    FIXED,
    /** Improved: respects markdown headings, paragraphs, and code blocks; carries section metadata. */
    STRUCTURE_AWARE
}
