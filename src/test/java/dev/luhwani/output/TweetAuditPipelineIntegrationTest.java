package dev.luhwani.output;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.luhwani.ai.AiProvider;
import dev.luhwani.client.RequestExecutor;
import dev.luhwani.model.AnalysisResult;
import dev.luhwani.model.Checkpoint;
import dev.luhwani.model.Criteria;
import dev.luhwani.model.Decision;
import dev.luhwani.model.TweetBatch;
import dev.luhwani.model.TweetData;
import dev.luhwani.model.TweetDecision;

class TweetAuditPipelineIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void sendsBatchesThroughExecutorAndPersistsResultsAndCheckpoint() throws Exception {
        Path csvPath = tempDir.resolve("output.csv");
        Files.writeString(csvPath, "tweet_link,decision,reason\n", StandardCharsets.UTF_8);
        Path checkpointPath = tempDir.resolve("checkpoint.json");

        List<TweetBatch> batches = List.of(
                batch(1, "111"),
                batch(2, "222"));
        LinkedBlockingQueue<TweetBatch> batchQueue = new LinkedBlockingQueue<>(batches);
        LinkedBlockingQueue<AnalysisResult> resultQueue = new LinkedBlockingQueue<>();
        Checkpoint checkpoint = Checkpoint.empty();
        AiProvider provider = providerReturningResults();
        CountDownLatch countDown = new CountDownLatch(batches.size());

        try (RequestExecutor executor = new RequestExecutor(2, batchQueue, resultQueue, countDown, provider);
                OutputWriter writer = new OutputWriter(new ObjectMapper(), resultQueue, countDown, checkpoint,
                        csvPath, checkpointPath)) {
            executor.start();
            writer.start();
            executor.awaitCompletion();
            writer.awaitCompletion();
        }

        List<String> output = Files.readAllLines(csvPath, StandardCharsets.UTF_8);
        assertEquals(3, output.size());
        assertTrue(output.stream().anyMatch(line -> line.contains("https://x.com/i/status/111")));
        assertTrue(output.stream().anyMatch(line -> line.contains("https://x.com/i/status/222")));

        Checkpoint savedCheckpoint = new ObjectMapper().readValue(checkpointPath.toFile(), Checkpoint.class);
        assertEquals(java.util.Set.of(1, 2), savedCheckpoint.successfulBatches());
    }

    private static TweetBatch batch(int batchNumber, String tweetId) {
        return new TweetBatch(batchNumber, List.of(new TweetData(tweetId, "text-" + tweetId)));
    }

    private static AiProvider providerReturningResults() throws java.io.IOException {
        return new AiProvider("unused", new Criteria(new ObjectMapper().createObjectNode()), new ObjectMapper()) {
            @Override
            public AnalysisResult analyze(TweetBatch batch) {
                TweetData tweet = batch.tweets().getFirst();
                return new AnalysisResult(batch.batchNumber(),
                        List.of(new TweetDecision(tweet.id(), Decision.KEEP, "accepted")));
            }
        };
    }
}