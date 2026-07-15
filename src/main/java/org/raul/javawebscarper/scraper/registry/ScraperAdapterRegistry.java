package org.raul.javawebscarper.scraper.registry;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.scraper.adapter.ScraperAdapter;
import org.raul.javawebscarper.scraper.adapter.UnsupportedSourceScraperAdapter;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ScraperAdapterRegistry {

	private final List<ScraperAdapter> adapters;
	private final UnsupportedSourceScraperAdapter unsupportedSourceScraperAdapter;

	public ScraperAdapterRegistry(
			List<ScraperAdapter> adapters,
			UnsupportedSourceScraperAdapter unsupportedSourceScraperAdapter
	) {
		this.unsupportedSourceScraperAdapter = unsupportedSourceScraperAdapter;
		this.adapters = validateUniqueAdapters(adapters.stream()
				.filter(adapter -> !(adapter instanceof UnsupportedSourceScraperAdapter))
				.toList());
	}

	@PostConstruct
	void logRegisteredAdapters() {
		log.info("Registered scraper adapters: {}", getRegisteredSourceCodes());
	}

	public ScraperAdapter getAdapter(Source source) {
		return findAdapter(source).orElse(unsupportedSourceScraperAdapter);
	}

	public Optional<ScraperAdapter> findAdapter(Source source) {
		return adapters.stream()
				.filter(adapter -> adapter.supports(source))
				.findFirst();
	}

	public List<String> getRegisteredSourceCodes() {
		return adapters.stream()
				.map(ScraperAdapter::sourceCode)
				.sorted()
				.toList();
	}

	private List<ScraperAdapter> validateUniqueAdapters(List<ScraperAdapter> adapters) {
		Map<String, List<String>> sourceCodesByNormalizedCode = new LinkedHashMap<>();
		for (ScraperAdapter adapter : adapters) {
			String sourceCode = adapter.sourceCode();
			if (!StringUtils.hasText(sourceCode)) {
				throw new IllegalStateException(
						"Scraper adapter source code must not be blank: " + adapter.getClass().getName()
				);
			}
			sourceCodesByNormalizedCode
					.computeIfAbsent(sourceCode.trim().toUpperCase(Locale.ROOT), ignored -> new ArrayList<>())
					.add(sourceCode);
		}

		List<String> duplicateCodes = sourceCodesByNormalizedCode.entrySet().stream()
				.filter(entry -> entry.getValue().size() > 1)
				.map(Map.Entry::getKey)
				.collect(Collectors.toList());
		if (!duplicateCodes.isEmpty()) {
			throw new IllegalStateException("Duplicate scraper adapter source codes: " + duplicateCodes);
		}
		return List.copyOf(adapters);
	}
}
