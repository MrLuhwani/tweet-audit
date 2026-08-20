# Technical Tradeoffs

## Language Choice: Java

I chose Java because that is the language I want to major in. Later down the line, I can always 
pivot to another one. The type safety catches errors before runtime, and Java features such as 
thread pools, ExecutorServices, and virtual threads help to make concurrency easier. 

Te only major downside to me is that it requires a lot of boilerplate code. Although there are features like records, and libraries like lombok, which help to reduce code, it is still verbose 
when compared to a  language like Python

## Architecture: Batch Processing with Checkpoints

The tool processes tweets in fixed-sized batches of 30. For now it is not configurable. Each batch is ***sequentially*** sent to Gemini 
Api, and the response is parsed and written to the output folder. After each write, progress is saved after each batch to a checkpoint.
json file. This lets the tool be re-run without having to start from the beginning.

The current implementation is not the fastest as it takes ~5mins to process ~3000 tweets. Async
implementation will be used in the future to improve processing speed.

## Error Handling Strategy

There are no major errror handling strategies implemented now. I planned to make this stage as a template for further improvement.

But, what is currently implemented fails immediately an error occurs. This is so that we do not accidentally update the checkpoint when
some tweets haven't been processed. The tool can then be rerun later, and we still use the correct checkpoint.

Better error handling will be implemented for transient failures soon.

## Output Format

I wrote both tweets to be kept, and those to be deleted. Our tool just generates recommendations, but it is still the users decision to 
make the final say if a tweet should be deleted or not. This helps to keep the full audit trail for the user. A reason column might be
added later to the CSV so as to highlight why a tweet should be kwpt/deleted.
