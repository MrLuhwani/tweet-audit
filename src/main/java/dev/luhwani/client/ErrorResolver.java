package dev.luhwani.client;

import java.util.Set;

import dev.luhwani.error.BatchException;
import dev.luhwani.error.RetryableException;

/**
 * 
 * Used to resolve the type of error based on the error message passed in
 */
public final class ErrorResolver {

    private static final Set<String> RETRYABLE_KEYWORDS = Set.of("timeout", "connection", "rate limit",
            "too many requests", "quota", "503",
            "429", "temporarily unavailable", "server error");

    public static void throwFromMessage(Exception e) throws RetryableException, BatchException {
        String errorMsg = e.getMessage();
        if (RETRYABLE_KEYWORDS.stream().anyMatch(errorMsg.toLowerCase()::contains)) {
            throw new RetryableException(e.getMessage(), e);
        }
        throw new BatchException(e.getMessage(), e);
    }

}
