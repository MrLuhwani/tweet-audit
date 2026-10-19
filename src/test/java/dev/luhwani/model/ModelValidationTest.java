package dev.luhwani.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class ModelValidationTest {

    @Test
    void parsesDecisionsCaseInsensitivelyAndRejectsUnknownValues() {
        assertEquals(Decision.KEEP, Decision.fromJson(" keep "));
        assertEquals(Decision.DELETE, Decision.fromJson("DELETE"));
        assertThrows(IllegalArgumentException.class, () -> Decision.fromJson("later"));
        assertThrows(NullPointerException.class, () -> Decision.fromJson(null));
    }

    @Test
    void validatesAndCopiesTweetBatch() {
        List<TweetData> tweets = new ArrayList<>(List.of(new TweetData("1", "text")));
        TweetBatch batch = new TweetBatch(1, tweets);
        tweets.clear();

        assertEquals(1, batch.tweets().size());
        assertThrows(IllegalArgumentException.class, () -> new TweetBatch(-1, List.of(new TweetData("1", "text"))));
        assertThrows(IllegalArgumentException.class, () -> new TweetBatch(1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new TweetBatch(1, null));
    }

    @Test
    void validatesAnalysisResultAndTweetDecision() {
        TweetDecision decision = new TweetDecision("123", Decision.DELETE, "reason");
        assertEquals("https://x.com/i/status/123", decision.tweetId());
        assertThrows(IllegalArgumentException.class, () -> new TweetDecision("", Decision.KEEP, "reason"));
        assertThrows(IllegalArgumentException.class, () -> new TweetDecision("1", null, "reason"));
        assertThrows(IllegalArgumentException.class, () -> new TweetDecision("1", Decision.KEEP, null));

        assertThrows(IllegalArgumentException.class, () -> new AnalysisResult(-1, List.of(decision)));
        assertThrows(IllegalArgumentException.class, () -> new AnalysisResult(1, null));
        assertThrows(IllegalArgumentException.class, () -> new AnalysisResult(1, List.of()));
    }
}