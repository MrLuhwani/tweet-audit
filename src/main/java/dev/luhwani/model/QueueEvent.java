package dev.luhwani.model;

import java.util.Objects;

/** Wraps a queued value or an end-of-processing signal for producer-consumer workflows. */
public abstract class QueueEvent<T> {
    private QueueEvent() {
    }

    /** A regular value placed on a processing queue. */
    public static final class Item<T> extends QueueEvent<T> {
        private final T value;

        public Item(T value) {
            this.value = Objects.requireNonNull(value, "value cannot be null");
        }

        public T value() {
            return value;
        }

    }

    /** A shared sentinel used to signal that a producer has finished. */
    public static final class End<T> extends QueueEvent<T> {
        private static final End<?> INSTANCE = new End<>();

        private End() {
        }

        @SuppressWarnings("unchecked")
        public static <T> End<T> instance() {
            return (End<T>) INSTANCE;
        }

    }
}