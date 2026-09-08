package dev.luhwani.tweetEvaluation;

import java.util.concurrent.ThreadLocalRandom;

import dev.luhwani.tweetEvaluation.exception.RetryableException;

public class RetryPolicy {

    private static final long BASE_DELAY = 3000;
    private static final long MAX_DELAY = 30000;

    static void awaitRetry(RetryableException e, int attempt, int maxAttempt) throws InterruptedException {
        long delay = exponentialBackoff(attempt, maxAttempt);
        System.err.printf("Retrying after %d ms (attempt %d)%n", delay, attempt + 1);
        Thread.sleep(delay);
        return;
    }

    private static long exponentialBackoff(int attempt, int maxAttempts) {
        long delay = Math.min(
                MAX_DELAY,
                BASE_DELAY * (1L << Math.min(attempt - 1, maxAttempts)));

        long jitter = ThreadLocalRandom.current().nextLong(0, 500);
        return delay + jitter;
    }

}
