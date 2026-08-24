package dev.luhwani.model;

public final class TweetData {

	private final String id;
	private final String text;

	public TweetData(
		String id,
		String text) {
		this.id = id;
		this.text = text;
	}

	public String id() {
		return id;
	}

	public String text() {
		return text;
	}
}