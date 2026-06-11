package com.eshant.insightrag.ingestion;

import com.eshant.insightrag.ingestion.chunking.Chunker;
import com.eshant.insightrag.ingestion.chunking.ChunkerFactory;
import com.eshant.insightrag.ingestion.chunking.ChunkingStrategy;
import com.eshant.insightrag.retrieval.Bm25Index;
import com.eshant.insightrag.retrieval.VectorIndex;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Owns the live indexes and (re)builds them from the data directory. Centralising indexing here is
 * what lets the {@code POST /ingest} endpoint refresh the corpus at runtime — the same indexes the
 * {@link com.eshant.insightrag.retrieval.RetrievalService} bean queries are cleared and rebuilt in
 * place, so retrieval picks up new documents without a restart.
 *
 * <p>The initial build happens once at startup via {@link #ingest()} in {@link PostConstruct}.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);

    private final DocumentLoader loader;
    private final ChunkerFactory chunkerFactory;
    private final VectorIndex vectorIndex;
    private final Bm25Index keywordIndex;

    private volatile IngestionStats lastStats = new IngestionStats(0, 0);

    public IngestionService(DocumentLoader loader, ChunkerFactory chunkerFactory,
                            VectorIndex vectorIndex, Bm25Index keywordIndex) {
        this.loader = loader;
        this.chunkerFactory = chunkerFactory;
        this.vectorIndex = vectorIndex;
        this.keywordIndex = keywordIndex;
    }

    /** Loads + chunks the data dir (structure-aware) and rebuilds both indexes from scratch. */
    @PostConstruct
    public synchronized IngestionStats ingest() {
        Chunker chunker = chunkerFactory.create(ChunkingStrategy.STRUCTURE_AWARE);
        List<RawDocument> docs = loader.loadAll();
        List<Chunk> chunks = docs.stream()
                .flatMap(doc -> chunker.chunk(doc).stream())
                .toList();

        vectorIndex.clear();
        vectorIndex.index(chunks);
        keywordIndex.reset();
        keywordIndex.index(chunks);

        lastStats = new IngestionStats(docs.size(), chunks.size());
        log.info("Ingestion complete: {} documents -> {} chunks indexed", docs.size(), chunks.size());
        return lastStats;
    }

    public IngestionStats lastStats() {
        return lastStats;
    }

    /** Counts from the most recent ingestion. */
    public record IngestionStats(int documents, int chunks) {
    }
}
