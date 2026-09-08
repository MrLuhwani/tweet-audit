package dev.luhwani.tweetProcessing;

import java.util.ArrayList;
import java.util.List;

import dev.luhwani.model.TweetBatch;
import dev.luhwani.model.TweetData;

public final class TweetBatchFactory {

    // TODO: make more configurable
    public static final int BATCH_SIZE = 15;

    private TweetBatchFactory() {
    }

    public static List<TweetBatch> createBatches(List<TweetData> tweets, int lastCompletedBatch) {
        if (lastCompletedBatch == Math.ceilDiv(tweets.size(), BATCH_SIZE)) {
            return List.of();
        }
        if (lastCompletedBatch > Math.ceilDiv(tweets.size(), BATCH_SIZE)) {
            throw new IllegalStateException(
                    "Last completed batch in checkpoint is ahead of available tweet batches.\n Confirm if batch size or checkpoint.json was edited on last run.");
        }
        List<TweetBatch> batches = new ArrayList<>();
        int start = lastCompletedBatch * BATCH_SIZE;
        for (int batchNum = lastCompletedBatch + 1; start < tweets.size(); start += BATCH_SIZE, batchNum++) {
            int end = Math.min(start + BATCH_SIZE, tweets.size());
            batches.add(new TweetBatch(batchNum, tweets.subList(start, end)));
        }
        return List.copyOf(batches);
    }
}
