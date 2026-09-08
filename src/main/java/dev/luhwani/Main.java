package dev.luhwani;

import java.io.File;
import java.io.InputStream;
import java.util.logging.LogManager;

import dev.luhwani.application.TweetAuditApp;

public final class Main {

    static {
        File logDir = new File("logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }

        try (InputStream stream = Main.class.getClassLoader().getResourceAsStream("logging.properties")) {
            if (stream == null) {
                throw new IllegalStateException("Could not load logging.properties file from resources");
            }
            LogManager.getLogManager().readConfiguration(stream);
        } catch (Exception e) {
            throw new ExceptionInInitializerError("Failed to initialize logging: " + e.getMessage());
        }
    }

    private Main() {
    }

    public static void main(String[] args) {
        TweetAuditApp.run();
        System.exit(1);
    }

}
