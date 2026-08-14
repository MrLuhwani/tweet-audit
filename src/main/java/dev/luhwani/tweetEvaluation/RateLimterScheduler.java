package dev.luhwani.tweetEvaluation;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import dev.luhwani.model.TweetBatch;

public final class RateLimterScheduler implements AutoCloseable {

    private final AiProvider provider;
    private final Duration interval;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile boolean running;
    private Duration retryAfter;
    private final BlockingQueue<TweetBatch> queue;
    private Future<?> task;

    public RateLimterScheduler(AiProvider provider, BlockingQueue<TweetBatch> queue) {
        this.provider = provider;
        this.interval = provider.getIntervalMillis();
        this.retryAfter = Duration.ZERO;
        this.queue = queue;
    }

    public void start() {
        if (running) {
            throw new IllegalStateException("Scheduler is already running");
        }
        running = true;
        task = executor.submit(this::runLoop);
    }

    public void awaitCompletion() throws InterruptedException, ExecutionException {
        if (task == null) {
            throw new IllegalStateException("Provider has not been started");
        }
        task.get();
    }

    private Object runLoop() throws InterruptedException {
        while (running) {
            canMakeRequest();
            TweetBatch batch = queue.poll();
            if (batch == null) {
                break;
            }
            provider.analyze(batch);
        }
        return null;

    }

    private void canMakeRequest() throws InterruptedException {
        long sleepTime = retryAfter.toMillis() + interval.toMillis();
        Thread.sleep(sleepTime);
    }

    // TODO: Implement
    private void retryPolicy(Exception e) {

    }

    @Override
    public void close() {
        running = false;
        executor.shutdown();
    }
}
