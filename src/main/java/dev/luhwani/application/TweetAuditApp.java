package dev.luhwani.application;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
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
import dev.luhwani.tweetEvaluation.BatchFailureHandler;
import dev.luhwani.tweetEvaluation.RequestExecutor;
import dev.luhwani.tweetEvaluation.RequestExecutor.EvaluationSummary;
import dev.luhwani.tweetEvaluation.gemini.GeminiAiProvider;
import dev.luhwani.tweetProcessing.CheckpointResolver;
import dev.luhwani.tweetProcessing.FailedBatchLoader;
import dev.luhwani.tweetProcessing.TweetArchiveLoader;
import dev.luhwani.tweetProcessing.TweetBatchFactory;

/** Coordinates archive loading, batch evaluation, checkpointing, and output writing. */
public final class TweetAuditApp {

	private TweetAuditApp() {
	}

	/** Holds the configuration shared by the evaluation components. */
	private final class AppConfig {
		private final String apiKey;
		private final Path criteria;

		AppConfig(String apiKey, Path criteria) {
			this.apiKey = apiKey;
			this.criteria = criteria;
		}
	}

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	private static final Logger LOGGER = Logger.getLogger(TweetAuditApp.class.getName());

	private static final Path CHECKPOINT_PATH = Paths
			.get("")
			.toAbsolutePath()
			.normalize()
			.resolve("output/checkpoint.json");

	private static final Path OUTPUT_PATH = Paths.get("")
			.toAbsolutePath()
			.normalize()
			.resolve("output/output.csv");

	private static final Path FAILED_BATCH_PATH = Paths
			.get("")
			.toAbsolutePath()
			.normalize()
			.resolve("output/failedBatches.jsonl");

