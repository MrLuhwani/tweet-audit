package dev.luhwani.tweetEvaluation.gemini;

import java.io.IOException;
import java.nio.file.Path;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.genai.Client;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;

import dev.luhwani.model.AnalysisResult;
import dev.luhwani.model.TweetBatch;
import dev.luhwani.model.TweetData;
import dev.luhwani.tweetEvaluation.AiProvider;

public class GeminiAiProvider extends AiProvider {

    private final long INTERVAL_MILLIS = 100;
    // TODO: make model choice more confifgurable
    private final String MODEL = "gemini-3.5-flash-lite";
    private final Client client;
    private final GenerateContentConfig requestConfig;

    public GeminiAiProvider(String apiKey, Path criteria, ObjectMapper mapper) throws IOException {
        super(apiKey, criteria, mapper);
        this.client = Client.builder()
                .apiKey(apiKey)
                .build();
        this.requestConfig = buildConfig();
    }

    @Override
    protected long definerequestInterval() {
        return INTERVAL_MILLIS;
    }

    @Override
    public AnalysisResult analyze(TweetBatch batch) throws IOException {

        String prompt = buildPrompt(batch, criteriaNode);

        GenerateContentResponse response = client.models.generateContent(
                MODEL,
                prompt,
                requestConfig);

        String jsonResponse = response.text();
        return mapper.readValue(jsonResponse, AnalysisResult.class);
    }

    private String buildPrompt(TweetBatch batch, JsonNode criteria) throws JsonProcessingException {

    ObjectNode batchJson = mapper.createObjectNode();

    batchJson.put("batch_index", batch.batchIndex());

    ArrayNode tweets = mapper.createArrayNode();

    for (TweetData tweet : batch.tweets()) {

        ObjectNode t = mapper.createObjectNode();

        t.put("id", tweet.id());
        t.put("text", tweet.text());

        tweets.add(t);
    }

    batchJson.set("tweets", tweets);

    return String.format(
        "Evaluate every tweet against the supplied deletion criteria. \n Rules: \n 1. Produce exactly one result for every tweet. \n 2. decision must be either KEEP or DELETE. \n 3. Never invent tweet ids. \n 4. Preserve tweet ordering. \n 5. Give reasons for your decision \n Deletion Criteria: \n %s \n Tweet Batch: \n %s \n",
        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(criteria),
        mapper.writerWithDefaultPrettyPrinter().writeValueAsString(batchJson)
    );
}


    private GenerateContentConfig buildConfig() {

        ObjectNode schema = mapper.createObjectNode();

        schema.put("type", "object");

        ObjectNode props = mapper.createObjectNode();

        ObjectNode batchIndex = mapper.createObjectNode();
        batchIndex.put("type", "integer");

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

        props.set("batch_index", batchIndex);
        props.set("results", results); 

        schema.set("properties", props);

        ArrayNode req = mapper.createArrayNode();
        req.add("batch_index");
        req.add("results"); 

        schema.set("required", req);

        return GenerateContentConfig
                .builder()
                .responseMimeType("application/json")
                .responseJsonSchema(schema)
                .build();
    }

}
