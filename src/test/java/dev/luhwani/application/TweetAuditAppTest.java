package dev.luhwani.application;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.luhwani.ai.AiProvider;
import dev.luhwani.model.Checkpoint;
import dev.luhwani.model.Criteria;

class TweetAuditAppTest {

    @Test
    void completesWithoutStartingEvaluationWhenThereAreNoBatches() throws Exception {
        AiProvider provider = new AiProvider("unused", new Criteria(new ObjectMapper().createObjectNode()),
                new ObjectMapper()) {
            @Override
            public dev.luhwani.model.AnalysisResult analyze(dev.luhwani.model.TweetBatch batch) {
                throw new AssertionError("The provider should not be used for an empty batch list");
            }
        };

        assertDoesNotThrow(() -> TweetAuditApp.evaluateTweets(List.of(), provider, Checkpoint.empty()));
    }
}