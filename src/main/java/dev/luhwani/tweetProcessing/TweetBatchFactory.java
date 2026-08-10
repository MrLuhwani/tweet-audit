package dev.luhwani.tweetProcessing;

import java.util.ArrayList;
import java.util.List;

import dev.luhwani.model.TweetBatch;
import dev.luhwani.model.TweetData;

public final class TweetBatchFactory {

    private static final int BATCH_SIZE = 30;

    private TweetBatchFactory() {
    }

    public static List<TweetBatch> createBatches(List<TweetData> tweets) {
        List<TweetBatch> batches = new ArrayList<>();
        for (int start = 0, index = 0; start < tweets.size(); start += BATCH_SIZE, index++) {
            int end = Math.min(start + BATCH_SIZE, tweets.size());
            batches.add(new TweetBatch(index, tweets.subList(start, end)));
        }
        return List.copyOf(batches);
    }
}
