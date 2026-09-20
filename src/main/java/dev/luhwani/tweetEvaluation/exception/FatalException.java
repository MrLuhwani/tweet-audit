package dev.luhwani.tweetEvaluation.exception;

import java.util.Optional;

/** Indicates a provider failure that should stop the evaluation pipeline. */
public class FatalException extends AiProviderException {
    
    public FatalException(Throwable cause, Optional<Integer> statusCode) {
        super(cause.getMessage(), cause, statusCode);
    }

}


