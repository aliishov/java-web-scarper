package org.raul.javawebscarper.scraper.adapter.tiktok;

import org.raul.javawebscarper.browser.BrowserSession;

public record TikTokResolvedSession(
		BrowserSession session,
		TikTokAuthenticationStatus authenticationStatus,
		boolean storageStateUsed,
		boolean anonymousFallbackUsed
) implements AutoCloseable {

	@Override
	public void close() {
		if (session != null) {
			session.close();
		}
	}
}
