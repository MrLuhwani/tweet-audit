package dev.luhwani.ai.gemini;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.luhwani.error.BatchException;
import dev.luhwani.error.FatalException;
import dev.luhwani.model.AnalysisResult;
import dev.luhwani.model.Criteria;
import dev.luhwani.model.Decision;
import dev.luhwani.model.TweetBatch;
import dev.luhwani.model.TweetData;

class GeminiAiProviderTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TweetBatch batch = new TweetBatch(7, List.of(new TweetData("123", "A useful tweet")));
    private final Criteria criteria = new Criteria(objectMapper.createObjectNode().put("delete", "spam"));

    @Test
    void parsesSuccessfulResponseAndSendsBatchAndCriteriaInPrompt() throws Exception {
        String response = """
                {"batch_number":7,"results":[{"tweet_id":"123","decision":"KEEP","reason":"Relevant"}]}
                """;
        String[] capturedPrompt = new String[1];
        GeminiAiProvider provider = providerReturning((model, prompt, config) -> {
            capturedPrompt[0] = prompt;
            assertEquals("gemini-3.5-flash-lite", model);
            return response;
        });

        AnalysisResult result = provider.analyze(batch);

        assertEquals(7, result.batchNumber());
        assertEquals(Decision.KEEP, result.results().get(0).decision());
        assertTrue(capturedPrompt[0].contains("A useful tweet"));
        assertTrue(capturedPrompt[0].contains("\"delete\" : \"spam\""));
    }

    @Test
    void rejectsEmptyProviderResponseAsBatchFailure() throws Exception {
        GeminiAiProvider provider = providerReturning((model, prompt, config) -> "");

        BatchException exception = assertThrows(BatchException.class, () -> provider.analyze(batch));

        assertEquals("Gemini responded with an empty response", exception.getMessage());
    }

    @Test
    void rejectsMalformedProviderResponseAsFatalFailure() throws Exception {
        GeminiAiProvider provider = providerReturning((model, prompt, config) -> "not json");

        FatalException exception = assertThrows(FatalException.class, () -> provider.analyze(batch));

        assertEquals("Could not properly parse json", exception.getMessage());
    }

    @Test
    void mapsJsonValuesIntoAnalysisResult() throws Exception {
        GeminiAiProvider provider = providerReturning((model, prompt, config) ->
                "{\"batch_number\":7,\"results\":[{\"tweet_id\":\"123\",\"decision\":\"delete\",\"reason\":\"Spam\"}]}");

        AnalysisResult result = provider.analyze(batch);

        assertEquals("https://x.com/i/status/123", result.results().get(0).tweetId());
        assertEquals(Decision.DELETE, result.results().get(0).decision());
    }

    private GeminiAiProvider providerReturning(ContentGenerator generator) throws Exception {
        return new GeminiAiProvider("test-key", criteria, objectMapper, generator);
    }
}