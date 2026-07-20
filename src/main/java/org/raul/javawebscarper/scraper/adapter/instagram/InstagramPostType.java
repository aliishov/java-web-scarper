package org.raul.javawebscarper.scraper.adapter.instagram;

public enum InstagramPostType {
	POST("p"),
	REEL("reel"),
	TV("tv");

	private final String pathSegment;

	InstagramPostType(String pathSegment) {
		this.pathSegment = pathSegment;
	}

	public String pathSegment() {
		return pathSegment;
	}
}
