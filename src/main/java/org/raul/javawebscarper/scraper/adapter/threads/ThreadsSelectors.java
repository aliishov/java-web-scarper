package org.raul.javawebscarper.scraper.adapter.threads;

public final class ThreadsSelectors {

	public static final String BODY = "body";
	public static final String POST_LINKS = "a[href*='/post/']";
	public static final String POST_ROOT = "article, div[role='article']";
	public static final String PROFILE_LINKS = "a[href^='/@'], a[href*='threads.com/@'], a[href*='threads.net/@']";
	public static final String POST_TEXT = "[data-pressable-container='true'] div[dir='auto'], div[dir='auto']";
	public static final String TIME = "time[datetime], time";
	public static final String MEDIA = "img[src], video[src], video[poster]";

	private ThreadsSelectors() {
	}
}
