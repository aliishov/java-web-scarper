package org.raul.javawebscarper.browser;

public class BrowserTimeoutException extends BrowserEngineException {

	public BrowserTimeoutException(String message) {
		super(message);
	}

	public BrowserTimeoutException(String message, Throwable cause) {
		super(message, cause);
	}
}
