package org.raul.javawebscarper.scraper.adapter.facebook;

public final class FacebookSelectors {

	public static final String AUTHENTICATED_NAVIGATION = String.join(", ",
			"a[aria-label='Home']",
			"a[aria-label='Facebook']",
			"div[role='navigation']",
			"a[href='/home.php']",
			"a[href='/friends/']",
			"[role='banner'] [role='search']"
	);
	public static final String LOGIN_INPUT = "#email, input[name='email'], input[type='email']";
	public static final String PASSWORD_INPUT = "#pass, input[name='pass'], input[type='password']";
	public static final String SEARCH_INPUT = String.join(", ",
			"input[type='search']",
			"input[name='q']",
			"[role='search'] input",
			"input[placeholder*='Search' i]",
			"input[aria-label*='Search' i]",
			"input[placeholder*='Поиск' i]",
			"input[aria-label*='Поиск' i]",
			"input[placeholder*='Axtar' i]",
			"input[aria-label*='Axtar' i]",
			"[role='search'] [contenteditable='true']",
			"[role='combobox'][contenteditable='true']",
			"[role='textbox'][contenteditable='true']"
	);
	public static final String POST_CONTAINER = String.join(", ",
			"article",
			"[role='article']",
			"[data-pagelet*='FeedUnit']",
			"[role='feed'] > div"
	);
	public static final String MESSAGE_NODE = String.join(", ",
			"[data-ad-rendering-role='story_message']",
			"[data-ad-comet-preview='message']",
			"[data-ad-preview='message']",
			"[data-testid='post_message']"
	);
	public static final String PROFILE_NAME = "[data-ad-rendering-role='profile_name']";
	public static final String POST_LINK = "a[href*='/posts/'], a[href*='/permalink.php'], a[href*='story_fbid='], "
			+ "a[href*='fbid='], a[href*='/groups/'][href*='/posts/'], a[href*='/share/p/'], a[href*='/watch/'], a[href*='/reel/']";
	// Facebook often renders the publication time as the text of the post permalink
	// (for example "6 h") without a datetime, title, or aria-label attribute.
	public static final String DATE_CANDIDATE = "time[datetime], [data-utime], abbr[title], meta[property='article:published_time'][content], meta[itemprop='datePublished'][content], a[aria-label][href], a[title][href], "
			+ POST_LINK;
	public static final String MEDIA = "img[src], img[data-src], video, video source[src]";

	private FacebookSelectors() {
	}
}
