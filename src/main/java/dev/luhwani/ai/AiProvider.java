package dev.luhwani.ai;

import java.io.IOException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.luhwani.error.BatchException;
import dev.luhwani.error.FatalException;
import dev.luhwani.error.RetryableException;
import dev.luhwani.model.AnalysisResult;
import dev.luhwani.model.Criteria;
import dev.luhwani.model.TweetBatch;

/** Defines the provider contract for turning a tweet batch into decisions. */
public abstract class AiProvider {

    protected final ObjectMapper mapper;
    protected final String apiKey;
    protected final JsonNode criteriaNode;

    /**
     * Creates a provider using the credentials, criteria, and JSON mapper shared
     * by an implementation.
     *
     * @param apiKey API key used by the provider
     * @param criteria rules used to evaluate tweets
     * @param mapper mapper used for request and response JSON
     * @throws IOException if the criteria cannot be converted to JSON
     */
    protected AiProvider(String apiKey, Criteria criteria, ObjectMapper mapper) throws IOException {
        this.mapper = mapper;
        this.apiKey = apiKey;
        this.criteriaNode = criteria.rules();
    }

        /**
         * Evaluates every tweet in a batch.
         *
         * @param batch batch to evaluate
         * @return the decisions returned for the batch
         * @throws RetryableException when the request may succeed if retried
         * @throws BatchException when the batch cannot be evaluated successfully
         * @throws FatalException when processing should stop without retrying
         */
        public abstract AnalysisResult analyze(TweetBatch batch)
            throws RetryableException, BatchException, FatalException;

}
