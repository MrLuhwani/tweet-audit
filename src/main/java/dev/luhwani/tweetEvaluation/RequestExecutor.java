package dev.luhwani.tweetEvaluation;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.luhwani.model.AnalysisResult;
import dev.luhwani.model.QueueEvent;
import dev.luhwani.model.TweetBatch;
import dev.luhwani.tweetEvaluation.exception.BatchException;
import dev.luhwani.tweetEvaluation.exception.RetryableException;

public final class RequestExecutor implements AutoCloseable {

    public final class EvaluationSummary {
        private final AtomicInteger succeeded = new AtomicInteger();
        private final AtomicInteger remaining = new AtomicInteger();

        public Integer getSucceeded() {
            return succeeded.get();
        }
    }

    private final class BatchFailureAppender {

        private final ObjectMapper objectMapper;
        private final Path FAILED_BATCH_PATH = Paths
                .get("")
                .toAbsolutePath()
                .normalize()
                .resolve("output/failedBatches.jsonl");
        private volatile boolean running = true;
        private Thread failureWriterThread;
        private final BlockingQueue<BatchException> failureQueue = new LinkedBlockingQueue<>();
        private final Set<Integer> failedBatchNumbers;

        private BatchFailureAppender(ObjectMapper objectMapper, Set<Integer> failedBatchNumbers) {
            this.objectMapper = objectMapper;
            this.failedBatchNumbers = failedBatchNumbers;
        }

        private void startFailureWriter() {
            failureWriterThread = new Thread(() -> {
                try (BufferedWriter writer = new BufferedWriter(
                        new FileWriter(failureAppender.FAILED_BATCH_PATH.toString(), true))) {
                    while (running || !failureQueue.isEmpty()) {
                        BatchException e = failureQueue.poll(500, TimeUnit.MILLISECONDS);
                        if (e != null) {
                            failureAppender.append(e, writer);
                            failedBatchNumbers.add(e.getFailedBatch().batchNumber());
                        }
                    }
                } catch (IOException | InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            });
            failureWriterThread.start();
        }

        private synchronized void append(BatchException e, BufferedWriter writer) throws IOException {
            TweetBatch batch = e.getFailedBatch();
            LOGGER.severe(
                    "Batch " + batch.batchNumber()
                            + " failed, FirstTweetId= "
                            + batch.tweets().getFirst().id()
                            + " Failed at: " + Instant.now()
                            + "\n Error name: " + e.getClass().getName()
                            + " Error Msg: " + e.getMessage());
            if (!Files.exists(FAILED_BATCH_PATH)) {
                Files.createDirectories(FAILED_BATCH_PATH.getParent());
            }
            String jsonLine = objectMapper.writeValueAsString(batch);
            writer.write(jsonLine);
            writer.newLine();
            writer.flush();
        }

        private void stopFailureWriter() throws InterruptedException {
            running = false;
            failureWriterThread.join();
        }

    }

    private final AiProvider provider;
    private final ExecutorService executor;
    private final BlockingQueue<QueueEvent<TweetBatch>> inputQueue;
    private final BlockingQueue<QueueEvent<AnalysisResult>> outputQueue;
    private List<Future<?>> tasks;
    private final EvaluationSummary evaluationSummary;
    private final BatchFailureAppender failureAppender;
    private final boolean persistFailures;
    private final Integer MAX_ATTEMPTS = 3;
    private static final Logger LOGGER = Logger.getLogger(RequestExecutor.class.getName());
    private volatile Exception lastBatchException = new RuntimeException("No BatchException has been thrown yet");

    public RequestExecutor(AiProvider provider, ExecutorService executor,
            BlockingQueue<QueueEvent<TweetBatch>> inputQueue,
            BlockingQueue<QueueEvent<AnalysisResult>> outputQueue, ObjectMapper objectMapper,
            Set<Integer> failedBatchNumbers) {
        this(provider, executor, inputQueue, outputQueue, objectMapper, failedBatchNumbers, true);
    }

    public RequestExecutor(AiProvider provider, ExecutorService executor,
            BlockingQueue<QueueEvent<TweetBatch>> inputQueue,
            BlockingQueue<QueueEvent<AnalysisResult>> outputQueue, ObjectMapper objectMapper,
            Set<Integer> failedBatchNumbers, boolean persistFailures) {
        this.provider = provider;
        this.executor = executor;
        this.inputQueue = inputQueue;
        this.outputQueue = outputQueue;
        this.failureAppender = new BatchFailureAppender(objectMapper, failedBatchNumbers);
        this.persistFailures = persistFailures;
        this.evaluationSummary = new EvaluationSummary();
        evaluationSummary.remaining.set(inputQueue.size());
        this.tasks = new ArrayList<>();
    }

    public void start(int workerCount) {
        AtomicInteger maxBatchExceptions = new AtomicInteger();
        int maxBatchExceptionInt = 3;
        maxBatchExceptions.set(maxBatchExceptionInt);
        failureAppender.startFailureWriter();
        for (int i = 0; i < workerCount; i++) {
            Future<?> task = executor.submit(() -> {
                QueueEvent<TweetBatch> event;
                while ((event = inputQueue.poll()) != null) {
                    QueueEvent.Item<TweetBatch> item = (QueueEvent.Item<TweetBatch>) event;
                    TweetBatch batch = item.value();
                    try {
                        AnalysisResult result = evaluateTweets(batch);
                        outputQueue.add(new QueueEvent.Item<>(result));
                        LOGGER.info(
                                "Batch " + result.batchNumber()
                                        + " succeded, FirstTweetId= "
                                        + result.results().getFirst().tweetId()
                                        + " Completed at: " + Instant.now());
                        evaluationSummary.succeeded.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(e);
                    } catch (BatchException e) {
                        failureAppender.failedBatchNumbers.add(e.getFailedBatch().batchNumber());
                        if (persistFailures) {
                            failureAppender.failureQueue.add(e);
                        }
                        if (lastBatchException.getMessage().equals(e.getMessage())) {
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
                            outputQueue.add(QueueEvent.End.instance());
                        }

                    }
                }
            });
            tasks.add(task);
        }
    }

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

    public AnalysisResult evaluateTweets(TweetBatch batch) throws InterruptedException, Exception {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                AnalysisResult result = provider.analyze(batch);
                return result;
            } catch (RetryableException e) {
                System.err.printf(
                        "%s: batch %d failed on attempt %d/%d; (%s)%n",
                        Thread.currentThread().getName(),
                        batch.batchNumber(),
                        attempt,
                        MAX_ATTEMPTS,
                        e.getMessage());
                if (attempt == MAX_ATTEMPTS) {
                    throw new BatchException(e, e.getStatusCode(), batch);
                }
                RetryPolicy.awaitRetry(e, attempt, MAX_ATTEMPTS);
            }
        }
        throw new RuntimeException("Max amount of request attempts reached");
    }

    @Override
    public void close() throws Exception {
        failureAppender.stopFailureWriter();
        executor.shutdown();
    }

}
