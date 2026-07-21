package org.raul.javawebscarper.scraper.adapter.tiktok;

public final class TikTokSelectors {

	public static final String BODY = "body";
	public static final String SEARCH_INPUT = "[data-e2e='search-user-input'], input[type='search'], [role='search'] input, input[placeholder*='Search' i], input[placeholder*='Поиск' i]";
	public static final String SEARCH_BUTTON = "[data-e2e='search-button'], button[aria-label*='Search' i], [role='button'][aria-label*='Search' i]";
	public static final String SEARCH_SUBMIT = "[data-e2e='search-box-button'], form button[type='submit'], button[type='submit']";
	public static final String VIDEO_TAB = "[role='tab'], a[href*='/search/video'], button, [role='button']";
	public static final String VIDEO_LINKS = "a[href*='/video/']";
	public static final String POST_ROOT = "main article, article, [data-e2e*='browse-video'], [data-e2e*='video-detail'], [role='dialog'], main";
	public static final String CAPTION_CANDIDATES = "[data-e2e*='browse-video-desc'], [data-e2e*='video-desc'], [data-e2e*='search-card-video-caption'], h1, article [dir='auto'], main [dir='auto']";
	public static final String PROFILE_LINKS = "a[href^='/@'], a[href*='tiktok.com/@']";
	public static final String TIME = "time[datetime], time, [datetime], [data-e2e*='date'], [data-e2e*='time']";
	public static final String MEDIA = "video, video source[src], img[src], img[data-src], meta[property='og:image'], meta[property='og:video'], meta[property='og:video:url'], meta[name='twitter:player:stream']";

	private TikTokSelectors() {
	}
}
