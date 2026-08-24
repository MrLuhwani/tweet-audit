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

public final class RateLimiterScheduler implements AutoCloseable {

    private final AiProvider provider;
    private final ExecutorService executor;
    private final BlockingQueue<QueueEvent<TweetBatch>> inputQueue;
    private final BlockingQueue<QueueEvent<AnalysisResult>> outputQueue;
    private Future<?> task;

    public RateLimiterScheduler(AiProvider provider, BlockingQueue<QueueEvent<TweetBatch>> inputQueue,
            BlockingQueue<QueueEvent<AnalysisResult>> outputQueue, ExecutorService executor) {
        this.provider = provider;
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

                if (event instanceof QueueEvent.Item) {
                    QueueEvent.Item<TweetBatch> itemEvent = (QueueEvent.Item<TweetBatch>) event;
                    TweetBatch batch = itemEvent.value();
                    
                    Instant start = Instant.now();
                    // TODO: remove this once you figure out the average time requests are made
                    System.out.println("batch" + batch.batchIndex() + ": " + start);
                    AnalysisResult result = provider.analyze(batch);
                    System.out.println("Time: " + Duration.between(start, Instant.now()));
                    outputQueue.put(new QueueEvent.Item<>(result));
                } else if (event instanceof QueueEvent.End) {
                    return;
                }
            }
        } catch (Exception e) {
            System.err.println("[ERROR] " + e.getMessage());
            System.err.println(e.getClass());
            e.printStackTrace();
        } finally {
            outputQueue.add(QueueEvent.End.instance());
        }
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}