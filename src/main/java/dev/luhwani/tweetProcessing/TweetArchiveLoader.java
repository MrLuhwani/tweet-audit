package dev.luhwani.tweetProcessing;

import java.io.IOException;
import java.io.PushbackReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.luhwani.configuration.AuditPaths;
import dev.luhwani.error.FatalException;
import dev.luhwani.model.TweetData;

/**
 * Parses X's JavaScript tweet archive and returns tweets.
 */
public final class TweetArchiveLoader {

    private static final Path TWEET_ARCHIVE_PATH = AuditPaths.TWEETS_PATH;
    private final ObjectMapper objectMapper;
    private final Path archivePath;

    /**
     * Creates a loader for the configured tweet archive path.
     *
     * @param objectMapper mapper used to parse the archive JSON
     */
    public TweetArchiveLoader(ObjectMapper objectMapper) {
        this(objectMapper, TWEET_ARCHIVE_PATH);
    }

    // for easy testability
    TweetArchiveLoader(ObjectMapper objectMapper, Path archivePath) {
        if (objectMapper == null) {
            throw new IllegalArgumentException("Object mapper must not be null");
        }
        if (archivePath == null) {
            throw new IllegalArgumentException("Archive path must not be null");
        }
        this.objectMapper = objectMapper;
        this.archivePath = archivePath;
    }

    /**
     * Loads tweets from the archive, skipping entries without an id or text.
     * The archive may contain the standard {@code window.YTD.tweets.part0 = }
     * JavaScript prefix before its JSON array.
     *
     * @return the valid tweets in archive order
     * @throws FatalException if the archive is missing, malformed, or unreadable
     */
    public List<TweetData> load() throws FatalException {
        if (!Files.exists(archivePath)) {
            throw new FatalException("Tweet archive not found at path: " + archivePath);
        }

        try (PushbackReader reader = new PushbackReader(Files.newBufferedReader(archivePath), 1)) {
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
                throw new FatalException("Tweets array not found");
            }

            JsonParser parser = objectMapper
                    .getFactory()
                    .createParser(reader);
            JsonToken token = parser.nextToken();
            if (token != JsonToken.START_ARRAY) {
                throw new FatalException("Tweets array not found");
            }

            int tweetCount = 0;
            List<TweetData> tweets = new ArrayList<>();
            while ((token = parser.nextToken()) != JsonToken.END_ARRAY) {
                if (token == null) {
                    throw new FatalException("Unexpected end of tweet archive");
                }
                JsonNode wrapper = objectMapper.readTree(parser);
                if (wrapper == null || !wrapper.isObject()) {
                    System.out.println("[WARN] Tweet " + (tweetCount + 1)
                            + " is not an object. Skipping tweet.");
                    tweetCount++;
                    continue;
                }
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

                String text = nullableText(tweetNode, "full_text");
                if (text == null) {
                    System.out.println("[WARN] Tweet " + (tweetCount + 1) + " is missing text. Skipping tweet.");
                    tweetCount++;
                    continue;
                }
                tweets.add(new TweetData(id, text));
                tweetCount++;
            }
            return tweets;
        } catch (IOException e) {
            throw new FatalException("IOException occured. Failed to read tweet archive: " + archivePath, e);
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