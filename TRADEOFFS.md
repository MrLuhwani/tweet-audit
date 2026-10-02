# Technical Tradeoffs

## Language Choice: Java

I chose Java because it is the language I want to specialize in. Its type system catches many errors before runtime, and its concurrency APIs make it practical to coordinate several workers. The main downside is verbosity compared with languages such as Python.

## Architecture: Concurrent Batch Processing with Checkpoints

The application loads the X archive, converts tweets into fixed-size batches, filters successful batches written previously in the checkpoint, and sends unprocessed batches to the AI provider used for evaluation. The batches are evaluated concurrently, and errors that occur during processing are split into retryable, batch failures, and fatal. An output writer consumes the results and uses a single thread to write to the CSV file and checkpoint json. The async workflow introduced shared state between the request executor, and output writer but the trade of simplicity for speed makes the audit process quicker for large tweet archives.

When batch size choices become configurable, you also get the benefit of being able to process more tweets, and since that also means an increased response time from the AI provider, you also have the benefit of being able to stay within your rate limit quota. The downside is that you spend more tokens per request.

The checkpoint records only successful batches. In the previous implementation, failed batches were also stored, but that introduced a lot of uneccessary complexity. With this new implementation, the application filters out successful batches, then resumes with the remaining archive. This keeps processing simple, since both failed batches, and batches that haven't even been attempted at all, can just be considered as unprocessed batches.

## Error Handling and Retries

Provider errors are classified as retryable, batch-specific, or fatal. Transient/Retryable errors use bounded exponential backoff with jitter. If the max retry is reached, that batch is classified as a failed batch. Fatal errors on the other hand, stop the whole evaluation pipeline.

## Output Format

I wrote both tweets to be kept, and those to be deleted. Our tool just generates recommendations, but it is still the users decision to 
make the final say if a tweet should be deleted or not. If they wish to filter, in the [README](README.md) I have written the steps out for them. This helps to keep the full audit trail for the user. Results are written as they arrive, after being processed by the AI, so there is no guarantee tweets are written in the same arrangement as your tweet archive.
