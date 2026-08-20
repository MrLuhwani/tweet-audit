package dev.luhwani.model;

public sealed interface QueueEvent<T>
        permits QueueEvent.Item, QueueEvent.End {

    record Item<T>(T value) implements QueueEvent<T> {
    }

    record End<T>() implements QueueEvent<T> {
    }
}