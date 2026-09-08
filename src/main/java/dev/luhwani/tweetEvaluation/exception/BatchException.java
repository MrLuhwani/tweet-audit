package dev.luhwani.tweetEvaluation.exception;

import java.util.Optional;

import dev.luhwani.model.TweetBatch;

/**
 * these exceptions should not cause the whole Executor -> Provider pipeline to
 * stop, but should mark
 * that a particular batch has failed
 */
public class BatchException extends AiProviderException {

    private final TweetBatch failedBatch;

    public BatchException(Throwable cause, Optional<Integer> statusCode, TweetBatch batch) {
        super(cause.getMessage(), cause, statusCode);
        this.failedBatch = batch;
    }

    public TweetBatch getFailedBatch() {
        return failedBatch;
    }

}


