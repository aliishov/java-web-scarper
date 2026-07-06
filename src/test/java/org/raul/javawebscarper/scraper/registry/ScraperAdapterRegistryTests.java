package org.raul.javawebscarper.scraper.registry;

import org.junit.jupiter.api.Test;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.scraper.adapter.ScraperAdapter;
import org.raul.javawebscarper.scraper.adapter.UnsupportedSourceScraperAdapter;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionContext;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ScraperAdapterRegistryTests {

	private final UnsupportedSourceScraperAdapter unsupportedAdapter = new UnsupportedSourceScraperAdapter();

	@Test
	void findsAdapterBySourceCode() {
		ScraperAdapter adapter = new TestScraperAdapter("baku-ws");
		ScraperAdapterRegistry registry = new ScraperAdapterRegistry(List.of(adapter, unsupportedAdapter), unsupportedAdapter);
		Source source = Source.builder().code("baku-ws").build();

		assertThat(registry.findAdapter(source)).contains(adapter);
		assertThat(registry.getAdapter(source)).isSameAs(adapter);
		assertThat(registry.getRegisteredSourceCodes()).containsExactly("baku-ws");
	}

	@Test
	void returnsUnsupportedAdapterWhenAdapterMissing() {
		ScraperAdapter adapter = new TestScraperAdapter("baku-ws");
		ScraperAdapterRegistry registry = new ScraperAdapterRegistry(List.of(adapter, unsupportedAdapter), unsupportedAdapter);
		Source source = Source.builder().code("unknown-source").build();

		assertThat(registry.findAdapter(source)).isEmpty();
		assertThat(registry.getAdapter(source)).isSameAs(unsupportedAdapter);
	}

	private record TestScraperAdapter(String sourceCode) implements ScraperAdapter {

		@Override
		public boolean supports(Source source) {
			return source != null && sourceCode.equals(source.getCode());
		}

		@Override
		public ScraperExecutionResult scrape(ScraperExecutionContext context) {
			return ScraperExecutionResult.empty();
		}
	}
}
