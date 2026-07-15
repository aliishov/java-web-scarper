package org.raul.javawebscarper.scraper.adapter.xcom;

import lombok.extern.slf4j.Slf4j;
import org.raul.javawebscarper.browser.BrowserEngineException;
import org.raul.javawebscarper.browser.BrowserPage;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class XAuthenticationVerifier {

	public XAuthenticationStatus verify(BrowserPage page) {
		try {
			XAuthenticationStatus status = XAuthenticationPageInspector.inspect(page.url(), page.content());
			log.debug("X authentication verification status={}", status);
			return status;
		} catch (BrowserEngineException exception) {
			log.warn("X authentication verification failed: {}", exception.getMessage());
			return XAuthenticationStatus.UNKNOWN;
		}
	}

	public boolean isAuthenticated(BrowserPage page) {
		return verify(page) == XAuthenticationStatus.AUTHENTICATED;
	}
}
