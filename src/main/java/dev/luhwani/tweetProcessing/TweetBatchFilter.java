package dev.luhwani.tweetProcessing;

import java.util.ArrayList;
import java.util.List;

import dev.luhwani.error.FatalException;
import dev.luhwani.model.Checkpoint;
import dev.luhwani.model.TweetBatch;
import dev.luhwani.model.TweetData;

/**
 * Converts bounded archive chunks into batches for request workers.
 * Batches in the checkpoint are filtered out and discarded
 */
public final class TweetBatchFilter {

    /** Number of batches retained by each bounded pipeline window. */
    public static final int BUFFER_BATCHES = 9;

    private final TweetArchiveBuffer archiveBuffer;
    private final TweetBatchQueue batchQueue;
    private final Checkpoint checkpoint;
    private final int batchSize;

    /**
     * Creates a filter for one bounded pipeline window.
     *
     * @param archiveBuffer producer state and queued tweets
     * @param batchQueue    downstream batches and completion state
     * @param checkpoint    batches evaluated on previous run
     * @param batchSize     maximum tweets per output batch
     */
    public TweetBatchFilter(TweetArchiveBuffer archiveBuffer, TweetBatchQueue batchQueue,
            Checkpoint checkpoint, int batchSize) {
        if (archiveBuffer == null || batchQueue == null || checkpoint == null) {
            throw new IllegalArgumentException("Pipeline dependencies must not be null");
        }
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be positive");
        }
        this.archiveBuffer = archiveBuffer;
        this.batchQueue = batchQueue;
        this.checkpoint = checkpoint;
        this.batchSize = batchSize;
    }

    /**
     * Runs until the archive producer has finished and the archive buffer is empty.
     *
     * <p>
     * Successful checkpointed batches are consumed but not offered downstream.
     * Producer or
     * request-worker failures are copied to {@link TweetBatchQueue#failure}, and
     * the queue is
     * always marked finished before this method returns or throws.
     * </p>
     *
     * @throws FatalException if interrupted or if downstream workers have failed
     */
    public void run() throws FatalException {
        int nextBatchNumber = 1;
        try {
            while (true) {
                archiveBuffer.awaitReady();
                List<TweetData> tweets = new ArrayList<>();
                archiveBuffer.tweets.drainTo(tweets);
                archiveBuffer.drained();

                if (!tweets.isEmpty()) {
                    for (TweetBatch batch : createBatches(tweets, batchSize, nextBatchNumber)) {
                        if (!checkpoint.successfulBatches().contains(batch.batchNumber())) {
                            while (!batchQueue.batches.offer(batch, 300, java.util.concurrent.TimeUnit.MILLISECONDS)) {
                                if (batchQueue.failure != null) {
                                    throw new FatalException("Request executor failed", batchQueue.failure);
                                }
                            }
                        }
                        nextBatchNumber++;
                    }
                }
                if (archiveBuffer.finished && archiveBuffer.tweets.isEmpty()) {
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            batchQueue.failure = e;
            throw new FatalException("Tweet batch filtering was interrupted", e);
        } finally {
            batchQueue.failure = batchQueue.failure == null ? archiveBuffer.failure : batchQueue.failure;
            batchQueue.finished = true;
        }
    }

    private static List<TweetBatch> createBatches(List<TweetData> tweets, int batchSize, int firstBatchNumber) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("Batch size must be positive");
        }
        if (tweets == null) {
            throw new IllegalArgumentException("tweets cannot be null");
        }
        if (tweets.isEmpty()) {
            throw new IllegalArgumentException("Cannot convert empty tweet list to batches");
        }
        List<TweetBatch> batches = new ArrayList<>();
        for (int batchNumber = firstBatchNumber, start = 0; start < tweets.size(); start += batchSize, batchNumber++) {
            int end = Math.min(start + batchSize, tweets.size());
            batches.add(new TweetBatch(batchNumber, tweets.subList(start, end)));
        }
        return List.copyOf(batches);
    }
}