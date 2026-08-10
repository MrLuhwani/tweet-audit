package dev.luhwani.tweetProcessing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.luhwani.model.Checkpoint;

public final class CheckpointResolver {
    
    private final ObjectMapper objectMapper;
    private static final Path CHECKPOINT_PATH = Path.of("data/checkpoint.json");

    public CheckpointResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Checkpoint load() throws IOException {
        if (Files.notExists(CHECKPOINT_PATH)) {
            return Checkpoint.empty();
        }
        return objectMapper.readValue(CHECKPOINT_PATH.toFile(), Checkpoint.class);
    }
}
