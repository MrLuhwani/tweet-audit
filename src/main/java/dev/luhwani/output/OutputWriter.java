package dev.luhwani.output;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.luhwani.model.Checkpoint;

public class OutputWriter {

    private static final String CSV_HEADER = "tweet_link,decision,reason";
    private static final Path CHECKPOINT_PATH = Paths
            .get("")
            .toAbsolutePath()
            .normalize()
            .resolve("output/checkpoint.json");

    private static final Path OUTPUT_PATH = Paths.get("")
            .toAbsolutePath()
            .normalize()
            .resolve("output/output.csv");

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public static Checkpoint loadCheckpoint() throws IOException {
        if (Files.exists(CHECKPOINT_PATH) && Files.exists(OUTPUT_PATH)) {
            Checkpoint checkpoint = objectMapper.readValue(CHECKPOINT_PATH.toFile(), Checkpoint.class);
            try (Stream<String> lines = Files.lines(OUTPUT_PATH, StandardCharsets.UTF_8)) {
                Optional<String> lastNonEmptyLine = lines
                        .filter(line -> !line.isBlank()) // slips empty lines
                        .filter(line -> !line.equals(CSV_HEADER)) // skips the header
                        .reduce((first, second) -> second); // Only keeps the latest line encountered

                if (lastNonEmptyLine.isEmpty() && checkpoint.isEmpty()) {
                    return checkpoint;
                }
                String lastTweetId = lastNonEmptyLine.get().split(",", 2)[0];
                if (!lastTweetId.contains(checkpoint.lastTweetId())) {
                    throw new IllegalStateException(
                            "Checkpoint data and output data do not match. Checkpoint last tweet = "
                                    + checkpoint.lastTweetId()
                                    + " Output last tweet id = " + lastTweetId);
                }

                return checkpoint;
            }
        }

        if (!Files.exists(CHECKPOINT_PATH) && !Files.exists(OUTPUT_PATH)) {

            Files.createDirectories(CHECKPOINT_PATH.getParent());
            Files.createDirectories(OUTPUT_PATH.getParent());
            Files.writeString(
                    OUTPUT_PATH,
                    CSV_HEADER + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            objectMapper.writerWithDefaultPrettyPrinter().writeValue(CHECKPOINT_PATH.toFile(), Checkpoint.empty());
            return Checkpoint.empty();
        }
        throw new IllegalStateException("Could not resolve audit progess. Checkpoint file, or output file missing");

    }
}
