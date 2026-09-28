package dev.luhwani.tweetProcessing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import dev.luhwani.model.TweetBatch;
import dev.luhwani.model.TweetData;

class TweetBatchFactoryTest {

    @Test
    void rejectsNullTweets() {
        assertThrows(
                IllegalArgumentException.class,
                () -> TweetBatchFactory.createBatches(null));
    }

    @Test
    void rejectsEmptyInput() {
        assertThrows(IllegalArgumentException.class, () -> TweetBatchFactory.createBatches(List.of()));
    }

    // 60 is used in these tests because that was the hardcoded value for the size
    // of tweet batches

    @Test
    void createsOneBatchWhenFewerThanSixtyTweetsExist() {
        List<TweetData> tweets = tweets(59);

        List<TweetBatch> batches = TweetBatchFactory.createBatches(tweets);

        assertEquals(1, batches.size());
        assertEquals(59, batches.get(0).tweets().size());
        assertEquals(tweets, batches.get(0).tweets());
    }

    @Test
    void createsOneBatchForExactlySixtyTweets() {
        List<TweetData> tweets = tweets(60);

        List<TweetBatch> batches = TweetBatchFactory.createBatches(tweets);

        assertEquals(1, batches.size());
        assertEquals(60, batches.get(0).tweets().size());
    }

    @Test
    void createsSecondBatchForSixtyOneTweets() {
        List<TweetData> tweets = tweets(61);

        List<TweetBatch> batches = TweetBatchFactory.createBatches(tweets);

        assertEquals(2, batches.size());
        assertEquals(60, batches.get(0).tweets().size());
        assertEquals(1, batches.get(1).tweets().size());
        assertEquals(tweets.get(60), batches.get(1).tweets().get(0));
    }

    @Test
    void createsExactlyTwoFullBatchesForOneHundredTwentyTweets() {
        List<TweetData> tweets = tweets(120);

        List<TweetBatch> batches = TweetBatchFactory.createBatches(tweets);

        assertEquals(2, batches.size());
        assertEquals(60, batches.get(0).tweets().size());
        assertEquals(60, batches.get(1).tweets().size());
    }

    @Test
    void numbersBatchesSequentiallyStartingAtOne() {
        List<TweetBatch> batches = TweetBatchFactory.createBatches(tweets(7), 3);

        assertEquals(3, batches.size());
        assertEquals(1, batches.get(0).batchNumber());
        assertEquals(2, batches.get(1).batchNumber());
        assertEquals(3, batches.get(2).batchNumber());
    }

    private List<TweetData> tweets(int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> new TweetData(
                        String.valueOf(index),
                        "Tweet " + index))
                .toList();
    }
}