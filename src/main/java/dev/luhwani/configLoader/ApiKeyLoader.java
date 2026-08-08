package dev.luhwani.configLoader;

public final class ApiKeyLoader {

    private ApiKeyLoader() {
    }

    public static String load() {
        String apiKey = System.getenv("GEMINI_API_KEY");

        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("""
                    [ERROR] GEMINI_API_KEY was not found.
                    Please set the environment variable before running Tweet Audit.
                    """);
        }

        return apiKey;
    }

}