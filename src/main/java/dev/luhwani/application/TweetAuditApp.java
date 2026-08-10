package dev.luhwani.application;

import java.io.IOException;
import java.nio.file.Path;
import java.time.format.DateTimeParseException;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.luhwani.configLoader.ApiKeyLoader;
import dev.luhwani.configLoader.CriteriaLoader;
import dev.luhwani.model.Checkpoint;
import dev.luhwani.model.TweetBatch;
import dev.luhwani.model.TweetData;
import dev.luhwani.tweetProcessing.CheckpointResolver;
import dev.luhwani.tweetProcessing.TweetArchiveLoader;
import dev.luhwani.tweetProcessing.TweetBatchFactory;
import dev.luhwani.tweetProcessing.TweetFilter;
import dev.luhwani.tweetProcessing.TweetSorter;

public final class TweetAuditApp {

    record AppConfig(
            String apiKey,
            Path criteria,
            List<TweetData> tweets) {
    }

    private static final ObjectMapper mapper = new ObjectMapper();

    public static void run() {

        try {
            AppConfig config = load();
            processTweets(config);
        } catch (IOException | InterruptedException | DateTimeParseException e) {
            System.err.println("Tweet processing failed: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        } catch (Exception e) {
            System.err.println(e.getClass());
            System.err.println("Tweet processing failed: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }

    }

    private static AppConfig load() throws IOException, InterruptedException, DateTimeParseException {
        String apiKey = ApiKeyLoader.load();
        Path criteria = new CriteriaLoader(mapper).load();
        List<TweetData> tweets = new TweetArchiveLoader(mapper).load();
        tweets = TweetFilter.removeRetweets(tweets);
        tweets = TweetSorter.sort(tweets);
        return new AppConfig(apiKey, criteria, tweets);
    }

    private static void processTweets(AppConfig config) throws IOException {
        
        List<TweetBatch> tweetBatches = TweetBatchFactory.createBatches(config.tweets());
        
        Checkpoint checkpoint = new CheckpointResolver(mapper).load();
        int firstBatch = checkpoint.nextBatchIndex();

        if (firstBatch > tweetBatches.size()) {
            throw new IllegalStateException("Checkpoint is ahead of the available tweet batches");
        }

        List<TweetBatch> remainingBatches = tweetBatches.stream()
                .filter(batch -> batch.batchIndex() >= firstBatch)
                .toList();
        System.out.printf("Parsed %,d tweets into %,d batches\n", config.tweets().size(), tweetBatches.size());
        if (remainingBatches.isEmpty()) {
            System.out.println("Nothing to process. The checkpoint already covers every batch.");
            return;
        }
        System.out.printf("Resuming from batch %d; %,d batches remain\n", firstBatch, remainingBatches.size());
    }

}
