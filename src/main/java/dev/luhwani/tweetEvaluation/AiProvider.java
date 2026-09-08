package dev.luhwani.tweetEvaluation;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.luhwani.model.AnalysisResult;
import dev.luhwani.model.TweetBatch;
import dev.luhwani.tweetEvaluation.exception.AiProviderException;

public abstract class AiProvider {

    protected final ObjectMapper mapper;
    protected final String apiKey;
    protected final JsonNode criteriaNode;

    protected AiProvider(String apiKey, Path criteria, ObjectMapper mapper) throws IOException {
        this.mapper = mapper;
        this.apiKey = apiKey;
        this.criteriaNode = mapper.readTree(Files.readString(criteria));
    }

    public abstract AnalysisResult analyze(TweetBatch batch) throws IOException, AiProviderException;

}