	/**
	 * Starts the tweet audit workflow, including retries for failed batches.
	 * In charge of orchestrating other classes and internal methods used
	 * for the audit process.
	 * <p>
	 * Errors are logged in case any happen at runtime
	 */
	public static void run() {
		try {
			System.out.println("----------Tweet Audit App----------");
			AppConfig config = load();
			List<TweetBatch> tweetBatches = loadFailedBatches();
			if (!tweetBatches.isEmpty()) {
				LOGGER.info("Retrying " + tweetBatches.size() + " failed batches from previous run.");
				evaluateTweets(tweetBatches, config, true);
			}
			tweetBatches = processTweets();
			evaluateTweets(tweetBatches, config, false);
		} catch (IOException | InterruptedException | IllegalStateException | ExecutionException e) {
			LOGGER.severe("Error message: " + e.getMessage());
			e.printStackTrace();
		} catch (Exception e) {
			LOGGER.severe("Error name: " + e.getClass().getName());
			LOGGER.severe("Error message: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Loads the API key and criteria file used by the evaluation provider.
	 *
	 * @return the loaded application configuration
	 * @throws IOException
	 */
	private static AppConfig load() throws IOException {
		String apiKey = ApiKeyLoader.load();
		Path criteria = new CriteriaLoader(OBJECT_MAPPER).load();
		TweetAuditApp app = new TweetAuditApp();
		return app.new AppConfig(apiKey, criteria);
	}

	/**
	 * Loads failed batches and verifies that they agree with the checkpoint.
	 *
	 * @return tweetbatches, whether it is empty or not
	 * @throws IOException           if an error occured from inside the
	 *                               {@link FailedBatchLoader}
	 * @throws IllegalStateException if the failed batches in the checkpoint
	 *                               and the failed batches in failedbatches.jsonl
	 *                               do not match
	 */
	private static List<TweetBatch> loadFailedBatches() throws IOException {
		Map<Integer, TweetBatch> failedBatches = new FailedBatchLoader(OBJECT_MAPPER, FAILED_BATCH_PATH).load();
		Checkpoint checkpoint = new CheckpointResolver(CHECKPOINT_PATH, OUTPUT_PATH, FAILED_BATCH_PATH, OBJECT_MAPPER)
				.load();
		for (Integer batchNumber : checkpoint.getFailedBatches()) {
			if (!failedBatches.containsKey(batchNumber)) {
				throw new IllegalStateException("Checkpoint references missing failed batch: " + batchNumber);
			}
		}
		for (Integer batchNumber : failedBatches.keySet()) {
			if (!checkpoint.getFailedBatches().contains(batchNumber)) {
				throw new IllegalStateException(
						"Failed batch file contains batch not present in checkpoint: " + batchNumber);
			}
		}
		return List.copyOf(failedBatches.values());
	}

	/**
	 * Loads unprocessed tweets from the archive and creates the next batches.
	 *
	 * @return a list of tweet batches, whether it is empty or not
	 * @throws IOException
	 * @throws InterruptedException
	 */
	private static List<TweetBatch> processTweets() throws IOException, InterruptedException {

		Checkpoint checkpoint = new CheckpointResolver(CHECKPOINT_PATH, OUTPUT_PATH, FAILED_BATCH_PATH, OBJECT_MAPPER)
				.load();
		List<TweetData> tweets;
		if (checkpoint.isEmpty()) {
			tweets = new TweetArchiveLoader(OBJECT_MAPPER).load("");
		} else {
			tweets = new TweetArchiveLoader(OBJECT_MAPPER).load(checkpoint.getLastProcessedTweet());
		}
		if (tweets.isEmpty()) {
			return List.of();
		}
		int lastCompletedBatch = checkpoint.getLastProcessedBatch();
		List<TweetBatch> tweetBatches = TweetBatchFactory.createBatches(tweets, lastCompletedBatch);
		LOGGER.info(
				"Parsed " + tweets.size() + " tweets\n" +
						"Resuming from batch " + tweetBatches.getFirst().batchNumber());
		return tweetBatches;
	}

	/**
	 * Runs the provider, result writer, and failed-batch writer for one pass.
	 *
	 * @param tweetBatches
	 * @param config
	 * @param isRetrying to identify if we are trying to evaluate a
	 * previously failed batch or not
	 * @see AiProvider
	 * @see RequestExecutor
	 * @see OutputWriter
	 * @see BatchFailureHandler
	 * @throws Exception if any fatal error happens internally during evaluation
	 */
	private static void evaluateTweets(List<TweetBatch> tweetBatches, AppConfig config, boolean isRetrying)
			throws Exception {
		if (tweetBatches.isEmpty()) {
			System.out.println("Tweet Processing had been completed previously");
			return;
		}
		BlockingQueue<QueueEvent<TweetBatch>> batchQueue = new ArrayBlockingQueue<>(tweetBatches.size());
		for (TweetBatch batch : tweetBatches) {
			batchQueue.put(new QueueEvent.Item<>(batch));
		}
		BlockingQueue<QueueEvent<AnalysisResult>> resultQueue = new LinkedBlockingQueue<>();
		BlockingQueue<QueueEvent<TweetBatch>> failureQueue = new LinkedBlockingQueue<>();

		Map<Integer, String> failedBatchToLastTweetMap = new ConcurrentHashMap<>();
		BlockingQueue<QueueEvent<Integer>> successfulRetriedBatches = new LinkedBlockingQueue<>();

		int workers = 3;
		AiProvider provider = new GeminiAiProvider(config.apiKey, config.criteria, OBJECT_MAPPER);

		try (
				RequestExecutor requestExecutor = new RequestExecutor(batchQueue, resultQueue, failureQueue,
						failedBatchToLastTweetMap, workers, provider);
				OutputWriter outputWriter = new OutputWriter(resultQueue, failedBatchToLastTweetMap,
						CHECKPOINT_PATH, OBJECT_MAPPER, OUTPUT_PATH, FAILED_BATCH_PATH,
						tweetBatches.getFirst().batchNumber(),
						successfulRetriedBatches);
				BatchFailureHandler batchFailureHandler = new BatchFailureHandler(failureQueue,
						FAILED_BATCH_PATH, OBJECT_MAPPER, successfulRetriedBatches)) {

			requestExecutor.start(isRetrying);
			outputWriter.start(isRetrying);
			batchFailureHandler.start(isRetrying);
			EvaluationSummary summary = requestExecutor.awaitCompletion();
			outputWriter.awaitCompletion();
			batchFailureHandler.awaitCompletion();
			int failed = tweetBatches.size() - summary.getSucceeded();
			LOGGER.info("Completed batches: " + summary.getSucceeded() + "\nFailed batches: " + failed);
		}

	}

}
