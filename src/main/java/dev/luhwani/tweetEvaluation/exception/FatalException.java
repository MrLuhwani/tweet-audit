package dev.luhwani.tweetEvaluation.exception;

import java.util.Optional;

/**
 * This represents exceptions that should cause the {@link RequestExecutor} -> {@link AiProvider} pipeline to
 * fail completely
 */
public class FatalException extends AiProviderException {
    
    public FatalException(Throwable cause, Optional<Integer> statusCode) {
        super(cause.getMessage(), cause, statusCode);
    }

}


