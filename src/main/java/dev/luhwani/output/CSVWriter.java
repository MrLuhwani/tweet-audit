package dev.luhwani.output;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;

import dev.luhwani.model.AnalysisResult;
import dev.luhwani.model.TweetDecision;

final class CSVWriter implements AutoCloseable {

    private final CsvMapper csvMapper = new CsvMapper();
    private final FileOutputStream outputStream;

    CSVWriter(Path file) throws IOException {
        csvMapper.getFactory().configure(JsonGenerator.Feature.AUTO_CLOSE_TARGET, false);
        this.outputStream = new FileOutputStream(file.toFile(), true);
    }

    void write(AnalysisResult result) throws IOException {
        CsvSchema schema = csvMapper.schemaFor(TweetDecision.class).withoutHeader();
        csvMapper.writer(schema).writeValue(outputStream, result.results());
    }

    @Override
    public void close() throws Exception {
        outputStream.close();
    }
}
