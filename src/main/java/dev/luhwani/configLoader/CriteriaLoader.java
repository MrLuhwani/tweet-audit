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

			if (!Files.exists(criteriaDefault)) {
				throw new IOException("Fallback criteria file could not be found at " + projectRoot);
			}

			if (!Files.isRegularFile(criteriaDefault)) {
				throw new IOException("Fallback criteria at " + projectRoot + " is not a regular file");
			}
			criteriaPath = criteriaDefault;
			System.out.printf(
					"[WARN] Missing Criteria Data: Could not find %s at path: %s \n Default criteria will be used for tweet analysis. \n",
					CRITERIA_PATH, projectRoot);
		}

		if (!Files.isReadable(criteriaPath)) {
			throw new IOException("Could not read criteria file at " + criteriaPath);
		}

		String criteriaJson = Files.readString(criteriaPath);

		if (criteriaJson == null || criteriaJson.strip().isEmpty()) {
			throw new IOException("Empty criteria file found");
		}

		JsonNode node = objectMapper.readTree(criteriaJson);

		if (node.isEmpty()) {
			throw new IOException("Empty criteria file found");
		}

		return criteriaPath;
	}
}
