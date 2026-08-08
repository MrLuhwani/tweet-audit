package dev.luhwani.tweetProcessing;

import java.util.List;

import dev.luhwani.model.TweetData;

public final class TweetFilter {

    private TweetFilter() {
    }

    public static List<TweetData> removeRetweets(List<TweetData> tweets) {
        return tweets
                .stream()
                .filter(tweet -> !tweet.text().startsWith("RT "))
                .toList();
    }
}
