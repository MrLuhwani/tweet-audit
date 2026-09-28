package dev.luhwani.tweetProcessing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.luhwani.error.FatalException;
import dev.luhwani.model.Checkpoint;

class CheckpointResolverTest {

    private static final String CSV_HEADER = "tweet_link,decision,reason";

    @TempDir
    Path tempDir;

    private final CheckpointResolver resolver = new CheckpointResolver();

    @Test
    void createsEmptyCheckpointAndCsvWhenNeitherFileExists() throws Exception {
        Path checkpointPath = tempDir.resolve("test/output/checkpoint.json");
        Path outputPath = tempDir.resolve("test/output/output.csv");

        Checkpoint checkpoint = resolver.load(checkpointPath, outputPath);

        assertTrue(checkpoint.isEmpty());
        assertTrue(Files.exists(checkpointPath));
        assertEquals(CSV_HEADER + System.lineSeparator(), Files.readString(outputPath));
        assertTrue(resolver.load(checkpointPath, outputPath).isEmpty());
    }

    @Test
    void loadsExistingCheckpointWhenBothFilesExist() throws Exception {
        Path checkpointPath = tempDir.resolve("checkpoint.json");
        Path outputPath = tempDir.resolve("output.csv");
        Checkpoint expected = new Checkpoint(Set.of(1, 3), Set.of(2), 3, "tweet-3");
        Files.writeString(checkpointPath, """
                {
                  "successful_batches": [1, 3],
                  "failed_batches": [2],
                  "last_processed_batch": 3,
                  "last_processed_tweet": "tweet-3"
                }
                """, StandardCharsets.UTF_8);
        Files.writeString(outputPath, "existing output", StandardCharsets.UTF_8);

        Checkpoint actual = resolver.load(checkpointPath, outputPath);

        assertEquals(expected.getSuccessfulBatches(), actual.getSuccessfulBatches());
        assertEquals(expected.getFailedBatches(), actual.getFailedBatches());
        assertEquals(expected.getLastProcessedBatch(), actual.getLastProcessedBatch());
        assertEquals(expected.getLastProcessedTweet(), actual.getLastProcessedTweet());
        assertEquals("existing output", Files.readString(outputPath));
    }

    @Test
    void rejectsMissingCheckpointWhenOutputExists() throws Exception {
        Path checkpointPath = tempDir.resolve("checkpoint.json");
        Path outputPath = Files.createFile(tempDir.resolve("output.csv"));

        assertThrows(IllegalStateException.class, () -> resolver.load(checkpointPath, outputPath));
        assertFalse(Files.exists(checkpointPath));
    }

    @Test
    void rejectsMissingOutputWhenCheckpointExists() throws Exception {
        Path checkpointPath = Files.createFile(tempDir.resolve("checkpoint.json"));
        Path outputPath = tempDir.resolve("output.csv");

        assertThrows(IllegalStateException.class, () -> resolver.load(checkpointPath, outputPath));
        assertTrue(Files.exists(checkpointPath));
    }

    @Test
    void rejectsNullPaths() {
        Path checkpointPath = tempDir.resolve("checkpoint.json");
        Path outputPath = tempDir.resolve("output.csv");

        assertThrows(IllegalArgumentException.class, () -> resolver.load(checkpointPath, null));
        assertThrows(IllegalArgumentException.class, () -> resolver.load(null, outputPath));
    }

    @Test
    void wrapsMalformedCheckpointAsFatalException() throws Exception {
        Path checkpointPath = Files.writeString(tempDir.resolve("checkpoint.json"), "not json");
        Path outputPath = Files.createFile(tempDir.resolve("output.csv"));

        assertThrows(FatalException.class, () -> resolver.load(checkpointPath, outputPath));
    }
}