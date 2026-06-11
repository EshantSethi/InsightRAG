package com.eshant.insightrag.ingestion.chunking;

import com.eshant.insightrag.ingestion.Chunk;
import com.eshant.insightrag.ingestion.RawDocument;

import java.util.ArrayList;
import java.util.List;

/**
 * Naive baseline chunker (the "v0" path). Treats the document as a flat stream of words and
 * cuts a fixed-size sliding window with overlap — no awareness of headings, paragraphs, or code
 * blocks. This is deliberately the strategy that breaks on structured docs (a heading ends up in
 * one chunk, its definition in the next), so the eval can show how much structure-aware chunking
 * helps.
 */
public class FixedSizeChunker implements Chunker {

    private final int chunkSizeTokens;
    private final int overlapTokens;

    public FixedSizeChunker(int chunkSizeTokens, int overlapTokens) {
        if (overlapTokens >= chunkSizeTokens) {
            throw new IllegalArgumentException("overlap must be smaller than chunk size");
        }
        this.chunkSizeTokens = chunkSizeTokens;
        this.overlapTokens = overlapTokens;
    }

    @Override
    public List<Chunk> chunk(RawDocument document) {
        String[] words = document.content().trim().split("\\s+");
        List<Chunk> chunks = new ArrayList<>();
        int step = chunkSizeTokens - overlapTokens;
        int ordinal = 0;

        for (int start = 0; start < words.length; start += step) {
            int end = Math.min(start + chunkSizeTokens, words.length);
            String text = String.join(" ", List.of(words).subList(start, end));
            chunks.add(new Chunk(
                    Chunk.idFor(document.source(), ordinal),
                    document.source(),
                    "",                       // fixed-size chunking has no section awareness
                    ordinal,
                    text,
                    end - start));
            ordinal++;
            if (end == words.length) {
                break;
            }
        }
        return chunks;
    }
}
