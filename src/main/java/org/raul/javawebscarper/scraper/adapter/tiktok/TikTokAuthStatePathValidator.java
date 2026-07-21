package org.raul.javawebscarper.scraper.adapter.tiktok;

import org.raul.javawebscarper.browser.BrowserEngineException;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public final class TikTokAuthStatePathValidator {

	private TikTokAuthStatePathValidator() {
	}

	public static Path normalize(String value) {
		if (value == null || value.isBlank()) {
			throw new BrowserEngineException("TikTok authentication state is not configured");
		}
		return Path.of(value.trim()).toAbsolutePath().normalize();
	}

	public static Path validateReadableStatePath(Path statePath) {
		Path normalizedPath = statePath.toAbsolutePath().normalize();
		validateNotForbiddenRepositoryPath(normalizedPath);
		if (!Files.isRegularFile(normalizedPath)) {
			throw new BrowserEngineException("TikTok authentication state file does not exist");
		}
		if (!Files.isReadable(normalizedPath)) {
			throw new BrowserEngineException("TikTok authentication state file is not readable");
		}
		try {
			if (Files.size(normalizedPath) <= 0) {
				throw new BrowserEngineException("TikTok authentication state file is empty");
			}
		} catch (BrowserEngineException exception) {
			throw exception;
		} catch (Exception exception) {
			throw new BrowserEngineException("TikTok authentication state file cannot be inspected", exception);
		}
		return normalizedPath;
	}

	public static Path validateOutputPath(Path statePath) {
		Path normalizedPath = statePath.toAbsolutePath().normalize();
		if (!normalizedPath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".json")) {
			throw new IllegalArgumentException("TIKTOK_AUTH_STATE_PATH must point to a JSON file.");
		}
		validateNotForbiddenRepositoryPath(normalizedPath);
		validateNotCurrentDirectoryFile(normalizedPath);
		if (Files.isDirectory(normalizedPath)) {
			throw new IllegalArgumentException("TIKTOK_AUTH_STATE_PATH must point to a file, not a directory.");
		}
		Path parent = normalizedPath.getParent();
		if (parent != null) {
			try {
				Files.createDirectories(parent);
			} catch (Exception exception) {
				throw new IllegalArgumentException("Failed to create parent directory for TikTok authentication state file.");
			}
		}
		return normalizedPath;
	}

	private static void validateNotForbiddenRepositoryPath(Path statePath) {
		Path workingDirectory = Path.of("").toAbsolutePath().normalize();
		if (!statePath.startsWith(workingDirectory)) {
			return;
		}
		Path relative = workingDirectory.relativize(statePath);
		if (relative.getNameCount() == 0) {
			throw forbiddenPath();
		}
		String firstSegment = relative.getName(0).toString().toLowerCase(Locale.ROOT);
		if (firstSegment.equals("src")
				|| firstSegment.equals(".git")
				|| firstSegment.equals("gradle")
				|| firstSegment.equals("docs")
				|| firstSegment.equals(".idea")) {
			throw forbiddenPath();
		}
		String fileName = statePath.getFileName().toString().toLowerCase(Locale.ROOT);
		if (fileName.equals("build.gradle")
				|| fileName.equals("settings.gradle")
				|| fileName.equals("application.yaml")
				|| fileName.equals("application.yml")
				|| fileName.equals("compose.yaml")
				|| fileName.equals("dockerfile")) {
			throw forbiddenPath();
		}
	}

	private static void validateNotCurrentDirectoryFile(Path statePath) {
		Path workingDirectory = Path.of("").toAbsolutePath().normalize();
		Path parent = statePath.getParent();
		if (parent != null && parent.equals(workingDirectory)) {
			throw new IllegalArgumentException("TIKTOK_AUTH_STATE_PATH must not point to a file in the current directory.");
		}
	}

	private static IllegalArgumentException forbiddenPath() {
		return new IllegalArgumentException("TIKTOK_AUTH_STATE_PATH must not point to source, config, docs, or Git metadata files.");
	}
}
