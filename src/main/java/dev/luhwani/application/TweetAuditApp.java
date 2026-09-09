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
import dev.luhwani.tweetProcessing.FailedBatchLoader;

public final class TweetAuditApp {

	private TweetAuditApp() {
	}

	private final class AppConfig {
		private final String apiKey;
		private final Path criteria;
		private final List<TweetData> tweets;
		private final List<TweetBatch> failedBatches;

		AppConfig(String apiKey, Path criteria, List<TweetData> tweets, List<TweetBatch> failedBatches) {
			this.apiKey = apiKey;
			this.criteria = criteria;
			this.tweets = tweets;
			this.failedBatches = failedBatches;
		}
	}

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	private static final Logger LOGGER = Logger.getLogger(TweetAuditApp.class.getName());

	public static void run() {
		try {
			System.out.println("----------Tweet Audit App----------");
			AppConfig config = load();
			retryFailedBatches(config);
			List<TweetBatch> tweetBatches = processTweets(config);
			evaluateTweets(tweetBatches, config, true);
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
		List<TweetBatch> failedBatches = new FailedBatchLoader(OBJECT_MAPPER).load();
		if (!failedBatches.isEmpty()) {
			System.out.println("[ALERT] Failed batches from a previous run are being retried.");
		}
		List<TweetData> tweets = new TweetArchiveLoader(OBJECT_MAPPER).load();
		TweetAuditApp app = new TweetAuditApp();
		TweetAuditApp.AppConfig config = app.new AppConfig(apiKey, criteria, tweets, failedBatches);
		return config;
	}

	private static void retryFailedBatches(AppConfig config) throws Exception {
		if (config.failedBatches.isEmpty()) {
			return;
		}
		Checkpoint lastReprocessedBatch = evaluateTweets(config.failedBatches, config, false);
		if (lastReprocessedBatch != null) {
			OutputWriter.updateCheckpointIfAhead(lastReprocessedBatch);
		}
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

	private static Checkpoint evaluateTweets(List<TweetBatch> tweetBatches, AppConfig config,
			boolean updateCheckpoint) throws Exception {
		if (tweetBatches.isEmpty()) {
			return null;
		}
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
				OBJECT_MAPPER, failedBatchNumbers, updateCheckpoint);
				OutputWriter writer = new OutputWriter(writerPool, resultQueue, failedBatchNumbers, updateCheckpoint);) {
			requestExecutor.start(workerCount);
			writer.start(tweetBatches.get(0).batchNumber());
			EvaluationSummary summary = requestExecutor.awaitCompletion();
			writer.awaitCompletion();
			int failed = tweetQueue.size() - summary.getSucceeded();
			LOGGER.info("Completed batches: " + summary.getSucceeded() + "\nFailed batches: " + failed);
			return writer.lastWrittenCheckpoint();
		} finally {
			workerPool.shutdown();
			writerPool.shutdown();
		}
	}

}
