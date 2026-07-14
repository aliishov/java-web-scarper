package org.raul.javawebscarper.scraper.adapter.bakuws;

public final class BakuWsSelectors {

	public static final String[] SEARCH_TOGGLE_SELECTORS = {
			"a:has(svg use[href*='#search-normal'])",
			"a:has(svg use[xlink\\:href*='#search-normal'])",
			"button:has(svg use[href*='#search-normal'])",
			"button:has(svg use[xlink\\:href*='#search-normal'])",
			".custom-navbar-search-toggle",
			"svg.svg-icon.normal"
	};
	public static final String SEARCH_FORM = "form.custom-navbar-search-block";
	public static final String SEARCH_FORM_OPEN = "form.custom-navbar-search-block.open";
	public static final String SEARCH_INPUT = "form.custom-navbar-search-block input[name='query']";
	public static final String SEARCH_INPUT_FALLBACK = "input[name='query'][placeholder='Acar sozu'], input[name='query'][placeholder='Açar sözü']";
	public static final String RESULT_CARD = ".post-item";
	public static final String RESULT_CARD_FALLBACK = ".post-item:has(.post-item-title a)";
	public static final String RESULT_CARD_DATA_URL = ".post-item-content[data-url]";
	public static final String RESULT_CARD_TITLE_LINK = ".post-item-title a[href]";
	public static final String RESULT_CARD_IMAGE_LINK = ".post-item-img a[href]";
	public static final String RESULT_CARD_TITLE = ".post-item-title a";
	public static final String RESULT_CARD_THUMBNAIL = ".post-item-img img[src], .post-item-img img[data-src]";
	public static final String RESULT_CARD_TIME = ".post-item-date-time";
	public static final String RESULT_CARD_DAY = ".post-item-date-day";
	public static final String SEARCH_AD_BLOCK = ".cat-left-bnr";
	public static final String ARTICLE_MAIN_IMAGE = String.join(", ",
			".post-detail-top .post-detail-img > img[src]",
			".post-detail-top .post-detail-img > img[data-src]",
			".post-detail-top .post-detail-img > img[srcset]",
			".post-detail-top .post-detail-img > img[data-srcset]",
			".post-detail-img > img[src]",
			".post-detail-img > img[data-src]",
			".post-detail-img > img[srcset]",
			".post-detail-img > img[data-srcset]"
	);
	public static final String ARTICLE_DATE_DAY = ".post-detail-top .post-detail-img .post-date-day, .post-detail-img .post-date-day";
	public static final String ARTICLE_DATE_MONTH = ".post-detail-top .post-detail-img .post-date-month, .post-detail-img .post-date-month";
	public static final String ARTICLE_DATE_YEAR = ".post-detail-top .post-detail-img .post-date-year, .post-detail-img .post-date-year";
	public static final String ARTICLE_DATE_TIME = ".post-detail-top .post-detail-img .post-date-time, .post-detail-img .post-date-time";
	public static final String ARTICLE_TEXT_CONTENT = ".post-detail-content.resize-area";
	public static final String ARTICLE_DATA_PAGE = ".post-detail[data-page], [data-page]";

	private BakuWsSelectors() {
	}
}
