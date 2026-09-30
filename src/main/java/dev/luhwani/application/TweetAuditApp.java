package dev.luhwani.application;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.luhwani.configuration.ApiKeyLoader;
import dev.luhwani.criteria.CriteriaLoader;
import dev.luhwani.criteria.CriteriaValidator;
import dev.luhwani.error.FatalException;
import dev.luhwani.model.AnalysisResult;
import dev.luhwani.model.Checkpoint;
import dev.luhwani.model.Criteria;
import dev.luhwani.model.TweetBatch;
import dev.luhwani.model.TweetData;
import dev.luhwani.output.OutputWriter;
import dev.luhwani.ai.AiProvider;
import dev.luhwani.ai.gemini.GeminiAiProvider;
import dev.luhwani.client.RequestExecutor;
import dev.luhwani.tweetProcessing.CheckpointResolver;
import dev.luhwani.tweetProcessing.TweetArchiveLoader;
import dev.luhwani.tweetProcessing.TweetBatchFactory;
import dev.luhwani.tweetProcessing.TweetFilter;

/**
 * Coordinates archive loading, batch evaluation, checkpointing, and output
 * writing.
 */
public final class TweetAuditApp {

	private TweetAuditApp() {
	}

	/** Holds the configuration shared by the evaluation components. */
	private record AppConfig(String apiKey, Criteria criteria) {
	}

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	private static final Logger LOGGER = Logger.getLogger(TweetAuditApp.class.getName());

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
			LOGGER.info("Started tweet audit application");
			AppConfig config = load();
			List<TweetBatch> tweetBatches = processTweets();
			evaluateTweets(tweetBatches, config);
			LOGGER.info("Tweet audit application completed successfully");
		} catch (Exception e) {
			LOGGER.severe(e.getMessage());
		}
	}

	/**
	 * Loads the API key and criteria file used by the evaluation provider.
	 *
	 * @return the loaded application configuration
	 * @throws FatalException
	 */
	private static AppConfig load() throws FatalException {
		String apiKey = ApiKeyLoader.load();
		Path criteriaPath = CriteriaLoader.load();
		Criteria criteria = new CriteriaValidator(OBJECT_MAPPER).validate(criteriaPath);
		return new AppConfig(apiKey, criteria);
	}

	/**
	 * Loads tweets from the archive and creates the next batches.
	 *
	 * @return a list of tweet batches
	 * @throws FatalException
	 */
	private static List<TweetBatch> processTweets() throws FatalException {
		List<TweetData> tweets = new TweetArchiveLoader(OBJECT_MAPPER).load();
		LOGGER.info("Parsed " + tweets.size() + " tweets\n");
		List<TweetBatch> tweetBatches = TweetBatchFactory.createBatches(tweets);
		Checkpoint checkpoint = new CheckpointResolver(OBJECT_MAPPER).load();
		tweetBatches = TweetFilter.filter(tweetBatches, checkpoint);
		return tweetBatches;
	}

	/**
	 * Runs the provider, result writer, and failed-batch writer for one pass.
	 *
	 * @param tweetBatches
	 * @param config
	 * @param isRetrying   to identify if we are trying to evaluate a
	 *                     previously failed batch or not
	 * @see AiProvider
	 * @see RequestExecutor
	 * @see OutputWriter
	 * @see BatchFailureHandler
	 * @throws Exception if any fatal error happens internally during evaluation
	 */
	private static void evaluateTweets(List<TweetBatch> tweetBatches, AppConfig config)
			throws Exception {
		AiProvider provider = new GeminiAiProvider(config.apiKey, config.criteria, OBJECT_MAPPER);
		Checkpoint checkpoint = new CheckpointResolver(OBJECT_MAPPER).fromFile();
		evaluateTweets(tweetBatches, provider, checkpoint);
	}

	static void evaluateTweets(List<TweetBatch> tweetBatches, AiProvider provider, Checkpoint checkpoint)
			throws Exception {
		if (tweetBatches.isEmpty()) {
			LOGGER.info("Tweet Processing completed on previous run");
			return;
		}
		BlockingQueue<TweetBatch> batchQueue = new ArrayBlockingQueue<>(tweetBatches.size());
		for (TweetBatch batch : tweetBatches) {
			batchQueue.put(batch);
		}
		BlockingQueue<AnalysisResult> resultQueue = new LinkedBlockingQueue<>();

		CountDownLatch countDown = new CountDownLatch(batchQueue.size());
		int workers = 3;
		try (
				RequestExecutor requestExecutor = new RequestExecutor(workers, batchQueue, resultQueue, countDown,
						provider);
				OutputWriter outputWriter = new OutputWriter(OBJECT_MAPPER, resultQueue, countDown, checkpoint)) {
			requestExecutor.start();
			outputWriter.start();
			requestExecutor.awaitCompletion();
			outputWriter.awaitCompletion();
		}

	}

}
