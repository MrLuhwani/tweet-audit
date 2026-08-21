package dev.luhwani.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record AnalysisResult(
                @JsonProperty("batch_index") int batchIndex,
                List<TweetDecision> results) {

        public AnalysisResult(int batchIndex, List<TweetDecision> results) {
                if (batchIndex < 0) {
                        throw new IllegalArgumentException("[ERROR] Batch index cannot be negative");
                }
                if (results == null || results.isEmpty()) {
                        throw new IllegalArgumentException("[ERROR] A batch cannot be empty");
                }
                this.batchIndex = batchIndex;
                this.results = List.copyOf(results);
        }
}
