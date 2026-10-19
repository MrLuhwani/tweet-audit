package dev.luhwani.output;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.luhwani.model.AnalysisResult;
import dev.luhwani.model.Checkpoint;
import dev.luhwani.model.Decision;
import dev.luhwani.model.TweetDecision;

class OutputWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void writesCsvAndCheckpointForCompletedResult() throws Exception {
        var queue = new ArrayBlockingQueue<AnalysisResult>(2);
        var complete = new AtomicBoolean(false);
        Checkpoint checkpoint = Checkpoint.empty();
        queue.add(new AnalysisResult(4, List.of(new TweetDecision("123", Decision.DELETE, "reason"))));
        OutputWriter writer = new OutputWriter(new ObjectMapper(), queue, complete::get, checkpoint, tempDir);

        writer.start();
        complete.set(true);
        writer.awaitCompletion();
        writer.close();

        assertTrue(Files.readString(tempDir.resolve("output.csv")).contains("https://x.com/i/status/123"));
        assertTrue(Files.readString(tempDir.resolve("checkpoint.json")).contains("4"));
        assertTrue(checkpoint.successfulBatches().containsAll(Set.of(4)));
    }
}