package dev.luhwani.tweetProcessing;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import dev.luhwani.model.TweetData;

/**
 * Coordinates the bounded, back-pressured hand-off between the loader and the
 * filter
 * @see TweetArchiveLoader
 * @see TweetBatchFilter
 */
public final class TweetArchiveBuffer {

    public final BlockingQueue<TweetData> tweets;

    public volatile boolean ready;
    public volatile boolean finished;
    // we store failures so that, if any occurs, the pipeline
    // between the Archive loader, and the Batch Filter
    // can be notified
    public volatile Throwable failure;

    /** Creates a buffer with the supplied maximum number of queued tweets. */
    public TweetArchiveBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Archive buffer capacity must be positive");
        }
        this.tweets = new ArrayBlockingQueue<>(capacity);
    }

    /**
     * Called by the {@code producer} to signal to the consumer
     * that the queue of TweetData can be drained
     */
    synchronized void signalReady() {
        ready = true;
        notifyAll();
    }

    /**
     * Called by the {@code consumer} to signal to the {@code producer}
     * that the tweet queue has been drained, and it can continue
     * parsing through the archive
     */
    synchronized void drained() {
        ready = false;
        notifyAll();
    }

    /**
     * Used to signal the archiveBuffer -> batchFilter pipeline
     * that processing is complete
     * 
     * @param error error tht occured while tweets were moving
     * from the buffer to the batch filter
     */
    synchronized void finish(Throwable error) {
        failure = error;
        finished = true;
        ready = true;
        notifyAll();
    }

    /**
     * The caller of this method has to wait, until it is
     * signaled by another thread that it can resume processing
     * @throws InterruptedException
     */
    synchronized void awaitReady() throws InterruptedException {
        while (!ready && !finished) {
            wait();
        }
    }
}