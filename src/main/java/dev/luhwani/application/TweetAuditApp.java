package dev.luhwani.application;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.luhwani.configLoader.ApiKeyLoader;
import dev.luhwani.configLoader.CriteriaLoader;
import dev.luhwani.model.AnalysisResult;
import dev.luhwani.model.Checkpoint;
import dev.luhwani.model.QueueEvent;
import dev.luhwani.model.TweetBatch;
import dev.luhwani.model.TweetData;
import dev.luhwani.output.OutputWriter;
import dev.luhwani.tweetEvaluation.AiProvider;
import dev.luhwani.tweetEvaluation.RequestExecutor;
import dev.luhwani.tweetEvaluation.RequestExecutor.EvaluationSummary;
import dev.luhwani.tweetEvaluation.gemini.GeminiAiProvider;
import dev.luhwani.tweetProcessing.TweetArchiveLoader;
import dev.luhwani.tweetProcessing.TweetBatchFactory;

public final class TweetAuditApp {

	private TweetAuditApp() {
	}

	private final class AppConfig {
		private final String apiKey;
		private final Path criteria;
		private final List<TweetData> tweets;

		AppConfig(String apiKey, Path criteria, List<TweetData> tweets) {
			this.apiKey = apiKey;
			this.criteria = criteria;
			this.tweets = tweets;
		}
	}

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	private static final Logger LOGGER = Logger.getLogger(TweetAuditApp.class.getName());

	public static void run() {
		try {
			System.out.println("----------Tweet Audit App----------");
			AppConfig config = load();
			List<TweetBatch> tweetBatches = processTweets(config);
			evaluateTweets(tweetBatches, config);
		} catch (IOException | InterruptedException | IllegalStateException | ExecutionException e) {
			LOGGER.severe("Error message: " + e.getMessage());
		} catch (Exception e) {
			LOGGER.severe("Error namee: " + e.getClass().getName());
			LOGGER.severe("Error message: " + e.getMessage());
		}
	}

	private static AppConfig load() throws IOException {
		String apiKey = ApiKeyLoader.load();
		Path criteria = new CriteriaLoader(OBJECT_MAPPER).load();
		List<TweetData> tweets = new TweetArchiveLoader(OBJECT_MAPPER).load();
		TweetAuditApp app = new TweetAuditApp();
		TweetAuditApp.AppConfig config = app.new AppConfig(apiKey, criteria, tweets);
		return config;
	}

	private static List<TweetBatch> processTweets(AppConfig config) throws IOException, InterruptedException {
		Checkpoint checkpoint = OutputWriter.loadCheckpoint();
		int lastCompletedBatch = checkpoint.lastCompletedBatchNumber();
		List<TweetBatch> tweetBatches = TweetBatchFactory.createBatches(config.tweets, lastCompletedBatch);
		if (tweetBatches.isEmpty()) {
			LOGGER.info("Tweet Processing had been completed previously");
			return List.of();
		}
		LOGGER.info(
				"Parsed " + config.tweets.size() + " tweets\n" +
						"Resuming from batch " + tweetBatches.getFirst().batchNumber() + "/"
						+ Math.ceilDiv(config.tweets.size(), TweetBatchFactory.BATCH_SIZE)
						+ " batches\n");
		return tweetBatches;
	}

	private static void evaluateTweets(List<TweetBatch> tweetBatches, AppConfig config) throws Exception {
		if (tweetBatches.isEmpty()) {
			return;
		}
		// the plus one is for an instance of an End event
		BlockingQueue<QueueEvent<TweetBatch>> tweetQueue = new ArrayBlockingQueue<>(tweetBatches.size());
		for (TweetBatch batch : tweetBatches) {
			tweetQueue.put(new QueueEvent.Item<>(batch));
		}
		BlockingQueue<QueueEvent<AnalysisResult>> resultQueue = new LinkedBlockingQueue<>();
		int workerCount = 3;
		ExecutorService workerPool = Executors.newFixedThreadPool(workerCount);
		ExecutorService writerPool = Executors.newSingleThreadExecutor();
		AiProvider provider = new GeminiAiProvider(config.apiKey, config.criteria, OBJECT_MAPPER);
		Set<Integer> failedBatchNumbers = new HashSet<>();
		try (RequestExecutor requestExecutor = new RequestExecutor(provider, workerPool, tweetQueue, resultQueue,
				OBJECT_MAPPER, failedBatchNumbers);
				OutputWriter writer = new OutputWriter(writerPool, resultQueue, failedBatchNumbers);) {
			requestExecutor.start(workerCount);
			writer.start(tweetBatches.get(0).batchNumber());
			EvaluationSummary summary = requestExecutor.awaitCompletion();
			writer.awaitCompletion();
			int failed = tweetQueue.size() - summary.getSucceeded();
			LOGGER.info("Completed batches: " + summary.getSucceeded() + "\nFailed batches: " + failed);
		} finally {
			workerPool.shutdown();
			writerPool.shutdown();
		}
	}

}
