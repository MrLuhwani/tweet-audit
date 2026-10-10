package dev.luhwani.application;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.luhwani.ai.AiProvider;
import dev.luhwani.ai.gemini.GeminiAiProvider;
import dev.luhwani.client.RequestExecutor;
import dev.luhwani.criteria.CriteriaValidator;
import dev.luhwani.error.FatalException;
import dev.luhwani.model.AnalysisResult;
import dev.luhwani.model.Checkpoint;
import dev.luhwani.model.Criteria;
import dev.luhwani.model.TweetBatch;
import dev.luhwani.model.TweetData;
import dev.luhwani.output.OutputWriter;
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
	private record AppConfig(String apiKey, String model, Criteria criteria) {
	}

	record CliArguments(String apiKey, String model, String tweetArchive, String criteria, String output) {
	}

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
			.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	private static final Logger LOGGER = Logger.getLogger(TweetAuditApp.class.getName());
	private static final String DEFAULT_MODEL = "gemini-3.5-flash-lite";

	/**
	 * Starts the tweet audit workflow.
	 * In charge of orchestrating other classes and internal methods used
	 * for the audit process.
	 * <p>
	 * Errors are logged in case any happen at runtime
	 */
	public static void run(String[] args) {
		try {
			System.out.println("----------Tweet Audit App----------");
			LOGGER.info("Started tweet audit application");
			CliArguments cliArguments = parseArgs(args);
			Path outputDirectory = resolveOutputDirectory(cliArguments.output());
			Path outputPath = outputDirectory.resolve("output.csv");
			Path checkpointPath = outputDirectory.resolve("checkpoint.json");
			Criteria criteria = loadCriteria(cliArguments.criteria());
			AppConfig config = new AppConfig(cliArguments.apiKey(), cliArguments.model(), criteria);
			List<TweetBatch> tweetBatches = processTweets(Path.of(cliArguments.tweetArchive()), outputPath,
				checkpointPath);
			evaluateTweets(tweetBatches, config, outputPath, checkpointPath);
			LOGGER.info("Tweet audit application completed successfully");
		} catch (Exception e) {
			LOGGER.severe(e.getMessage());
		}
	}

	/**
	 * Valiates the number of command line arguments passed when running the
	 * tool
	 */
	static void validArgs(String[] args) {
		parseArgs(args);
	}

	static CliArguments parseArgs(String[] args) {
		if (args == null) {
			throw new IllegalArgumentException("Null args array passed into audit arguments");
		}

		String apiKey = null;
		String model = null;
		String tweetArchive = null;
		String criteria = null;
		String output = null;
		for (int index = 0; index < args.length; index++) {
			String option = args[index];
			if (!option.startsWith("--")) {
				throw usageError();
			}
			if (index + 1 >= args.length || args[index + 1].startsWith("--")) {
				throw new IllegalArgumentException("Missing value for option: " + option + "\n" + usageMessage());
			}
			String value = args[++index];
			switch (option) {
			case "--api-key" -> apiKey = requireUnset(option, apiKey, value);
			case "--model" -> model = requireUnset(option, model, value);
			case "--tweet-archive" -> tweetArchive = requireUnset(option, tweetArchive, value);
			case "--criteria" -> criteria = requireUnset(option, criteria, value);
			case "--output" -> output = requireUnset(option, output, value);
			default -> throw new IllegalArgumentException("Unknown option: " + option + "\n" + usageMessage());
			}
		}
		if (apiKey == null || tweetArchive == null) {
			throw usageError();
		}
		return new CliArguments(apiKey, model == null ? DEFAULT_MODEL : model, tweetArchive, criteria, output);
	}

	private static String requireUnset(String option, String currentValue, String value) {
		if (currentValue != null) {
			throw new IllegalArgumentException("Option specified more than once: " + option);
		}
		if (value.isBlank()) {
			throw new IllegalArgumentException("Value for option cannot be blank: " + option);
		}
		return value;
	}

	private static IllegalArgumentException usageError() {
		return new IllegalArgumentException(usageMessage());
	}

	private static String usageMessage() {
		return "Usage: java -jar tweet-audit-1.0-SNAPSHOT.jar --api-key <key> --tweet-archive <path> "
				+ "[--model <model>] [--criteria <path>] [--output <directory>]";
	}

	/**
	 * Resolves and validates the directory used for CSV output and checkpoint state.
	 *
	 * @param configuredOutputDirectory optional user-supplied output directory
	 * @return the normalized output directory
	 * @throws IOException if the directory cannot be created
	 */
	static Path resolveOutputDirectory(String configuredOutputDirectory) throws IOException {
		if (configuredOutputDirectory != null && configuredOutputDirectory.isBlank()) {
			throw new IllegalArgumentException("Output directory cannot be blank");
		}
		Path outputDirectory = configuredOutputDirectory == null
				? Path.of("output")
				: Path.of(configuredOutputDirectory);
		outputDirectory = outputDirectory.toAbsolutePath().normalize();
		if (Files.exists(outputDirectory) && !Files.isDirectory(outputDirectory)) {
			throw new IllegalArgumentException("Output path is not a directory: " + outputDirectory);
		}
		Files.createDirectories(outputDirectory);
		return outputDirectory;
	}

	/**
	 * Loads the criteria file used by the evaluation provider.
	 *
	 * @return the criteria used for evaluation
	 * @throws FatalException
	 */
	static Criteria loadCriteria(String configuredCriteriaPath) throws FatalException {
		if (configuredCriteriaPath == null || configuredCriteriaPath.isBlank()) {
			var resource = TweetAuditApp.class.getClassLoader().getResourceAsStream("config.example.json");
			return new CriteriaValidator(OBJECT_MAPPER).validate(resource, "config.example.json");
		}
		return new CriteriaValidator(OBJECT_MAPPER).validate(Path.of(configuredCriteriaPath));
	}

	/**
	 * Loads tweets from the archive and creates the next set of
	 * unprocessed batches
	 *
	 * @return a list of tweet batches
	 * @throws FatalException
	 */
	private static List<TweetBatch> processTweets(Path tweetArchivePath, Path outputPath, Path checkpointPath)
			throws FatalException {
		List<TweetData> tweets = new TweetArchiveLoader(OBJECT_MAPPER, tweetArchivePath).load();
		LOGGER.info("Parsed " + tweets.size() + " tweets\n");
		List<TweetBatch> tweetBatches = TweetBatchFactory.createBatches(tweets);
		Checkpoint checkpoint = new CheckpointResolver(OBJECT_MAPPER, checkpointPath, outputPath).load();
		tweetBatches = TweetFilter.filter(tweetBatches, checkpoint);
		return tweetBatches;
	}

	/**
	 * Runs the provider and result writer.
	 *
	 * @param tweetBatches
	 * @param config
	 * @param isRetrying   to identify if we are trying to evaluate a
	 *                     previously failed batch or not
	 * @see AiProvider
	 * @see RequestExecutor
	 * @see OutputWriter
	 * @throws Exception if any fatal error happens internally during evaluation
	 */
	private static void evaluateTweets(List<TweetBatch> tweetBatches, AppConfig config, Path outputPath,
			Path checkpointPath)
			throws Exception {
		AiProvider provider = new GeminiAiProvider(config.apiKey, config.model, config.criteria, OBJECT_MAPPER);
		Checkpoint checkpoint = new CheckpointResolver(OBJECT_MAPPER, checkpointPath, outputPath).fromFile();
		evaluateTweets(tweetBatches, provider, checkpoint, outputPath, checkpointPath);
	}

	private static void evaluateTweets(List<TweetBatch> tweetBatches, AiProvider provider, Checkpoint checkpoint,
			Path outputPath, Path checkpointPath) throws Exception {
		if (tweetBatches.isEmpty()) {
			LOGGER.info("Tweet Processing completed on previous run");
			return;
		}
		LOGGER.info("Evaluating " + tweetBatches.size() + " tweet batches");
		BlockingQueue<TweetBatch> batchQueue = new ArrayBlockingQueue<>(tweetBatches.size());
		for (TweetBatch batch : tweetBatches) {
			batchQueue.put(batch);
		}
		BlockingQueue<AnalysisResult> resultQueue = new LinkedBlockingQueue<>();
		CountDownLatch countDown = new CountDownLatch(batchQueue.size());
		int workers = 3;
		try (RequestExecutor requestExecutor = new RequestExecutor(workers, batchQueue, resultQueue, countDown,
				provider);
			OutputWriter outputWriter = new OutputWriter(OBJECT_MAPPER, resultQueue, countDown, checkpoint, outputPath,
					checkpointPath)) {
			requestExecutor.start();
			outputWriter.start();
			requestExecutor.awaitCompletion();
			outputWriter.awaitCompletion();
		}
	}

	static void evaluateTweets(List<TweetBatch> tweetBatches, AiProvider provider, Checkpoint checkpoint,
			Path outputDirectory)
			throws Exception {
		if (tweetBatches.isEmpty()) {
			LOGGER.info("Tweet Processing completed on previous run");
			return;
		}
		LOGGER.info("Evaluating " + tweetBatches.size() + " tweet batches");
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
				OutputWriter outputWriter = new OutputWriter(OBJECT_MAPPER, resultQueue, countDown, checkpoint,
						outputDirectory)) {
			requestExecutor.start();
			outputWriter.start();
			requestExecutor.awaitCompletion();
			outputWriter.awaitCompletion();
		}

	}

}
