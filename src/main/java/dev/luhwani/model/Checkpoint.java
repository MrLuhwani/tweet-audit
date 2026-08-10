package dev.luhwani.model;

public record Checkpoint(int lastCompletedBatchIndex, String lastTweetId) {
    public static Checkpoint empty() {
        return new Checkpoint(-1, null);
    }

    public int nextBatchIndex() {
        return lastCompletedBatchIndex + 1;
    }
}
