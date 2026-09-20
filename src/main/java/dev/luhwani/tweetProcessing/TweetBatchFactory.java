package dev.luhwani.tweetProcessing;

import java.util.ArrayList;
import java.util.List;

import dev.luhwani.model.TweetBatch;
import dev.luhwani.model.TweetData;

/** Creates fixed-size, sequentially numbered batches from archived tweets. */
public final class TweetBatchFactory {

    // TODO: make more configurable
    private static final int BATCH_SIZE = 5;

    private TweetBatchFactory() {
    }

    public static List<TweetBatch> createBatches(List<TweetData> tweets, int lastCompletedBatch) {
        List<TweetBatch> batches = new ArrayList<>();
        int start = lastCompletedBatch * BATCH_SIZE;
        for (int batchNum = lastCompletedBatch + 1; start < tweets.size(); start += BATCH_SIZE, batchNum++) {
            int end = Math.min(start + BATCH_SIZE, tweets.size());
            batches.add(new TweetBatch(batchNum, tweets.subList(start, end)));
        }
        batches.sort((b1, b2) -> Integer.compare(b1.batchNumber(), b2.batchNumber()));
        return List.copyOf(batches);
    }
}
