package dev.luhwani.configuration;

import dev.luhwani.error.FatalException;

/** Loads the Gemini API key from the {@code GEMINI_API_KEY} environment variable. */
public final class ApiKeyLoader {

    private ApiKeyLoader() {
    }

    public static String load() {
        String apiKey = System.getenv("GEMINI_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            throw new FatalException("GEMINI_API_KEY was not found. \n Please set the environment variable before running Tweet Audit.");
        }

        return apiKey;
    }

}