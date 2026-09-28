package dev.luhwani.tweetProcessing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.luhwani.configuration.AuditPaths;
import dev.luhwani.error.FatalException;
import dev.luhwani.model.Checkpoint;

public final class CheckpointResolver {

    private static final String CSV_HEADER = "tweet_link,decision,reason";

    private static final Path CHECKPOINT_PATH = AuditPaths.CHECKPOINT_PATH;
    private static final Path OUTPUT_PATH = AuditPaths.OUTPUT_PATH;

    private final ObjectMapper objectMapper;

    public CheckpointResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    CheckpointResolver() {
        this(new ObjectMapper());
    }

    public Checkpoint load() {
        return load(CHECKPOINT_PATH, OUTPUT_PATH);
    }

    Checkpoint load(Path checkpointPath, Path outputPath) {
        if (checkpointPath == null) {
            throw new IllegalArgumentException("Checkpoint path cannot be null");
        }
        if (outputPath == null) {
            throw new IllegalArgumentException("Output path cannot be null");
        }

        try {

            if (Files.exists(checkpointPath) && Files.exists(outputPath)) {
                Checkpoint checkpoint = objectMapper.readValue(checkpointPath.toFile(), Checkpoint.class);
                return checkpoint;
            }

            if (!Files.exists(checkpointPath) && !Files.exists(outputPath)) {
                Files.createDirectories(checkpointPath.getParent());
                Files.createDirectories(outputPath.getParent());
                Files.writeString(
                        outputPath,
                        CSV_HEADER + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);

                objectMapper.writerWithDefaultPrettyPrinter().writeValue(checkpointPath.toFile(), Checkpoint.empty());
                return Checkpoint.empty();
            }

        } catch (IOException e) {
            throw new FatalException("Could not resolve audit progress: " + e.getMessage(), e);
        }

        throw new IllegalStateException("Could not resolve audit progess. Checkpoint file, or output file missing");
    }
}
