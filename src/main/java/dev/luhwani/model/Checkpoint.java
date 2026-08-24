package dev.luhwani.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class Checkpoint {

    private final int lastCompletedBatchIndex;
    private final String lastTweetId;

    @JsonCreator
    public Checkpoint(
            @JsonProperty("last_completed_batch_index") int lastCompletedBatchIndex,
            @JsonProperty("last_tweet_id") String lastTweetId) {
        this.lastCompletedBatchIndex = lastCompletedBatchIndex;
        this.lastTweetId = lastTweetId;
    }

    public static Checkpoint empty() {
        return new Checkpoint(-1, null);
    }

    public int nextBatchIndex() {
        return lastCompletedBatchIndex + 1;
    }

    @JsonProperty("last_completed_batch_index")
    public int lastCompletedBatchIndex() {
        return lastCompletedBatchIndex;
    }

    @JsonProperty("last_tweet_id")
    public String lastTweetId() {
        return lastTweetId;
    }
}