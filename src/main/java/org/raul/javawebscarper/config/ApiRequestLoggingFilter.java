package org.raul.javawebscarper.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ApiRequestLoggingFilter extends OncePerRequestFilter {

	public static final String REQUEST_ID_HEADER = "X-Request-ID";
	public static final String REQUEST_ID_MDC_KEY = "requestId";
	private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,100}");

	@Override
	protected void doFilterInternal(
			HttpServletRequest request,
			HttpServletResponse response,
			FilterChain filterChain
	) throws ServletException, IOException {
		String requestId = resolveRequestId(request.getHeader(REQUEST_ID_HEADER));
		long startedAt = System.nanoTime();
		boolean completed = false;

		MDC.put(REQUEST_ID_MDC_KEY, requestId);
		response.setHeader(REQUEST_ID_HEADER, requestId);
		log.info("API request started: method={}, path={}, remoteAddress={}",
				request.getMethod(), request.getRequestURI(), request.getRemoteAddr());

		try {
			filterChain.doFilter(request, response);
			completed = true;
		} catch (Exception exception) {
			log.error(
					"API request failed: method={}, path={}, durationMs={}",
					request.getMethod(),
					request.getRequestURI(),
					elapsedMillis(startedAt),
					exception
			);
			throw exception;
		} finally {
			if (completed) {
				log.info(
						"API request completed: method={}, path={}, status={}, durationMs={}",
						request.getMethod(),
						request.getRequestURI(),
						response.getStatus(),
						elapsedMillis(startedAt)
				);
			}
			MDC.remove(REQUEST_ID_MDC_KEY);
		}
	}

	private String resolveRequestId(String candidate) {
		if (candidate != null && SAFE_REQUEST_ID.matcher(candidate).matches()) {
			return candidate;
		}
		return UUID.randomUUID().toString();
	}

	private long elapsedMillis(long startedAt) {
		return (System.nanoTime() - startedAt) / 1_000_000;
	}
}
