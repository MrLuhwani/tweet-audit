package dev.luhwani.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public final class TweetBatch {
    private final int batchNumber;
    private final List<TweetData> tweets;

    @JsonCreator
    public TweetBatch(
        @JsonProperty("batchNumber") int batchNumber, 
        @JsonProperty("tweets") List<TweetData> tweets
    ) {
        if (batchNumber < 0) {
            throw new IllegalArgumentException("Batch index cannot be negative");
        }
        if (tweets == null || tweets.isEmpty()) {
            throw new IllegalArgumentException("A batch cannot be empty");
        }
        this.batchNumber = batchNumber;
        this.tweets = List.copyOf(tweets);
    }

    @JsonProperty("batchNumber")
    public int batchNumber() {
        return batchNumber;
    }

    @JsonProperty("tweets")
    public List<TweetData> tweets() {
        return tweets;
    }
}
