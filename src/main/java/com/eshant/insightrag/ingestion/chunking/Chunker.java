package com.eshant.insightrag.ingestion.chunking;

import com.eshant.insightrag.ingestion.Chunk;
import com.eshant.insightrag.ingestion.RawDocument;

import java.util.List;

/**
 * Splits a {@link RawDocument} into retrievable {@link Chunk}s. Implementations are swappable
 * so retrieval quality can be measured per strategy (see {@link ChunkingStrategy}).
 */
public interface Chunker {

    List<Chunk> chunk(RawDocument document);

    /** Approximate token count: whitespace-delimited words. Good enough for chunk budgeting. */
    static int approxTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }
}
