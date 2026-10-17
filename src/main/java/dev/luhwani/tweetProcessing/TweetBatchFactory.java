package dev.luhwani.tweetProcessing;

import java.util.List;

import dev.luhwani.model.TweetBatch;
import dev.luhwani.model.TweetData;

/** Creates fixed-size, sequentially numbered batches from archived tweets. */
public final class TweetBatchFactory {

    private static final int BATCH_SIZE = 60;

    private TweetBatchFactory() {
    }

    /**
     * Creates sequential batches using the application's default batch size.
     *
     * @param tweets tweets to divide into batches
     * @param batchSize the number of tweets in each batch
     * @return immutable, sequentially numbered batches
     * @throws IllegalArgumentException if {@code tweets} is null or empty
     */
    public static List<TweetBatch> createBatches(List<TweetData> tweets, int batchSize) {
        return TweetBatchFilter.createBatches(tweets, batchSize);
    }

    static List<TweetBatch> createBatches(List<TweetData> tweets) {
        return createBatches(tweets, BATCH_SIZE);
    }

}
