package org.raul.javawebscarper.scraper.adapter.instagram;

public final class InstagramSelectors {

	public static final String BODY = "body";
	public static final String SEARCH_NAVIGATION = "a[href='/explore/'], a[href^='/explore/'], [role='link'][href^='/explore/']";
	public static final String SEARCH_INPUT = "input[placeholder='Search'], input[aria-label='Search'], input[type='search'], input[role='combobox']";
	public static final String GRID_POST_LINKS = "a[href^='/p/'], a[href^='/reel/'], a[href^='/tv/'], a[href*='instagram.com/p/'], a[href*='instagram.com/reel/'], a[href*='instagram.com/tv/']";
	public static final String POST_ROOT = "main article, div[role='dialog'] article, article";
	public static final String TIME = "time[datetime], time[title], time";
	public static final String PROFILE_LINKS = "header a[href], a[role='link'][href], a[href]";
	public static final String CAPTION_CANDIDATES = "h1[dir='auto'], [data-testid='post-comment-root'] span[dir='auto'], ul li span[dir='auto'], article span[dir='auto']";
	public static final String MEDIA = "img[src], video";

	private InstagramSelectors() {
	}
}
