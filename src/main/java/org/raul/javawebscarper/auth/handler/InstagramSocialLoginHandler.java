package org.raul.javawebscarper.auth.handler;

import org.raul.javawebscarper.auth.SocialAuthProperties;
import org.raul.javawebscarper.auth.SocialAuthStateValidator;
import org.raul.javawebscarper.auth.SocialPlatform;
import org.raul.javawebscarper.browser.BrowserEngine;
import org.springframework.stereotype.Component;

@Component
public class InstagramSocialLoginHandler extends AbstractSocialLoginHandler {
	public InstagramSocialLoginHandler(BrowserEngine engine, SocialAuthProperties properties, SocialAuthStateValidator validator) {
		super(engine, properties, validator);
	}
	public SocialPlatform platform() { return SocialPlatform.INSTAGRAM; }
	protected String loginSelector() { return "input[name='username']"; }
	protected String passwordSelector() { return "input[name='password'], input[type='password']"; }
	protected String submitSelector() { return "button[type='submit']"; }
}
