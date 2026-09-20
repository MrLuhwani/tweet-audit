package dev.luhwani.model;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Stores completed and failed batches so an audit can resume safely. */
public final class Checkpoint {

    private final Set<Integer> successfulBatches;
    private final Set<Integer> failedBatches;
    private final int lastProcessedBatch;
    private final String lastProcessedTweet;

    @JsonCreator
    public Checkpoint(
            @JsonProperty("successful_batches") Set<Integer> successfulBatches,
            @JsonProperty("failed_batches") Set<Integer> failedBatches,
            @JsonProperty("last_processed_batch") int lastProcessedBatch,
            @JsonProperty("last_processed_tweet") String lastProcessedTweet) {
        this.successfulBatches = successfulBatches;
        this.failedBatches = failedBatches;
        this.lastProcessedBatch = lastProcessedBatch;
        this.lastProcessedTweet = lastProcessedTweet;
    }

    public static Checkpoint empty() {
        return new Checkpoint(Set.of(), Set.of(), 0, "");
    }

    public Set<Integer> getSuccessfulBatches() {
        return successfulBatches;
    }

    public Set<Integer> getFailedBatches() {
        return failedBatches;
    }

    public int getLastProcessedBatch() {
        return lastProcessedBatch;
    }

    public String getLastProcessedTweet() {
        return lastProcessedTweet;
    }

    public boolean isEmpty() {
        return successfulBatches.isEmpty() && failedBatches.isEmpty() && (lastProcessedBatch == 0) && lastProcessedTweet.isBlank();
    }
}