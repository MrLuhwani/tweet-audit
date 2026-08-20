package dev.luhwani.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TweetDecision(
        @JsonProperty("tweet_id") String tweetId,
        Decision decision) {

}