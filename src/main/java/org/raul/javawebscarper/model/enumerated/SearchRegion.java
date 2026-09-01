package org.raul.javawebscarper.model.enumerated;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public enum SearchRegion {
	GLOBAL("GLOBAL", "Global", "", "en-US", List.of(), Set.of()),
	AZ("AZ", "Azerbaijan", "Azərbaycan", "az-AZ",
			List.of("Azərbaycan", "Azerbaycan", "Azerbaijan", "Azerbaijani", "Azeri", "Bakı", "Baku"), Set.of("az")),
	TR("TR", "Turkey", "Türkiye", "tr-TR",
			List.of("Türkiye", "Turkey", "Türkiyə", "Turkiye", "Istanbul", "Ankara"), Set.of("tr")),
	RU("RU", "Russia", "Россия", "ru-RU",
			List.of("Россия", "Russia", "Rusiya", "Moscow", "Москва"), Set.of()),
	GE("GE", "Georgia", "Georgia", "ka-GE",
			List.of("Georgia", "Gürcüstan", "Tbilisi", "Tbilisi Georgia"), Set.of()),
	US("US", "United States", "United States", "en-US",
			List.of("United States", "USA", "America", "New York", "Washington"), Set.of()),
	GB("GB", "United Kingdom", "United Kingdom", "en-GB",
			List.of("United Kingdom", "Britain", "London"), Set.of()),
	DE("DE", "Germany", "Deutschland", "de-DE",
			List.of("Deutschland", "Germany", "Almaniya", "Berlin"), Set.of()),
	FR("FR", "France", "France", "fr-FR",
			List.of("France", "Fransa", "Paris"), Set.of()),
	IR("IR", "Iran", "Iran", "fa-IR",
			List.of("Iran", "İran", "Tehran", "Tehran Iran"), Set.of()),
	UA("UA", "Ukraine", "Ukraine", "uk-UA",
			List.of("Ukraine", "Ukrayna", "Kyiv", "Kiev"), Set.of()),
	AM("AM", "Armenia", "Armenia", "hy-AM",
			List.of("Armenia", "Ermənistan", "Yerevan", "İrəvan"), Set.of());

	private final String code;
	private final String displayName;
	private final String searchContext;
	private final String locale;
	private final List<String> aliases;
	private final Set<String> languageHints;

	SearchRegion(
			String code,
			String displayName,
			String searchContext,
			String locale,
			List<String> aliases,
			Set<String> languageHints
	) {
		this.code = code;
		this.displayName = displayName;
		this.searchContext = searchContext;
		this.locale = locale;
		this.aliases = List.copyOf(aliases);
		this.languageHints = Set.copyOf(languageHints);
	}

	public String code() {
		return code;
	}

	public String displayName() {
		return displayName;
	}

	public String searchContext() {
		return searchContext;
	}

	public String locale() {
		return locale;
	}

	public List<String> aliases() {
		return aliases;
	}

	public boolean supportsLanguageHint(String language) {
		return language != null && languageHints.contains(language.trim().toLowerCase(Locale.ROOT));
	}

	public static Optional<SearchRegion> find(String value) {
		if (value == null || value.isBlank()) {
			return Optional.empty();
		}
		String normalized = value.trim().toUpperCase(Locale.ROOT);
		for (SearchRegion region : values()) {
			if (region.name().equals(normalized) || region.code.equals(normalized)) {
				return Optional.of(region);
			}
		}
		return Optional.empty();
	}

	public static SearchRegion defaultRegion() {
		return AZ;
	}
}
