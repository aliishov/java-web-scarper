package org.raul.javawebscarper.auth.handler;

import org.raul.javawebscarper.auth.SocialAuthProperties;
import org.raul.javawebscarper.auth.SocialAuthStateValidator;
import org.raul.javawebscarper.auth.SocialPlatform;
import org.raul.javawebscarper.browser.BrowserEngine;
import org.springframework.stereotype.Component;
import com.microsoft.playwright.Page;

@Component
public class FacebookSocialLoginHandler extends AbstractSocialLoginHandler {
	public FacebookSocialLoginHandler(BrowserEngine engine, SocialAuthProperties properties, SocialAuthStateValidator validator) {
		super(engine, properties, validator);
	}
	public SocialPlatform platform() { return SocialPlatform.FACEBOOK; }
	protected String loginSelector() { return "input[name='email'], input#email"; }
	protected String passwordSelector() { return "input[name='pass'], input[type='password']"; }
	protected String submitSelector() { return "button[name='login'], button[type='submit']"; }

	@Override
	protected boolean isReadyForAuthenticationValidation(Page page) {
		// Keep Facebook's CAPTCHA and credential controls untouched until its
		// login flow has returned to the authenticated home page.
		return page.url().matches("https?://(?:www\\.)?facebook\\.com/?(?:[?#].*)?");
	}
}
