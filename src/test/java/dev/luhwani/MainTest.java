package dev.luhwani;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.LogManager;

import org.junit.jupiter.api.Test;

class MainTest {

    @Test
    void initializesLoggingWhenMainClassIsLoaded() throws Exception {
        Class.forName("dev.luhwani.Main");

        assertTrue(Files.isDirectory(Path.of("logs")));
        assertEquals("logs/app-%g.log",
                LogManager.getLogManager().getProperty("java.util.logging.FileHandler.pattern"));
    }
}
