package dev.luhwani.tweetEvaluation.exception;

import java.util.Optional;
import java.util.Set;

public abstract class AiProviderException extends Exception {

    private final Optional<Integer> statusCode;
    private static final Set<String> RETRYABLE_KEYWORDS = Set.of("timeout", "connection", "rate limit",
            "too many requests", "quota", "503",
            "429", "temporarily unavailable", "server errror");
            
    protected AiProviderException(
            String message,
            Throwable cause,
            Optional<Integer> statusCode) {

        super(message, cause);
        this.statusCode = statusCode;
    }

    public Optional<Integer> getStatusCode() {
        return statusCode;
    }

    public static AiProviderException fromMessage(Exception e) throws AiProviderException {
        String errorMsg = e.getMessage();
        if (RETRYABLE_KEYWORDS.stream().anyMatch(errorMsg.toLowerCase()::contains)) {
            return new RetryableException(e, Optional.empty());
        }
        return new FatalException(e, Optional.empty());
    }

}
