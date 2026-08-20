package dev.luhwani.tweetEvaluation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.luhwani.model.AnalysisResult;
import dev.luhwani.model.TweetBatch;

public abstract class AiProvider {

    protected final ObjectMapper mapper;
    protected final Duration requestInterval;
    protected final String apiKey;
    protected final JsonNode criteriaNode;
    protected final int maxAttempts = 5;

    protected AiProvider(String apiKey, Path criteria, ObjectMapper mapper) throws IOException {

        long millis = definerequestInterval();
        if (millis <= 0) {
            throw new IllegalArgumentException("Provider interval set to " + millis + ". Must be greater than zero");
        }
        this.mapper = mapper;

        this.requestInterval = Duration.ofMillis(millis);
        this.apiKey = apiKey;
        this.criteriaNode = mapper.readTree(Files.readString(criteria));
    }

    public String getApiKey() {
        return apiKey;
    }

    public JsonNode getCriteria() {
        return criteriaNode;
    }

    public abstract AnalysisResult analyze(TweetBatch batch) throws IOException;

    public Duration getrequestInterval() {
        return requestInterval;
    }

    /**
     * each {@link AiProvider} implementation must provide it's own number of
     * milliseconds for request intervals
     * 
     * @return millis
     */
    protected abstract long definerequestInterval();
}
