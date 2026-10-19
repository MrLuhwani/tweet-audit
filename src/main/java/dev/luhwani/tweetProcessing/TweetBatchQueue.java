package dev.luhwani.tweetProcessing;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import dev.luhwani.model.TweetBatch;

/** Bounded hand-off state between the batch filter and request workers.
 *
 * <p>The filter marks this queue finished after the archive has been consumed. A non-null
 * failure signals request workers that the producer could not complete normally.</p>
 */
public final class TweetBatchQueue {

    public final BlockingQueue<TweetBatch> batches = new ArrayBlockingQueue<>(TweetBatchFilter.BUFFER_BATCHES);
    public volatile boolean finished;
    public volatile Throwable failure;
}