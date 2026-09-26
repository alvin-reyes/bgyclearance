package com.thub.areyes1.clearance;

/**
 * What to show in the clearance list.
 *
 * @param text       free-text search over name and address, or an exact control number
 * @param type       only this type, or null for all
 * @param sort       sort column
 * @param descending sort direction
 * @param page       zero-based page number
 * @param size       page size
 */
public record ClearanceQuery(String text, ClearanceType type, ClearanceSort sort, boolean descending, int page,
		int size) {

	public static final int DEFAULT_PAGE_SIZE = 25;

	public ClearanceQuery {
		text = text == null ? "" : text.strip();
		sort = sort == null ? ClearanceSort.ISSUED : sort;
		page = Math.max(page, 0);
		size = size <= 0 ? DEFAULT_PAGE_SIZE : size;
	}

	public static ClearanceQuery all() {
		return new ClearanceQuery("", null, ClearanceSort.ISSUED, true, 0, DEFAULT_PAGE_SIZE);
	}
}
