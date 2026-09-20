# Tweet Audit

The first project in [Ben X's backend engineering path](https://github.com/benx421/backend-engineer-path). This CLI is a tool used to analyze your X tweets based on a set of criteria you define, and generates a list of tweet URLs marked for deletion.

## Table of Contents

- [Things Worth Noting](#things-worth-noting)
- [Prerequisites](#prerequisites)
- [Installations](#installations)
- [Configuration](#configuration)
- [Usage Instructions](#usage-instructions)
- [Sorting Results by Batch](#sorting-results-by-batch)
- [Roadmap](#roadmap)

## Things Worth Noting

Before you go into reading how to use this tool, I want to clarify some things:

- Defining number of `likes`, `comments`, or `reposts` does not affect the evaulation results, as tweets are judged based on the topic/content of the tweet rather than the engagement data.
- This tool does not automate the deletion process. You still have to manually go through your tweets to decide what will be kept and what will be deleted. You can always use an automation tool if you have one.
- This tool recommends both tweets to keep, and delete. The file would have a long list of tweets, so to help you filter to just those to delete/keep, I have written the steps out for you [here](#filtering-results).

Info on how to use the tool has been written below:

## Prerequisites
These are the things you need to run this software application:
- [Java 11](https://www.oracle.com/java/technologies/javase-jdk11-downloads.html)
- [Maven 3.9+](https://maven.apache.org/download.cgi)
- Download your X archive from Settings → Your Account → Download an archive of your data (takes 24-48 hours)
- Get a Gemini Api Key from [Google Ai Studio](https://aistudio.google.com/app/apikey)
- Create a file named `criteria.json` and define your criteria there

## Installations
Clone the repo from github:
```bash
git clone https://github.com/MrLuhwani/tweet-audit.git
cd tweet-audit
mvn clean install
```
You can also download the project zip from [my github](https://github.com/MrLuhwani/tweet-audit)

## Usage Instructions

- Create a folder called `data` and place your `tweets.js` file from your X archive zip.
- Create a `criteria.json` file to define what tweets should be flagged (see [config.example.json](config.example.json)). If you don't provide a criteria file, the tool defaults to the config.example.json criteria.
- Insert you api key in the terminal

```bash
$env:GEMINI_API_KEY='your-api-key-here'
```

Run the app

```bash
mvn compile exec:java
```

After the first time you run the tool, on subsequent runs, you may skip the `compile` keyword, as the source code has not been changed, so there is no need to recompile your code again.

### Other Implementation Notes

- Tweets are processed in batches of 15 tweets. The batch size is not configurable yet.
- The tool uses `gemini-3.5-flash-lite` internally. The model choice is not configurable for now.
- Analysis results are created in `output\output.csv`.
- If the tool closes for any reason, the CLI creates a `checkpoint.json` in the `output` folder.
- The `checkpoint` represents where the tool has reached in the processing, both for failed, and for successful tweet batches.

```json
{"successfulBatches":[1,2,3,4,5,6,10,11,12,13,14,15,16],"failedBatches":[7,8,9],"lastProcessedBatch":16,"lastProcessedTweet":"20686821481932295099","empty":false}
```

- When the tool is reloaded, it checks the checkpoint file, and continues from where it stopped
- If for any reason, a batch fails, a `failedBatches.jsonl` is created in the output folder to identify batches that where not processed

```json
{"batchNumber":12,"tweets":[{"id":"1874645171535257831","text":"RT @_Tech…"},{"id":"2074644957747450308","text":"RT @bISHAMON Wow!…"}]}
{"batchNumber":26,"tweets":[{"id":"1874645171535234831","text":"We really…"},{"id":"2074644957747450308","text":"Emphasis…"}]}
{"batchNumber":37,"tweets":[{"id":"1874645171535234831","text":"Sentiment is not…"},{"id":"2074644957747450308","text":"Smirking…"}]}
```

- On the next app run, the tool firstly retries the failed batches, before continuing with other unprocessed batches

### Sorting Results by Batch

During a normal run, results are written in batch order. When a run retries failed batches, those retry results are appended as they finish, so `output/output.csv` may not be sorted by `batch_number`.

To sort the file in Microsoft Excel:

1. Open `output/output.csv` in Excel.
2. Select the full table, including the header row.
3. On the `Data` tab, choose `Sort`.
4. Select `batch_number` as the sort column and choose `Smallest to Largest`.

Keep the header row enabled when prompted so that `batch_number` is treated as a column name. If you use another spreadsheet program, sort the complete CSV table by the numeric `batch_number` column rather than sorting only one column.

An example of how the output folder looks like
```csv
batch_number,tweet_link,decision,reason
1,https://x.com/i/status/1111111111111111111,KEEP,"Polite and casual conversation, complies with all criteria."
1,https://x.com/i/status/1223456765434565643,KEEP,Normal bug report / product feedback tweet.
2,https://x.com/i/status/1236464576879898865,DELETE,"Mentions Web3, which is included in topics_to_exclude."
3,https://x.com/i/status/2838488457757477382,KEEP,Harmless personal thought.
```

## Filtering Results

Here are the steps to filter the results of the csv in `Microsoft Excel`.

- Open the csv in Excel
- In the `Home` tab, at the extreme right, you will see a `Sort and Filter` option.
- If you do not find that option, go to the `Data` tab, and click on `Filter`.
- A dropdown appears on the csv heading
- On the `decision` cell, click either `KEEP`, or `DELETE`, based on your choice.

## Roadmap

These are other features I plan to implement:

- Add appropriate tests for the project
- Add a simple loading animation to improve visual appeal
- Add model configuration options
- Add options to allow or remove retweets
- Add a way to type out a criteria and determine the specifications based on the typed out text
- Upgrade the code from Java 11 to 21
- Add docker support
- Add CI/CD
