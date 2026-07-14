package org.raul.javawebscarper.scraper.adapter.lentaz;

public final class LentAzSelectors {

	public static final String SEARCH_OPEN_BUTTON = "button#search_btn";
	public static final String SEARCH_OPEN_BUTTON_FALLBACK = "#search_btn, button[aria-label='Search']";
	public static final String SEARCH_FORM = "form[action*='/axtaris-neticesi']";
	public static final String SEARCH_INPUT = "form[action*='/axtaris-neticesi'] input[name='search']";
	public static final String SEARCH_INPUT_FALLBACK = "input#search, input[placeholder*='Axtarılacaq sözü']";
	public static final String SEARCH_TYPE_SELECT = "form[action*='/axtaris-neticesi'] select[name='type']";
	public static final String SEARCH_SUBMIT_BUTTON = "form[action*='/axtaris-neticesi'] button.btn_search";
	public static final String SEARCH_SUBMIT_BUTTON_FALLBACK = "button[type='submit'].btn_search";
	public static final String SEARCH_RESULT_CARD = ".item[id^='news_'][data-id]";
	public static final String SEARCH_RESULT_CARD_FALLBACK = ".item:not(.rek_item)";
	public static final String SEARCH_RESULT_LINK_OVERLAY = ".item_head a.overlay[href]";
	public static final String SEARCH_RESULT_LINK_TITLE = ".item_foot a.title[href]";
	public static final String SEARCH_RESULT_LINK_ANY = "a[href*='/xeber/']";
	public static final String SEARCH_RESULT_TITLE = ".item_foot a.title h3";
	public static final String SEARCH_RESULT_THUMBNAIL = ".item_head img[src]";
	public static final String SEARCH_RESULT_DATE_SPAN = ".item_head a.overlay span";
	public static final String AD_CARD = ".item.rek_item, .rek_item";
	public static final String PAGINATION_ROOT = "ul.pagination";
	public static final String PAGINATION_CURRENT = "ul.pagination li.active_li";
	public static final String PAGINATION_NEXT_LINK = "ul.pagination a[rel='next']";
	public static final String PAGINATION_NUMBERED_LINK = "ul.pagination li a[href*='page=']";
	public static final String ARTICLE_ROOT = "#content.left_column";
	public static final String ARTICLE_TITLE = "h1.news_title";
	public static final String ARTICLE_MAIN_IMAGE = ".news_img > img[src]";
	public static final String ARTICLE_MAIN_IMAGE_FALLBACK = ".news_img img[src]";
	public static final String ARTICLE_DATE = ".news_img .overlay span";
	public static final String ARTICLE_TEXT_CANDIDATE = ".news_content, .news_text, .article_content, .news_body, .text";

	private LentAzSelectors() {
	}
}
