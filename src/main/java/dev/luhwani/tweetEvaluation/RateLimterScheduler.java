package dev.luhwani.tweetEvaluation;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import dev.luhwani.model.AnalysisResult;
import dev.luhwani.model.QueueEvent;
import dev.luhwani.model.TweetBatch;

public final class RateLimterScheduler implements AutoCloseable {

    private final AiProvider provider;
    private final Duration interval;
    private final ExecutorService executor;
    private Duration retryAfter;
    private final BlockingQueue<QueueEvent<TweetBatch>> inputQueue;
    private final BlockingQueue<QueueEvent<AnalysisResult>> outputQueue;
    private Future<?> task;

    public RateLimterScheduler(AiProvider provider, BlockingQueue<QueueEvent<TweetBatch>> inputQueue,
            BlockingQueue<QueueEvent<AnalysisResult>> outputQueue, ExecutorService executor) {
        this.provider = provider;
        this.interval = provider.getrequestInterval();
        this.retryAfter = Duration.ZERO;
        this.inputQueue = inputQueue;
        this.outputQueue = outputQueue;
        this.executor = executor;
    }

    public void start() {
        task = executor.submit(this::runLoop);
    }

    public void awaitCompletion() throws InterruptedException, ExecutionException {
        if (task == null) {
            throw new IllegalStateException("Provider has not been started");
        }
        task.get();
    }

    private void runLoop() {
        try {
            while (true) {
                QueueEvent<TweetBatch> event = inputQueue.take();

                switch (event) {
                    case QueueEvent.Item<TweetBatch>(var batch) -> {
                        Instant start = Instant.now();
                        // TODO: remove this once you figure out the average time requests are made
                        System.out.println("batch" + batch.batchIndex() + ": " + start);
                        AnalysisResult result = provider.analyze(batch);
                        System.out.println("Time: " + Duration.between(start, Instant.now()));
                        outputQueue.put(new QueueEvent.Item<AnalysisResult>(result));
                    }
                    case QueueEvent.End<TweetBatch>() -> {
                        return;
                    }
                }
            }
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.err.println(e.getClass());
            e.printStackTrace();
        } finally {
            outputQueue.add(new QueueEvent.End<AnalysisResult>());
        }
    }

    // TODO: Implement
    private void canMakeRequest() throws InterruptedException {
        long sleepTime = retryAfter.toMillis() + interval.toMillis();
        Thread.sleep(sleepTime);
    }

    // TODO: Implement
    private void retryPolicy(Exception e) {

    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
