package dev.luhwani.tweetProcessing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.ObjectMapper;

import dev.luhwani.error.FatalException;
import dev.luhwani.model.TweetData;

class TweetArchiveLoaderTest {

    @TempDir
    Path tempDir;

    @Test
    void loadsTweetsAndSkipsInvalidRecords() throws Exception {
        Path archive = writeArchive("window.YTD.tweets.part0 = " + """
                [
                  {"tweet":{"id":"1","full_text":"first"}},
                  {"tweet":{"id_str":"2","full_text":"second"}},
                  {"tweet":{"id":"3"}},
                  {"tweet":{"full_text":"missing id"}},
                  {"unexpected":true}
                ]
                """);
        TweetArchiveBuffer buffer = new TweetArchiveBuffer(10);

        new TweetArchiveLoader(new ObjectMapper(), archive).streamTo(buffer);

        assertEquals(List.of(new TweetData("1", "first"), new TweetData("2", "second")),
                List.copyOf(buffer.tweets));
        assertTrue(buffer.finished);
        assertEquals(null, buffer.failure);
    }

    @Test
    void rejectsArchiveWithoutTweetEntry() throws Exception {
        Path archive = tempDir.resolve("tweets.zip");
        try (OutputStream output = Files.newOutputStream(archive);
                ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("data/other.js"));
            zip.write("[]".getBytes());
            zip.closeEntry();
        }
        TweetArchiveBuffer buffer = new TweetArchiveBuffer(2);

        assertThrows(FatalException.class,
                () -> new TweetArchiveLoader(new ObjectMapper(), archive).streamTo(buffer));
        assertTrue(buffer.finished);
        assertTrue(buffer.failure instanceof FatalException);
    }

    @Test
    void rejectsArchiveWithoutTweetArray() throws Exception {
        Path archive = writeArchive("window.YTD.tweets.part0 = {} ");
        TweetArchiveBuffer buffer = new TweetArchiveBuffer(2);

        assertThrows(FatalException.class,
                () -> new TweetArchiveLoader(new ObjectMapper(), archive).streamTo(buffer));
        assertTrue(buffer.failure instanceof FatalException);
    }

    @Test
    void rejectsNonZipAndMissingArchives() {
        Path textFile = tempDir.resolve("tweets.txt");
        try {
            Files.writeString(textFile, "tweets");
        } catch (Exception e) {
            throw new AssertionError(e);
        }

        TweetArchiveBuffer buffer = new TweetArchiveBuffer(2);
        assertThrows(FatalException.class,
                () -> new TweetArchiveLoader(new ObjectMapper(), textFile).streamTo(buffer));
        assertThrows(FatalException.class,
                () -> new TweetArchiveLoader(new ObjectMapper(), tempDir.resolve("missing.zip"))
                        .streamTo(new TweetArchiveBuffer(2)));
    }

    private Path writeArchive(String content) throws Exception {
        Path archive = tempDir.resolve("tweets.zip");
        try (OutputStream output = Files.newOutputStream(archive);
                ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("data/tweets.js"));
            zip.write(content.getBytes());
            zip.closeEntry();
        }
        return archive;
    }
}