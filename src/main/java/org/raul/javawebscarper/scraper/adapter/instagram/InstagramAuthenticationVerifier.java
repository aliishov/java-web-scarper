package org.raul.javawebscarper.scraper.adapter.instagram;

import org.raul.javawebscarper.browser.BrowserPage;
import org.springframework.stereotype.Component;

@Component
public class InstagramAuthenticationVerifier {

	public InstagramAuthenticationStatus verify(BrowserPage page) {
		return InstagramAuthenticationPageInspector.inspect(page.url(), page.content());
	}
}
