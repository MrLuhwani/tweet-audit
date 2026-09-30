package dev.luhwani.output;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.luhwani.configuration.AuditPaths;
import dev.luhwani.error.FatalException;
import dev.luhwani.model.AnalysisResult;
import dev.luhwani.model.Checkpoint;

public final class OutputWriter implements AutoCloseable {

    private static final Path CSV_PATH = AuditPaths.CSV_PATH;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final CSVWriter csvWriter;
    private final CheckpointWriter checkpointWriter;
    private final Checkpoint checkpoint;

    private final BlockingQueue<AnalysisResult> resultQueue;
    private final CountDownLatch countDown;

    private Future<?> task;

    public OutputWriter(ObjectMapper objectMapper, BlockingQueue<AnalysisResult> resultQueue, CountDownLatch countDown,
            Checkpoint checkpoint)
            throws IOException {
        this(objectMapper, resultQueue, countDown, checkpoint, CSV_PATH, AuditPaths.CHECKPOINT_PATH);
    }

    OutputWriter(ObjectMapper objectMapper, BlockingQueue<AnalysisResult> resultQueue, CountDownLatch countDown,
            Checkpoint checkpoint, Path csvPath, Path checkpointPath) throws IOException {
        this.csvWriter = new CSVWriter(csvPath);
        this.checkpointWriter = new CheckpointWriter(objectMapper, checkpointPath);
        this.resultQueue = resultQueue;
        this.countDown = countDown;
        this.checkpoint = checkpoint;
    }

    public void start() {
        task = executor.submit(() -> {
            try {
                write();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Writer thread interrupted while writing to output", e);
            } catch (IOException e) {
                throw new RuntimeException("Parsing error while writing to output", e);
            }
        });
    }

    public void awaitCompletion() throws FatalException {
        try {
            task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new FatalException(e);
        }
    }

    private void write() throws InterruptedException, IOException {
        while (true) {
            AnalysisResult result = resultQueue.poll(500, TimeUnit.MILLISECONDS);
            if (result == null) {
                if (countDown.getCount() == 0) {
                    break;
                }
                continue;
            }
            checkpoint.successfulBatches().add(result.batchNumber());
            csvWriter.write(result);
            checkpointWriter.update(checkpoint.successfulBatches());
        }
    }

    @Override
    public void close() throws Exception {
        csvWriter.close();
        executor.shutdown();
    }

}
