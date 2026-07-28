package org.raul.javawebscarper.auth;

import lombok.extern.slf4j.Slf4j;
import org.raul.javawebscarper.auth.handler.SocialLoginHandler;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class SocialAuthBootstrapService {
	private final SocialAuthProperties properties;
	private final SocialAuthStateValidator validator;
	private final SocialAuthStateService stateService;
	private final Map<SocialPlatform, SocialLoginHandler> handlers = new EnumMap<>(SocialPlatform.class);

	public SocialAuthBootstrapService(SocialAuthProperties properties, SocialAuthStateValidator validator,
			SocialAuthStateService stateService, List<SocialLoginHandler> handlers) {
		this.properties = properties;
		this.validator = validator;
		this.stateService = stateService;
		handlers.forEach(handler -> this.handlers.put(handler.platform(), handler));
	}

	@EventListener(ApplicationReadyEvent.class)
	public void bootstrap() {
		if (!properties.isBootstrapEnabled()) {
			log.info("Social authentication bootstrap is disabled");
			return;
		}
		boolean failed = false;
		for (SocialPlatform platform : SocialPlatform.values()) {
			SocialAuthAccountProperties account = properties.account(platform);
			SocialAuthResult result;
			try {
				result = validator.validate(platform);
				if (!result.isValid() && account.isEnabled()) {
					SocialLoginHandler handler = handlers.get(platform);
					result = handler == null
							? SocialAuthResult.of(platform, AuthStateStatus.LOGIN_FAILED, "No login handler is registered")
							: handler.loginAndSaveState(account);
				}
			} catch (RuntimeException exception) {
				result = SocialAuthResult.of(platform, AuthStateStatus.LOGIN_FAILED, "Unexpected authentication bootstrap failure");
			}
			stateService.update(result);
			failed |= account.isAuthenticationRequired() && !result.isValid();
			log.info("Social authentication bootstrap result: platform={}, status={}, message={}",
					platform, result.status(), result.message());
		}
		if (failed && properties.isFailStartupIfAuthFails()) {
			throw new IllegalStateException("Required social authentication could not be established");
		}
	}
}
