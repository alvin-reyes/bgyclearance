package com.thub.areyes1.web;

import org.springframework.web.util.UriComponentsBuilder;

import com.thub.areyes1.clearance.ClearanceQuery;
import com.thub.areyes1.clearance.ClearanceSort;

/** Builds the links on the clearance list (sort headers, paging) while keeping the current filters. */
public record ListView(ClearanceQuery query) {

	/** Link that sorts by this column, flipping direction if it is already the sort column. */
	public String sortLink(String column) {
		ClearanceSort sort = ClearanceSort.from(column);
		boolean descending = sort == query.sort() ? !query.descending() : sort != ClearanceSort.NAME;
		return link(sort, descending, 0);
	}

	/** "ascending" / "descending" for the active sort column, "none" otherwise (for aria-sort). */
	public String ariaSort(String column) {
		if (ClearanceSort.from(column) != query.sort()) {
			return "none";
		}
		return query.descending() ? "descending" : "ascending";
	}

	public String pageLink(int page) {
		return link(query.sort(), query.descending(), page);
	}

	public String typeParam() {
		return query.type() == null ? "" : query.type().name();
	}

	private String link(ClearanceSort sort, boolean descending, int page) {
		UriComponentsBuilder b = UriComponentsBuilder.fromPath("/clearances");
		if (!query.text().isEmpty()) {
			b.queryParam("q", query.text());
		}
		if (query.type() != null) {
			b.queryParam("type", query.type().name());
		}
		if (sort != ClearanceSort.ISSUED || !descending) {
			b.queryParam("sort", sort.name().toLowerCase());
			b.queryParam("dir", descending ? "desc" : "asc");
		}
		if (page > 0) {
			b.queryParam("page", page + 1);
		}
		return b.encode().build().toUriString();
	}
}
