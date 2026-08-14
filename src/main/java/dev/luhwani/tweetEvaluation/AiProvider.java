package dev.luhwani.tweetEvaluation;

import java.nio.file.Path;
import java.time.Duration;

import dev.luhwani.model.TweetBatch;

public abstract class AiProvider {

    protected final Duration intervalMillis;
    private final String apiKey;
    private final Path criteria;

    protected AiProvider(String apiKey, Path criteria) {

        long millis = defineIntervalMillis();
        if (millis <= 0) {
            throw new IllegalArgumentException("Provider interval set to " + millis + ". Must be greater than zero");
        }

        this.intervalMillis = Duration.ofMillis(millis);
        this.apiKey = apiKey;
        this.criteria = criteria;
    }

    public String getApiKey() {
        return apiKey;
    }

    public Path getCriteria() {
        return criteria;
    }

    public abstract void analyze(TweetBatch batch);

    public Duration getIntervalMillis() {
        return intervalMillis;
    }

    /**
     * each @link AiProvider implementation must provide it's own number of
     * milliseconds for request intervals
     * 
     * @return millis
     */
    protected abstract long defineIntervalMillis();
}
