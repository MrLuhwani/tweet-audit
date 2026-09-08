package dev.luhwani.tweetEvaluation.exception;

import java.util.Optional;

/**
 * this class represents exceptions that can be retried, i.e they are transient
 */
public class RetryableException extends AiProviderException {

    public RetryableException(Throwable cause, Optional<Integer> statusCode) {
        super(cause.getMessage(), cause, statusCode);
    }
    
}
