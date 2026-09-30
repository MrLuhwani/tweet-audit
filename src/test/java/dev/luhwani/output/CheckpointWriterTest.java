package dev.luhwani.output;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.luhwani.model.Checkpoint;

class CheckpointWriterTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void writesSuccessfulBatchesToCheckpointFile() throws Exception {
        Path checkpointPath = tempDir.resolve("checkpoint.json");
        CheckpointWriter writer = new CheckpointWriter(objectMapper, checkpointPath);

        writer.update(Set.of(1, 3));

        Checkpoint checkpoint = objectMapper.readValue(checkpointPath.toFile(), Checkpoint.class);
        assertEquals(Set.of(1, 3), checkpoint.successfulBatches());
    }

    @Test
    void propagatesIOExceptionWhenCheckpointCannotBeWritten() throws Exception {
        Path checkpointDirectory = Files.createDirectory(tempDir.resolve("checkpoint"));
        CheckpointWriter writer = new CheckpointWriter(objectMapper, checkpointDirectory);

        assertThrows(IOException.class, () -> writer.update(Set.of(1)));
    }
}