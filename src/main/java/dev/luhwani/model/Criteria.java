package dev.luhwani.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 
 * Represents the criteria used for evaluating tweets. This class encapsulates
 * the rules defined in a JSON structure.
 */
public record Criteria(
        @JsonAlias("criteria") JsonNode rules) {
}
