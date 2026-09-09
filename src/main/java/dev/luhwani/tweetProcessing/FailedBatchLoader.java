package dev.luhwani.tweetProcessing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.luhwani.model.TweetBatch;

public final class FailedBatchLoader {

    public static final Path FAILED_BATCH_PATH = Paths.get("")
            .toAbsolutePath()
            .normalize()
            .resolve("output/failedBatches.jsonl");

    private final ObjectMapper objectMapper;

    public FailedBatchLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<TweetBatch> load() throws IOException {
        if (!Files.exists(FAILED_BATCH_PATH)) {
            return List.of();
        }
        if (!Files.isRegularFile(FAILED_BATCH_PATH) || !Files.isReadable(FAILED_BATCH_PATH)) {
            throw new IOException("Failed batches file is not readable at: " + FAILED_BATCH_PATH);
        }

        List<TweetBatch> batches = new ArrayList<>();
        List<String> lines = Files.readAllLines(FAILED_BATCH_PATH, StandardCharsets.UTF_8);
        for (int lineNumber = 0; lineNumber < lines.size(); lineNumber++) {
            String line = lines.get(lineNumber).trim();
            if (line.isEmpty()) {
                continue;
            }
            try {
                batches.add(objectMapper.readValue(line, TweetBatch.class));
            } catch (IOException | RuntimeException e) {
                throw new IOException("Could not parse failed batch on line " + (lineNumber + 1), e);
            }
        }
        return List.copyOf(batches);
    }
}