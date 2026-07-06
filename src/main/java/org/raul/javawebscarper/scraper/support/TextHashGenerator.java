package org.raul.javawebscarper.scraper.support;

import org.raul.javawebscarper.model.Source;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class TextHashGenerator {

	private static final String SEPARATOR = "\u001F";

	private TextHashGenerator() {
	}

	public static String sha256(String text) {
		String safeText = text == null ? "" : text;
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(safeText.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hash);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 algorithm is not available", exception);
		}
	}

	public static String sha256Post(Source source, String postUrl, String text) {
		String sourceCode = source == null || source.getCode() == null ? "" : source.getCode();
		String normalizedPostUrl = UrlNormalizer.normalize(postUrl);
		String payload = sourceCode + SEPARATOR + nullToEmpty(normalizedPostUrl) + SEPARATOR + nullToEmpty(text);
		return sha256(payload);
	}

	private static String nullToEmpty(String value) {
		return value == null ? "" : value;
	}
}
