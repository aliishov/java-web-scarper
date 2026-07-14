package org.raul.javawebscarper.scraper.adapter.qafqazinfoaz;

public final class QafqazInfoAzSelectors {

	public static final String SEARCH_FORM = "form#frmSearch";
	public static final String SEARCH_INPUT = "form#frmSearch input[name='keyword']";
	public static final String SEARCH_INPUT_FALLBACK = "form[action*='/news/search'] input[name='keyword']";
	public static final String SEARCH_INPUT_PLACEHOLDER_FALLBACK = "input[name='keyword'][placeholder='Axtar']";
	public static final String SEARCH_RESULT_LINK = "a[href*='/news/detail/']";
	public static final String SEARCH_RESULT_LINK_SCOPED = ".col-lg-4 a[href*='/news/detail/']";
	public static final String SEARCH_RESULT_TITLE = "h4.hemcinin";
	public static final String SEARCH_RESULT_THUMBNAIL = "img.img-responsive[src]";
	public static final String PAGINATION_ROOT = ".pager ul.yiiPager";
	public static final String PAGINATION_PAGE_LINK = "ul.yiiPager li.page a";
	public static final String PAGINATION_NEXT_LINK = "ul.yiiPager li.next:not(.hidden) a";
	public static final String ARTICLE_ROOT = ".panel-body";
	public static final String ARTICLE_TITLE = "h1";
	public static final String ARTICLE_TIME = ".news-time time";
	public static final String ARTICLE_TEXT = ".news_text";
	public static final String ARTICLE_PARAGRAPH = ".news_text p";
	public static final String ARTICLE_MAIN_IMAGE = "img.img-responsive[src]";

	private QafqazInfoAzSelectors() {
	}
}
