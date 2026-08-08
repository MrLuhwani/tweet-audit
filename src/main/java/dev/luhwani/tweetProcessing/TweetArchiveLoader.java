package dev.luhwani.tweetProcessing;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.luhwani.model.TweetData;

public final class TweetArchiveLoader {

    private static final String TWEET_ARCHIVE_PATH = "data/tweets.js";
    private static final String JS_PREFIX = "window.YTD.tweets.part0 =";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("EEE MMM dd HH:mm:ss Z yyyy", Locale.ENGLISH);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private TweetArchiveLoader() {

    }

    public static List<TweetData> load() throws IOException {
        Path projectRoot = Paths.get("").toAbsolutePath();
        Path criteriaPath = projectRoot.resolve(TWEET_ARCHIVE_PATH);
        if (!Files.exists(criteriaPath) || !Files.isRegularFile(criteriaPath)) {
            throw new IOException("Tweet archive not found at path: " + TWEET_ARCHIVE_PATH);
        }
        String rawContent = Files.readString(criteriaPath);
        String jsonContent = stripJavaScriptAssignment(rawContent);
        JsonNode jsonObjs = objectMapper.readTree(jsonContent);

        if (!jsonObjs.isArray()) {
            throw new IOException("Expected the tweet archive jsonObjs to be a JSON array");
        }

        List<TweetData> tweets = new ArrayList<>();
        for (JsonNode obj : jsonObjs) {

            JsonNode tweetNode = obj.path("tweet");
            String id = nullableText(tweetNode, "id_str");
            String text = nullableText(tweetNode, "full_text");
            String date = nullableText(tweetNode, "created_at");
            if (id != null && text != null && date != null) {
                ZonedDateTime dateTime = ZonedDateTime.parse(date, FORMATTER);
                tweets.add(new TweetData(id, text, dateTime));
            }
        }

        return List.copyOf(tweets);
    }

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
