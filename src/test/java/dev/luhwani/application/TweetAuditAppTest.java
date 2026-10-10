package dev.luhwani.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.luhwani.ai.AiProvider;
import dev.luhwani.model.Checkpoint;
import dev.luhwani.model.Criteria;

class TweetAuditAppTest {

    @Test
    void acceptsApiKeyAndTweetZipArguments() {
        assertDoesNotThrow(() -> TweetAuditApp.validArgs(
            new String[] { "--api-key", "api-key", "--tweet-archive", "tweets.zip" }));
        assertDoesNotThrow(() -> TweetAuditApp.validArgs(new String[] {
            "--api-key", "api-key", "--model", "gemini-2.5-flash", "--tweet-archive", "tweets.zip", "--criteria", "criteria.json", "--output",
            "output"
        }));
    }

    @Test
    void rejectsMissingOrExtraArguments() {
        assertThrows(IllegalArgumentException.class,
            () -> TweetAuditApp.validArgs(new String[] { "--api-key", "api-key" }));
        assertThrows(IllegalArgumentException.class,
            () -> TweetAuditApp.validArgs(
                new String[] { "--api-key", "api-key", "--tweet-archive", "tweets.zip", "--unknown", "value" }));
    }

    @Test
    void allowsOutputWithoutCriteria() {
        TweetAuditApp.CliArguments arguments = TweetAuditApp.parseArgs(new String[] {
            "--api-key", "api-key", "--tweet-archive", "tweets.zip", "--output", "custom-output"
        });

        assertEquals("custom-output", arguments.output());
        assertEquals(null, arguments.criteria());
        assertEquals("gemini-3.5-flash-lite", arguments.model());
    }

    @Test
    void usesProvidedModelWhenConfigured() {
        TweetAuditApp.CliArguments arguments = TweetAuditApp.parseArgs(new String[] {
            "--api-key", "api-key", "--model", "gemini-2.5-flash", "--tweet-archive", "tweets.zip"
        });

        assertEquals("gemini-2.5-flash", arguments.model());
    }

    @Test
    void createsConfiguredOutputDirectory() throws Exception {
        Path outputDirectory = Files.createTempDirectory("audit-parent-").resolve("output");

        Path actual = TweetAuditApp.resolveOutputDirectory(outputDirectory.toString());

        assertEquals(outputDirectory.toAbsolutePath().normalize(), actual);
        org.junit.jupiter.api.Assertions.assertTrue(Files.isDirectory(actual));
    }

    @Test
    void rejectsBlankAndFileOutputPaths() throws Exception {
        Path file = Files.createTempFile("audit-output-", ".tmp");

        Path defaultOutput = TweetAuditApp.resolveOutputDirectory(null);
        assertEquals(Path.of("output").toAbsolutePath().normalize(), defaultOutput);
        assertTrue(Files.isDirectory(defaultOutput));
        assertThrows(IllegalArgumentException.class, () -> TweetAuditApp.resolveOutputDirectory(" "));
        assertThrows(IllegalArgumentException.class, () -> TweetAuditApp.resolveOutputDirectory(file.toString()));
    }

    @Test
    void loadsCriteriaFromConfiguredPath() throws Exception {
        Path criteriaPath = Files.createTempFile("criteria-", ".json");
        Files.writeString(criteriaPath, "{\"rules\": {\"topic\": \"technology\"}}");

        Criteria criteria = TweetAuditApp.loadCriteria(criteriaPath.toString());

        assertEquals("technology", criteria.rules().get("topic").asText());
    }

    @Test
    void loadsDefaultCriteriaFromResources() throws Exception {
        Criteria criteria = TweetAuditApp.loadCriteria(null);

        assertEquals("Outdated political opinions", criteria.rules().get("topics_to_exclude").get(0).asText());
    }

    @Test
    void completesWithoutStartingEvaluationWhenThereAreNoBatches() throws Exception {
        AiProvider provider = new AiProvider("unused", new Criteria(new ObjectMapper().createObjectNode()),
                new ObjectMapper()) {
            @Override
            public dev.luhwani.model.AnalysisResult analyze(dev.luhwani.model.TweetBatch batch) {
                throw new AssertionError("The provider should not be used for an empty batch list");
            }
        };

        assertDoesNotThrow(() -> TweetAuditApp.evaluateTweets(List.of(), provider, Checkpoint.empty(),
            Files.createTempDirectory("audit-output-")));
    }
}