package dev.luhwani.error;

/** Indicates an unrecoverable application or data error. */
public class FatalException extends Exception {
    
    public FatalException(String message) {
        super(message);
    }

    public FatalException(String message, Throwable cause) {
        super(message, cause);
    }

    public FatalException(Throwable cause) {
        super(cause);
    }
}
