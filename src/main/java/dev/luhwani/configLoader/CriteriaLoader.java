package dev.luhwani.configLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class CriteriaLoader {

    private static final String DEFAULT_CRITERIA_PATH = "config.example.json";
    private static final String CRITERIA_PATH = "criteria.js";
    private final ObjectMapper objectMapper;

    public CriteriaLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }


    public Path load() throws InterruptedException, IOException {
        Path projectRoot = Paths.get("").toAbsolutePath();
        Path criteriaPath = projectRoot.resolve(CRITERIA_PATH);
        if (!Files.exists(criteriaPath) || !Files.isRegularFile(criteriaPath)) {
            criteriaPath = Path.of(DEFAULT_CRITERIA_PATH);
            System.out.printf("""
                    [WARN] Missing Criteria Data: Could not find %s at path: %s
                    Exit the console if you wish to configure your own criteria as the default criteria will be used. \n""", CRITERIA_PATH, projectRoot);
        }

        // just to give the user a chance to exit the console if they forgot to add their criteria
        Thread.sleep(3000);

        String criteriaJson = Files.readString(criteriaPath);
        objectMapper.readTree(criteriaJson); // Fail early when criteria.json is invalid JSON.
        
        return criteriaPath;
    }
}
