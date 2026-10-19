package dev.luhwani.client;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import dev.luhwani.ai.AiProvider;
import dev.luhwani.error.BatchException;
import dev.luhwani.error.FatalException;
import dev.luhwani.error.RetryableException;
import dev.luhwani.model.AnalysisResult;
import dev.luhwani.model.TweetBatch;
import dev.luhwani.tweetProcessing.TweetBatchQueue;

/** Evaluates queued tweet batches concurrently and publishes successful results. */
public final class RequestExecutor implements AutoCloseable {

    private static final Logger LOGGER = Logger.getLogger(RequestExecutor.class.getName());

    private final ExecutorService executor;
    private final AiProvider provider;
    private final int workerThreads;
    private final TweetBatchQueue batchQueueState;

    private final BlockingQueue<TweetBatch> tweetBatches;
    private final BlockingQueue<AnalysisResult> resultQueue;

    private static final int MAX_CONSECUTIVE_BATCH_EXCEPTIONS = 5;

    private final List<Future<?>> tasks = new ArrayList<>();
    private final Integer MAX_RETRY_ATTEMPTS = 3;
    private final Object batchExceptionLock = new Object();

    private volatile Exception lastBatchException;
    private volatile int consecutiveBatchExceptions;
    /**
     * Creates workers for a batch queue populated by a concurrent batch filter.
     *
     * @param workerThreads number of request workers to create
     * @param batchQueueState bounded queue of batches to evaluate
     * @param resultQueue receiving successful analysis results
     * @param provider used to evaluate batches
     * @throws IllegalArgumentException if the queue size does not match the latch
     */
    public RequestExecutor(int workerThreads, TweetBatchQueue batchQueueState,
            BlockingQueue<AnalysisResult> resultQueue, AiProvider provider) {
        this.workerThreads = workerThreads;
        this.executor = Executors.newFixedThreadPool(workerThreads);
        this.provider = provider;
        this.tweetBatches = batchQueueState.batches;
        this.batchQueueState = batchQueueState;
        this.resultQueue = resultQueue;
    }

    /** Starts workers that drain the batch queue. */
    public void start() {
        synchronized (batchExceptionLock) {
            lastBatchException = null;
            consecutiveBatchExceptions = 0;
        }

        for (int i = 0; i < workerThreads; i++) {
            Future<?> task = executor.submit(() -> {
                TweetBatch batch;
                while (true) {
                    try {
                        batch = tweetBatches.poll(100, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Request worker interrupted", e);
                    }
                    if (batch == null) {
                        if (batchQueueState == null || (batchQueueState.finished && tweetBatches.isEmpty())) {
                            break;
                        }
                        continue;
                    }
                    try {
                        AnalysisResult result = evaluateTweets(batch);
                        resultQueue.add(result);
                        LOGGER.info(
                                "Batch " + result.batchNumber()
                                        + " succeded, FirstTweetId= "
                                        + result.results().getFirst().tweetId()
                                        + " Returned at: " + Instant.now());
                        synchronized (batchExceptionLock) {
                            lastBatchException = null;
                            consecutiveBatchExceptions = 0;
                        }
                    } catch (BatchException e) {
                        LOGGER.severe(
                                "Batch " + batch.batchNumber()
                                        + " failed, FirstTweetId= "
                                        + batch.tweets().getFirst().id()
                                        + " Failed at: " + Instant.now()
                                        + "\n Error name: " + e.getClass().getName()
                                        + " Error Msg: " + e.getMessage());
                        synchronized (batchExceptionLock) {
                            if (isSameBatchFailure(lastBatchException, e)) {
                                consecutiveBatchExceptions++;
                            } else {
                                lastBatchException = e;
                                consecutiveBatchExceptions = 1;
                            }

                            if (consecutiveBatchExceptions >= MAX_CONSECUTIVE_BATCH_EXCEPTIONS) {
                                throw new IllegalStateException(
                                        "Execution stopped as the same Exception is thrown repeatedly",
                                        new FatalException("Execution stopped as the same Exception is thrown repeatedly", e));
                            }
                        }
                        continue;
                    } catch (Exception e) {
                        LOGGER.severe("Fatal error occured in request executor: " + e.getMessage());
                        if (batchQueueState != null) {
                            batchQueueState.failure = e;
                        }
                        throw new IllegalStateException("Fatal error occured in request executor", e);
                    }

                }
            });
            tasks.add(task);
        }
    }

    /**
     * Checks if all workerThreads have completed their tasks
     * @return true or false, depending on the state of the workerThreads
     */
    public boolean isFinished() {
        return tasks.stream().allMatch(Future::isDone);
    }

    private boolean isSameBatchFailure(Exception previous, Exception current) {
        if (previous == null || current == null) {
            return false;
        }
        return previous.getClass().equals(current.getClass())
                && Objects.equals(previous.getMessage(), current.getMessage());
    }

    /**
     * Waits for all started workers to finish.
     *
     * @throws FatalException if a worker fails or the waiting thread is interrupted
     */
    public void awaitCompletion() throws FatalException {
        for (Future<?> task : tasks) {
            try {
                task.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new FatalException(e);
            } catch (ExecutionException e) {
                throw new FatalException(e);
            }
        }
        if (batchQueueState != null && batchQueueState.failure != null) {
            throw new FatalException("Batch queue producer failed", batchQueueState.failure);
        }
    }

    /**
     * sends a tweet batc to the Ai provider for evaluation.
     * Retries transient failures
     * 
     * @param batch batch to be evaluated
     * @return
     * @throws BatchException if the same tweet batch has failed repeatedly
     * @throws FatalException if a fatal error occred during execution
     * @throws InterruptedException if this thread is interrupted for any reason
     */
    private AnalysisResult evaluateTweets(TweetBatch batch)
            throws BatchException, FatalException, InterruptedException {
        for (int attempt = 1; attempt <= MAX_RETRY_ATTEMPTS; attempt++) {
            try {
                LOGGER.info("Batch" + batch.batchNumber() + " sent");
                AnalysisResult result = provider.analyze(batch);
                return result;
            } catch (RetryableException e) {
                System.err.printf(
                        "%s: batch %d failed on attempt %d/%d; %n",
                        Thread.currentThread().getName(),
                        batch.batchNumber(),
                        attempt,
                        MAX_RETRY_ATTEMPTS);
                if (attempt == MAX_RETRY_ATTEMPTS) {
                    throw new BatchException(e);
                }
                RetryPolicy.awaitRetry(e, attempt, MAX_RETRY_ATTEMPTS);
            }
        }
        throw new BatchException("Max amount of retries reached");
    }

    @Override
    public void close() throws Exception {
        executor.shutdown();
    }

}
