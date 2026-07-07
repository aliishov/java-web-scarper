package org.raul.javawebscarper.scraper.adapter.bakuws;

import org.junit.jupiter.api.Test;
import org.raul.javawebscarper.model.Source;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class BakuWsNewsScraperAdapterTests {

	private final BakuWsNewsScraperAdapter adapter = new BakuWsNewsScraperAdapter(
			null,
			new BakuWsDateParser(ZoneId.of("Asia/Baku"))
	);

	@Test
	void supportsBakuWsSource() {
		Source source = Source.builder()
				.code("BAKU_WS")
				.baseUrl("https://example.com")
				.build();

		assertThat(adapter.sourceCode()).isEqualTo("BAKU_WS");
		assertThat(adapter.supports(source)).isTrue();
	}

	@Test
	void supportsBakuWsBaseUrl() {
		Source source = Source.builder()
				.code("news")
				.baseUrl("https://baku.ws/")
				.build();

		assertThat(adapter.supports(source)).isTrue();
	}
}
