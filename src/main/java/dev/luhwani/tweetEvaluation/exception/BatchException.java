package dev.luhwani.tweetEvaluation.exception;

import java.util.Optional;

import dev.luhwani.model.TweetBatch;

/** Indicates that one batch failed while the overall evaluation can continue. */
public class BatchException extends AiProviderException {

    public BatchException(Throwable cause, Optional<Integer> statusCode, TweetBatch batch) {
        super(cause.getMessage(), cause, statusCode);
    }

}


