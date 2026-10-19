package dev.luhwani.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.luhwani.error.FatalException;

class TweetAuditAppTest {

    @TempDir
    Path tempDir;

    @Test
    void parsesRequiredArgumentsAndAppliesDefaults() {
        TweetAuditApp.CliArguments arguments = TweetAuditApp.parseArgs(
                new String[] { "--api-key", "key", "--tweet-archive", "tweets.zip" });

        assertEquals("key", arguments.apiKey());
        assertEquals("tweets.zip", arguments.tweetArchive());
        assertEquals("gemini-3.5-flash-lite", arguments.model());
        assertEquals(60, arguments.batchSize());
        assertEquals(null, arguments.criteria());
        assertEquals(null, arguments.output());
    }

    @Test
    void parsesOptionalArguments() {
        TweetAuditApp.CliArguments arguments = TweetAuditApp.parseArgs(new String[] {
                "--api-key", "key", "--tweet-archive", "tweets.zip", "--model", "model",
                "--criteria", "criteria.json", "--output", "results", "--batch-size", "25"
        });

        assertEquals("model", arguments.model());
        assertEquals("criteria.json", arguments.criteria());
        assertEquals("results", arguments.output());
        assertEquals(25, arguments.batchSize());
    }

    @Test
    void rejectsInvalidCliArguments() {
        assertThrows(IllegalArgumentException.class, () -> TweetAuditApp.parseArgs(null));
        assertThrows(IllegalArgumentException.class, () -> TweetAuditApp.parseArgs(new String[] {}));
        assertThrows(IllegalArgumentException.class,
                () -> TweetAuditApp.parseArgs(new String[] { "--api-key" }));
        assertThrows(IllegalArgumentException.class,
                () -> TweetAuditApp.parseArgs(new String[] { "api-key", "key" }));
        assertThrows(IllegalArgumentException.class,
                () -> TweetAuditApp.parseArgs(new String[] { "--unknown", "value" }));
        assertThrows(IllegalArgumentException.class,
                () -> TweetAuditApp.parseArgs(new String[] { "--api-key", "key", "--tweet-archive", "archive",
                        "--api-key", "second" }));
        assertThrows(IllegalArgumentException.class,
                () -> TweetAuditApp.parseArgs(new String[] { "--api-key", " ", "--tweet-archive", "archive" }));
        assertThrows(IllegalArgumentException.class,
                () -> TweetAuditApp.parseArgs(new String[] { "--api-key", "key", "--tweet-archive", "archive",
                        "--batch-size", "0" }));
        assertThrows(IllegalArgumentException.class,
                () -> TweetAuditApp.parseArgs(new String[] { "--api-key", "key", "--tweet-archive", "archive",
                        "--batch-size", "not-a-number" }));
    }

    @Test
    void resolvesDefaultAndConfiguredOutputDirectories() throws Exception {
        Path configured = TweetAuditApp.resolveOutputDirectory(tempDir.resolve("nested").toString());

        assertTrue(Files.isDirectory(configured));
        assertEquals(configured, TweetAuditApp.resolveOutputDirectory(configured.toString()));
        assertThrows(IllegalArgumentException.class, () -> TweetAuditApp.resolveOutputDirectory(" "));

        Path file = Files.createFile(tempDir.resolve("output-file"));
        assertThrows(IllegalArgumentException.class, () -> TweetAuditApp.resolveOutputDirectory(file.toString()));
    }

    @Test
    void loadsDefaultAndCustomCriteria() throws Exception {
        assertTrue(TweetAuditApp.loadCriteria(null).rules().isObject());
        Path criteria = Files.writeString(tempDir.resolve("criteria.json"), "{\"criteria\":{\"tone\":\"calm\"}}");

        assertEquals("calm", TweetAuditApp.loadCriteria(criteria.toString()).rules().get("tone").asText());
        assertThrows(FatalException.class, () -> TweetAuditApp.loadCriteria(tempDir.resolve("missing.json").toString()));
    }
}