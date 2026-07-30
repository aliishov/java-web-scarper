package org.raul.javawebscarper.config;

import jakarta.servlet.ServletException;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class ApiRequestLoggingFilterTests {

	private final ApiRequestLoggingFilter filter = new ApiRequestLoggingFilter();

	@Test
	void keepsSafeRequestIdAndReturnsItInResponse() throws ServletException, IOException {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/posts");
		request.addHeader(ApiRequestLoggingFilter.REQUEST_ID_HEADER, "client-request-42");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, (servletRequest, servletResponse) ->
				assertThat(MDC.get(ApiRequestLoggingFilter.REQUEST_ID_MDC_KEY))
						.isEqualTo("client-request-42"));

		assertThat(response.getHeader(ApiRequestLoggingFilter.REQUEST_ID_HEADER))
				.isEqualTo("client-request-42");
		assertThat(MDC.get(ApiRequestLoggingFilter.REQUEST_ID_MDC_KEY)).isNull();
	}

	@Test
	void replacesUnsafeRequestId() throws ServletException, IOException {
		MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/scrape-jobs");
		request.addHeader(ApiRequestLoggingFilter.REQUEST_ID_HEADER, "unsafe\nrequest-id");
		MockHttpServletResponse response = new MockHttpServletResponse();

		filter.doFilter(request, response, (servletRequest, servletResponse) -> {
		});

		assertThat(response.getHeader(ApiRequestLoggingFilter.REQUEST_ID_HEADER))
				.matches("[0-9a-f-]{36}")
				.doesNotContain("\n");
	}
}
