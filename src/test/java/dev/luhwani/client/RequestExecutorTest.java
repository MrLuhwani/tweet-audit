package dev.luhwani.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.luhwani.ai.AiProvider;
import dev.luhwani.error.BatchException;
import dev.luhwani.error.FatalException;
import dev.luhwani.error.RetryableException;
import dev.luhwani.model.AnalysisResult;
import dev.luhwani.model.Criteria;
import dev.luhwani.model.Decision;
import dev.luhwani.model.TweetBatch;
import dev.luhwani.model.TweetData;
import dev.luhwani.model.TweetDecision;

class RequestExecutorTest {

    @Test
    void processesBatchAndPublishesAnalysisResult() throws Exception {
        TweetBatch batch = batch(1);
        BlockingQueue<TweetBatch> batches = new LinkedBlockingQueue<>(List.of(batch));
        BlockingQueue<AnalysisResult> results = new LinkedBlockingQueue<>();
        CountDownLatch countDown = new CountDownLatch(batches.size());
        AiProvider provider = providerReturning((ignored) -> resultFor(batch));

        try (RequestExecutor executor = new RequestExecutor(1, batches, results, countDown, provider)) {
            executor.start();
            executor.awaitCompletion();
        }

        assertEquals(resultFor(batch), results.take());
        assertEquals(0, countDown.getCount());
    }

    @Test
    void retriesRetryableFailureAndPublishesResult() throws Exception {
        TweetBatch batch = batch(2);
        AtomicInteger calls = new AtomicInteger();
        AiProvider provider = providerReturning(ignored -> {
            if (calls.getAndIncrement() == 0) {
                throw new RetryableException("temporary failure");
            }
            return resultFor(batch);
        });
        BlockingQueue<AnalysisResult> results = new LinkedBlockingQueue<>();

        try (RequestExecutor executor = new RequestExecutor(1,
                new LinkedBlockingQueue<>(List.of(batch)), results, new CountDownLatch(1), provider)) {
            executor.start();
            executor.awaitCompletion();
        }

        assertEquals(2, calls.get());
        assertEquals(resultFor(batch), results.take());
    }

    @Test
    void stopsRetryingAfterMaximumAttemptsAndLeavesNoResult() throws Exception {
        TweetBatch batch = batch(3);
        AtomicInteger calls = new AtomicInteger();
        AiProvider provider = providerReturning(ignored -> {
            calls.incrementAndGet();
            throw new RetryableException("temporary failure");
        });
        BlockingQueue<AnalysisResult> results = new LinkedBlockingQueue<>();

        try (RequestExecutor executor = new RequestExecutor(1,
                new LinkedBlockingQueue<>(List.of(batch)), results, new CountDownLatch(1), provider)) {
            executor.start();
            executor.awaitCompletion();
        }

        assertEquals(3, calls.get());
        assertEquals(0, results.size());
    }

    @Test
    void continuesWithNextBatchAfterBatchFailure() throws Exception {
        TweetBatch failedBatch = batch(4);
        TweetBatch successfulBatch = batch(5);
        AtomicInteger calls = new AtomicInteger();
        AiProvider provider = providerReturning(currentBatch -> {
            if (calls.getAndIncrement() == 0) {
                throw new BatchException("invalid response");
            }
            return resultFor(currentBatch);
        });
        BlockingQueue<AnalysisResult> results = new LinkedBlockingQueue<>();

        try (RequestExecutor executor = new RequestExecutor(1,
                new LinkedBlockingQueue<>(List.of(failedBatch, successfulBatch)), results,
                new CountDownLatch(2), provider)) {
            executor.start();
            executor.awaitCompletion();
        }

        assertEquals(2, calls.get());
        assertEquals(resultFor(successfulBatch), results.take());
        assertEquals(0, results.size());
    }

    @Test
    void wrapsUnexpectedProviderFailureAsFatalException() throws Exception {
        RuntimeException providerFailure = new RuntimeException("provider unavailable");
        AiProvider provider = providerReturning(ignored -> {
            throw providerFailure;
        });
        RequestExecutor executor = new RequestExecutor(1,
                new LinkedBlockingQueue<>(List.of(batch(6))), new LinkedBlockingQueue<>(),
                new CountDownLatch(1), provider);

        try (executor) {
            executor.start();
            FatalException exception = assertThrows(FatalException.class, executor::awaitCompletion);

            ExecutionException executionFailure = assertInstanceOf(ExecutionException.class, exception.getCause());
            IllegalStateException cause = assertInstanceOf(IllegalStateException.class, executionFailure.getCause());
            assertEquals(providerFailure, cause.getCause());
        }
    }

    @Test
    void rejectsQueueAndLatchSizeMismatch() {
        AiProvider provider = providerReturning(ignored -> resultFor(batch(7)));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new RequestExecutor(1, new LinkedBlockingQueue<>(), new LinkedBlockingQueue<>(),
                        new CountDownLatch(1), provider));

        assertEquals("tweetBatches not the same as countdown latch on executor init", exception.getMessage());
    }

    @Test
    void stopsAfterFiveConsecutiveBatchExceptions() throws Exception {
        BlockingQueue<TweetBatch> batches = new LinkedBlockingQueue<>();
        for (int i = 0; i < 10; i++) {
            batches.add(new TweetBatch(i, List.of(new TweetData("id-" + i, "text-" + i))));
        }

        AtomicInteger calls = new AtomicInteger();
        CountDownLatch countDown = new CountDownLatch(10);
        AiProvider provider = new AiProvider("unused", new Criteria(new ObjectMapper().createObjectNode()),
                new ObjectMapper()) {
            @Override
            public AnalysisResult analyze(TweetBatch batch) throws RetryableException, BatchException, FatalException {
                calls.incrementAndGet();
                throw new BatchException("same failure");
            }
        };

        try (RequestExecutor executor = new RequestExecutor(1, batches, new LinkedBlockingQueue<>(), countDown,
                provider)) {
            executor.start();

            FatalException ex = assertThrows(FatalException.class, executor::awaitCompletion);
            assertEquals(5, calls.get(), "The executor should stop after five consecutive batch exceptions.");
            assertEquals("Execution stopped as the same Exception is thrown repeatedly",
                    ex.getCause().getCause().getMessage());
        }
    }

    private static TweetBatch batch(int batchNumber) {
        return new TweetBatch(batchNumber, List.of(new TweetData("id-" + batchNumber, "text-" + batchNumber)));
    }

    private static AnalysisResult resultFor(TweetBatch batch) {
        return new AnalysisResult(batch.batchNumber(),
                List.of(new TweetDecision(batch.tweets().getFirst().id(), Decision.KEEP, "valid")));
    }

    private static AiProvider providerReturning(ProviderBehavior behavior) {
        try {
            return new AiProvider("unused", new Criteria(new ObjectMapper().createObjectNode()), new ObjectMapper()) {
                @Override
                public AnalysisResult analyze(TweetBatch batch)
                        throws RetryableException, BatchException, FatalException {
                    return behavior.analyze(batch);
                }
            };
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    @FunctionalInterface
    private interface ProviderBehavior {
        AnalysisResult analyze(TweetBatch batch) throws RetryableException, BatchException, FatalException;
    }
}
