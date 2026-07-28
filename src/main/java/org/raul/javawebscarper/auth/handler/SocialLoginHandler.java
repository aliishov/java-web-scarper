package org.raul.javawebscarper.auth.handler;

import org.raul.javawebscarper.auth.SocialAuthAccountProperties;
import org.raul.javawebscarper.auth.SocialAuthResult;
import org.raul.javawebscarper.auth.SocialPlatform;

public interface SocialLoginHandler {
	SocialPlatform platform();
	SocialAuthResult loginAndSaveState(SocialAuthAccountProperties properties);
}
