package dev.luhwani.configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 
 * Contains paths used in the audit process.
 */
public final class AuditPaths {

    public static final Path DEFAULT_CRITERIA_PATH = Paths.get("config.example.json");
    public static final Path CRITERIA_PATH = Paths.get("criteria.json");
    public static final Path TWEETS_PATH = Paths.get("data","tweets.js");
    public static final Path CHECKPOINT_PATH = Paths.get("output","checkpoint.json");
    public static final Path CSV_PATH = Paths.get("output","output.csv");

    private AuditPaths() {
    }

}