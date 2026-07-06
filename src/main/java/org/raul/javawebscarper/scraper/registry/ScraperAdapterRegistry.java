package org.raul.javawebscarper.scraper.registry;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.scraper.adapter.ScraperAdapter;
import org.raul.javawebscarper.scraper.adapter.UnsupportedSourceScraperAdapter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

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
		this.adapters = adapters.stream()
				.filter(adapter -> !(adapter instanceof UnsupportedSourceScraperAdapter))
				.toList();
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
}
