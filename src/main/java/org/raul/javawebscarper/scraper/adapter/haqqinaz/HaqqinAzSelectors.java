package org.raul.javawebscarper.scraper.adapter.haqqinaz;

public final class HaqqinAzSelectors {

	public static final String SEARCH_OPEN_BUTTON = ".page-header__open-search-form-button";
	public static final String SEARCH_INPUT = "form.page-header__search-form input[name='q']";
	public static final String SEARCH_SUBMIT_BUTTON = "form.page-header__search-form .search-form__submit-button";

	public static final String SEARCH_RESULT_CARD = "a.news-list__item.news-item[href], a.news-item[href]";
	public static final String SEARCH_CARD_TITLE = ".news-item__title";
	public static final String SEARCH_CARD_THUMBNAIL = ".news-item__image img[src], .news-item__image img[data-src]";
	public static final String SEARCH_CARD_DATE = ".news-item-pubinfo__date";
	public static final String LOAD_MORE_BUTTON = "section.load-more .load-more__button, .load-more__button[data-next-page]";

	public static final String ARTICLE_ROOT = "article.article";
	public static final String ARTICLE_TITLE = ".article-headline__name";
	public static final String ARTICLE_TITLE_FALLBACK = "h1";
	public static final String ARTICLE_DATE = ".article__date";
	public static final String ARTICLE_CONTENT = ".article__content";
	public static final String ARTICLE_TEXT_PARAGRAPH = ".article__content .article-block .block-text p";
	public static final String ARTICLE_TEXT_PARAGRAPH_FALLBACK = ".article__content .block-text p";
	public static final String ARTICLE_MEDIA = ".article__content .block-photo img[src], .article__content .block-photo img[data-src]";

	private HaqqinAzSelectors() {
	}
}
