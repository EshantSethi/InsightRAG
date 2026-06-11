package com.eshant.insightrag.embedding;

import com.eshant.insightrag.ingestion.Chunk;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Thin wrapper over the LangChain4j {@link EmbeddingModel}. Centralises embedding so the provider
 * is swappable in one place and the rest of the code deals in domain types ({@link Chunk}) rather
 * than LangChain4j primitives. The model itself (in-process all-MiniLM-L6-v2) is a shared bean.
 */
@Service
public class EmbeddingService {

    private final EmbeddingModel model;

    public EmbeddingService(EmbeddingModel model) {
        this.model = model;
    }

    /** Embeds a single query string. */
    public Embedding embed(String text) {
        return model.embed(text).content();
    }

    /** Embeds a batch of chunks, preserving order so embeddings line up with their segments. */
    public List<Embedding> embedChunks(List<Chunk> chunks) {
        List<TextSegment> segments = chunks.stream().map(ChunkSegments::toSegment).toList();
        return model.embedAll(segments).content();
    }

    public int dimension() {
        return model.dimension();
    }
}
