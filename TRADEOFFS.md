# Technical Tradeoffs

## Language Choice: Java

I chose Java because it is the language I want to specialize in. Its type system catches many errors before runtime, and its concurrency APIs make it practical to coordinate several workers. The main downside is verbosity compared with languages such as Python.

## Architecture: Buffered Batch Processing with Checkpoints

The former implementation converted your X archive to batches of tweets, and stored them in a queue. The client layer takes from that queue and sends for evaluation. If the user has a very large archive, that's a lot of load on the RAM.

The new implementation uses a buffered pipeline system. Instead of loading the whole archive into memory, it is parsed in smaller buffers, which are bounded by a max window size. This ensures the RAM is not overloaded. This workflow uses 2 major pipelines:

### Archive Loader to Batch Filter Pipeline

The application streams the X archive through bounded queues: a loader publishes tweets in buffers that have a max amount of tweets, while a batch filter constantly drains from the queue, creates fixed-size batches from the tweets, and filters out batches that have been previously evaluated

### Filter Pipeline to Request Executor

Batches produced by the filter are evaluated by request workers concurrently. The tweet batches are stored in a thread safe queue to prevent multi-threading issues. An output writer uses a single thread to write results from the request workers to the CSV and checkpoint JSON.

Backpressure pauses producers when either queue is full, which bounds memory use while preserving concurrency. The async workflow introduces shared completion, failure state between stages, and A LOT of complexity, but the tradeoff prevents system resources from being overwhelmed.

With configurable batch sizes, you get the benefit of processing more tweets. This leads to an increased response time from the AI provider, giving you the benefit of being able to stay within your rate limit quota. The downside is that you spend more tokens per request.

The checkpoint records only successfully written batches, then resumes with the remaining archive. This keeps processing simple, since failed batches and unattempted batches can be treated as unprocessed batches.

## Error Handling and Retries

Provider errors are classified as retryable, batch-specific, or fatal. Retryable errors use bounded exponential backoff. If max retry is reached, that batch is classified as a failed batch. Fatal errors on the other hand, stop the whole evaluation pipeline.

## Output Format

I wrote both tweets to be kept, and those to be deleted. Our tool just generates recommendations, but it is still the users decision to 
make the final say if a tweet should be deleted or not. If they wish to filter, in the [README](README.md) I have written the steps out for them. This helps to keep the full audit trail for the user. Results are written as they arrive, after being processed by the AI, so there is no guarantee tweets are written in the same arrangement as your tweet archive.
