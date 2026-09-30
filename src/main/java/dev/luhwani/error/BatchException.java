package dev.luhwani.error;

/** Indicates that one batch could not be processed successfully. */
public class BatchException extends Exception {
    public BatchException(String message) {
        super(message);
    }

    public BatchException(String message, Throwable cause) {
        super(message, cause);
    }

    public BatchException(Throwable cause) {
        super(cause);
    }

}
