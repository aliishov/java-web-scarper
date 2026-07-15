package org.raul.javawebscarper.browser;

import java.nio.file.Path;
import java.util.Map;

public record BrowserSessionOptions(
		Path storageStatePath,
		String locale,
		String timezoneId,
		String userAgent,
		Map<String, String> extraHttpHeaders
) {

	public static BrowserSessionOptions defaults() {
		return new BrowserSessionOptions(null, null, null, null, Map.of());
	}

	public static BrowserSessionOptions withStorageState(Path storageStatePath) {
		return new BrowserSessionOptions(storageStatePath, null, null, null, Map.of());
	}
}
