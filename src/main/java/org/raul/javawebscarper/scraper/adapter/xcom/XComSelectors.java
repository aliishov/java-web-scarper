package org.raul.javawebscarper.scraper.adapter.xcom;

public final class XComSelectors {

	public static final String TWEET_ARTICLE = "article[data-testid='tweet']";
	public static final String TWEET_TIME = "time[datetime]";
	public static final String STATUS_LINK = "a[href*='/status/']";
	public static final String USER_NAME = "[data-testid='User-Name']";
	public static final String TWEET_TEXT = "[data-testid='tweetText']";
	public static final String TWEET_PHOTO = "[data-testid='tweetPhoto'] img[src], [data-testid='tweetPhoto'] img[srcset]";
	public static final String VIDEO = "video";
	public static final String AVATAR = "img[src*='profile_images']";
	public static final String LOGIN_INPUT = "input[name='text'], input[autocomplete*='username'], input[type='password']";
	public static final String LOGIN_BUTTON = "a[href='/login'], a[href*='/i/flow/login'], [data-testid='loginButton']";
	public static final String LATEST_TAB = "a[href*='f=live'][role='tab'], a[href*='f=live']";
	public static final String PRIMARY_COLUMN = "[data-testid='primaryColumn']";

	private XComSelectors() {
	}
}
