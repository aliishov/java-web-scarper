package org.raul.javawebscarper.scraper.adapter.tiktok;

public class TikTokAuthenticationException extends RuntimeException {

	private final TikTokAuthenticationStatus status;

	public TikTokAuthenticationException(TikTokAuthenticationStatus status, String message) {
		super(message);
		this.status = status == null ? TikTokAuthenticationStatus.UNKNOWN : status;
	}

	public TikTokAuthenticationStatus status() {
		return status;
	}
}
