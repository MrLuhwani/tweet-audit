package dev.luhwani.client;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import dev.luhwani.error.BatchException;
import dev.luhwani.error.RetryableException;

class RetryPolicyTest {

    @Test
    void convertsFinalAttemptIntoBatchFailureWithoutSleeping() {
        RetryableException failure = new RetryableException("temporary");

        BatchException exception = assertThrows(BatchException.class,
                () -> RetryPolicy.awaitRetry(failure, 3, 3));

        assertSameCause(failure, exception);
    }

    @Test
    void propagatesInterruptionDuringBackoff() {
        Thread.currentThread().interrupt();
        try {
            assertThrows(InterruptedException.class,
                    () -> RetryPolicy.awaitRetry(new RetryableException("temporary"), 1, 3));
        } finally {
            Thread.interrupted();
        }
    }

    private static void assertSameCause(Throwable expected, Throwable actual) {
        if (actual.getCause() != expected) {
            throw new AssertionError("Expected the retry failure to be preserved", actual);
        }
    }
}