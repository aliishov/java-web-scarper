package org.raul.javawebscarper.scraper.adapter.tiktok;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.raul.javawebscarper.browser.BrowserEngineProperties;
import org.raul.javawebscarper.browser.BrowserSession;
import org.raul.javawebscarper.browser.BrowserSessionOptions;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TikTokSessionResolverTests {

	@TempDir
	private Path tempDir;

	@Test
	void autoUsesValidConfiguredState() throws Exception {
		TikTokProperties properties = properties();
		properties.setAuthStatePath(validState().toString());
		TestResolver resolver = new TestResolver(properties, TikTokAuthenticationStatus.AUTHENTICATED);

		try (TikTokResolvedSession session = resolver.resolve()) {
			assertThat(session.authenticationStatus()).isEqualTo(TikTokAuthenticationStatus.AUTHENTICATED);
			assertThat(session.storageStateUsed()).isTrue();
			assertThat(session.anonymousFallbackUsed()).isFalse();
		}
		assertThat(resolver.options).hasSize(1);
		assertThat(resolver.options.getFirst().storageStatePath()).isNotNull();
	}

	@Test
	void autoWithoutStateFallsBackToAnonymousAccess() {
		TikTokProperties properties = properties();
		TestResolver resolver = new TestResolver(properties, TikTokAuthenticationStatus.ANONYMOUS_ACCESS);

		try (TikTokResolvedSession session = resolver.resolve()) {
			assertThat(session.authenticationStatus()).isEqualTo(TikTokAuthenticationStatus.ANONYMOUS_ACCESS);
			assertThat(session.storageStateUsed()).isFalse();
			assertThat(session.anonymousFallbackUsed()).isFalse();
		}
		assertThat(resolver.options.getFirst().storageStatePath()).isNull();
	}

	@Test
	void autoExpiredStateFallsBackToAnonymousWhenAllowed() throws Exception {
		TikTokProperties properties = properties();
		properties.setAuthStatePath(validState().toString());
		properties.setAllowAnonymousFallback(true);
		TestResolver resolver = new TestResolver(
				properties,
				TikTokAuthenticationStatus.AUTH_REQUIRED,
				TikTokAuthenticationStatus.ANONYMOUS_ACCESS
		);

		try (TikTokResolvedSession session = resolver.resolve()) {
			assertThat(session.authenticationStatus()).isEqualTo(TikTokAuthenticationStatus.ANONYMOUS_ACCESS);
			assertThat(session.storageStateUsed()).isFalse();
			assertThat(session.anonymousFallbackUsed()).isTrue();
		}
		assertThat(resolver.options).hasSize(2);
		assertThat(resolver.options.get(0).storageStatePath()).isNotNull();
		assertThat(resolver.options.get(1).storageStatePath()).isNull();
		assertThat(resolver.sessions.getFirst().closed).isTrue();
	}

	@Test
	void autoExpiredStateFailsWhenAuthenticationRequired() throws Exception {
		TikTokProperties properties = properties();
		properties.setAuthStatePath(validState().toString());
		properties.setAuthenticationRequired(true);
		TestResolver resolver = new TestResolver(properties, TikTokAuthenticationStatus.AUTH_REQUIRED);

		assertThatThrownBy(resolver::resolve)
				.isInstanceOf(TikTokAuthenticationException.class)
				.extracting("status")
				.isEqualTo(TikTokAuthenticationStatus.AUTH_STATE_EXPIRED);
	}

	@Test
	void authenticatedModeRequiresStateAndRejectsExpiredState() throws Exception {
		TikTokProperties missing = properties();
		missing.setAuthenticationMode(TikTokAuthenticationMode.AUTHENTICATED);
		assertThatThrownBy(() -> new TestResolver(missing, TikTokAuthenticationStatus.AUTHENTICATED).resolve())
				.isInstanceOf(TikTokAuthenticationException.class)
				.extracting("status")
				.isEqualTo(TikTokAuthenticationStatus.AUTH_STATE_MISSING);

		TikTokProperties expired = properties();
		expired.setAuthenticationMode(TikTokAuthenticationMode.AUTHENTICATED);
		expired.setAuthStatePath(validState().toString());
		assertThatThrownBy(() -> new TestResolver(expired, TikTokAuthenticationStatus.AUTH_REQUIRED).resolve())
				.isInstanceOf(TikTokAuthenticationException.class)
				.extracting("status")
				.isEqualTo(TikTokAuthenticationStatus.AUTH_STATE_EXPIRED);
	}

	@Test
	void anonymousModeAllowsAnonymousAndFailsForBlockers() {
		TikTokProperties properties = properties();
		properties.setAuthenticationMode(TikTokAuthenticationMode.ANONYMOUS);
		TestResolver resolver = new TestResolver(properties, TikTokAuthenticationStatus.ANONYMOUS_ACCESS);

		try (TikTokResolvedSession session = resolver.resolve()) {
			assertThat(session.authenticationStatus()).isEqualTo(TikTokAuthenticationStatus.ANONYMOUS_ACCESS);
			assertThat(session.storageStateUsed()).isFalse();
		}

		TestResolver blocked = new TestResolver(properties, TikTokAuthenticationStatus.LOGIN_MODAL_BLOCKING);
		assertThatThrownBy(blocked::resolve)
				.isInstanceOf(TikTokAuthenticationException.class)
				.extracting("status")
				.isEqualTo(TikTokAuthenticationStatus.LOGIN_MODAL_BLOCKING);
	}

	@Test
	void captchaAndVerificationAreFailures() throws Exception {
		TikTokProperties properties = properties();
		properties.setAuthenticationMode(TikTokAuthenticationMode.AUTHENTICATED);
		properties.setAuthStatePath(validState().toString());

		assertThatThrownBy(() -> new TestResolver(properties, TikTokAuthenticationStatus.CAPTCHA_REQUIRED).resolve())
				.isInstanceOf(TikTokAuthenticationException.class)
				.extracting("status")
				.isEqualTo(TikTokAuthenticationStatus.CAPTCHA_REQUIRED);
		assertThatThrownBy(() -> new TestResolver(properties, TikTokAuthenticationStatus.VERIFICATION_REQUIRED).resolve())
				.isInstanceOf(TikTokAuthenticationException.class)
				.extracting("status")
				.isEqualTo(TikTokAuthenticationStatus.VERIFICATION_REQUIRED);
	}

	private TikTokProperties properties() {
		TikTokProperties properties = new TikTokProperties();
		properties.setAuthVerificationUrl("https://www.tiktok.com/");
		return properties;
	}

	private Path validState() throws Exception {
		Path path = tempDir.resolve("tiktok-storage-state.json");
		Files.writeString(path, """
				{"cookies":[],"origins":[]}
				""");
		return path;
	}

	private static class TestResolver extends TikTokSessionResolver {

		private final ArrayDeque<TikTokAuthenticationStatus> statuses;
		private final List<BrowserSessionOptions> options = new ArrayList<>();
		private final List<FakeBrowserSession> sessions = new ArrayList<>();

		TestResolver(TikTokProperties properties, TikTokAuthenticationStatus... statuses) {
			super(null, properties, new TikTokAuthenticationVerifier());
			this.statuses = new ArrayDeque<>(List.of(statuses));
		}

		@Override
		protected BrowserSession createSession(BrowserSessionOptions options) {
			this.options.add(options);
			FakeBrowserSession session = new FakeBrowserSession();
			sessions.add(session);
			return session;
		}

		@Override
		protected TikTokAuthenticationStatus verifySession(BrowserSession session) {
			return statuses.removeFirst();
		}
	}

	private static class FakeBrowserSession extends BrowserSession {

		private boolean closed;

		FakeBrowserSession() {
			super(null, null, new BrowserEngineProperties());
		}

		@Override
		public void close() {
			closed = true;
		}
	}
}
