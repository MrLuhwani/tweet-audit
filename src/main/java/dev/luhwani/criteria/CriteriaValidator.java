package dev.luhwani.criteria;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.luhwani.error.FatalException;
import dev.luhwani.model.Criteria;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Validates that a criteria path contains non-empty, readable JSON. */
public final class CriteriaValidator {

    private final ObjectMapper objectMapper;

    CriteriaValidator() {
        this(new ObjectMapper());
    }

    /**
     * Creates a validator using the supplied JSON mapper.
     *
     * @param objectMapper mapper used to parse criteria JSON
     */
    public CriteriaValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Reads and validates a criteria file.
     *
     * @param criteriaPath file containing the evaluation rules
     * @return parsed criteria rules
     * @throws FatalException if the file is missing, empty, unreadable, or invalid
     */
    public Criteria validate(Path criteriaPath) throws FatalException {
        if (criteriaPath == null) {
            throw new IllegalArgumentException("Criteria path cannot be null");
        }

        try {
            if (!Files.isRegularFile(criteriaPath)) {
                throw new FatalException("Criteria file does not exist: " + criteriaPath);
            }

            return validate(Files.newInputStream(criteriaPath), criteriaPath.toString());
        } catch (JsonProcessingException e) {
            throw new FatalException("Criteria file contains invalid JSON: " + criteriaPath, e);
        } catch (IOException e) {
            throw new FatalException("Unable to read criteria file: " + criteriaPath, e);
        }
    }

    /** Reads and validates criteria supplied by a classpath resource or another stream. */
    public Criteria validate(InputStream criteriaStream, String sourceDescription) throws FatalException {
        if (criteriaStream == null) {
            throw new IllegalArgumentException("Criteria stream cannot be null");
        }

        try (criteriaStream) {
            JsonNode json = objectMapper.readTree(criteriaStream);
            if (json == null || containsEmptyContainer(json)) {
                throw new FatalException("Criteria file is empty: " + sourceDescription);
            }
            return objectMapper.treeToValue(json, Criteria.class);
        } catch (JsonProcessingException e) {
            throw new FatalException("Criteria file contains invalid JSON: " + sourceDescription, e);
        } catch (IOException e) {
            throw new FatalException("Unable to read criteria file: " + sourceDescription, e);
        }
    }

    private boolean containsEmptyContainer(JsonNode node) {
        if (node.isObject() || node.isArray()) {
            if (node.isEmpty()) {
                return true;
            }
            for (JsonNode child : node) {
                if (containsEmptyContainer(child)) {
                    return true;
                }
            }
        }
        return false;
    }
}
