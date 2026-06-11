package com.eshant.insightrag.embedding;

import com.eshant.insightrag.ingestion.Chunk;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.segment.TextSegment;

/**
 * Converts between our domain {@link Chunk} and LangChain4j's {@link TextSegment}. Chunk metadata
 * (id, source, section, ordinal) is stored on the segment so a vector-store hit can be turned back
 * into a fully-attributed, citable chunk without a separate lookup table.
 */
public final class ChunkSegments {

    public static final String META_CHUNK_ID = "chunkId";
    public static final String META_SOURCE = "source";
    public static final String META_SECTION = "section";
    public static final String META_ORDINAL = "ordinal";

    private ChunkSegments() {
    }

    public static TextSegment toSegment(Chunk chunk) {
        Metadata md = new Metadata();
        md.put(META_CHUNK_ID, chunk.id());
        md.put(META_SOURCE, chunk.source());
        md.put(META_SECTION, chunk.section() == null ? "" : chunk.section());
        md.put(META_ORDINAL, String.valueOf(chunk.ordinal()));
        return TextSegment.from(chunk.text(), md);
    }

    public static Chunk fromSegment(TextSegment segment) {
        Metadata md = segment.metadata();
        String id = md.getString(ChunkSegments.META_CHUNK_ID);
        String source = md.getString(ChunkSegments.META_SOURCE);
        String section = md.getString(ChunkSegments.META_SECTION);
        Integer ordinal = md.getInteger(ChunkSegments.META_ORDINAL);
        String text = segment.text();
        return new Chunk(
                id != null ? id : "unknown#0",
                source != null ? source : "unknown",
                section != null ? section : "",
                ordinal != null ? ordinal : 0,
                text,
                com.eshant.insightrag.ingestion.chunking.Chunker.approxTokens(text));
    }
}
