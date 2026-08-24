package org.raul.javawebscarper.auth.handler;

import org.raul.javawebscarper.auth.SocialAuthProperties;
import org.raul.javawebscarper.auth.SocialAuthStateValidator;
import org.raul.javawebscarper.auth.SocialPlatform;
import org.raul.javawebscarper.browser.BrowserEngine;
import org.springframework.stereotype.Component;
import com.microsoft.playwright.Page;

@Component
public class InstagramSocialLoginHandler extends AbstractSocialLoginHandler {
	public InstagramSocialLoginHandler(BrowserEngine engine, SocialAuthProperties properties, SocialAuthStateValidator validator) {
		super(engine, properties, validator);
	}
	public SocialPlatform platform() { return SocialPlatform.INSTAGRAM; }
	protected String loginSelector() { return "input[name='username']"; }
	protected String passwordSelector() { return "input[name='password'], input[type='password']"; }
	protected String submitSelector() { return "button[type='submit']"; }

	@Override
	protected boolean isReadyForAuthenticationValidation(Page page) {
		// Instagram can keep authenticated-looking layout elements on the login,
		// SMS-code and challenge steps. Saving the state on those intermediate
		// pages closes the interactive window before the user has confirmed 2FA.
		// Only persist the session after Instagram has returned to its home page.
		return page.url().matches("https?://(?:www\\.)?instagram\\.com/?(?:[?#].*)?");
	}
}
