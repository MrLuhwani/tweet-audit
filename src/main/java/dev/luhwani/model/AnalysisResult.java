package dev.luhwani.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

/** The decisions returned by an {@link dev.luhwani.ai.AiProvider} for one tweet batch. */
public record AnalysisResult(
    @JsonProperty("batch_number") Integer batchNumber,
    List<TweetDecision> results) {

    /**
     * Creates an immutable analysis result.
     *
     * @param batchNumber number of the analyzed batch
     * @param results non-empty decisions for that batch
     * @throws IllegalArgumentException if the number is negative or results are empty
     */
    public AnalysisResult(Integer batchNumber, List<TweetDecision> results) {
        if (batchNumber < 0) {
            throw new IllegalArgumentException("Batch index cannot be negative");
        }
        if (results == null || results.isEmpty()) {
            throw new IllegalArgumentException("A batch cannot be empty");
        }
        this.batchNumber = batchNumber;
        this.results = List.copyOf(results);
    }

}