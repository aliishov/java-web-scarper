package org.raul.javawebscarper.auth.handler;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.WaitUntilState;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.raul.javawebscarper.auth.AuthStateStatus;
import org.raul.javawebscarper.auth.SocialAuthAccountProperties;
import org.raul.javawebscarper.auth.SocialAuthCredentials;
import org.raul.javawebscarper.auth.SocialAuthProperties;
import org.raul.javawebscarper.auth.SocialAuthResult;
import org.raul.javawebscarper.auth.SocialAuthStateValidator;
import org.raul.javawebscarper.auth.SocialPlatform;
import org.raul.javawebscarper.browser.BrowserEngine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@RequiredArgsConstructor
@Slf4j
abstract class AbstractSocialLoginHandler implements SocialLoginHandler {
	private final BrowserEngine browserEngine;
	private final SocialAuthProperties authProperties;
	private final SocialAuthStateValidator validator;

	protected abstract String loginSelector();
	protected abstract String passwordSelector();
	protected abstract String submitSelector();

	/**
	 * Returns whether the current interactive page is far enough through the
	 * platform login flow for its authentication state to be inspected.
	 */
	protected boolean isReadyForAuthenticationValidation(Page page) {
		return true;
	}

	@Override
	public SocialAuthResult loginAndSaveState(SocialAuthAccountProperties account, SocialAuthCredentials credentials) {
		Path statePath = authProperties.statePath(platform());
		Path candidateStatePath = candidateStatePath(statePath);
		String stage = "opening the login page";
		try {
			Files.createDirectories(statePath.getParent());
			try (BrowserContext context = browserEngine.newInteractiveContext()) {
				Page page = context.newPage();
				openLoginPage(page, account.getLoginUrl());
				log.info("Interactive social login is ready: platform={}. Complete login in the opened browser window.",
						platform());
				stage = "waiting for interactive verification";
				SocialAuthResult validation = waitForValidSession(context, candidateStatePath);
				if (!validation.isValid()) {
					return validation;
				}
				stage = "activating the verified browser session";
				moveCandidateState(candidateStatePath, statePath);
				return validation;
			}
		} catch (RuntimeException | java.io.IOException exception) {
			log.warn("Social login did not complete: platform={}, stage={}, exception={}",
					platform(), stage, exception.getClass().getSimpleName(), exception);
			return result(AuthStateStatus.LOGIN_FAILED,
					"Automatic login failed while " + stage + " (" + exception.getClass().getSimpleName() + ")");
		} finally {
			try {
				Files.deleteIfExists(candidateStatePath);
			} catch (java.io.IOException exception) {
				log.debug("Could not remove temporary social authentication state: platform={}", platform());
			}
		}
	}

	private void openLoginPage(Page page, String loginUrl) {
		try {
			page.navigate(loginUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
		} catch (PlaywrightException exception) {
			// Some social sites keep loading tracking resources indefinitely. Leave the visible
			// page open so the administrator can still complete login manually.
			log.info("Login page navigation did not finish promptly: platform={}, url={}", platform(), loginUrl);
		}
	}

	private SocialAuthResult waitForValidSession(BrowserContext context, Path candidateStatePath) {
		long timeoutMs = authProperties.getInteractiveLoginTimeoutMs();
		long deadline = System.currentTimeMillis() + timeoutMs;
		SocialAuthResult latest = result(AuthStateStatus.AUTH_REQUIRED, "Waiting for platform login to complete");
		do {
			// OAuth providers such as Threads can complete authentication in a
			// replacement/popup page. The most recently opened page represents the
			// active step, while the original login page can remain behind it.
			Page loginPage = context.pages().getLast();
			try {
				if (isReadyForAuthenticationValidation(loginPage)) {
					context.storageState(new BrowserContext.StorageStateOptions().setPath(candidateStatePath));
					latest = validator.inspectCurrentPage(platform(), loginPage.url(), loginPage.content());
					if (latest.status() == AuthStateStatus.VALID) {
						return latest;
					}
				}
			} catch (PlaywrightException exception) {
				// Social networks commonly redirect several times after credential or
				// challenge completion. Page.content() is unavailable during that
				// short transition, but the interactive login must remain open.
				log.debug("Interactive social login page is still navigating: platform={}", platform());
			}
			if (System.currentTimeMillis() < deadline) {
				loginPage.waitForTimeout(1_000);
			}
		} while (System.currentTimeMillis() < deadline);

		AuthStateStatus status = latest.status() == AuthStateStatus.CHALLENGE_REQUIRED
				? AuthStateStatus.CHALLENGE_REQUIRED : AuthStateStatus.LOGIN_FAILED;
		return result(status, "Login was not completed within " + timeoutMs / 1_000 + " seconds");
	}

	private Path candidateStatePath(Path statePath) {
		return statePath.resolveSibling("." + statePath.getFileName() + "." + UUID.randomUUID() + ".tmp.json");
	}

	private void moveCandidateState(Path candidateStatePath, Path statePath) throws java.io.IOException {
		try {
			Files.move(candidateStatePath, statePath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
		} catch (java.nio.file.AtomicMoveNotSupportedException exception) {
			Files.move(candidateStatePath, statePath, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	protected void performLogin(Page page, SocialAuthCredentials credentials) {
		fill(page, loginSelector(), credentials.login());
		fill(page, passwordSelector(), credentials.password());
		clickOrSubmit(page);
	}

	protected void clickOrSubmit(Page page) {
		Locator submit = page.locator(submitSelector()).first();
		if (submit.count() > 0) submit.click(); else page.locator(passwordSelector()).first().press("Enter");
	}

	protected void fill(Page page, String selector, String value) {
		Locator locator = page.locator(selector).first();
		locator.waitFor(new Locator.WaitForOptions().setTimeout(30_000));
		locator.fill(value);
	}

	private SocialAuthResult result(AuthStateStatus status, String message) {
		return SocialAuthResult.of(platform(), status, message);
	}
}
