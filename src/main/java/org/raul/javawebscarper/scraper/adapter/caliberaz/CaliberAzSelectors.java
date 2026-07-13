package org.raul.javawebscarper.scraper.adapter.caliberaz;

public final class CaliberAzSelectors {

	public static final String SEARCH_OPEN_BUTTON = ".header_button.search_button";
	public static final String SEARCH_OPEN_BUTTON_FALLBACK = ".search_button";
	public static final String SEARCH_INPUT = ".input_block input[type='text']";
	public static final String SEARCH_INPUT_FALLBACK = "input[placeholder*=Что], input[placeholder*=ищите]";

	public static final String SEARCH_RESULT_CARD = ".float_block";
	public static final String SEARCH_RESULT_LINK = "a[href*='/post/']";
	public static final String SEARCH_RESULT_TITLE = ".float_block_title";
	public static final String SEARCH_RESULT_DATE = ".float_block_time";
	public static final String SEARCH_RESULT_THUMBNAIL = ".float_block_image";

	public static final String ARTICLE_ROOT = ".post";
	public static final String ARTICLE_TITLE = ".post_title";
	public static final String ARTICLE_TITLE_FALLBACK = "h1";
	public static final String ARTICLE_DATE = ".post_time";
	public static final String ARTICLE_COVER = ".post_cover";
	public static final String ARTICLE_BODY = ".post_body";
	public static final String ARTICLE_PARAGRAPH = ".post_body p";
	public static final String ARTICLE_BODY_IMAGE = ".post_body img[src], .post_body img[data-src]";

	private CaliberAzSelectors() {
	}
}
