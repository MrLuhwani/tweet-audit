package dev.luhwani.configuration;

import java.nio.file.Path;

/**
 * 
 * Contains paths used in the audit process.
 */
public final class AuditPaths {

    public static final Path DEFAULT_CRITERIA_PATH = Path.of("config.example.json");
    public static final Path CRITERIA_PATH = Path.of("criteria.json");
    public static final Path TWEETS_PATH = Path.of("/data/tweets.js");
    public static final Path CHECKPOINT_PATH = Path.of("/output/checkpoint.json");
    public static final Path OUTPUT_PATH = Path.of("/output/output.csv");

    private AuditPaths() {
    }

}