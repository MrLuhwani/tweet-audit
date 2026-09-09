package dev.luhwani.output;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.luhwani.model.AnalysisResult;
import dev.luhwani.model.Checkpoint;
import dev.luhwani.model.QueueEvent;
import dev.luhwani.model.QueueEvent.Item;
import dev.luhwani.model.TweetDecision;
import dev.luhwani.tweetProcessing.FailedBatchLoader;

public class OutputWriter implements AutoCloseable {

    private final BlockingQueue<QueueEvent<AnalysisResult>> outputQueue;
    private final ExecutorService executor;
    private final BufferedWriter writer;
    private Future<?> task;
    private Integer nextExpectedBatchNumber;
    private final TreeMap<Integer, AnalysisResult> pending;
    private final Set<Integer> failedBatchNumbers;
    private final boolean updateCheckpoint;
    private Checkpoint lastWrittenCheckpoint;

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final String CSV_HEADER = "tweet_link,decision,reason";
    private static final Path CHECKPOINT_PATH = Paths
            .get("")
            .toAbsolutePath()
            .normalize()
            .resolve("output/checkpoint.json");

    private static final Path OUTPUT_PATH = Paths.get("")
            .toAbsolutePath()
            .normalize()
            .resolve("output/output.csv");

    public OutputWriter(ExecutorService executor,
            BlockingQueue<QueueEvent<AnalysisResult>> outputQueue, Set<Integer> failedBatchNumbers,
            boolean updateCheckpoint) throws IOException {
        this.outputQueue = outputQueue;
        this.executor = executor;
        this.writer = Files.newBufferedWriter(OUTPUT_PATH, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
        this.pending = new TreeMap<>();
        this.failedBatchNumbers = failedBatchNumbers;
        this.updateCheckpoint = updateCheckpoint;
    }

    public static Checkpoint loadCheckpoint() throws IOException {
        if (Files.exists(CHECKPOINT_PATH) && Files.exists(OUTPUT_PATH)) {
            Checkpoint checkpoint = objectMapper.readValue(CHECKPOINT_PATH.toFile(), Checkpoint.class);
            try (Stream<String> lines = Files.lines(OUTPUT_PATH, StandardCharsets.UTF_8)) {
                Optional<String> lastNonEmptyLine = lines
                        .filter(line -> !line.isBlank()) // slips empty lines
                        .filter(line -> !line.equals(CSV_HEADER)) // skips the header
                        .reduce((first, second) -> second); // Only keeps the latest line encountered

                if (lastNonEmptyLine.isEmpty() && checkpoint.isEmpty()) {
                    return checkpoint;
                }
                String lastTweetId = lastNonEmptyLine.get().split(",", 2)[0];
                if (!lastTweetId.contains(checkpoint.lastTweetId())) {
                    throw new IllegalStateException(
                            "Checkpoint data and output data do not match. Checkpoint last tweet = "
                                    + checkpoint.lastTweetId()
                                    + " Output last tweet id = " + lastTweetId);
                }

                return checkpoint;
            }
        }

        if (!Files.exists(CHECKPOINT_PATH) && !Files.exists(OUTPUT_PATH)) {

            Files.createDirectories(CHECKPOINT_PATH.getParent());
            Files.createDirectories(OUTPUT_PATH.getParent());
            Files.writeString(
                    OUTPUT_PATH,
                    CSV_HEADER + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(CHECKPOINT_PATH.toFile(), Checkpoint.empty());
            return Checkpoint.empty();
        }
        throw new IllegalStateException("Could not resolve audit progess. Checkpoint file, or output file missing");

    }

    public void start(int nextExpectedBatchNumber) {
        this.nextExpectedBatchNumber = nextExpectedBatchNumber;
        task = executor.submit(() -> {
            try {
                handleResult();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    public void awaitCompletion() throws ExecutionException {
        try {
            task.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        }
    }

    public Checkpoint lastWrittenCheckpoint() {
        return lastWrittenCheckpoint;
    }

    public static void updateCheckpointIfAhead(Checkpoint candidate) throws IOException {
        Checkpoint current = loadCheckpoint();
        if (candidate != null && candidate.lastCompletedBatchNumber() > current.lastCompletedBatchNumber()) {
            objectMapper.writeValue(CHECKPOINT_PATH.toFile(), candidate);
        }
    }

    public void handleResult() throws Exception {
        while (true) {
            QueueEvent<AnalysisResult> event = outputQueue.poll(3, TimeUnit.SECONDS);
            if (event == null) {
                continue;
            }
            if (event instanceof QueueEvent.End) {
                if (!updateCheckpoint) {
                    for (AnalysisResult result : pending.values()) {
                        write(result);
                    }
                    pending.clear();
                }
                break;
            }
            QueueEvent.Item<AnalysisResult> item = (Item<AnalysisResult>) event;
            AnalysisResult result = item.value();
            if (!updateCheckpoint) {
                pending.put(result.batchNumber(), result);
                continue;
            }
            pending.put(result.batchNumber(), result);
            while (pending.containsKey(nextExpectedBatchNumber)) { // while, not if — drain consecutive ready batches
                AnalysisResult ready = pending.remove(nextExpectedBatchNumber);
                write(ready);
                nextExpectedBatchNumber++;
            }
            while (failedBatchNumbers.contains(nextExpectedBatchNumber)) {
                nextExpectedBatchNumber++;
            }
        }
    }

    private void write(AnalysisResult result) throws IOException {
        for (TweetDecision decision : result.results()) {
            writer.write("https://x.com/i/status/" + decision.tweetId());
            writer.write(",");
            writer.write(decision.decision().name());
            writer.write(",");
            writer.write(cleanForCsv(decision.reason()));
            writer.newLine();
        }
        writer.flush();
        Checkpoint checkpoint = new Checkpoint(result.batchNumber(), getLastTweetId(result.results()));
        if (lastWrittenCheckpoint == null
                || checkpoint.lastCompletedBatchNumber() > lastWrittenCheckpoint.lastCompletedBatchNumber()) {
            lastWrittenCheckpoint = checkpoint;
        }
        if (updateCheckpoint) {
            objectMapper.writeValue(CHECKPOINT_PATH.toFile(), checkpoint);
        } else {
            removeFailedBatch(result.batchNumber());
        }
    }

    private static synchronized void removeFailedBatch(int batchNumber) throws IOException {
        if (!Files.exists(FailedBatchLoader.FAILED_BATCH_PATH)) {
            return;
        }
        List<String> retained = Files.readAllLines(FailedBatchLoader.FAILED_BATCH_PATH, StandardCharsets.UTF_8)
                .stream()
                .filter(line -> {
                    if (line.isBlank()) {
                        return false;
                    }
                    try {
                        return objectMapper.readTree(line).path("batchNumber").asInt() != batchNumber;
                    } catch (IOException e) {
                        return true;
                    }
                })
                .toList();
        Path temporaryPath = FailedBatchLoader.FAILED_BATCH_PATH.resolveSibling("failedBatches.jsonl.tmp");
        Files.write(temporaryPath, retained, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        Files.move(temporaryPath, FailedBatchLoader.FAILED_BATCH_PATH,
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    private String getLastTweetId(List<TweetDecision> results) {
        if (results.isEmpty()) {
            throw new IllegalStateException("Results list cannot be empty");
        }
        return results.get(results.size() - 1).tweetId();
    }

    private String cleanForCsv(String input) {

        if (input == null) {
            return "";
        }

        String cleaned = input.trim();

        cleaned = removeControlCharacters(cleaned);

        cleaned = cleaned.replace("\"", "\"\"");

        if (needsQuoting(cleaned)) {
            cleaned = "\"" + cleaned + "\"";
        }

        return cleaned;
    }

    private String removeControlCharacters(String value) {
        return value
                .replace("\r\n", " ") // Windows line ending
                .replace("\n", " ") // Unix line ending
                .replace("\r", " "); // Old Mac line ending
    }

    private boolean needsQuoting(String value) {
        return value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
    }

    @Override
    public void close() throws Exception {
        executor.awaitTermination(10, TimeUnit.SECONDS);
        writer.close();
    }
}
