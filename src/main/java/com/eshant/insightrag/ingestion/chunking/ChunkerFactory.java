package com.eshant.insightrag.ingestion.chunking;

import com.eshant.insightrag.config.RagProperties;
import org.springframework.stereotype.Component;

/**
 * Builds a {@link Chunker} for a given {@link ChunkingStrategy} using the configured chunk size
 * and overlap. The eval harness calls this with both strategies to compare them; the rest of the
 * app uses the strategy from {@link RagProperties}.
 */
@Component
public class ChunkerFactory {

    private final RagProperties props;

    public ChunkerFactory(RagProperties props) {
        this.props = props;
    }

    public Chunker create(ChunkingStrategy strategy) {
        int size = props.getIngestion().getChunkSizeTokens();
        int overlap = props.getIngestion().getChunkOverlapTokens();
        return switch (strategy) {
            case FIXED -> new FixedSizeChunker(size, overlap);
            case STRUCTURE_AWARE -> new StructureAwareChunker(size, overlap);
        };
    }
}
