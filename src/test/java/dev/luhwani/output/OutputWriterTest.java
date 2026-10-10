package dev.luhwani.output;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.luhwani.error.FatalException;
import dev.luhwani.model.AnalysisResult;
import dev.luhwani.model.Checkpoint;
import dev.luhwani.model.Decision;
import dev.luhwani.model.TweetDecision;

class OutputWriterTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void writesResultsAndCheckpointAfterAllBatchesComplete() throws Exception {
        Path csvPath = tempDir.resolve("output.csv");
        Files.writeString(csvPath, "tweet_link,decision,reason\n", StandardCharsets.UTF_8);
        Path checkpointPath = tempDir.resolve("checkpoint.json");
        LinkedBlockingQueue<AnalysisResult> results = new LinkedBlockingQueue<>();
        CountDownLatch countDown = new CountDownLatch(2);
        Checkpoint checkpoint = Checkpoint.empty();
        results.add(result(2, "222"));
        results.add(result(1, "111"));

        try (OutputWriter writer = new OutputWriter(objectMapper, results, countDown, checkpoint,
                csvPath, checkpointPath)) {
            writer.start();
            countDown.countDown();
            countDown.countDown();
            writer.awaitCompletion();
        }

        assertEquals(
                List.of(
                        "tweet_link,decision,reason",
                        "\"https://x.com/i/status/222\",KEEP,\"Result 222\"",
                        "\"https://x.com/i/status/111\",KEEP,\"Result 111\""),
                Files.readAllLines(csvPath, StandardCharsets.UTF_8));
        Checkpoint savedCheckpoint = objectMapper.readValue(checkpointPath.toFile(), Checkpoint.class);
        assertEquals(java.util.Set.of(1, 2), savedCheckpoint.successfulBatches());
        assertEquals(java.util.Set.of(1, 2), checkpoint.successfulBatches());
    }

    @Test
    void writesToConfiguredOutputDirectory() throws Exception {
        Path outputDirectory = tempDir.resolve("configured-output");
        Files.createDirectories(outputDirectory);
        Files.writeString(outputDirectory.resolve("output.csv"), "tweet_link,decision,reason\n",
                StandardCharsets.UTF_8);
        LinkedBlockingQueue<AnalysisResult> results = new LinkedBlockingQueue<>();
        CountDownLatch countDown = new CountDownLatch(1);
        results.add(result(1, "111"));

        try (OutputWriter writer = new OutputWriter(objectMapper, results, countDown, Checkpoint.empty(),
                outputDirectory)) {
            writer.start();
            countDown.countDown();
            writer.awaitCompletion();
        }

        assertEquals(2, Files.readAllLines(outputDirectory.resolve("output.csv"), StandardCharsets.UTF_8).size());
        assertEquals(java.util.Set.of(1),
                objectMapper.readValue(outputDirectory.resolve("checkpoint.json").toFile(), Checkpoint.class)
                        .successfulBatches());
    }

    @Test
    void waitsForResultsBeforeCompletingWhenProducerHasNotFinished() throws Exception {
        Path csvPath = tempDir.resolve("output.csv");
        Files.writeString(csvPath, "tweet_link,decision,reason\n", StandardCharsets.UTF_8);
        Path checkpointPath = tempDir.resolve("checkpoint.json");
        LinkedBlockingQueue<AnalysisResult> results = new LinkedBlockingQueue<>();
        CountDownLatch countDown = new CountDownLatch(1);

        try (OutputWriter writer = new OutputWriter(objectMapper, results, countDown, Checkpoint.empty(),
                csvPath, checkpointPath)) {
            writer.start();
            results.put(result(1, "111"));
            countDown.countDown();
            writer.awaitCompletion();
        }

        assertEquals(2, Files.readAllLines(csvPath, StandardCharsets.UTF_8).size());
    }

    @Test
    void reportsCheckpointFailureThroughAwaitCompletion() throws Exception {
        Path csvPath = tempDir.resolve("output.csv");
        Files.writeString(csvPath, "tweet_link,decision,reason\n", StandardCharsets.UTF_8);
        Path checkpointDirectory = Files.createDirectory(tempDir.resolve("checkpoint"));
        LinkedBlockingQueue<AnalysisResult> results = new LinkedBlockingQueue<>();
        CountDownLatch countDown = new CountDownLatch(1);
        results.add(result(1, "111"));

        try (OutputWriter writer = new OutputWriter(objectMapper, results, countDown, Checkpoint.empty(),
                csvPath, checkpointDirectory)) {
            writer.start();
            countDown.countDown();

            assertThrows(FatalException.class, writer::awaitCompletion);
        }
    }

    private AnalysisResult result(int batchNumber, String tweetId) {
        return new AnalysisResult(batchNumber,
                List.of(new TweetDecision(tweetId, Decision.KEEP, "Result " + tweetId)));
    }
}