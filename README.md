# Tweet Audit

The first project in [Ben X's backend engineering path](https://github.com/benx421/backend-engineer-path). This CLI is a tool used to analyze your X tweets based on a set of criteria you define, and generates a list of tweet URLs marked for deletion.

## Table of Contents

- [Things Worth Noting](#things-worth-noting)
- [Prerequisites](#prerequisites)
- [Installations](#installations)
- [Usage Instructions](#usage-instructions)
- [Architecture](#architecture)
- [Roadmap](#roadmap)

## Things Worth Noting

Before you go into reading how to use this tool, I want to clarify some things:

- Defining number of `likes`, `comments`, or `reposts` does not affect the evaulation results, as tweets are judged based on the topic/content of the tweet rather than the engagement data.
- This tool does not automate the deletion process. You still have to manually go through your tweets to decide what will be kept and what will be deleted. You can always use an automation tool if you have one, or if you have a twitter api key.
- This tool recommends both tweets to keep, and delete. The file would have a long list of tweets, so to help you filter to just those to delete/keep, I have written the steps out for you [here](#filtering-results).

Info on how to use the tool has been written below:

## Prerequisites
These are the things you need to run this software application:
- [Java 21](https://www.oracle.com/java/technologies/javase-jdk21-downloads.html)
- [Maven 3.9+](https://maven.apache.org/download.cgi)
- Download your X archive from Settings → Your Account → Download an archive of your data (takes 24-48 hours)
- Get a Gemini Api Key from [Google Ai Studio](https://aistudio.google.com/app/apikey)
- Optionally create a file named `criteria.json` and define your criteria there

## Installations
Clone the repo from github:
```bash
git clone https://github.com/MrLuhwani/tweet-audit.git
cd tweet-audit
mvn clean install
```
You can also download the project zip from [my github](https://github.com/MrLuhwani/tweet-audit)

## Usage Instructions

- Pass your Gemini API key and the path to your X archive using named command-line options.
- You can optionally pass a criteria file's directory. If omitted, the tool uses the packaged [`config.example.json`](src\main\resources\config.example.json) criteria

```json
{
  "criteria": {
    "forbidden_words": [
      ""
    ],
    "topics_to_exclude": [
      "Outdated political opinions",
      "Controversial statements"
    ],
    "tone_requirements": [
      "Professional language only",
      "No personal attacks or insults"
    ],
    "additional_instructions": "Flag any content that could harm professional reputation"
  }
}
```

- It is also optional to pass an output directory. If omitted, an `output` folder is created in the current working directory.
- The output folder contains both `output.csv` and `checkpoint.json`.

Build the fat jar:

```bash
mvn clean package
```

Run the app with the two required named arguments:

```bash
java -jar target/tweet-audit-1.0-SNAPSHOT.jar --api-key your-api-key --tweet-archive path/to/tweets.zip
```

You can also pass any optional argument:

```bash
java -jar target/tweet-audit-1.0-SNAPSHOT.jar --api-key your-api-key --tweet-archive path/to/tweets.zip --criteria path/to/criteria.json --output path/to/output
```

The available options are:

```text
--api-key <key>                  Gemini API key (required)
--tweet-archive <path>           Path to the X archive (required)
--criteria <path>                Optional criteria JSON file
--output <directory>             Optional output directory
```

Also, if any of the file path contains spaces, place it in a single quote when running the app

```bash
java -jar target/tweet-audit-1.0-SNAPSHOT.jar --api-key your-api-key --tweet-archive "path/to/my tweets.zip"
```

## Architecture

- **ai**: classes related to ai model used for evaluation
- **application**: orchestration layer
- **client**: classes for sending http requests to the api used for evaluation
- **crieria**: validatig criteria used for audit
- **error**: Checked exceptions, split based on severity (transient, batch, and fatal)
- **models**: Immutable data classes (TweetBatch, AnalysisResult, TweetData)
- **output**: Writing and checkpointing logic
- **tweetProcessing**: parsing tweets in archive zip

See [TRADEOFFS.md](TRADEOFFS.md) for detailed architectural decisions and design rationale.

### Other Implementation Notes

- Tweets are processed in batches of 60 tweets. The batch size is not configurable yet.
- The tool uses `gemini-3.5-flash-lite` internally. The model choice is not configurable for now.
- Analysis results are created in `output/output.csv` by default, or in the output directory supplied on the command line.
- If the tool closes for any reason, the CLI creates `checkpoint.json` in the same output folder.
- Do not change or delete the output folder until the audit is complete. The CSV and checkpoint must stay together to prevent an audit from being rerun incorrectly.
- The `checkpoint` represents which batch of tweets were successfully processed.

```json
{"successfulBatches":[1,2,3,4,5,6,10,11,12,13,14,15,16]}
```

- When the tool is reloaded, it checks the checkpoint file, and filters out successful batches
- If you wish to go into the source code and edit batch size after first run, to prevent inconsistencies with the checkpoint file, you would have to delete the output folder also.

## Filtering Results

Here are the steps to filter the results of the csv in `Microsoft Excel`.

- Open the csv in Excel
- In the `Home` tab, at the extreme right, you will see a `Sort and Filter` option.
- If you do not find that option, go to the `Data` tab, and click on `Filter`.
- A dropdown appears on the csv heading
- On the `decision` cell, click either `KEEP`, or `DELETE`, based on your choice.

## Roadmap

These are other features I plan to implement:

- Add model configuration options
- Add batch size configuration options
- Add CI/CD
