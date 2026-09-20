package dev.luhwani.tweetEvaluation;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import dev.luhwani.model.AnalysisResult;
import dev.luhwani.model.QueueEvent;
import dev.luhwani.model.TweetBatch;
import dev.luhwani.tweetEvaluation.exception.BatchException;
import dev.luhwani.tweetEvaluation.exception.RetryableException;

/**
 * This class is responsible for executing the evaluation of tweet batches using
 * an AI provider. It manages concurrent execution, handles retries for
 * transient failures, and routes successful results and failed batches to their
 * respective queues. The class also provides a summary of the evaluation
 * process, including the number of succeeded and remaining batches.
 */
public final class RequestExecutor implements AutoCloseable {

    /** Reports the number of batches that completed successfully. */
    public final class EvaluationSummary {
        private final AtomicInteger succeeded = new AtomicInteger();
        private final AtomicInteger remaining = new AtomicInteger();

        public Integer getSucceeded() {
            return succeeded.get();
        }
    }

    private static final Logger LOGGER = Logger.getLogger(RequestExecutor.class.getName());
    private final AiProvider provider;
    private final ExecutorService executor;
    private final EvaluationSummary evaluationSummary = new EvaluationSummary();
    private final Integer MAX_RETRY_ATTEMPTS = 3;
    private final Integer workerThreads;

    private final BlockingQueue<QueueEvent<TweetBatch>> batchQueue;
    private final BlockingQueue<QueueEvent<AnalysisResult>> resultQueue;
    private final BlockingQueue<QueueEvent<TweetBatch>> failureQueue;
    private final Map<Integer, String> failedBatchToLastTweetMap;

    private List<Future<?>> tasks = new ArrayList<>();

    private volatile Exception lastBatchException = new RuntimeException("No BatchException has been thrown yet");

    public RequestExecutor(BlockingQueue<QueueEvent<TweetBatch>> batchQueue,
            BlockingQueue<QueueEvent<AnalysisResult>> resultQueue,
            BlockingQueue<QueueEvent<TweetBatch>> failureQueue, Map<Integer, String> failedBatchToLastTweetMap,
            Integer workerThreads, AiProvider provider) {

        this.workerThreads = workerThreads;
        this.provider = provider;
        this.executor = Executors.newFixedThreadPool(workerThreads);
        this.batchQueue = batchQueue;
        this.resultQueue = resultQueue;
        this.failureQueue = failureQueue;
        this.failedBatchToLastTweetMap = failedBatchToLastTweetMap;
        evaluationSummary.remaining.set(batchQueue.size());
    }

    public void start(boolean isRetrying) {
        AtomicInteger maxBatchExceptions = new AtomicInteger();
        int maxBatchExceptionInt = 5;
        maxBatchExceptions.set(maxBatchExceptionInt);
        for (int i = 0; i < workerThreads; i++) {
            Future<?> task = executor.submit(() -> {
                QueueEvent<TweetBatch> event;
                while ((event = batchQueue.poll()) != null) {
                    QueueEvent.Item<TweetBatch> item = (QueueEvent.Item<TweetBatch>) event;
                    TweetBatch batch = item.value();
                    try {
                        AnalysisResult result = evaluateTweets(batch);
                        resultQueue.add(new QueueEvent.Item<>(result));
                        LOGGER.info(
                                "Batch " + result.batchNumber()
                                        + " succeded, LastTweetId= "
                                        + result.results().getLast().tweetId()
                                        + " Completed at: " + Instant.now());
                        evaluationSummary.succeeded.incrementAndGet();
                        if (maxBatchExceptions.get() < maxBatchExceptionInt) {
                            maxBatchExceptions.set(maxBatchExceptionInt);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    } catch (BatchException e) {
                        LOGGER.severe(
                                "Batch " + batch.batchNumber()
                                        + " failed, LastTweetId= "
                                        + batch.tweets().getLast().id()
                                        + " Failed at: " + Instant.now()
                                        + "\n Error name: " + e.getClass().getName()
                                        + " Error Msg: " + e.getMessage());
                        if (!isRetrying) {
                            failedBatchToLastTweetMap.put(batch.batchNumber(), batch.tweets().getLast().id());
                            failureQueue.add(new QueueEvent.Item<>(batch));
                        }

                        if ((lastBatchException.getClass() + lastBatchException.getMessage())
                                .equals(e.getClass() + e.getMessage())) {
                            maxBatchExceptions.decrementAndGet();
                        } else {
                            lastBatchException = e;
                            maxBatchExceptions.set(maxBatchExceptionInt);
                        }
                        if (maxBatchExceptions.get() == 0) {
                            throw new RuntimeException(
                                    "Execution stopped as the same BatchException is thrown repeatedly", e);
                        }
                        continue;
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    } finally {
                        if (evaluationSummary.remaining.decrementAndGet() == 0) {
                            resultQueue.add(QueueEvent.End.instance());
                            failureQueue.add(QueueEvent.End.instance());
                        }

                    }
                }
            });
            tasks.add(task);
        }
    }

    /** Waits for all worker tasks and returns their success summary. */
    public EvaluationSummary awaitCompletion() throws ExecutionException {
        try {
            for (Future<?> task : tasks) {
                task.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
        return evaluationSummary;
    }

    private AnalysisResult evaluateTweets(TweetBatch batch) throws InterruptedException, Exception {
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                Instant start = Instant.now();
                // TODO: remove this once you figure out the average time requests are made
                System.out.println("batch" + batch.batchNumber() + ": " + start);
                AnalysisResult result = provider.analyze(batch);
                System.out.println("Time: " + Duration.between(start, Instant.now()));
                return result;
            } catch (RetryableException e) {
                System.err.printf(
                        "%s: batch %d failed on attempt %d/%d; (%s)%n",
                        Thread.currentThread().getName(),
                        batch.batchNumber(),
                        attempt,
                        MAX_RETRY_ATTEMPTS,
                        e.getMessage());
                if (attempt == MAX_RETRY_ATTEMPTS) {
                    throw new BatchException(e, e.getStatusCode(), batch);
                }
                RetryPolicy.awaitRetry(e, attempt, MAX_RETRY_ATTEMPTS);
            }
        }
        throw new BatchException(new RuntimeException("Max amount of request attempts reached"), Optional.empty(),
                batch);
    }

    @Override
    public void close() throws Exception {
        executor.shutdown();
        executor.awaitTermination(2000,TimeUnit.MILLISECONDS);
    }

}
