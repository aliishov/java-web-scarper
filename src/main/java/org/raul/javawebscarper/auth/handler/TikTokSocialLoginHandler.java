package org.raul.javawebscarper.auth.handler;

import org.raul.javawebscarper.auth.SocialAuthProperties;
import org.raul.javawebscarper.auth.SocialAuthStateValidator;
import org.raul.javawebscarper.auth.SocialPlatform;
import org.raul.javawebscarper.browser.BrowserEngine;
import org.springframework.stereotype.Component;

@Component
public class TikTokSocialLoginHandler extends AbstractSocialLoginHandler {
	public TikTokSocialLoginHandler(BrowserEngine engine, SocialAuthProperties properties, SocialAuthStateValidator validator) {
		super(engine, properties, validator);
	}
	public SocialPlatform platform() { return SocialPlatform.TIKTOK; }
	protected String loginSelector() { return "input[name='username'], input[type='text']"; }
	protected String passwordSelector() { return "input[type='password']"; }
	protected String submitSelector() { return "button[type='submit']"; }
}
