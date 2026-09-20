package dev.luhwani.model;

import com.fasterxml.jackson.annotation.JsonCreator;

/** The recommendation produced for a tweet. */
public enum Decision {
    KEEP, DELETE;

    @JsonCreator
    public static Decision fromJson(String value) {
        return Decision.valueOf(value.trim().toUpperCase());
    }
}
