package dev.luhwani.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public final class AnalysisResult {

    private final int batchIndex;
    private final List<TweetDecision> results;

    @JsonCreator
    public AnalysisResult(
            @JsonProperty("batch_index") int batchIndex,
            @JsonProperty("results") List<TweetDecision> results) {
        if (batchIndex < 0) {
            throw new IllegalArgumentException("Batch index cannot be negative");
        }
        if (results == null || results.isEmpty()) {
            throw new IllegalArgumentException("A batch cannot be empty");
        }
        this.batchIndex = batchIndex;
        this.results = List.copyOf(results);
    }

    public int batchIndex() {
        return batchIndex;
    }

    public List<TweetDecision> results() {
        return results;
    }
}