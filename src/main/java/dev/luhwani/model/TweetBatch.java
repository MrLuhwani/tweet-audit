package dev.luhwani.model;

import java.util.List;

public record TweetBatch(int batchIndex, List<TweetData> tweets) {
    public TweetBatch {
        if (batchIndex < 0) {
            throw new IllegalArgumentException("Batch index cannot be negative");
        }
        tweets = List.copyOf(tweets);
        if (tweets.isEmpty()) {
            throw new IllegalArgumentException("A batch cannot be empty");
        }
    }
}
