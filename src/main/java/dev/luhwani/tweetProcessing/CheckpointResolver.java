package dev.luhwani.tweetProcessing;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.luhwani.model.Checkpoint;

/** Creates or validates the checkpoint and output files used for resuming work. */
public class CheckpointResolver {

    private static final String CSV_HEADER = "batch_number,tweet_link,decision,reason";
    private final Path checkpointPath;
    private final Path outputPath;
    private final Path failurePath;
    private final ObjectMapper objectMapper;

    public CheckpointResolver(Path checkpointPath, Path outputPath, Path failurePath, ObjectMapper objectMapper) {
        this.checkpointPath = checkpointPath;
        this.outputPath = outputPath;
        this.failurePath = failurePath;
        this.objectMapper = objectMapper;
    }

    public Checkpoint load() throws IOException {
        if (Files.exists(checkpointPath) && Files.exists(outputPath)) {
            Checkpoint checkpoint = objectMapper.readValue(checkpointPath.toFile(), Checkpoint.class);
            int lastProcessedBatch = checkpoint.getLastProcessedBatch();

            try {
                boolean lastWrittenBatchFound;
                try (Stream<String> lines = Files.lines(outputPath, StandardCharsets.UTF_8)) {
                    lastWrittenBatchFound = lines
                            .skip(1) // skip CSV header
                            .map(line -> line.split(","))
                            .filter(parts -> parts.length > 0)
                            .map(parts -> parts[0])
                            .map(Integer::parseInt)
                            .anyMatch(batchNumber -> batchNumber == lastProcessedBatch);
                }

                if (!lastWrittenBatchFound && Files.exists(failurePath)) {
                    try (Stream<String> jsonLines = Files.lines(failurePath, StandardCharsets.UTF_8)) {
                        lastWrittenBatchFound = jsonLines
                                .filter(line -> !line.isBlank())
                                .mapToInt(line -> {
                                    try {
                                        return objectMapper.readTree(line).get("batchNumber").asInt();
                                    } catch (JsonProcessingException e) {
                                        throw new IllegalStateException(
                                                "Malformed line in failed batches file: " + line, e);
                                    }
                                })
                                .anyMatch(batchNumber -> batchNumber == lastProcessedBatch);
                    }
                }

                if (lastWrittenBatchFound || checkpoint.isEmpty()) {
                    return checkpoint;
                } else {
                    throw new IllegalStateException("Checkpoint file and output file are out of sync");
                }
            } catch (NumberFormatException e) {
                throw new IllegalStateException("Output file contains invalid batch number", e);
            }
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

        throw new IllegalStateException("Could not resolve audit progess. Checkpoint file, or output file missing");
    }
}
