package dev.luhwani.application;

import java.io.IOException;
import java.nio.file.Path;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import dev.luhwani.configLoader.ApiKeyLoader;
import dev.luhwani.configLoader.CriteriaLoader;
import dev.luhwani.model.TweetData;
import dev.luhwani.tweetProcessing.TweetArchiveLoader;
import dev.luhwani.tweetProcessing.TweetFilter;
import dev.luhwani.tweetProcessing.TweetSorter;

public final class TweetAuditApp {

    public static void run() {
        String apiKey = ApiKeyLoader.load();
        Path criteria = CriteriaLoader.load();
        List<TweetData> tweets = new ArrayList<>();
        try {
            tweets = TweetArchiveLoader.load();
        } catch (IOException | DateTimeParseException e) {
            System.err.println("Tweet processing failed: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
        tweets = TweetFilter.removeRetweets(tweets);
        tweets = TweetSorter.sort(tweets);
    }
}
