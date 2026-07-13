package org.raul.javawebscarper.scraper.adapter.onenews;

final class OneNewsAzSelectors {

	static final String SEARCH_INPUT = "form[action='/az/axtarish/'] input[name='q']";
	static final String SEARCH_INPUT_FALLBACK = "#searchInput";
	static final String SEARCH_RESULT = ".gsc-webResult.gsc-result, .gsc-result";
	static final String SEARCH_RESULT_LINK = ".gs-title a[href], .gsc-thumbnail-inside a[href]";
	static final String SEARCH_RESULT_SNIPPET = ".gs-snippet, .gsc-table-result .gs-snippet";
	static final String SEARCH_RESULTS_CONTAINER = ".gsc-control-cse, .gsc-results, .gsc-no-results-result";
	static final String SORT_SELECTED = ".gsc-selected-option-container";
	static final String SORT_SELECTED_TEXT = ".gsc-selected-option, .gsc-selected-option-container";
	static final String SORT_MENU_ITEM = ".gsc-option-menu-item";
	static final String SORT_MENU_ITEM_OPTION = ".gsc-option";
	static final String CURSOR = ".gsc-cursor";
	static final String CURSOR_PAGE = ".gsc-cursor-page";
	static final String CURSOR_CURRENT_PAGE = ".gsc-cursor-current-page";

	static final String ARTICLE_ROOT = "article.mainArticle";
	static final String ARTICLE_TITLE = "h1.title";
	static final String ARTICLE_DATE = ".date, time, [class*=date]";
	static final String ARTICLE_AUTHOR = "span.author a, .author a";
	static final String ARTICLE_CONTENT = ".content";
	static final String ARTICLE_PARAGRAPH = ".content > p, .content p";
	static final String ARTICLE_MAIN_IMAGE = ".content .thumb > img[src], .content .thumb > img[data-src], "
			+ ".content .thumb > img[srcset], .content .thumb > img[data-srcset]";

	private OneNewsAzSelectors() {
	}
}
