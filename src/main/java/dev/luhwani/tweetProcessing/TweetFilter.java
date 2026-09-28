package dev.luhwani.tweetProcessing;

import java.util.List;

import dev.luhwani.model.Checkpoint;
import dev.luhwani.model.TweetBatch;

/**
 * 
 * Filters tweets that have successfully been audited from
 * a given list of tweetbatches
 */
public final class TweetFilter {

    public static List<TweetBatch> filter(List<TweetBatch> tweetBatches, Checkpoint checkpoint) {
        return tweetBatches.stream()
                .filter(batch -> !checkpoint.getSuccessfulBatches().contains(batch.batchNumber()))
                .toList();
    }

}
