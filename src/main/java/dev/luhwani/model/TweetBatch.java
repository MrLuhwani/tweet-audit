package dev.luhwani.model;

import java.util.List;

/** An immutable, numbered group of tweets sent to the evaluation provider. */
public record TweetBatch(int batchNumber, List<TweetData> tweets) {
    public TweetBatch(int batchNumber, List<TweetData> tweets) {
        if (batchNumber < 0) {
            throw new IllegalArgumentException("Batch index cannot be negative");
        }
        if (tweets == null || tweets.isEmpty()) {
            throw new IllegalArgumentException("A batch cannot be empty");
        }
        this.batchNumber = batchNumber;
        this.tweets = List.copyOf(tweets);
    }
}
