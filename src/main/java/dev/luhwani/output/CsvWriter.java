package dev.luhwani.output;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import dev.luhwani.model.AnalysisResult;
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
    private static final String CSV_HEADER = "\"tweet_link\",\"decision\"";

    public CsvWriter(BlockingQueue<QueueEvent<AnalysisResult>> queue, ExecutorService executor) throws IOException {
        this.queue = queue;
        this.executor = executor;

        Path parentDir = outputPath.getParent();

        if (parentDir != null && Files.notExists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        if (Files.notExists(outputPath)) {
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
        } else {
            List<String> lines = Files.readAllLines(outputPath, StandardCharsets.UTF_8);

            boolean hasContent = !lines.isEmpty();
            boolean validHeader = hasContent && lines.get(0).trim().startsWith(CSV_HEADER);
            if (hasContent && validHeader) {
                this.writer = Files.newBufferedWriter(
                        outputPath,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE,
                        StandardOpenOption.APPEND);
            } else {
                throw new IllegalStateException("Output file is invalid");
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
                switch (event) {
                    case QueueEvent.Item<AnalysisResult>(var result) -> {
                        for (TweetDecision decision : result.results()) {
                            writer.write("https://x.com/i/status/" + decision.tweetId());
                            writer.write(",");
                            writer.write(decision.decision().name());
                            writer.newLine();
                        }
                        writer.flush();
                        CheckpointResolver.save(result.batchIndex(), result.results().getLast().tweetId());
                    }
                    case QueueEvent.End<AnalysisResult>() -> {
                        return;
                    }
                }
            }
        } catch (IOException | InterruptedException e) {
            System.err.println(e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.err.println(e.getClass());
            e.printStackTrace();
        } finally {
            writer.close();
            executor.shutdown();
        }
    }

    public void awaitCompletion() throws InterruptedException, ExecutionException {
        if (task == null) {
            throw new IllegalStateException("Provider has not been started");
        }
        task.get();
    }

    @Override
    public void close() throws Exception {
        writer.close();
    }
}
