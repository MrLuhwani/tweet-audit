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

    /**
     * Removes batches recorded as successfully processed in a checkpoint.
     *
     * @param tweetBatches batches available for processing
     * @param checkpoint persisted processing progress
     * @return an immutable list containing only unfinished batches
     */
    public static List<TweetBatch> filter(List<TweetBatch> tweetBatches, Checkpoint checkpoint) {
        return tweetBatches.stream()
                .filter(batch -> !checkpoint.successfulBatches().contains(batch.batchNumber()))
                .toList();
    }

}
