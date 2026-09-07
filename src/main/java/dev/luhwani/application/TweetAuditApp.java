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
import dev.luhwani.output.OutputWriter;
import dev.luhwani.tweetEvaluation.AiProvider;
import dev.luhwani.tweetEvaluation.RateLimiterScheduler;
import dev.luhwani.tweetEvaluation.gemini.GeminiAiProvider;
import dev.luhwani.tweetProcessing.TweetArchiveLoader;
import dev.luhwani.tweetProcessing.TweetBatchFactory;

public final class TweetAuditApp {

	private TweetAuditApp(){}

	private final class AppConfig {
		private final String apiKey;
		private final Path criteria;
		private final List<TweetData> tweets;

		AppConfig(
			String apiKey,
			Path criteria,
			List<TweetData> tweets) {
			this.apiKey = apiKey;
			this.criteria = criteria;
			this.tweets = tweets;
		}
	}

	private static final ObjectMapper mapper = new ObjectMapper();
	public static void run() {
		try {
			System.out.println("----------Tweet Audit App----------");
			AppConfig config = load();
			List<TweetBatch> tweetBatches = processTweets(config);
			evaluateTweetBatches(tweetBatches, config);
		} catch (IllegalStateException | IOException | InterruptedException | DateTimeParseException | ExecutionException e) {
			// TODO: use a better error handling strategy
			System.err.println("[ERROR] Tweet processing failed: " + e.getMessage());
			e.printStackTrace(System.err);
			System.exit(1);
		} catch (Exception e) {
			System.err.println(e.getClass());
			System.err.println("[ERROR] Tweet processing failed: " + e.getMessage());
			e.printStackTrace(System.err);
			System.exit(1);
		}

	}

	private static AppConfig load() throws IOException, InterruptedException, DateTimeParseException {
		String apiKey = ApiKeyLoader.load();
		Path criteria = new CriteriaLoader(mapper).load();
		List<TweetData> tweets = new TweetArchiveLoader(mapper).load();
		TweetAuditApp app = new TweetAuditApp();
		TweetAuditApp.AppConfig config = app.new AppConfig(apiKey, criteria, tweets);
		return config;
	}

	private static List<TweetBatch> processTweets(AppConfig config) throws IOException, InterruptedException {
		Checkpoint checkpoint = OutputWriter.loadCheckpoint();
		int lastCompletedBatch = checkpoint.lastCompletedBatchNumber();
		List<TweetBatch> tweetBatches = TweetBatchFactory.createBatches(config.tweets, lastCompletedBatch);
		if (tweetBatches.isEmpty()) {
			System.out.println("Tweet Processing had been completed previously");
			return List.of();
		}
		System.out.println(
				"Parsed " + config.tweets.size() + " tweets\n" +
						"Resuming from batch " + tweetBatches.getFirst().batchNumber() + "/" + tweetBatches.size()
						+ " batches\n");
		return tweetBatches;
	}

	private static void evaluateTweetBatches(List<TweetBatch> tweetBatches, AppConfig config)
	throws InterruptedException, ExecutionException, IOException {
		BlockingQueue<QueueEvent<TweetBatch>> tweetQueue = new ArrayBlockingQueue<>(tweetBatches.size() + 1);;
		BlockingQueue<QueueEvent<AnalysisResult>> resultQueue = new LinkedBlockingDeque<>();
		for (int i = 0; i < tweetBatches.size(); i++) {
			tweetQueue.put(new QueueEvent.Item<>(tweetBatches.get(i)));
		}
		tweetQueue.put(QueueEvent.End.instance());
		AiProvider provider = new GeminiAiProvider(config.apiKey, config.criteria, mapper);
		ExecutorService executor = Executors.newCachedThreadPool();

		try (RateLimiterScheduler scheduler = new RateLimiterScheduler(provider, tweetQueue, resultQueue, executor);
					CsvWriter writer = new CsvWriter(resultQueue, executor);) {
			scheduler.start();
			writer.start();
			scheduler.awaitCompletion();
			writer.awaitCompletion();
		} catch (Exception e) {
			System.err.println("[ERROR] " + e.getMessage());
			System.err.println(e.getClass());
			e.printStackTrace();
			executor.shutdown();
		} finally {
			executor.shutdown();
		}
	}

}
