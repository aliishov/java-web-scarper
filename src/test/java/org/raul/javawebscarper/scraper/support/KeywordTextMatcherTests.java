package org.raul.javawebscarper.scraper.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeywordTextMatcherTests {

	@Test
	void matchesAzerbaijaniLettersWithAsciiKeyword() {
		assertThat(KeywordTextMatcher.findMatch(
				"Ali Məhkəmə yeni qərar açıqladı",
				"Ali Mehkeme"
		)).contains("Ali Mehkeme");
	}

	@Test
	void matchesAsciiTextWithAzerbaijaniKeyword() {
		assertThat(KeywordTextMatcher.findMatch(
				"Secki komissiyasi barede xeber yayildi",
				"Seçki komissiyası"
		)).contains("Seçki komissiyası");
	}

	@Test
	void treatsPunctuationAsWordSeparator() {
		assertThat(KeywordTextMatcher.findMatch(
				"Ali-Məhkəmə Plenumu toplandı",
				"ali mehkeme"
		)).contains("ali mehkeme");
	}

	@Test
	void doesNotMatchInsideLongerWords() {
		assertThat(KeywordTextMatcher.findMatch(
				"Bu yazı məhkəməsiz prosedur haqqındadır",
				"məhkəmə"
		)).isEmpty();
	}

	@Test
	void doesNotMatchUnrelatedText() {
		assertThat(KeywordTextMatcher.findMatch(
				"Zavrsila sam i uslikala je pre 5 min",
				"Ali Mehkeme"
		)).isEmpty();
	}
}
