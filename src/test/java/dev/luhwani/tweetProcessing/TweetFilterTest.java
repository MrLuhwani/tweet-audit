package dev.luhwani.tweetProcessing;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.luhwani.model.Checkpoint;
import dev.luhwani.model.TweetBatch;
import dev.luhwani.model.TweetData;

class TweetFilterTest {

    @Test
    void removesSuccessfullyAuditedBatches() {
        TweetBatch batch1 = createBatch(1);
        TweetBatch batch2 = createBatch(2);
        TweetBatch batch3 = createBatch(3);
        TweetBatch batch4 = createBatch(4);

        List<TweetBatch> batches = new ArrayList<>(List.of(batch1, batch2, batch3, batch4));

        Checkpoint checkpoint = checkpointWithSuccessfulBatches(1, 3);

        List<TweetBatch> result = TweetFilter.filter(batches, checkpoint);
        assertEquals(List.of(batch2, batch4), result);
    }

    @Test
    void keepsBatchesThatAreNotSuccessful() {
        TweetBatch batch1 = createBatch(1);
        TweetBatch batch2 = createBatch(2);

        List<TweetBatch> batches = new ArrayList<>(List.of(batch1, batch2));

        Checkpoint checkpoint = checkpointWithSuccessfulBatches();

        List<TweetBatch> result = TweetFilter.filter(batches, checkpoint);
        assertEquals(List.of(batch1, batch2), result);
    }

    @Test
    void returnsEmptyListWhenAllBatchesAreSuccessful() {
        TweetBatch batch1 = createBatch(1);
        TweetBatch batch2 = createBatch(2);

        List<TweetBatch> batches = new ArrayList<>(List.of(batch1, batch2));

        Checkpoint checkpoint = checkpointWithSuccessfulBatches(1, 2);

        List<TweetBatch> result = TweetFilter.filter(batches, checkpoint);

        assertEquals(List.of(), result);
    }

    private TweetBatch createBatch(int batchNumber) {
        return new TweetBatch(batchNumber,
                List.of(new TweetData("1", "test tweet")));
    }

    private Checkpoint checkpointWithSuccessfulBatches(Integer... batchNumbers) {
        return new Checkpoint(
                new HashSet<>(List.of(batchNumbers)),
                new HashSet<>(),
                0,
                "");
    }
}