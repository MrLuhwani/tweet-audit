package dev.luhwani.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import dev.luhwani.error.BatchException;
import dev.luhwani.error.RetryableException;

class ErrorResolverTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "timeout", "connection", "rate limit", "too many requests", "quota", "503", "429",
            "temporarily unavailable", "server error"
    })
    void classifiesRetryableMessages(String keyword) {
        Exception cause = new Exception("Request failed: " + keyword);

        RetryableException exception = assertThrows(RetryableException.class,
                () -> ErrorResolver.throwFromMessage(cause));

        assertEquals(cause.getMessage(), exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    @Test
    void matchesRetryableKeywordsWithoutRespectingCase() {
        Exception cause = new Exception("The request hit a RATE LIMIT");

        assertThrows(RetryableException.class, () -> ErrorResolver.throwFromMessage(cause));
    }

    @Test
    void classifiesUnrecognizedMessagesAsBatchFailures() {
        Exception cause = new Exception("Invalid request payload");

        BatchException exception = assertThrows(BatchException.class,
                () -> ErrorResolver.throwFromMessage(cause));

        assertEquals(cause.getMessage(), exception.getMessage());
        assertSame(cause, exception.getCause());
    }
}