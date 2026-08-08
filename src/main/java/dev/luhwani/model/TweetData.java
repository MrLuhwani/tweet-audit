package dev.luhwani.model;

import java.time.ZonedDateTime;

public record TweetData(
                String id,
                String text,
                ZonedDateTime createdAt) {
}