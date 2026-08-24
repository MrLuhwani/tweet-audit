package dev.luhwani.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public final class TweetDecision {
    
    private final String tweetId;
    private final Decision decision;
    private final String reason;

    public TweetDecision(
        @JsonProperty("tweet_id")String tweetId,
        @JsonProperty("decision")Decision decision,
        @JsonProperty("reason")String reason) {
        if (tweetId == null || tweetId.isEmpty()) {
            throw new IllegalArgumentException("tweetId cannot be null or empty");
        }
        if (decision == null) {
            throw new IllegalArgumentException("decision cannot be null or empty");
        }
        if (reason == null) {
            throw new IllegalArgumentException("reason cannot be null or empty");
        }
        this.tweetId = tweetId;
        this.decision = decision;
        this.reason = reason;
    }

    public String tweetId() {
        return tweetId;
    }

    public Decision decision() {
        return decision;
    }

    public String reason() {
        return reason;
    }

}