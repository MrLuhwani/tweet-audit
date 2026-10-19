package dev.luhwani.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
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
import dev.luhwani.tweetProcessing.TweetBatchQueue;

class RequestExecutorTest {

    @Test
    void evaluatesBatchAndPublishesResult() throws Exception {
        AnalysisResult expected = result(1);
        TweetBatchQueue batches = readyQueue(batch(1));
        var results = new ArrayBlockingQueue<AnalysisResult>(1);

        try (RequestExecutor executor = new RequestExecutor(1, batches, results, provider((ignored) -> expected))) {
            executor.start();
            executor.awaitCompletion();
            assertTrue(executor.isFinished());
        }

        assertEquals(expected, results.take());
    }

    @Test
    void retriesTransientFailureBeforePublishingResult() throws Exception {
        AnalysisResult expected = result(2);
        AtomicInteger attempts = new AtomicInteger();
        TweetBatchQueue batches = readyQueue(batch(2));
        var results = new ArrayBlockingQueue<AnalysisResult>(1);
        AiProvider provider = provider(ignored -> {
            if (attempts.getAndIncrement() == 0) {
                throw new RetryableException("temporary");
            }
            return expected;
        });

        try (RequestExecutor executor = new RequestExecutor(1, batches, results, provider)) {
            executor.start();
            executor.awaitCompletion();
        }

        assertEquals(2, attempts.get());
        assertEquals(expected, results.take());
    }

    @Test
    void recordsFatalProviderFailureAndReportsItOnCompletion() throws Exception {
        TweetBatchQueue batches = readyQueue(batch(3));
        var results = new ArrayBlockingQueue<AnalysisResult>(1);
        AiProvider provider = provider(ignored -> {
            throw new FatalException("fatal");
        });

        try (RequestExecutor executor = new RequestExecutor(1, batches, results, provider)) {
            executor.start();
            assertThrows(FatalException.class, executor::awaitCompletion);
        }

        assertTrue(batches.failure instanceof FatalException);
        assertTrue(results.isEmpty());
    }

    @Test
    void stopsAfterRepeatedIdenticalBatchFailures() throws Exception {
        TweetBatchQueue batches = readyQueue(batch(4));
        batches.batches.add(batch(5));
        batches.batches.add(batch(6));
        batches.batches.add(batch(7));
        batches.batches.add(batch(8));
        var results = new ArrayBlockingQueue<AnalysisResult>(1);
        AiProvider provider = provider(ignored -> {
            throw new BatchException("bad batch");
        });

        try (RequestExecutor executor = new RequestExecutor(1, batches, results, provider)) {
            executor.start();
            assertThrows(FatalException.class, executor::awaitCompletion);
        }

        assertTrue(results.isEmpty());
    }

    private static TweetBatchQueue readyQueue(TweetBatch batch) {
        TweetBatchQueue queue = new TweetBatchQueue();
        queue.batches.add(batch);
        queue.finished = true;
        return queue;
    }

    private static TweetBatch batch(int number) {
        return new TweetBatch(number, List.of(new TweetData("1", "text")));
    }

    private static AnalysisResult result(int number) {
        return new AnalysisResult(number, List.of(new TweetDecision("1", Decision.KEEP, "reason")));
    }

    private static AiProvider provider(Analyzer analyzer) throws IOException {
        return new AiProvider("key", new Criteria(new ObjectMapper().createObjectNode()), new ObjectMapper()) {
            @Override
            public AnalysisResult analyze(TweetBatch batch)
                    throws RetryableException, BatchException, FatalException {
                return analyzer.analyze(batch);
            }
        };
    }

    @FunctionalInterface
    private interface Analyzer {
        AnalysisResult analyze(TweetBatch batch) throws RetryableException, BatchException, FatalException;
    }
}