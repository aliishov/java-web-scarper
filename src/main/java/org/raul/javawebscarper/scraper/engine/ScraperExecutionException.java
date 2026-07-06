package org.raul.javawebscarper.scraper.engine;

public class ScraperExecutionException extends RuntimeException {

	public ScraperExecutionException(String message) {
		super(message);
	}

	public ScraperExecutionException(String message, Throwable cause) {
		super(message, cause);
	}
}
