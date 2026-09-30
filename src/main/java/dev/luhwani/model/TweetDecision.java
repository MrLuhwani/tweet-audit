package dev.luhwani.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import dev.luhwani.ai.AiProvider;

/** An {@link AiProvider}'s decision and explanation for one tweet. */
@JsonPropertyOrder({ "tweet_id", "decision", "reason" })
public record TweetDecision(
        @JsonProperty("tweet_id") String tweetId,
        Decision decision,
        String reason) {

    /**
     * Creates a decision and converts the supplied tweet id to its X status URL.
     *
     * @param tweetId raw X tweet id
     * @param decision keep or delete recommendation
     * @param reason explanation for the recommendation
     * @throws IllegalArgumentException if any argument is missing
     */
    public TweetDecision(String tweetId, Decision decision, String reason) {
        if (tweetId == null || tweetId.isEmpty()) {
            throw new IllegalArgumentException("tweetId cannot be null or empty");
        }
        if (decision == null) {
            throw new IllegalArgumentException("decision cannot be null or empty");
        }
        if (reason == null) {
            throw new IllegalArgumentException("reason cannot be null or empty");
        }
        this.tweetId = "https://x.com/i/status/" + tweetId;
        this.decision = decision;
        this.reason = reason;
    }
}