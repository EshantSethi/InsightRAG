package com.eshant.insightrag.retrieval;

import java.util.ArrayList;
import java.util.List;

/**
 * A step-by-step record of how a single query moved through the retrieval pipeline.
 *
 * <p>This is the backbone of the project's "fully traceable" claim: each stage (vector search,
 * keyword search, fusion, re-rank) appends an entry with its candidate count, elapsed time, and a
 * short note. The trace is surfaced by the API's debug mode and is invaluable when explaining, in
 * the README's failure-analysis section, <em>why</em> a particular answer was or wasn't grounded.
 *
 * <p>Mutable by intent — it is filled in as the pipeline executes — but only the owning
 * {@link RetrievalService} writes to it; callers read.
 */
public class QueryTrace {

    /** One pipeline stage's outcome. */
    public record Stage(String name, int candidateCount, long elapsedMillis, String note) {
    }

    private final String query;
    private final RetrievalStrategy strategy;
    private final List<Stage> stages = new ArrayList<>();
    private double confidence;

    public QueryTrace(String query, RetrievalStrategy strategy) {
        this.query = query;
        this.strategy = strategy;
    }

    /** Records a completed stage. */
    public void addStage(String name, int candidateCount, long elapsedMillis, String note) {
        stages.add(new Stage(name, candidateCount, elapsedMillis, note));
    }

    /** Records the retrieval confidence (max raw cosine, pre-rerank) that drove the abstention gate. */
    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    /** The retrieval confidence for this query — see {@link RetrievalResult#confidence()}. */
    public double confidence() {
        return confidence;
    }

    public String query() {
        return query;
    }

    public RetrievalStrategy strategy() {
        return strategy;
    }

    public List<Stage> stages() {
        return List.copyOf(stages);
    }

    /** Total time across all recorded stages, in milliseconds. */
    public long totalMillis() {
        return stages.stream().mapToLong(Stage::elapsedMillis).sum();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("QueryTrace[").append(strategy).append("] \"")
                .append(query).append("\"\n");
        for (Stage s : stages) {
            sb.append("  - ").append(s.name()).append(": ").append(s.candidateCount())
                    .append(" candidates, ").append(s.elapsedMillis()).append("ms");
            if (s.note() != null && !s.note().isEmpty()) {
                sb.append(" (").append(s.note()).append(")");
            }
            sb.append('\n');
        }
        sb.append("  total: ").append(totalMillis()).append("ms");
        sb.append(String.format("  (confidence=%.3f)", confidence));
        return sb.toString();
    }
}
