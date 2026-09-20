package dev.luhwani.tweetEvaluation;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.luhwani.model.QueueEvent;
import dev.luhwani.model.QueueEvent.Item;
import dev.luhwani.model.TweetBatch;

/**
 * Persists failed batches and removes them after successful retries.
 * 
 */
public final class BatchFailureHandler implements AutoCloseable {

    private final Path failedBatchPath;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService executor;

    private final BlockingQueue<QueueEvent<TweetBatch>> failureQueue;
    private final BlockingQueue<QueueEvent<Integer>> successfulRetriedBatches;

    private Future<?> task;
    private boolean isRetriesCompleted = false;

    public BatchFailureHandler(BlockingQueue<QueueEvent<TweetBatch>> failureQueue, Path failedBatchPath,
            ObjectMapper objectMapper, BlockingQueue<QueueEvent<Integer>> successfulRetriedBatches) throws IOException {
        this.failedBatchPath = failedBatchPath;
        this.objectMapper = objectMapper;
        this.failureQueue = failureQueue;
        this.executor = Executors.newSingleThreadScheduledExecutor();
        this.successfulRetriedBatches = successfulRetriedBatches;
        if (!Files.exists(failedBatchPath)) {
            Files.createDirectories(failedBatchPath.getParent());
        }
    }

    public void start(boolean isRetrying) {
        if (!isRetrying) {
            task = executor.submit(() -> {
                try {
                    writeFailures();
                } catch (IOException | InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            });
            return;
        }
        task = executor.scheduleAtFixedRate(this::appendSuccessfulRetries, 0, 800, TimeUnit.MILLISECONDS);
    }

    /** Waits for failure handling to finish and propagates task failures. */
    public void awaitCompletion() throws ExecutionException {
        try {
            task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (CancellationException e) {
            // In the method executed by the scheduledRequestExecutor, task.cancel()
            // is set to false. This triggers the executor service to stop its processing
            // but also throws a CancellationException when you call task.get()
            // So, we just catch the exception and continue processing
        }
    }

    private void writeFailures() throws IOException, InterruptedException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(failedBatchPath.toString(), true));) {
            while (true) {
                QueueEvent<TweetBatch> failureEvent = failureQueue.poll(500, TimeUnit.MILLISECONDS);
                if (failureEvent == null) {
                    continue;
                }
                if (failureEvent instanceof QueueEvent.End) {
                    break;
                }
                QueueEvent.Item<TweetBatch> failedItem = (Item<TweetBatch>) failureEvent;
                TweetBatch batch = failedItem.value();
                append(batch, writer);
            }
        }
    }

    private void append(TweetBatch batch, BufferedWriter writer) throws IOException {
        String jsonLine = objectMapper.writeValueAsString(batch);
        writer.write(jsonLine);
        writer.newLine();
        writer.flush();
    }

    private void appendSuccessfulRetries() {
        // it is possble that isRetriesCompleted is true, but the task object can be
        // null
        // if the first execution at delay=0 finishes before the task assignment
        // completes
        // So this serves as a safety net
        if (isRetriesCompleted && task != null) {
            task.cancel(false);
        }

        if (successfulRetriedBatches.isEmpty()) {
            return;
        }
        Set<Integer> batchesToRemove = new HashSet<>();
        if (successfulRetriedBatches.contains(QueueEvent.End.instance())) {
            isRetriesCompleted = true;
            successfulRetriedBatches.remove(QueueEvent.End.instance());
        }
        for (QueueEvent<Integer> batchNum : successfulRetriedBatches) {
            QueueEvent.Item<Integer> item = (Item<Integer>) batchNum;
            Integer num = item.value();
            batchesToRemove.add(num);
        }
        successfulRetriedBatches.clear();
        try {
            if (!batchesToRemove.isEmpty()) {
                swapFile(batchesToRemove);
            }

            if (isRetriesCompleted && task != null) {
                task.cancel(false);
            }
        } catch (IOException e) {
            throw new RuntimeException("Error while updating " + failedBatchPath.toString() + ": " + e.getMessage(), e);
        }
    }

    private void swapFile(Set<Integer> toRemove) throws IOException {
        Path tempFile = Files.createTempFile(
                failedBatchPath.getParent(), "failedBatches", ".tmp");
        try (BufferedReader reader = Files.newBufferedReader(failedBatchPath);
                BufferedWriter writer = Files.newBufferedWriter(tempFile)) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }

                JsonNode node = objectMapper.readTree(line);
                int batchNumber = node.get("batchNumber").asInt();

                if (!toRemove.contains(batchNumber)) {
                    writer.write(line);
                    writer.newLine();
                }
            }
        }

        Files.move(tempFile, failedBatchPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    @Override
    public void close() throws Exception {
        executor.shutdown();
        executor.awaitTermination(4000, TimeUnit.MILLISECONDS);
    }

}
