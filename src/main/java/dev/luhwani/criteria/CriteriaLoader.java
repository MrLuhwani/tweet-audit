package dev.luhwani.criteria;

import java.nio.file.Files;
import java.nio.file.Path;

import dev.luhwani.configuration.AuditPaths;
import dev.luhwani.error.FatalException;

/** Locates and validates the criteria file used to audit tweets. */
public final class CriteriaLoader {

	private static final Path DEFAULT_CRITERIA_PATH = AuditPaths.DEFAULT_CRITERIA_PATH;
	private static final Path CRITERIA_PATH = AuditPaths.CRITERIA_PATH;

	public static Path load() {
		return load(CRITERIA_PATH, DEFAULT_CRITERIA_PATH);
	}

	static Path load(Path criteriaPath, Path defaultCriteriaPath) {
		if (Files.exists(criteriaPath)) {
			return criteriaPath;
		}

		if (Files.exists(defaultCriteriaPath)) {
			return defaultCriteriaPath;
		}

		throw new FatalException("Could not find criteria file at " + criteriaPath + " or fallback at "
				+ defaultCriteriaPath);
	}
}