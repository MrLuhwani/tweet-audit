package dev.luhwani.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/** The recommendation produced for a tweet. */
public enum Decision {
    KEEP, DELETE;

    /**
     * Parses a case-insensitive JSON decision value.
     *
     * @param value JSON value such as {@code KEEP} or {@code DELETE}
     * @return the matching decision
     * @throws IllegalArgumentException if the value is not a supported decision
     */
    @JsonCreator
    public static Decision fromJson(String value) {
        return Decision.valueOf(value.trim().toUpperCase());
    }
}
