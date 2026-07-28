package org.raul.javawebscarper.auth.handler;

import org.raul.javawebscarper.auth.SocialAuthProperties;
import org.raul.javawebscarper.auth.SocialAuthStateValidator;
import org.raul.javawebscarper.auth.SocialPlatform;
import org.raul.javawebscarper.browser.BrowserEngine;
import org.springframework.stereotype.Component;

@Component
public class XSocialLoginHandler extends AbstractSocialLoginHandler {
	public XSocialLoginHandler(BrowserEngine engine, SocialAuthProperties properties, SocialAuthStateValidator validator) {
		super(engine, properties, validator);
	}
	public SocialPlatform platform() { return SocialPlatform.X; }
	protected String loginSelector() { return "input[autocomplete='username'], input[name='text']"; }
	protected String passwordSelector() { return "input[type='password'], input[name='password']"; }
	protected String submitSelector() { return "button:has-text('Log in'), div[role='button']:has-text('Log in')"; }
}
