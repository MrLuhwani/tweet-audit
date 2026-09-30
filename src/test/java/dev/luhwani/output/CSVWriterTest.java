package dev.luhwani.output;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.luhwani.model.AnalysisResult;
import dev.luhwani.model.Decision;
import dev.luhwani.model.TweetDecision;

class CSVWriterTest {

    @TempDir
    Path tempDir;

    @Test
    void appendsRowsToExistingCsvContent() throws Exception {
        Path outputPath = tempDir.resolve("output.csv");
        Files.writeString(outputPath, "tweet_link,decision,reason\n", StandardCharsets.UTF_8);
        AnalysisResult result = new AnalysisResult(1,
                List.of(new TweetDecision("123", Decision.KEEP, "Relevant tweet")));

        try (CSVWriter writer = new CSVWriter(outputPath)) {
            writer.write(result);
        }

        assertEquals(
                List.of(
                        "tweet_link,decision,reason",
                        "\"https://x.com/i/status/123\",KEEP,\"Relevant tweet\""),
                Files.readAllLines(outputPath, StandardCharsets.UTF_8));
    }
}