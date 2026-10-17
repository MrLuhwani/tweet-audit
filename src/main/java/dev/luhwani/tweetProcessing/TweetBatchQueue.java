package dev.luhwani.tweetProcessing;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import dev.luhwani.model.TweetBatch;

/** Bounded hand-off state between the batch filter and request workers. */
public final class TweetBatchQueue {

    public final BlockingQueue<TweetBatch> batches = new ArrayBlockingQueue<>(TweetBatchFilter.BUFFER_BATCHES);
    public volatile boolean finished;
    public volatile Throwable failure;
}