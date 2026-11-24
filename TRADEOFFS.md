# Technical Tradeoffs

## Language Choice: Java

I chose Java because it is the language I want to specialize in. Compared to Go, it offers broader job opportunities in the ecosystems I am targeting. Compared to Python, its static type system catches many errors before runtime, and its concurrency tooling makes coordinating multiple worker threads practical. The main trade-off is verbosity.

## Architecture: Buffered Batch Processing with Checkpoints

Rather than loading an entire X archive into memory at once, the pipeline streams data through bounded queues in two stages so RAM usage stays minimal.

### Archive Loader to Batch Filter Stage

A loader streams the archive and publishes tweets in memory-bounded chunks to a queue (say queueA). A batch filter drains those chunks, groups tweets into fixed-size batches, and skips batches that have already been completed.

### Request Execution and Checkpointing Stage

Batches sent by the filter move into a thread-safe queue (say queueB) where concurrent workers send them to the AI provider. A single writer thread persists results to the CSV and updates the checkpoint JSON. The single thread write avoids multi-threaded write contention and partial-state errors.

The two bounded queues handle backpressure by pausing upstream producers when downstream queues fill up, trading extra coordination and async state complexity for protection against memory spikes and resource exhaustion.

Batch size is a direct trade-off: larger batches reduce request count and help stay within rate limits, at the cost of higher per-request latency and more tokens sent per call. Only successfully written batches are recorded in the checkpoint; any unattempted or failed batches remain unprocessed and can be retried cleanly on a subsequent run.

## Error Handling and Retries

Provider failures are split into **retryable**, **batch-specific**, and **fatal** errors:
- **Retryable errors** use bounded exponential backoff. If retries are exhausted, the batch is marked as failed so the rest of the run can continue.
- **Fatal errors** (such as invalid api keys or configuration issues) halt the pipeline immediately rather than wasting retries.

## Output Format

The output includes both tweets recommended for deletion and tweets recommended to keep. The tool only produces recommendations. The final decision to delete anything rests with the user, preserving a full audit trail. Steps for filtering the output are documented in the [README](README.md). Because results are written as worker responses finish, rather than in strict archive order, row ordering in the output CSV is not guaranteed to match the original archive.
