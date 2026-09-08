package dev.luhwani.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public final class AnalysisResult {

    private final int batchNumber;
    private final List<TweetDecision> results;

    @JsonCreator
    public AnalysisResult(
            @JsonProperty("batch_number") int batchNumber,
            @JsonProperty("results") List<TweetDecision> results) {
        if (batchNumber < 0) {
            throw new IllegalArgumentException("Batch index cannot be negative");
        }
        if (results == null || results.isEmpty()) {
            throw new IllegalArgumentException("A batch cannot be empty");
        }
        this.batchNumber = batchNumber;
        this.results = List.copyOf(results);
    }

    public int batchNumber() {
        return batchNumber;
    }

    public List<TweetDecision> results() {
        return results;
    }
}