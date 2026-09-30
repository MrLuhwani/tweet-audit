package dev.luhwani.tweetProcessing;

import java.util.ArrayList;
import java.util.List;

import dev.luhwani.model.TweetBatch;
import dev.luhwani.model.TweetData;

/** Creates fixed-size, sequentially numbered batches from archived tweets. */
public final class TweetBatchFactory {

    // TODO: make more configurable
    private static final int BATCH_SIZE = 60;

    private TweetBatchFactory() {
    }

    // for easy testablility, this method is package-private
    static List<TweetBatch> createBatches(List<TweetData> tweets, int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch Sizes must be positive");
        }
        if (tweets == null) {
            throw new IllegalArgumentException("tweets cannot be null");
        }
        if (tweets.isEmpty()) {
            throw new IllegalArgumentException("Cannot convert empty tweet list to batches");
        }
        List<TweetBatch> batches = new ArrayList<>();
        for (int batchNum = 1, start = 0; start < tweets.size(); start += batchSize, batchNum++) {
            int end = Math.min(start + batchSize, tweets.size());
            batches.add(new TweetBatch(batchNum, tweets.subList(start, end)));
        }
        return List.copyOf(batches);
    }

    /**
     * Creates sequential batches using the application's default batch size.
     *
     * @param tweets tweets to divide into batches
     * @return immutable, sequentially numbered batches
     * @throws IllegalArgumentException if {@code tweets} is null or empty
     */
    public static List<TweetBatch> createBatches(List<TweetData> tweets) {
        return createBatches(tweets, BATCH_SIZE);
    }
}
