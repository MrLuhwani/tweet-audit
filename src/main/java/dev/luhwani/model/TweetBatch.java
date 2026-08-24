package dev.luhwani.model;

import java.util.List;

public final class TweetBatch {
    private final int batchIndex;
    private final List<TweetData> tweets;

    public TweetBatch(int batchIndex, List<TweetData> tweets) {
        if (batchIndex < 0) {
            throw new IllegalArgumentException("Batch index cannot be negative");
        }
        if (tweets == null || tweets.isEmpty()) {
            throw new IllegalArgumentException("A batch cannot be empty");
        }
        this.batchIndex = batchIndex;
        this.tweets = List.copyOf(tweets);
    }

    public int batchIndex() {
        return batchIndex;
    }

    public List<TweetData> tweets() {
        return tweets;
    }

}