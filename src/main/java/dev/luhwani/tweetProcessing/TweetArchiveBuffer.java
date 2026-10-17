package dev.luhwani.tweetProcessing;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import dev.luhwani.model.TweetData;

/** Coordinates the bounded archive buffer between the loader and batch filter. */
public final class TweetArchiveBuffer {

    public final BlockingQueue<TweetData> tweets;
    public volatile boolean ready;
    public volatile boolean finished;
    public volatile Throwable failure;

    public TweetArchiveBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Archive buffer capacity must be positive");
        }
        this.tweets = new ArrayBlockingQueue<>(capacity);
    }

    synchronized void signalReady() {
        ready = true;
        // what is the notify all used for?
        notifyAll();
    }

    synchronized void finish(Throwable error) {
        failure = error;
        finished = true;
        ready = true;
        notifyAll();
    }

    synchronized void awaitReady() throws InterruptedException {
        while (!ready && !finished) {
            // what is the wait used for?
            wait();
        }
    }

    synchronized void drained() {
        ready = false;
        notifyAll();
    }
}