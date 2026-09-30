package dev.luhwani.error;

/** Indicates a transient failure that may succeed when retried. */
public class RetryableException extends Exception {
    public RetryableException(String message) {
        super(message);
    }

    public RetryableException(String message, Throwable cause) {
        super(message, cause);
    }

    public RetryableException(Throwable cause) {
        super(cause);
    }
}
