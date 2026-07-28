package org.raul.javawebscarper.scraper.adapter.threads;

public final class ThreadsSelectors {

	public static final String BODY = "body";
	public static final String POST_LINKS = "a[href*='/post/']";
	public static final String POST_ROOT = "main article, article, main [data-pressable-container]";
	public static final String PROFILE_LINKS = "a[href^='/@'], a[href*='threads.com/@']";
	public static final String TIME = "time[datetime], time[title], time";
	public static final String TEXT = "[data-pressable-container] div[dir='auto'], div[dir='auto'], span[dir='auto']";
	public static final String MEDIA = "img[src], video[src], video[poster], video source[src]";

	private ThreadsSelectors() {
	}
}
