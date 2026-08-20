package dev.luhwani.output;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.luhwani.model.Checkpoint;

public final class CheckpointResolver {
    
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Path CHECKPOINT_PATH = Path.of("output/checkpoint.json");

    public static Checkpoint load() throws IOException {
        if (Files.notExists(CHECKPOINT_PATH)) {
            return Checkpoint.empty();
        }
        return objectMapper.readValue(CHECKPOINT_PATH.toFile(), Checkpoint.class);
    }

    public static void save(int lastCompletedBatchIndex, String lastTweetId) throws IOException {
        objectMapper.writeValue(CHECKPOINT_PATH.toFile(), new Checkpoint(lastCompletedBatchIndex, lastTweetId));
    }
}
