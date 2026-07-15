package org.raul.javawebscarper.scraper.adapter.xcom;

public enum XSearchMode {
	LATEST("live"),
	TOP("top"),
	MEDIA("media");

	private final String queryValue;

	XSearchMode(String queryValue) {
		this.queryValue = queryValue;
	}

	public String queryValue() {
		return queryValue;
	}
}
