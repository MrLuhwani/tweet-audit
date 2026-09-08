package dev.luhwani.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class TweetData {

	private final String id;
	private final String text;

	@JsonCreator
	public TweetData(
			@JsonProperty("id") String id,
			@JsonProperty("text") String text) {
		this.id = id;
		this.text = text;
	}

	@JsonProperty("id")
	public String id() {
		return id;
	}

	@JsonProperty("text")
	public String text() {
		return text;
	}
}
