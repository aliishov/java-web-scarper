package org.raul.javawebscarper.dto.response.scrapejob;

import org.raul.javawebscarper.model.enumerated.SearchRegion;

public record SearchRegionResponseDTO(
		SearchRegion code,
		String name,
		String locale
) {
	public static SearchRegionResponseDTO from(SearchRegion region) {
		return new SearchRegionResponseDTO(region, region.displayName(), region.locale());
	}
}
