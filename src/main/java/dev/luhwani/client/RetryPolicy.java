package dev.luhwani.client;

import java.util.concurrent.ThreadLocalRandom;

import dev.luhwani.error.BatchException;
import dev.luhwani.error.RetryableException;

/** Applies bounded exponential backoff with jitter between retry attempts. */
final class RetryPolicy {

    private static final long BASE_DELAY = 300;
    private static final long MAX_DELAY = 3000;
    private static final long JITTER_MAX = 500;

    static void awaitRetry(RetryableException e, int attempt, int maxAttempt) throws InterruptedException, BatchException {
        if (attempt >= maxAttempt) {
            throw new BatchException(e);
        }
        long delay = exponentialBackoff(attempt);
        System.err.printf("Retrying after %d ms (attempt %d)%n", delay, attempt + 1);
        Thread.sleep(delay);
        return;
    }

    private static long exponentialBackoff(int attempt) {
        // To prevent exponent from overflowing max_int in java
        int safeExponent = Math.min(attempt, 30);

        long calculatedDelay = BASE_DELAY * (1L << safeExponent);
        long delay = Math.min(MAX_DELAY, calculatedDelay);

        long jitter = ThreadLocalRandom.current().nextLong(0, JITTER_MAX);
        return delay + jitter;
    }

}
