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

/** Creates and reads the checkpoint and CSV files used to resume an audit. */
public final class CheckpointResolver {

    // changes to the header here should also be applied to the
    // TweetDecision class
    static final String CSV_HEADER = "tweet_link,decision,reason";

    private static final Path CHECKPOINT_PATH = AuditPaths.CHECKPOINT_PATH;
    private static final Path CSV_PATH = AuditPaths.CSV_PATH;

    private final ObjectMapper objectMapper;

    /**
     * Creates a resolver using the supplied JSON mapper.
     *
     * @param objectMapper mapper used to read and write checkpoints
     */
    public CheckpointResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    CheckpointResolver() {
        this(new ObjectMapper());
    }

    /**
     * Resolves the configured output files, creating an empty pair when neither
     * file exists.
     *
     * @return the current checkpoint
     * @throws FatalException if the files cannot be created or read
     * @throws IllegalStateException if only one of the two files exists
     */
    public Checkpoint load() throws FatalException {
        return load(CHECKPOINT_PATH, CSV_PATH);
    }

    Checkpoint load(Path checkpointPath, Path outputPath) throws FatalException {
        if (checkpointPath == null) {
            throw new IllegalArgumentException("Checkpoint path cannot be null");
        }
        if (outputPath == null) {
            throw new IllegalArgumentException("Output path cannot be null");
        }

        try {

            boolean checkpointExists = Files.exists(checkpointPath);
            boolean outputExists = Files.exists(outputPath);

            if (checkpointExists && outputExists) {
                return objectMapper.readValue(checkpointPath.toFile(), Checkpoint.class);
            }

            if (!checkpointExists && !outputExists) {
                Files.createDirectories(checkpointPath.getParent());
                Files.createDirectories(outputPath.getParent());
                Files.writeString(
                        outputPath,
                        CSV_HEADER + System.lineSeparator(),
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING);
                objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValue(checkpointPath.toFile(), Checkpoint.empty());
                return Checkpoint.empty();
            }

            throw new IllegalStateException(
                    "Checkpoint and output files must exist together: checkpoint=" + checkpointPath +
                            ", output=" + outputPath);

        } catch (IOException e) {
            throw new FatalException("Could not resolve audit progress: " + e.getMessage(), e);
        }

    }

    /**
     * Reads the configured checkpoint without creating or validating the CSV.
     *
     * @return the stored checkpoint
     * @throws IOException if the checkpoint cannot be read or deserialized
     */
    public Checkpoint fromFile() throws IOException {
        return fromFile(CHECKPOINT_PATH);
    }

    Checkpoint fromFile(Path checkpointPath) throws IOException {
        if (checkpointPath == null) {
            throw new IllegalArgumentException("Checkpoint path cannot be null");
        }
        Checkpoint checkpoint = objectMapper.readValue(checkpointPath.toFile(), Checkpoint.class);
        return checkpoint;
    }
}
