package dev.luhwani.tweetProcessing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.luhwani.model.TweetBatch;

/** Reads previously failed tweet batches from the JSON Lines recovery file. */
public class FailedBatchLoader {

    private final ObjectMapper objectMapper;
    private final Path failedBatchPath;
    
    public FailedBatchLoader(ObjectMapper objectMapper, Path failedBatchPath) {
        this.objectMapper = objectMapper;
        this.failedBatchPath = failedBatchPath;
    }

    public Map<Integer, TweetBatch> load() throws IOException {
        if (!Files.exists(failedBatchPath)) {
            return Map.of();
        }
        if (!Files.isRegularFile(failedBatchPath) || !Files.isReadable(failedBatchPath)) {
            throw new IOException("Failed batches file is not readable at: " + failedBatchPath);
        }

        Map<Integer, TweetBatch> batchNumToBatchMap = new HashMap<>();
        List<String> lines = Files.readAllLines(failedBatchPath, StandardCharsets.UTF_8);
        for (int lineNumber = 0; lineNumber < lines.size(); lineNumber++) {
            String line = lines.get(lineNumber).trim();
            if (line.isEmpty()) {
                continue;
            }
            try {
                TweetBatch batch = objectMapper.readValue(line, TweetBatch.class);
                batchNumToBatchMap.put(batch.batchNumber(), batch);
            } catch (IOException | RuntimeException e) {
                throw new IOException("Could not parse failed batch on line " + (lineNumber + 1), e);
            }
        }
        return Map.copyOf(batchNumToBatchMap);
    }
}
