package org.raul.javawebscarper.scraper.support;

import org.junit.jupiter.api.Test;
import org.raul.javawebscarper.model.enumerated.SearchRegion;

import static org.assertj.core.api.Assertions.assertThat;

class SocialSearchRegionContextTests {

	@Test
	void appendsDefaultAzerbaijanContext() {
		assertThat(SocialSearchRegionContext.apply("Ali Mehkeme", SearchRegion.AZ))
				.isEqualTo("Ali Mehkeme Azərbaycan");
	}

	@Test
	void doesNotDuplicateEquivalentContext() {
		assertThat(SocialSearchRegionContext.apply("Ali Məhkəmə Azerbaycan", SearchRegion.AZ))
				.isEqualTo("Ali Məhkəmə Azerbaycan");
	}

	@Test
	void returnsOriginalKeywordForGlobalRegion() {
		assertThat(SocialSearchRegionContext.apply("Ali Mehkeme", SearchRegion.GLOBAL))
				.isEqualTo("Ali Mehkeme");
	}

	@Test
	void appendsSelectedCountryContext() {
		assertThat(SocialSearchRegionContext.apply("Ali Mehkeme", SearchRegion.TR))
				.isEqualTo("Ali Mehkeme Türkiye");
	}

	@Test
	void matchesAzerbaijanAliasesAndAzerbaijaniLanguage() {
		assertThat(SocialSearchRegionContext.matches("Ali Məhkəmə Bakıda qərar verdi", "en", SearchRegion.AZ))
				.isTrue();
		assertThat(SocialSearchRegionContext.matches("Ali Məhkəmə qərar verdi", "az", SearchRegion.AZ))
				.isTrue();
	}

	@Test
	void rejectsUnrelatedForeignTextForSelectedRegion() {
		assertThat(SocialSearchRegionContext.matches(
				"Supreme court published a decision in Canada",
				"en",
				SearchRegion.AZ
		)).isFalse();
	}
}
