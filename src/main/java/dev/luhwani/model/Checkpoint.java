package dev.luhwani.model;

import java.util.HashSet;
import java.util.Set;

/** Stores completed and failed batches so an audit can resume safely. */
public record Checkpoint(Set<Integer> successfulBatches) {
    
    public static Checkpoint empty() {
        return new Checkpoint(new HashSet<>());
    }

}