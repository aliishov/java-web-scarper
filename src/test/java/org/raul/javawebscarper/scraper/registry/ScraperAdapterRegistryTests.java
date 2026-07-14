package org.raul.javawebscarper.scraper.registry;

import org.junit.jupiter.api.Test;
import org.raul.javawebscarper.model.Source;
import org.raul.javawebscarper.scraper.adapter.ScraperAdapter;
import org.raul.javawebscarper.scraper.adapter.UnsupportedSourceScraperAdapter;
import org.raul.javawebscarper.scraper.adapter.caliberaz.CaliberAzDateParser;
import org.raul.javawebscarper.scraper.adapter.caliberaz.CaliberAzNewsScraperAdapter;
import org.raul.javawebscarper.scraper.adapter.haqqinaz.HaqqinAzDateParser;
import org.raul.javawebscarper.scraper.adapter.haqqinaz.HaqqinAzNewsScraperAdapter;
import org.raul.javawebscarper.scraper.adapter.lentaz.LentAzDateParser;
import org.raul.javawebscarper.scraper.adapter.lentaz.LentAzNewsScraperAdapter;
import org.raul.javawebscarper.scraper.adapter.lentaz.LentAzSearchPeriodResolver;
import org.raul.javawebscarper.scraper.adapter.qafqazinfoaz.QafqazInfoAzDateParser;
import org.raul.javawebscarper.scraper.adapter.qafqazinfoaz.QafqazInfoAzNewsScraperAdapter;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionContext;
import org.raul.javawebscarper.scraper.engine.ScraperExecutionResult;
import org.raul.javawebscarper.scraper.adapter.onenews.OneNewsAzDateParser;
import org.raul.javawebscarper.scraper.adapter.onenews.OneNewsAzScraperAdapter;

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

	@Test
	void findsOneNewsAzAdapterBySourceCode() {
		OneNewsAzScraperAdapter oneNewsAdapter = new OneNewsAzScraperAdapter(
				null,
				new OneNewsAzDateParser("Asia/Baku")
		);
		ScraperAdapterRegistry registry = new ScraperAdapterRegistry(List.of(oneNewsAdapter, unsupportedAdapter), unsupportedAdapter);
		Source source = Source.builder().code("ONE_NEWS_AZ").build();

		assertThat(registry.findAdapter(source)).contains(oneNewsAdapter);
		assertThat(registry.getAdapter(source)).isSameAs(oneNewsAdapter);
		assertThat(registry.getRegisteredSourceCodes()).contains("ONE_NEWS_AZ");
	}

	@Test
	void findsHaqqinAzAdapterBySourceCode() {
		HaqqinAzNewsScraperAdapter haqqinAdapter = new HaqqinAzNewsScraperAdapter(
				null,
				new HaqqinAzDateParser("Asia/Baku")
		);
		ScraperAdapterRegistry registry = new ScraperAdapterRegistry(List.of(haqqinAdapter, unsupportedAdapter), unsupportedAdapter);
		Source source = Source.builder().code("HAQQIN_AZ").build();

		assertThat(registry.findAdapter(source)).contains(haqqinAdapter);
		assertThat(registry.getAdapter(source)).isSameAs(haqqinAdapter);
		assertThat(registry.getRegisteredSourceCodes()).contains("HAQQIN_AZ");
	}

	@Test
	void findsCaliberAzAdapterBySourceCode() {
		CaliberAzNewsScraperAdapter caliberAdapter = new CaliberAzNewsScraperAdapter(
				null,
				new CaliberAzDateParser("Asia/Baku")
		);
		ScraperAdapterRegistry registry = new ScraperAdapterRegistry(List.of(caliberAdapter, unsupportedAdapter), unsupportedAdapter);
		Source source = Source.builder().code("CALIBER_AZ").build();

		assertThat(registry.findAdapter(source)).contains(caliberAdapter);
		assertThat(registry.getAdapter(source)).isSameAs(caliberAdapter);
		assertThat(registry.getRegisteredSourceCodes()).contains("CALIBER_AZ");
	}

	@Test
	void findsQafqazInfoAzAdapterBySourceCode() {
		QafqazInfoAzNewsScraperAdapter qafqazInfoAdapter = new QafqazInfoAzNewsScraperAdapter(
				null,
				new QafqazInfoAzDateParser("Asia/Baku")
		);
		ScraperAdapterRegistry registry = new ScraperAdapterRegistry(List.of(qafqazInfoAdapter, unsupportedAdapter), unsupportedAdapter);
		Source source = Source.builder().code("QAFQAZINFO_AZ").build();

		assertThat(registry.findAdapter(source)).contains(qafqazInfoAdapter);
		assertThat(registry.getAdapter(source)).isSameAs(qafqazInfoAdapter);
		assertThat(registry.getRegisteredSourceCodes()).contains("QAFQAZINFO_AZ");
	}

	@Test
	void findsLentAzAdapterBySourceCode() {
		LentAzNewsScraperAdapter lentAdapter = new LentAzNewsScraperAdapter(
				null,
				new LentAzDateParser("Asia/Baku"),
				new LentAzSearchPeriodResolver("Asia/Baku")
		);
		ScraperAdapterRegistry registry = new ScraperAdapterRegistry(List.of(lentAdapter, unsupportedAdapter), unsupportedAdapter);
		Source source = Source.builder().code("LENT_AZ").build();

		assertThat(registry.findAdapter(source)).contains(lentAdapter);
		assertThat(registry.getAdapter(source)).isSameAs(lentAdapter);
		assertThat(registry.getRegisteredSourceCodes()).contains("LENT_AZ");
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
