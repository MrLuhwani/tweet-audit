package dev.luhwani.tweetProcessing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.luhwani.error.FatalException;
import dev.luhwani.model.TweetData;

class TweetArchiveLoaderTest {

    @TempDir
    Path tempDir;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void loadsTweetsFromJavaScriptArchive() throws Exception {
        Path archive = copyFixtureToTempDir();

        List<TweetData> tweets = new TweetArchiveLoader(objectMapper, archive).load();

        assertEquals(4, tweets.size());
    }

    @Test
    void usesIdStrWhenIdIsMissing() throws Exception {
        Path archive = writeArchive("""
                window.YTD.tweets.part0 = [
                  {"tweet": {"id_str": "  300  ", "full_text": "  Third tweet  "}},
                  {"tweet": {"id": "500", "full_text": "Valid tweet"}}
                ];
                """);

        List<TweetData> tweets = new TweetArchiveLoader(objectMapper, archive).load();

        assertEquals(2, tweets.size());
        assertTweet(tweets.get(0), "300", "Third tweet");
        assertTweet(tweets.get(1), "500", "Valid tweet");
    }

    @Test
    void skipsInvalidTweets() throws Exception {
        Path archive = writeArchive("""
                window.YTD.tweets.part0 = [
                  {"tweet": {"id": "   ", "full_text": "No id"}},
                  {"tweet": {"id": "400", "full_text": "   "}},
                  {"tweet": {"id": "500", "full_text": "Valid tweet"}},
                  {"other": {"id": "600", "full_text": "Not a tweet"}}
                ];
                """);

        List<TweetData> tweets = new TweetArchiveLoader(objectMapper, archive).load();

        assertEquals(1, tweets.size());
        assertTweet(tweets.get(0), "500", "Valid tweet");
    }

    @Test
    void throwsWhenArchiveDoesNotExist() {
        Path archive = tempDir.resolve("missing-tweets.js");

        assertThrows(FatalException.class,
                () -> new TweetArchiveLoader(objectMapper, archive).load());
    }

    @Test
    void throwsWhenTweetsArrayIsMissing() throws Exception {
        Path archive = writeArchive("window.YTD.tweets.part0 = {};");

        assertThrows(FatalException.class,
                () -> new TweetArchiveLoader(objectMapper, archive).load());
    }

    @Test
    void throwsWhenArchiveContainsMalformedJson() throws Exception {
        Path archive = writeArchive("window.YTD.tweets.part0 = [{\"tweet\": {");

        assertThrows(FatalException.class,
                () -> new TweetArchiveLoader(objectMapper, archive).load());
    }

    @Test
    void rejectsNullDependencies() {
        assertThrows(IllegalArgumentException.class,
                () -> new TweetArchiveLoader(null, tempDir.resolve("tweets.js")));
        assertThrows(IllegalArgumentException.class,
                () -> new TweetArchiveLoader(objectMapper, null));
    }

    private Path copyFixtureToTempDir() throws IOException {
        Path fixture = Path.of("src", "test", "resources", "tweets.js");
        return Files.copy(fixture, tempDir.resolve("tweets.js"));
    }

    private Path writeArchive(String contents) throws IOException {
        return Files.writeString(tempDir.resolve("tweets.js"), contents);
    }

    private void assertTweet(TweetData tweet, String expectedId, String expectedText) {
        assertEquals(expectedId, tweet.id());
        assertEquals(expectedText, tweet.text());
    }
}