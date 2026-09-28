package dev.luhwani.error;

public class FatalException extends RuntimeException {
    
    public FatalException(String message) {
        super(message);
    }

    public FatalException(String message, Throwable cause) {
        super(message, cause);
    }
}
