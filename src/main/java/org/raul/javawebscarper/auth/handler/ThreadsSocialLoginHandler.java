package org.raul.javawebscarper.auth.handler;

import org.raul.javawebscarper.auth.SocialAuthProperties;
import org.raul.javawebscarper.auth.SocialAuthStateValidator;
import org.raul.javawebscarper.auth.SocialPlatform;
import org.raul.javawebscarper.browser.BrowserEngine;
import org.springframework.stereotype.Component;

@Component
public class ThreadsSocialLoginHandler extends AbstractSocialLoginHandler {
	public ThreadsSocialLoginHandler(BrowserEngine engine, SocialAuthProperties properties, SocialAuthStateValidator validator) {
		super(engine, properties, validator);
	}
	public SocialPlatform platform() { return SocialPlatform.THREADS; }
	protected String loginSelector() { return "input[name='username'], input[autocomplete='username']"; }
	protected String passwordSelector() { return "input[name='password'], input[type='password']"; }
	protected String submitSelector() { return "button[type='submit'], div[role='button']:has-text('Log in')"; }
}
