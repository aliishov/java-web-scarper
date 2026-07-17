package org.raul.javawebscarper.scraper.adapter.facebook;

import lombok.extern.slf4j.Slf4j;
import org.raul.javawebscarper.browser.BrowserEngineException;
import org.raul.javawebscarper.browser.BrowserPage;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class FacebookAuthenticationVerifier {

	public FacebookAuthenticationStatus verify(BrowserPage page) {
		try {
			FacebookAuthenticationStatus status = FacebookAuthenticationPageInspector.inspect(page.url(), page.content());
			log.debug("Facebook authentication verification status={}", status);
			return status;
		} catch (BrowserEngineException exception) {
			log.warn("Facebook authentication verification failed: {}", exception.getMessage());
			return FacebookAuthenticationStatus.UNKNOWN;
		}
	}

	public boolean isAuthenticated(BrowserPage page) {
		return verify(page) == FacebookAuthenticationStatus.AUTHENTICATED;
	}
}
