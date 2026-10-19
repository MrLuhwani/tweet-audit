package dev.luhwani.tweetProcessing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;

class TweetArchiveBufferTest {

    @Test
    void validatesCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new TweetArchiveBuffer(0));
        assertThrows(IllegalArgumentException.class, () -> new TweetArchiveBuffer(-1));
    }

    @Test
    void signalsReadyAndDrainedState() throws Exception {
        TweetArchiveBuffer buffer = new TweetArchiveBuffer(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            var waiting = executor.submit(() -> {
                buffer.awaitReady();
                return buffer.ready;
            });
            buffer.signalReady();

            assertTrue(waiting.get());
            buffer.drained();
            assertEquals(false, buffer.ready);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void finishPublishesFailureAndWakesWaiters() throws Exception {
        TweetArchiveBuffer buffer = new TweetArchiveBuffer(1);
        RuntimeException failure = new RuntimeException("failed");
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            var waiting = executor.submit(() -> {
                buffer.awaitReady();
                return buffer.finished;
            });
            buffer.finish(failure);

            assertTrue(waiting.get());
            assertTrue(buffer.finished);
            assertTrue(buffer.ready);
            assertEquals(failure, buffer.failure);
        } finally {
            executor.shutdownNow();
        }
    }
}