package dev.luhwani.tweetProcessing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.luhwani.error.FatalException;
import dev.luhwani.model.Checkpoint;
import dev.luhwani.model.TweetData;

class TweetBatchFilterTest {

    @Test
    void partitionsTweetsAndSkipsSuccessfulCheckpointedBatches() throws Exception {
        TweetArchiveBuffer archive = new TweetArchiveBuffer(10);
        archive.tweets.addAll(List.of(new TweetData("1", "one"), new TweetData("2", "two"),
                new TweetData("3", "three"), new TweetData("4", "four"), new TweetData("5", "five")));
        archive.finish(null);
        TweetBatchQueue batches = new TweetBatchQueue();

        new TweetBatchFilter(archive, batches, new Checkpoint(Set.of(1)), 2).run();

        assertEquals(List.of(2, 3), batches.batches.stream().map(batch -> batch.batchNumber()).toList());
        assertEquals(List.of("3", "4"), batches.batches.poll().tweets().stream().map(TweetData::id).toList());
        assertEquals(List.of("5"), batches.batches.poll().tweets().stream().map(TweetData::id).toList());
        assertTrue(batches.finished);
    }

    @Test
    void propagatesArchiveFailureAndMarksQueueFinished() throws Exception {
        TweetArchiveBuffer archive = new TweetArchiveBuffer(2);
        FatalException failure = new FatalException("archive failed");
        archive.finish(failure);
        TweetBatchQueue batches = new TweetBatchQueue();

        new TweetBatchFilter(archive, batches, Checkpoint.empty(), 2).run();

        assertTrue(batches.finished);
        assertEquals(failure, batches.failure);
    }

    @Test
    void rejectsInvalidDependenciesAndBatchSize() {
        TweetArchiveBuffer archive = new TweetArchiveBuffer(2);
        TweetBatchQueue batches = new TweetBatchQueue();

        assertThrows(IllegalArgumentException.class,
                () -> new TweetBatchFilter(null, batches, Checkpoint.empty(), 1));
        assertThrows(IllegalArgumentException.class,
                () -> new TweetBatchFilter(archive, batches, null, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new TweetBatchFilter(archive, batches, Checkpoint.empty(), 0));
    }
}