package dev.luhwani.tweetEvaluation.exception;

import java.util.Optional;

/** Indicates a transient provider failure that may succeed on a later attempt. */
public class RetryableException extends AiProviderException {

    public RetryableException(Throwable cause, Optional<Integer> statusCode) {
        super(cause.getMessage(), cause, statusCode);
    }
    
}
