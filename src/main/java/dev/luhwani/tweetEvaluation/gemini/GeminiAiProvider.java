package dev.luhwani.tweetEvaluation.gemini;

import java.nio.file.Path;

import dev.luhwani.model.TweetBatch;
import dev.luhwani.tweetEvaluation.AiProvider;

public class GeminiAiProvider extends AiProvider {

    private final long INTERVAL_MILLIS = 6000;

    public GeminiAiProvider(String apiKey, Path criteria){
        super(apiKey, criteria);
    }

    @Override
    protected long defineIntervalMillis() {
        return INTERVAL_MILLIS;
    }

    @Override
    public void analyze(TweetBatch batch) {
        System.out.println("Unimplemented method 'analyze'");
    }
    
}
