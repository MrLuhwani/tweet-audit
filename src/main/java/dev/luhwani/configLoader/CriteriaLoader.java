package dev.luhwani.configLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class CriteriaLoader {

	private static final String DEFAULT_CRITERIA_PATH = "config.example.json";
	private static final String CRITERIA_PATH = "criteria.json";
	private final ObjectMapper objectMapper;

	public CriteriaLoader(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}


	public Path load() throws IOException {
		Path projectRoot = Paths.get("").toAbsolutePath();
		Path criteriaPath = projectRoot.resolve(CRITERIA_PATH);
		if (!Files.exists(criteriaPath) || !Files.isRegularFile(criteriaPath)) {
			Path criteriaDefault = projectRoot.resolve(DEFAULT_CRITERIA_PATH);
			if (!Files.exists(criteriaDefault) || !Files.isRegularFile(criteriaDefault)) {
				// not sure if this is concatenable
				throw new IllegalStateException("Default criteria not be found at " + projectRoot);
			}
			criteriaPath = criteriaDefault;
			System.out.printf("[WARN] Missing Criteria Data: Could not find %s at path: %s \n Exit the console if you wish to configure your own criteria as the default criteria will be used. \n", CRITERIA_PATH, projectRoot);
		}

	String criteriaJson = Files.readString(criteriaPath);
		JsonNode node = objectMapper.readTree(criteriaJson);

		if (node.isEmpty()) {
			throw new IllegalStateException("Empty criteria file found");
		}

		return criteriaPath;
	}
}
