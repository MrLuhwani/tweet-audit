# Technical Tradeoffs

## Language Choice: Java

I chose Java because it is the language I want to specialize in. Its type system catches many errors before runtime, and its concurrency APIs make it practical to coordinate several workers. The main downside is verbosity compared with languages such as Python.

## Architecture: Concurrent Batch Processing with Checkpoints

The application loads the X archive, converts tweets into fixed-size batches, and sends those batches to the AI provider used for evaluation. The batches are evaluated concurrently, while an output writer consumes the results and writes the CSV file. A separate failure handler, which also runs in the background, stores batches that could not be processed in a JSON Lines file. The async workflow introduced a lot of shared state between the request executor, output writer, and the failure handler, but the trade of simplicity for speed makes the audit process quicker for large tweet archives. When batch size choices become configurable, you also get the benefit of being able to process more tweets, and since that also means an increased response time from the AI provider, you also have the benefit of being able to stay within your rate limit quota. The downside is that you spend more tokens per request.

The checkpoint records successful batches, failed batches, and the last processed tweet. On a later run, the application first loads and retries failed batches in failedbatches.jsonl, updates the checkpoint, then resumes with the remaining archive. The separation bettween successful and failed batches in the checkpoint is because, if we are storing failedbatches, there has to be a way to correctly ensure
that what is in failedbatches.jsonl is accurate with the audit checkpoint.

## Error Handling and Retries

Provider errors are classified as retryable, batch-specific, or fatal. Transient errors use bounded exponential backoff with jitter. A batch that still fails is recorded for a later run, while fatal errors stop the current evaluation. This keeps one problematic batch from silently advancing the checkpoint as though it had succeeded.

## Output Format

I wrote both tweets to be kept, and those to be deleted. Our tool just generates recommendations, but it is still the users decision to 
make the final say if a tweet should be deleted or not. This helps to keep the full audit trail for the user. Because retry results are not written in an arranged manner, the CSV may need to be sorted by batch number after a rerun.