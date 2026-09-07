package dev.luhwani.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class Checkpoint {

    private final int lastCompletedBatchNumber;
    private final String lastTweetId;

    @JsonCreator
    public Checkpoint(
            @JsonProperty("last_completed_batch_number") int lastCompletedBatchNumber,
            @JsonProperty("last_tweet_id") String lastTweetId) {
        this.lastCompletedBatchNumber = lastCompletedBatchNumber;
        this.lastTweetId = lastTweetId;
    }

    public static Checkpoint empty() {
        return new Checkpoint(0, "");
    }

    @JsonProperty("last_completed_batch_number")
    public int lastCompletedBatchNumber() {
        return lastCompletedBatchNumber;
    }

    @JsonProperty("last_tweet_id")
    public String lastTweetId() {
        return lastTweetId;
    }

    public boolean isEmpty() {
        if (lastCompletedBatchNumber == 0 && lastTweetId.isEmpty()) {
            return true;
        }
        return false;
    }
}