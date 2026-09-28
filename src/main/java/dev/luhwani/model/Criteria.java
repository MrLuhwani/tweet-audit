package dev.luhwani.model;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 
 * Represents the criteria used for evaluating tweets. This class encapsulates
 * the rules defined in a JSON structure.
 */
public record Criteria(
        JsonNode rules) {
}
