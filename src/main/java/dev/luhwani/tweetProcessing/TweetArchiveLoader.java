package dev.luhwani.tweetProcessing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.luhwani.model.TweetData;

public final class TweetArchiveLoader {

    private static final String TWEET_ARCHIVE_PATH = "data/tweets.js";
    private final ObjectMapper objectMapper;

    public TweetArchiveLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<TweetData> load() throws IOException {
        Path projectRoot = Paths.get("").toAbsolutePath();
        Path criteriaPath = projectRoot.resolve(TWEET_ARCHIVE_PATH);
        if (!Files.exists(criteriaPath) || !Files.isRegularFile(criteriaPath)) {
            throw new IOException("Tweet archive not found at path: " + TWEET_ARCHIVE_PATH);
        }
        String rawContent = Files.readString(criteriaPath);
        String jsonContent = stripJavaScriptAssignment(rawContent);

        // this throws an error if the file is invalid json, so even if we successfully
        // strip the js assignment, but the file itself doesn't have a proper json structure,
        // it would throw an err
        JsonNode jsonObjs = objectMapper.readTree(jsonContent);

        if (!jsonObjs.isArray()) {
            throw new IOException("Expected the tweet archive jsonObjs to be a JSON array");
        }

        List<TweetData> tweets = new ArrayList<>();
        for (JsonNode obj : jsonObjs) {

            JsonNode tweetNode = obj.path("tweet");
            String id = nullableText(tweetNode, "id_str");
            String text = nullableText(tweetNode, "full_text");
            if (id != null && text != null) {
                tweets.add(new TweetData(id, text));
            }
        }

        return List.copyOf(tweets);
    }

    /**
     * An unmodified {@code tweet.js} file starts with {@code "window.YTD.tweets.part0"}. This method
     * looks for the first index of a square bracket ('[') that identifies the start of the tweet array
     * @param tweets
     * @return tweets in a string format, without the js prefix
     * @throws IOException
     */
    private static String stripJavaScriptAssignment(String tweets) throws IOException {
        String trimmed = tweets.stripLeading();

        if (trimmed.startsWith("[")) {
            return trimmed;
        }

        int arrayStart = trimmed.indexOf('[');

        if (arrayStart < 0) {
            throw new IOException("Could not find the JSON array in the tweets archive");
        }

        return trimmed.substring(arrayStart);
    }

    private static String nullableText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        } else {
            return value.asText();
        }
    }
}
