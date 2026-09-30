package dev.luhwani.model;

import java.util.HashSet;
import java.util.Set;

/** Stores completed batches so an audit doesn't have to start from scratch. */
public record Checkpoint(Set<Integer> successfulBatches) {
    
    /**
     * Creates a checkpoint with no successfully processed batches.
     *
     * @return an empty checkpoint
     */
    public static Checkpoint empty() {
        return new Checkpoint(new HashSet<>());
    }

}