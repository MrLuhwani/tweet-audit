package dev.luhwani.application;

import java.io.IOException;
import java.nio.file.Path;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.luhwani.configLoader.ApiKeyLoader;
import dev.luhwani.configLoader.CriteriaLoader;
import dev.luhwani.model.AnalysisResult;
import dev.luhwani.model.Checkpoint;
import dev.luhwani.model.QueueEvent;
import dev.luhwani.model.TweetBatch;
import dev.luhwani.model.TweetData;
import dev.luhwani.output.CsvWriter;
import dev.luhwani.tweetEvaluation.AiProvider;
import dev.luhwani.tweetEvaluation.RateLimterScheduler;
import dev.luhwani.tweetEvaluation.gemini.GeminiAiProvider;
import dev.luhwani.tweetProcessing.CheckpointResolver;
import dev.luhwani.tweetProcessing.TweetArchiveLoader;
import dev.luhwani.tweetProcessing.TweetBatchFactory;

public final class TweetAuditApp {

    record AppConfig(
            String apiKey,
            Path criteria,
            List<TweetData> tweets) {
    }

    private static final ObjectMapper mapper = new ObjectMapper();

    public static void run() {
        try {
            System.out.println("---------Tweet Audit App---------");
            AppConfig config = load();
            List<TweetBatch> tweetBatches = processTweets(config);
            evaluateTweetBatches(tweetBatches, config);
        } catch (IOException | InterruptedException | DateTimeParseException | ExecutionException e) {
            // TODO: use a better error handling strategy
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
        return new AppConfig(apiKey, criteria, tweets);
    }

    private static List<TweetBatch> processTweets(AppConfig config) throws IOException {

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
            return null;
        }
        System.out.printf("Resuming from batch %d; %,d batches remain\n", firstBatch, remainingBatches.size());
        return remainingBatches;
    }

    private static void evaluateTweetBatches(List<TweetBatch> tweetBatches, AppConfig config)
            throws InterruptedException, ExecutionException, IOException {
        BlockingQueue<QueueEvent<TweetBatch>> tweetQueue = new ArrayBlockingQueue<>(tweetBatches.size() + 1);;
        BlockingQueue<QueueEvent<AnalysisResult>> resultQueue = new LinkedBlockingDeque<>();
        for (int i = 0; i < tweetBatches.size(); i++) {
            tweetQueue.put(new QueueEvent.Item<>(tweetBatches.get(i)));
        }
        tweetQueue.put(new QueueEvent.End<TweetBatch>());
        AiProvider provider = new GeminiAiProvider(config.apiKey, config.criteria, mapper);
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        
        try (RateLimterScheduler scheduler = new RateLimterScheduler(provider, tweetQueue, resultQueue, executor);
                CsvWriter writer = new CsvWriter(resultQueue, executor);) {
            scheduler.start();
            writer.start();
            scheduler.awaitCompletion();
            writer.awaitCompletion();
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.err.println(e.getClass());
            e.printStackTrace();
            executor.shutdown();
        } finally {
            executor.shutdown();
        }
    }

}
