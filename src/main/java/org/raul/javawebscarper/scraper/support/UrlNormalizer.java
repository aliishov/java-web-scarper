package org.raul.javawebscarper.scraper.support;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

public final class UrlNormalizer {

	private static final Set<String> TRACKING_PARAM_NAMES = Set.of(
			"fbclid",
			"gclid",
			"yclid",
			"mc_cid",
			"mc_eid",
			"igshid"
	);

	private UrlNormalizer() {
	}

	public static String normalize(String url) {
		if (url == null) {
			return null;
		}
		String trimmed = url.trim();
		if (trimmed.isEmpty()) {
			return trimmed;
		}

		URI uri = URI.create(trimmed).normalize();
		try {
			return new URI(
					lowercase(uri.getScheme()),
					uri.getUserInfo(),
					lowercase(uri.getHost()),
					uri.getPort(),
					uri.getPath(),
					uri.getQuery(),
					null
			).toString();
		} catch (URISyntaxException exception) {
			throw new IllegalArgumentException("Invalid URL: %s".formatted(url), exception);
		}
	}

	public static String resolve(String baseUrl, String maybeRelativeUrl) {
		if (maybeRelativeUrl == null || maybeRelativeUrl.isBlank()) {
			return normalize(baseUrl);
		}
		if (baseUrl == null || baseUrl.isBlank()) {
			return normalize(maybeRelativeUrl);
		}
		return normalize(URI.create(baseUrl.trim()).resolve(maybeRelativeUrl.trim()).toString());
	}

	public static String removeTrackingParams(String url) {
		if (url == null || url.isBlank()) {
			return url;
		}
		URI uri = URI.create(url.trim());
		String query = uri.getQuery();
		if (query == null || query.isBlank()) {
			return normalize(url);
		}

		String filteredQuery = Arrays.stream(query.split("&"))
				.filter(parameter -> !isTrackingParameter(parameter))
				.collect(Collectors.joining("&"));

		try {
			return normalize(new URI(
					uri.getScheme(),
					uri.getUserInfo(),
					uri.getHost(),
					uri.getPort(),
					uri.getPath(),
					filteredQuery.isBlank() ? null : filteredQuery,
					null
			).toString());
		} catch (URISyntaxException exception) {
			throw new IllegalArgumentException("Invalid URL: %s".formatted(url), exception);
		}
	}

	private static boolean isTrackingParameter(String parameter) {
		String name = parameter;
		int separatorIndex = parameter.indexOf('=');
		if (separatorIndex >= 0) {
			name = parameter.substring(0, separatorIndex);
		}
		String normalizedName = name.toLowerCase(Locale.ROOT);
		return normalizedName.startsWith("utm_") || TRACKING_PARAM_NAMES.contains(normalizedName);
	}

	private static String lowercase(String value) {
		return value == null ? null : value.toLowerCase(Locale.ROOT);
	}
}
