package dev.luhwani.model;

import java.util.Objects;

public abstract class QueueEvent<T> {
    private QueueEvent() {
    }

    public static final class Item<T> extends QueueEvent<T> {
        private final T value;

        public Item(T value) {
            this.value = Objects.requireNonNull(value, "value cannot be null");
        }

        public T value() {
            return value;
        }

    }

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