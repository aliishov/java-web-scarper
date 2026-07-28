package org.raul.javawebscarper.auth.handler;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import lombok.RequiredArgsConstructor;
import org.raul.javawebscarper.auth.AuthStateStatus;
import org.raul.javawebscarper.auth.SocialAuthAccountProperties;
import org.raul.javawebscarper.auth.SocialAuthProperties;
import org.raul.javawebscarper.auth.SocialAuthResult;
import org.raul.javawebscarper.auth.SocialAuthStateValidator;
import org.raul.javawebscarper.auth.SocialPlatform;
import org.raul.javawebscarper.browser.BrowserEngine;

import java.nio.file.Files;
import java.nio.file.Path;

@RequiredArgsConstructor
abstract class AbstractSocialLoginHandler implements SocialLoginHandler {
	private final BrowserEngine browserEngine;
	private final SocialAuthProperties authProperties;
	private final SocialAuthStateValidator validator;

	protected abstract String loginSelector();
	protected abstract String passwordSelector();
	protected abstract String submitSelector();

	@Override
	public SocialAuthResult loginAndSaveState(SocialAuthAccountProperties account) {
		if (blank(account.getLogin()) || blank(account.getPassword())) {
			return result(AuthStateStatus.AUTH_REQUIRED, "Credentials are not configured");
		}
		Path statePath = authProperties.statePath(platform());
		try {
			Files.createDirectories(statePath.getParent());
			Browser browser = browserEngine.getBrowser();
			try (BrowserContext context = browser.newContext()) {
				Page page = context.newPage();
				page.navigate(account.getLoginUrl());
				fill(page, loginSelector(), account.getLogin());
				fill(page, passwordSelector(), account.getPassword());
				Locator submit = page.locator(submitSelector()).first();
				if (submit.count() > 0) submit.click(); else page.locator(passwordSelector()).first().press("Enter");
				page.waitForTimeout(3_000);
				AuthStateStatus immediate = challengeStatus(page);
				if (immediate == AuthStateStatus.CHALLENGE_REQUIRED) {
					return result(immediate, "Platform requires CAPTCHA, 2FA, checkpoint, or verification");
				}
				context.storageState(new BrowserContext.StorageStateOptions().setPath(statePath));
			}
			SocialAuthResult validation = validator.validate(platform());
			return validation.status() == AuthStateStatus.VALID
					? validation
					: result(validation.status() == AuthStateStatus.CHALLENGE_REQUIRED
							? AuthStateStatus.CHALLENGE_REQUIRED : AuthStateStatus.LOGIN_FAILED,
							"Automatic login did not produce a valid authentication state");
		} catch (RuntimeException | java.io.IOException exception) {
			return result(AuthStateStatus.LOGIN_FAILED, "Automatic login failed");
		}
	}

	protected AuthStateStatus challengeStatus(Page page) {
		String content = page.content().toLowerCase();
		return content.contains("captcha") || content.contains("two-factor") || content.contains("verification code")
				|| content.contains("checkpoint") || content.contains("challenge")
				? AuthStateStatus.CHALLENGE_REQUIRED : AuthStateStatus.AUTH_REQUIRED;
	}

	private void fill(Page page, String selector, String value) {
		Locator locator = page.locator(selector).first();
		locator.waitFor(new Locator.WaitForOptions().setTimeout(30_000));
		locator.fill(value);
	}

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private SocialAuthResult result(AuthStateStatus status, String message) {
		return SocialAuthResult.of(platform(), status, message);
	}
}
