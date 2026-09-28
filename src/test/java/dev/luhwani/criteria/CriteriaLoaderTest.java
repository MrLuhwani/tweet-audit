package dev.luhwani.criteria;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CriteriaLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void returnsCriteriaPathWhenItExists() throws Exception {
        Path criteriaPath = Files.createFile(tempDir.resolve("criteria.json"));
        Path defaultPath = tempDir.resolve("config.example.json");

        assertEquals(criteriaPath, CriteriaLoader.load(criteriaPath, defaultPath));
    }

    @Test
    void returnsDefaultPathWhenCriteriaPathDoesNotExist() throws Exception {
        Path criteriaPath = tempDir.resolve("criteria.json");
        Path defaultPath = Files.createFile(tempDir.resolve("config.example.json"));

        assertEquals(defaultPath, CriteriaLoader.load(criteriaPath, defaultPath));
    }

    @Test
    void throwsWhenNeitherPathExists() {
        Path criteriaPath = tempDir.resolve("criteria.json");
        Path defaultPath = tempDir.resolve("config.example.json");

        assertThrows(RuntimeException.class, () -> CriteriaLoader.load(criteriaPath, defaultPath));
    }
}