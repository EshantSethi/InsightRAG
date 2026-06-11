package com.eshant.insightrag.retrieval;

import com.eshant.insightrag.embedding.ChunkSegments;
import com.eshant.insightrag.embedding.EmbeddingService;
import com.eshant.insightrag.ingestion.Chunk;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingStore;

import java.util.ArrayList;
import java.util.List;

/**
 * Dense / semantic retriever: embeds chunks into a vector store and answers queries by cosine
 * similarity. This is the {@code VECTOR} arm of the pipeline and, on its own, the whole of the
 * naive (v0) retrieval strategy.
 *
 * <p><strong>Instantiable by design.</strong> It is not a Spring singleton — it holds its own
 * {@link EmbeddingStore}. The eval harness constructs one {@code VectorIndex} per pipeline (v0 and
 * v1) over a throwaway in-memory store so the two configurations never share state. The embedding
 * <em>model</em> ({@link EmbeddingService}) is the one shared bean; only the index is per-pipeline.
 */
public class VectorIndex {

    private final EmbeddingService embeddingService;
    private final EmbeddingStore<TextSegment> store;

    public VectorIndex(EmbeddingService embeddingService, EmbeddingStore<TextSegment> store) {
        this.embeddingService = embeddingService;
        this.store = store;
    }

    /** Removes everything from the underlying store so the index can be rebuilt (used by re-ingest). */
    public void clear() {
        store.removeAll();
    }

    /**
     * Embeds the given chunks and adds them to the store. Embeddings and segments are built in the
     * same order so each vector lines up with the segment carrying its citation metadata.
     */
    public void index(List<Chunk> chunks) {
        if (chunks.isEmpty()) {
            return;
        }
        List<Embedding> embeddings = embeddingService.embedChunks(chunks);
        List<TextSegment> segments = chunks.stream().map(ChunkSegments::toSegment).toList();
        store.addAll(embeddings, segments);
    }

    /**
     * Returns the top-{@code topK} chunks by semantic similarity to {@code query}, dropping any
     * below {@code minScore}.
     *
     * @param topK     maximum number of results to return
     * @param minScore minimum cosine score [0,1]; matches below this are discarded by the store
     */
    public List<RetrievedChunk> search(String query, int topK, double minScore) {
        Embedding queryEmbedding = embeddingService.embed(query);
        EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(topK)
                .minScore(minScore)
                .build();
        List<EmbeddingMatch<TextSegment>> matches = store.search(request).matches();

        List<RetrievedChunk> results = new ArrayList<>(matches.size());
        for (int rank = 0; rank < matches.size(); rank++) {
            EmbeddingMatch<TextSegment> match = matches.get(rank);
            Chunk chunk = ChunkSegments.fromSegment(match.embedded());
            results.add(new RetrievedChunk(chunk, match.score(), RetrievedChunk.Origin.VECTOR, rank));
        }
        return results;
    }
}
