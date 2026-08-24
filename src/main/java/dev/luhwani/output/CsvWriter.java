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
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import dev.luhwani.model.AnalysisResult;
import dev.luhwani.model.Checkpoint;
import dev.luhwani.model.QueueEvent;
import dev.luhwani.model.TweetDecision;

public class CsvWriter implements AutoCloseable {

    private final BlockingQueue<QueueEvent<AnalysisResult>> queue;
    private final ExecutorService executor;
    private final BufferedWriter writer;
    private Future<?> task;
    Path outputPath = Paths.get("")
            .toAbsolutePath()
            .normalize()
            .resolve("output/output.csv");
    private static final String CSV_HEADER = "\"tweet_link\",\"decision\",\"reason\"";

    public CsvWriter(BlockingQueue<QueueEvent<AnalysisResult>> queue, ExecutorService executor) throws IOException {
        this.queue = queue;
        this.executor = executor;

        Path parentDir = outputPath.getParent();

        if (parentDir != null && Files.notExists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        Checkpoint checkpoint = CheckpointResolver.load();
        if (Files.notExists(outputPath)) {

            if (checkpoint.lastCompletedBatchIndex() != Checkpoint.empty().lastCompletedBatchIndex()) {
                throw new IllegalStateException("Checkpoint exists, but output.csv file not found");
            }
            Files.writeString(
                    outputPath,
                    CSV_HEADER + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE_NEW);
            System.out.println("Created output.csv at " + outputPath.toString());
            this.writer = Files.newBufferedWriter(
                    outputPath,
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.APPEND);
            return;
        }
        try (Stream<String> lines = Files.lines(outputPath, StandardCharsets.UTF_8)) {
            Optional<String> lastNonEmptyLine = lines
                    .filter(line -> !line.isEmpty())
                    .reduce((first, second) -> second); // Only keeps the latest line encountered
            if (lastNonEmptyLine.isEmpty()) {
                if (checkpoint.lastCompletedBatchIndex() != Checkpoint.empty().lastCompletedBatchIndex()) {
                    throw new IllegalStateException("Checkpoint exists, but output.csv file is empty");
                }
                this.writer = Files.newBufferedWriter(
                        outputPath,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND);
                writer.write(CSV_HEADER);
                writer.newLine();
                return;
            }
            if (lastNonEmptyLine.get().contains(checkpoint.lastTweetId())) {
                this.writer = Files.newBufferedWriter(
                        outputPath,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND);
            } else {
                throw new IllegalStateException(
                        "The last line of the file does not match the last tweet ID in the checkpoint.");
            }

        }
    }

    public void start() {
        task = executor.submit(() -> {
            try {
                write();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    private void write() throws IOException {
        try {
            while (true) {
                QueueEvent<AnalysisResult> event = queue.take();

                if (event instanceof QueueEvent.Item) {
                    QueueEvent.Item<AnalysisResult> itemEvent = (QueueEvent.Item<AnalysisResult>) event;
                    AnalysisResult result = itemEvent.value();

                    for (TweetDecision decision : result.results()) {
                        writer.write("https://x.com/i/status/" + decision.tweetId());
                        writer.write(",");
                        writer.write(decision.decision().name());
                        writer.write(",");
                        writer.write(cleanForCsv(decision.reason()));
                        writer.newLine();
                    }
                    writer.flush();
                    CheckpointResolver.save(result.batchIndex(), getLastTweetId(result.results()));
                } else if (event instanceof QueueEvent.End) {
                    return;
                }
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("[ERROR] " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("[ERROR] " + e.getMessage());
            System.err.println(e.getClass());
            e.printStackTrace();
        } finally {
            writer.close();
            executor.shutdown();
        }
    }

    private static String getLastTweetId(List<TweetDecision> results) {
        if (results.isEmpty()) {
            throw new IllegalStateException("Results list cannot be empty");
        }
        return results.get(results.size() - 1).tweetId();
    }

    public void awaitCompletion() throws InterruptedException, ExecutionException {
        if (task == null) {
            throw new IllegalStateException("Provider has not been started");
        }
        task.get();
    }

    private static String cleanForCsv(String input) {

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

    private static String removeControlCharacters(String value) {
        return value
                .replace("\r\n", " ") // Windows line ending
                .replace("\n", " ") // Unix line ending
                .replace("\r", " "); // Old Mac line ending
    }

    private static boolean needsQuoting(String value) {
        return value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
    }

    @Override
    public void close() throws Exception {
        writer.close();
    }
}