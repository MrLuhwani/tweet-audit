package dev.luhwani.configLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class CriteriaLoader {

    private static final String DEFAULT_CRITERIA_PATH = "config.example.json";
    private static final String CRITERIA_PATH = "criteria.js";

    private CriteriaLoader() {
    }

    public static Path load() {
        Path projectRoot = Paths.get("").toAbsolutePath();
        Path criteriaPath = projectRoot.resolve(CRITERIA_PATH);
        if (!Files.exists(criteriaPath) || !Files.isRegularFile(criteriaPath)) {
            criteriaPath = Path.of(DEFAULT_CRITERIA_PATH);
            System.out.printf("""
                    [WARN] Missing Criteria Data: Fallback to default configuration. Could not find %s
                    at path: %s
                    Exit the console if you wish to configure your own criteria""", CRITERIA_PATH, projectRoot);
        }
        return criteriaPath;
    }
}
