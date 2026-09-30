package dev.luhwani.ai.gemini;

import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.ClientOptions;
import com.google.genai.types.GenerateContentConfig;

import dev.luhwani.ai.AiProvider;
import dev.luhwani.client.ErrorResolver;
import dev.luhwani.error.*;
import dev.luhwani.model.AnalysisResult;
import dev.luhwani.model.Criteria;
import dev.luhwani.model.TweetBatch;
import dev.luhwani.model.TweetData;
import okhttp3.OkHttpClient;

/**
 * Uses Google's Gemini API to classify tweets against the configured criteria.
 */
public class GeminiAiProvider extends AiProvider {

    // TODO: make model choice more confifgurable
    private final String MODEL = "gemini-3.5-flash-lite";
    private final Client client;
    private final GenerateContentConfig requestConfig;
    private final ContentGenerator contentGenerator;

    /**
     * Creates a Gemini-backed evaluation provider.
     *
     * @param apiKey Gemini API key
     * @param criteria rules sent with each evaluation request
     * @param mapper mapper used to serialize prompts and parse responses
     * @throws IOException if the criteria cannot be prepared for requests
     */
    public GeminiAiProvider(String apiKey, Criteria criteria, ObjectMapper mapper) throws IOException {
        super(apiKey, criteria, mapper);
        OkHttpClient customHttpClient = new OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(40))
                .build();
        this.client = Client.builder()
                .apiKey(apiKey)
                .clientOptions(ClientOptions.builder()
                        .customHttpClient(customHttpClient)
                        .build())
                .build();
        this.requestConfig = buildConfig();
                this.contentGenerator = (model, prompt, config) -> client.models.generateContent(model, prompt, config).text();
                }

                GeminiAiProvider(String apiKey, Criteria criteria, ObjectMapper mapper, ContentGenerator contentGenerator)
                    throws IOException {
                super(apiKey, criteria, mapper);
                this.client = null;
                this.requestConfig = buildConfig();
                this.contentGenerator = contentGenerator;
    }

    /**
     * Sends a batch to Gemini and parses its structured classification response.
     *
     * @param batch batch to classify
     * @return Gemini's decisions for the batch
     * @throws RetryableException for transient API or network failures
     * @throws BatchException for an unusable batch response
     * @throws FatalException for permanent API or response-format failures
     */
    @Override
    public AnalysisResult analyze(TweetBatch batch) throws RetryableException, BatchException, FatalException {
        try {
            String prompt = buildPrompt(batch, criteriaNode);
            String jsonResponse = contentGenerator.generate(MODEL, prompt, requestConfig);
            if (jsonResponse == null || jsonResponse.isEmpty()) {
                throw new BatchException("Gemini responded with an empty response");
            }
            return mapper.readValue(jsonResponse, AnalysisResult.class);

        } catch (ApiException e) {
            Optional<Integer> code = Optional.ofNullable(e.code());
            if (code.isEmpty()) {
                ErrorResolver.throwFromMessage(e);
            }
            if (code.get() == 408 || code.get() == 429 || code.get() >= 500) {
                throw new RetryableException(e);
            }
            if (code.get() >= 400) {
                throw new FatalException(e);
            }
            throw new BatchException(e);
        } catch (GenAiIOException e) {
            throw new RetryableException("Error while sending request to Gemini", e);
        } catch (JsonProcessingException e) {
            throw new FatalException("Could not properly parse json", e);
        }

    }

    private String buildPrompt(TweetBatch batch, JsonNode criteria) throws JsonProcessingException {

        ObjectNode batchJson = mapper.createObjectNode();

        batchJson.put("batch_number", batch.batchNumber());

        ArrayNode tweets = mapper.createArrayNode();

        for (TweetData tweet : batch.tweets()) {

            ObjectNode t = mapper.createObjectNode();

            t.put("id", tweet.id());
            t.put("text", tweet.text());

            tweets.add(t);
        }

        batchJson.set("tweets", tweets);

        return String.format(
                """
                                Evaluate every tweet against the supplied deletion criteria.
                                Rules:
                                1. Decision must be either KEEP or DELETE.
                                2. Give reasons for your decision
                                3. Never invent tweet ids.
                                Deletion Criteria:
                                %s
                                Tweet Batch:
                                %s
                        """,
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(criteria),
                mapper.writerWithDefaultPrettyPrinter().writeValueAsString(batchJson));
    }

    private GenerateContentConfig buildConfig() {

        ObjectNode schema = mapper.createObjectNode();

        schema.put("type", "object");

        ObjectNode props = mapper.createObjectNode();

        ObjectNode batchNumber = mapper.createObjectNode();
        batchNumber.put("type", "integer");

        ObjectNode results = mapper.createObjectNode();
        results.put("type", "array");

        ObjectNode item = mapper.createObjectNode();
        item.put("type", "object");

        ObjectNode itemProps = mapper.createObjectNode();

        ObjectNode tweetId = mapper.createObjectNode();
        tweetId.put("type", "string");

        ObjectNode decision = mapper.createObjectNode();
        decision.put("type", "string");

        ArrayNode enums = mapper.createArrayNode();
        enums.add("KEEP");
        enums.add("DELETE");

        decision.set("enum", enums);

        ObjectNode reason = mapper.createObjectNode();
        reason.put("type", "string");

        itemProps.set("tweet_id", tweetId);
        itemProps.set("decision", decision);
        itemProps.set("reason", reason);

        item.set("properties", itemProps);

        ArrayNode reqItem = mapper.createArrayNode();
        reqItem.add("tweet_id");
        reqItem.add("decision");
        reqItem.add("reason");

        item.set("required", reqItem);

        results.set("items", item);

        props.set("batch_number", batchNumber);
        props.set("results", results);

        schema.set("properties", props);

        ArrayNode req = mapper.createArrayNode();
        req.add("batch_number");
        req.add("results");

        schema.set("required", req);

        return GenerateContentConfig
                .builder()
                .responseMimeType("application/json")
                .responseJsonSchema(schema)
                .build();
    }

}

@FunctionalInterface
interface ContentGenerator {

    String generate(String model, String prompt, GenerateContentConfig config)
            throws ApiException, GenAiIOException;
}
