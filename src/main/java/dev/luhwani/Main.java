package dev.luhwani;

import java.io.File;
import java.io.InputStream;
import java.util.logging.LogManager;

import dev.luhwani.application.TweetAuditApp;

/**
 * Entry point that initializes logging and starts the Tweet Audit application.
 */
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
        /*
         * After the application finishes running, some google worker
         * threads are still awake in the JVM. They have no negative
         * effect on the application
         * but if you call system.exit() without this initial sleep
         * sometimes the jvm closes before the OutputWriter
         * can write the very last batch to the output file
         * 
         */
        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            e.printStackTrace();
        }
        System.exit(0);
    }

}
