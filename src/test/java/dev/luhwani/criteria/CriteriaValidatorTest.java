package dev.luhwani.criteria;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.luhwani.error.FatalException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CriteriaValidatorTest {

    @TempDir
    Path tempDir;

    private final CriteriaValidator validator = new CriteriaValidator();

    @Test
    void throwsWhenPathIsNull() {
        assertThrows(FatalException.class, () -> validator.validate(null));
    }

    @Test
    void throwsWhenFileDoesNotExist() {
        Path path = tempDir.resolve("criteria.json");

        assertThrows(FatalException.class, () -> validator.validate(path));
    }

    @Test
    void throwsWhenPathIsDirectory() throws Exception {
        Path directory = Files.createDirectory(tempDir.resolve("criteria.json"));

        assertThrows(FatalException.class, () -> validator.validate(directory));
    }

    @Test
    void throwsWhenFileIsEmpty() throws Exception {
        Path path = Files.createFile(tempDir.resolve("criteria.json"));

        assertThrows(FatalException.class, () -> validator.validate(path));
    }

    @Test
    void throwsWhenFileContainsOnlyWhitespace() throws Exception {
        Path path = writeCriteria("   \n\t");

        assertThrows(FatalException.class, () -> validator.validate(path));
    }

    @Test
    void throwsWhenJsonIsInvalid() throws Exception {
        Path path = writeCriteria("{ invalid json");

        assertThrows(FatalException.class, () -> validator.validate(path));
    }

    @Test
    void throwsWhenJsonObjectIsEmpty() throws Exception {
        Path path = writeCriteria("{}");

        assertThrows(FatalException.class, () -> validator.validate(path));
    }

    @Test
    void throwsWhenJsonArrayIsEmpty() throws Exception {
        Path path = writeCriteria("[]");

        assertThrows(FatalException.class, () -> validator.validate(path));
    }

    @Test
    void throwsWhenJsonContainsAnEmptyNestedContainer() throws Exception {
        Path path = writeCriteria("""
                {
                  "criteria": []
                }
                """);

        assertThrows(FatalException.class, () -> validator.validate(path));
    }

    @Test
void acceptsNonEmptyValidJson() throws Exception {
    Path path = writeCriteria("""
            {
              "criteria": "example"
            }
            """);

    ObjectMapper mapper = new ObjectMapper() {
        @Override
        public <T> T treeToValue(TreeNode node, Class<T> valueType)
                throws JsonProcessingException {
            return null;
        }
    };

    assertDoesNotThrow(() -> new CriteriaValidator(mapper).validate(path));
}

    private Path writeCriteria(String contents) throws Exception {
        return Files.writeString(tempDir.resolve("criteria.json"), contents);
    }
}