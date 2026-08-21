# Tweet Audit

The first project in [Ben X's backend engineering path](https://github.com/benx421/backend-engineer-path). This CLI is a tool used to analyze your X tweets based on a set of criteria you define, and generates a list of tweet URLs marked for deletion.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Installations](#installations)
- [Configuration](#configuration)
- [Usage Instructions](#usage-instructions)
- [Roadmap](#roadmap)

## Prerequisites
These are the things you need to run this software application:
- [Java 21](https://www.oracle.com/java/technologies/javase-jdk21-downloads.html)
- [Maven 3.9+](https://maven.apache.org/download.cgi)
- Download your X archive from Settings → Your Account → Download an archive of your data (takes 24-48 hours)
- Get a *free* Gemini Api Key from [Google Ai Studio](https://aistudio.google.com/app/apikey)
- Create a file named `criteria.json` and define your criteria there

## Installations
Clone the repo from github:
```bash
git clone https://github.com/MrLuhwani/tweet-audit.git
cd tweet-audit
mvn clean install
```
You can also download the project zip from [my github](https://github.com/MrLuhwani/tweet-audit)

## Configuration
```bash
$env:GEMINI_API_KEY='your-api-key-here'
```

Create a `criteria.json` file to define what tweets should be flagged (see [config.example.json](config.example.json)). If you don't provide a criteria file, the tool defaults to the config.example.json criteria.

## Usage Instructions

- Create a folder called `data` and place your `tweets.js` file from your X archive zip.
- Define the criteria to use for evaluating your tweets
- Insert you api key in the terminal
```bash
$env:GEMINI_API_KEY='your-api-key-here'
```
Run the app
```bash
mvn compile exec:java
```

### Other Implementation Notes

- Tweets are processed in batches of tweets. The size of the batches aren't configurable.
- The tool uses `gemini-3.5-flash-lite` internally. The model choice is not configurable for now.
- Analysis results are created in `output\output.csv`.
- If the tool closes for any reason, the CLI creates a `checkpoint.json` in the `output` folder.

```json
{
    "lastCompletedBatchIndex":0,
    "lastTweetId":"tweet-id"
}
```

- When the tool is reloaded, it checks the checkpoint file, and continues from where it stopped

An example of how the output folder looks like
```csv
"tweet_link","decision","reason"
https://x.com/i/status/1111111111111111111,KEEP,"Polite and casual conversation, complies with all criteria."
https://x.com/i/status/1223456765434565643,KEEP,Normal bug report / product feedback tweet.
https://x.com/i/status/1236464576879898865,DELETE,"Mentions Web3, which is included in topics_to_exclude."
https://x.com/i/status/2838488457757477382,KEEP,Harmless personal thought.
```


## Roadmap

The current implementation is an MVP. These are other features I plan to implement:

- Move from sequential to better concurrent implementations
- Add better error handling strategies and logging
- Add appropriate tests for the project
- Add a simple loading animation to improve visual appeal
- Add docker support
- Add CI/CD
- Add model configuration options
- Add options to allow or remove retweets
- Add a way to type out a criteria and determine the specifications based on the typed out text