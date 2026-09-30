package dev.luhwani.configuration;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 
 * Contains paths used in the audit process.
 */
public final class AuditPaths {

    /** Fallback criteria file included with the project. */
    public static final Path DEFAULT_CRITERIA_PATH = Paths.get("config.example.json");
    /** User-provided criteria file, preferred over the fallback. */
    public static final Path CRITERIA_PATH = Paths.get("criteria.json");
    /** Exported X tweet archive. */
    public static final Path TWEETS_PATH = Paths.get("data", "tweets.js");
    /** JSON file containing successfully processed batch numbers. */
    public static final Path CHECKPOINT_PATH = Paths.get("output", "checkpoint.json");
    /** CSV file containing the decisions produced by the audit. */
    public static final Path CSV_PATH = Paths.get("output", "output.csv");

    private AuditPaths() {
    }

}