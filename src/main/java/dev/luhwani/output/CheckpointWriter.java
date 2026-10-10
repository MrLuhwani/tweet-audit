package dev.luhwani.output;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.luhwani.model.Checkpoint;

final class CheckpointWriter {

    private final ObjectMapper objectMapper;
    private final Path checkpointPath;

    CheckpointWriter(ObjectMapper objectMapper, Path checkpointPath) {
        this.objectMapper = objectMapper;
        this.checkpointPath = checkpointPath;
    }

    void update(Set<Integer> successfulBatches) throws IOException {
        objectMapper.writeValue(checkpointPath.toFile(), new Checkpoint(successfulBatches));
    }
    
}
