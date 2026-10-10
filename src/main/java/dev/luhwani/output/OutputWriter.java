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

import dev.luhwani.error.FatalException;
import dev.luhwani.model.AnalysisResult;
import dev.luhwani.model.Checkpoint;

/** Consumes analysis results and persists CSV rows and checkpoint progress. */
public final class OutputWriter implements AutoCloseable {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private final CSVWriter csvWriter;
    private final CheckpointWriter checkpointWriter;
    private final Checkpoint checkpoint;

    private final BlockingQueue<AnalysisResult> resultQueue;
    private final CountDownLatch countDown;

    private Future<?> task;

    /**
     * Creates a writer for the CSV and checkpoint files in the supplied directory.
     *
     * @param objectMapper mapper used to serialize checkpoint data
     * @param resultQueue queue of completed analysis results
     * @param countDown latch used to detect when all batches have finished
     * @param checkpoint current checkpoint to update
     * @param outputDirectory directory where output files are stored
     * @throws IOException if an output file cannot be opened
     */
    public OutputWriter(ObjectMapper objectMapper, BlockingQueue<AnalysisResult> resultQueue, CountDownLatch countDown,
            Checkpoint checkpoint, Path outputDirectory)
            throws IOException {
        if (outputDirectory == null) {
            throw new IllegalArgumentException("Output directory cannot be null");
        }
        this.csvWriter = new CSVWriter(outputDirectory.resolve("output.csv"));
        this.checkpointWriter = new CheckpointWriter(objectMapper, outputDirectory.resolve("checkpoint.json"));
        this.resultQueue = resultQueue;
        this.countDown = countDown;
        this.checkpoint = checkpoint;
    }

    public OutputWriter(ObjectMapper objectMapper, BlockingQueue<AnalysisResult> resultQueue, CountDownLatch countDown,
            Checkpoint checkpoint, Path csvPath, Path checkpointPath) throws IOException {
        this.csvWriter = new CSVWriter(csvPath);
        this.checkpointWriter = new CheckpointWriter(objectMapper, checkpointPath);
        this.resultQueue = resultQueue;
        this.countDown = countDown;
        this.checkpoint = checkpoint;
    }

    /** Starts the background writer. */
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

    /**
     * Waits for the background writer to finish.
     *
     * @throws FatalException if writing fails
     */
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
            AnalysisResult result = resultQueue.poll(200, TimeUnit.MILLISECONDS);
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
