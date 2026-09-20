package dev.luhwani.tweetProcessing;

import java.io.IOException;
import java.io.PushbackReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.luhwani.model.TweetData;

/** Parses X's JavaScript tweet archive and returns tweets after a checkpoint. */
public final class TweetArchiveLoader {

    private static final Path TWEET_ARCHIVE_PATH = Paths
            .get("")
            .toAbsolutePath()
            .normalize()
            .resolve("data/tweets.js");
    private final ObjectMapper objectMapper;

    public TweetArchiveLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<TweetData> load(String lastProcessedTweet) throws IOException {
        if (!Files.exists(TWEET_ARCHIVE_PATH)) {
            throw new IOException("Tweet archive not found at path: " + TWEET_ARCHIVE_PATH);
        }
        if (!Files.isRegularFile(TWEET_ARCHIVE_PATH)) {
            throw new IOException("Could not read tweet data at: " + TWEET_ARCHIVE_PATH);
        }
        if (!Files.isReadable(TWEET_ARCHIVE_PATH)) {
            throw new IOException("Tweet archive is not readable at path: " + TWEET_ARCHIVE_PATH);
        }

        try (PushbackReader reader = new PushbackReader(Files.newBufferedReader(TWEET_ARCHIVE_PATH), 1)) {
            int ch;

            /**
             * An unmodified tweet.js file starts with
             * "window.YTD.tweets.part0 = ". THis loop looks for the first index of a square
             * bracket ('[') that identifies the start
             * of the tweet array
             */
            while ((ch = reader.read()) != -1) {
                if (ch == '[') {
                    reader.unread(ch);
                    break;
                }
            }

            if (ch == -1) {
                throw new IOException("Tweets array not found");
            }

            JsonParser parser = objectMapper
                    .getFactory()
                    .createParser(reader);
            JsonToken token = parser.nextToken();
            if (token != JsonToken.START_ARRAY) {
                throw new IOException("Tweets array not found");
            }

            int tweetCount = 0;
            List<TweetData> tweets = new ArrayList<>();
            boolean checkpointFound = false;
            if (lastProcessedTweet.isEmpty()) {
                checkpointFound = true;
            }
            while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
                JsonNode wrapper = objectMapper.readTree(parser);
                JsonNode tweetNode = wrapper.get("tweet");

                if (tweetNode == null || tweetNode.isNull()) {
                    continue;
                }

                String id = nullableText(tweetNode, "id");
                if (id == null) {
                    id = nullableText(tweetNode, "id_str");
                }
                if (id == null) {
                    System.out.println("[WARN] Tweet " + (tweetCount + 1) + " is missing id. Skipping tweet.");
                    tweetCount++;
                    continue;
                }

                if (!checkpointFound) {
                    if (lastProcessedTweet.equals(id)) {
                        checkpointFound = true;
                    }
                    tweetCount++;
                    continue;
                }

                String text = nullableText(tweetNode, "full_text");
                if (text == null) {
                    System.out.println("[WARN] Tweet " + (tweetCount + 1) + " is missing text. Skipping tweet.");
                    tweetCount++;
                    continue;
                }
                tweets.add(new TweetData(id, text));
                tweetCount++;
            }

            if (!checkpointFound) {
                throw new IllegalStateException(
                        "Checkpoint tweet " + lastProcessedTweet + " was not found in the tweet archive.");
            }
            return tweets;
        }

    }

    private static String nullableText(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText().trim();
        if (text.isEmpty()) {
            return null;
        }
        return text;
    }
}
