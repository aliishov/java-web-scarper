package org.raul.javawebscarper.scraper.adapter.lentaz;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class LentAzSearchPeriodResolverTests {

	private static final ZoneId BAKU = ZoneId.of("Asia/Baku");
	private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-07-14T12:00:00+04:00");

	private final LentAzSearchPeriodResolver resolver = new LentAzSearchPeriodResolver(
			Clock.fixed(Instant.parse("2026-07-14T08:00:00Z"), BAKU)
	);

	@Test
	void resolvesRecentRangesToLastWeek() {
		assertThat(resolveFromDaysAgo(1)).isEqualTo(1);
		assertThat(resolveFromDaysAgo(6)).isEqualTo(1);
		assertThat(resolveFromDaysAgo(7)).isEqualTo(1);
	}

	@Test
	void resolvesMonthRangesToLastMonth() {
		assertThat(resolveFromDaysAgo(8)).isEqualTo(2);
		assertThat(resolveFromDaysAgo(30)).isEqualTo(2);
	}

	@Test
	void resolvesSixMonthRangesToLastSixMonths() {
		assertThat(resolveFromMonthsAgo(3)).isEqualTo(3);
		assertThat(resolveFromMonthsAgo(6)).isEqualTo(3);
	}

	@Test
	void resolvesOlderOrMissingRangesToAllTime() {
		assertThat(resolveFromMonthsAgo(8)).isEqualTo(4);
		assertThat(resolver.resolve(null, NOW)).isEqualTo(4);
		assertThat(resolver.resolve(NOW.minusDays(1), null)).isEqualTo(4);
		assertThat(resolver.resolve(NOW, NOW.minusDays(1))).isEqualTo(4);
	}

	private int resolveFromDaysAgo(long days) {
		return resolver.resolve(NOW.minusDays(days), NOW);
	}

	private int resolveFromMonthsAgo(long months) {
		return resolver.resolve(NOW.minusMonths(months), NOW);
	}
}
