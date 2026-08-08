package dev.luhwani.tweetProcessing;

import java.util.Comparator;
import java.util.List;

import dev.luhwani.model.TweetData;

public final class TweetSorter {
    
    private TweetSorter(){}

    public static List<TweetData> sort(List<TweetData> tweets) {
        return tweets
                .stream()
                .sorted(Comparator.comparing(TweetData::createdAt))
                .toList();
    }
}
